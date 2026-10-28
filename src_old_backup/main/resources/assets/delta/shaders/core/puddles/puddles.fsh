#version 150

in vec2 TexCoord;
out vec4 fragColor;

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

uniform vec2 Resolution;
uniform float Time;
uniform float Scale;

uniform vec3 WaveColor;
uniform float Density;

uniform float UseRain;
uniform float DropFrequency;
uniform float UseWaves;
uniform float UseWaveTheme;

uniform mat4 ProjMat;
uniform mat4 ViewMat;
uniform mat4 InvProjMat;
uniform mat4 InvViewMat;
uniform vec3 CameraPos;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1.0, 0.0)), f.x),
               mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p *= 2.0;
        a *= 0.5;
    }
    return v;
}

float rainRipples(vec2 worldXZ, float t) {
    vec2 p = worldXZ * (DropFrequency * 0.6);
    vec2 id = floor(p);

    float h1 = hash12(id);
    float h2 = hash12(id + vec2(13.37, 71.19));

    float speed = 1.2;
    float timeCycle = t * speed + h1 * 6.28;
    float cellTimeId = floor(timeCycle);
    float progress = fract(timeCycle);

    float spawnHash = hash12(id + vec2(cellTimeId, cellTimeId * 0.5));
    if (spawnHash > 0.35) return 0.0;

    vec2 dropCenter = (vec2(h1, h2) - 0.5) * 0.6;
    vec2 g = fract(p) - 0.5 - dropCenter;

    float dist = length(g);
    float radius = progress * 0.35;

    float timeFade = smoothstep(0.0, 0.15, progress) * (1.0 - smoothstep(0.3, 1.0, progress));
    float waveDist = dist - radius;
    float circle = sin(waveDist * 25.0);
    float ringMask = exp(-abs(waveDist) * 10.0);
    float spatialFade = smoothstep(0.45, 0.1, dist);

    return circle * ringMask * timeFade * spatialFade;
}

vec3 getViewPos(vec2 uv, float depth) {
    vec4 ndc = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 viewPos = InvProjMat * ndc;
    return viewPos.xyz / viewPos.w;
}

vec3 getWorldPosFromUV(vec2 uv) {
    float d = texture(Sampler1, uv).r;
    vec4 ndc = vec4(uv * 2.0 - 1.0, d * 2.0 - 1.0, 1.0);
    vec4 viewPos = InvProjMat * ndc;
    vec3 vPos = viewPos.xyz / viewPos.w;
    vec4 worldRelPos = InvViewMat * vec4(vPos, 0.0);
    return CameraPos + worldRelPos.xyz;
}

vec4 traceSSR(vec3 rayStartView, vec3 rayDirView) {
    int steps = 24;
    float maxDist = 20.0;
    float stepSize = maxDist / float(steps);

    vec3 curr = rayStartView + rayDirView * 0.15;

    for (int i = 0; i < steps; i++) {
        curr += rayDirView * stepSize;

        vec4 clip = ProjMat * vec4(curr, 1.0);
        if (clip.w <= 0.0) continue;

        vec3 ndc = clip.xyz / clip.w;
        vec2 uv = ndc.xy * 0.5 + 0.5;

        if (uv.x < 0.001 || uv.x > 0.999 || uv.y < 0.001 || uv.y > 0.999) break;

        float sampledDepth = texture(Sampler1, uv).r;
        if (sampledDepth >= 0.9999) {
            return vec4(texture(Sampler0, uv).rgb, 1.0);
        }

        vec3 sampledViewPos = getViewPos(uv, sampledDepth);
        float depthDiff = curr.z - sampledViewPos.z;

        if (depthDiff < 0.0 && depthDiff > -1.2) {
            float edgeFade = smoothstep(0.0, 0.08, uv.x) * (1.0 - smoothstep(0.92, 1.0, uv.x)) *
                             smoothstep(0.0, 0.08, uv.y) * (1.0 - smoothstep(0.92, 1.0, uv.y));
            return vec4(texture(Sampler0, uv).rgb, edgeFade);
        }
    }
    return vec4(0.0);
}

void main() {
    vec4 sceneColor = texture(Sampler0, TexCoord);
    float depth = texture(Sampler1, TexCoord).r;

    if (depth >= 0.9999) {
        fragColor = sceneColor;
        return;
    }

    vec3 viewPos = getViewPos(TexCoord, depth);
    vec4 worldRelPos = InvViewMat * vec4(viewPos, 0.0);
    vec3 worldPos = CameraPos + worldRelPos.xyz;

    vec2 texel = 1.0 / Resolution;
    vec3 pR = getWorldPosFromUV(TexCoord + vec2(texel.x, 0.0));
    vec3 pL = getWorldPosFromUV(TexCoord - vec2(texel.x, 0.0));
    vec3 pU = getWorldPosFromUV(TexCoord + vec2(0.0, texel.y));
    vec3 pD = getWorldPosFromUV(TexCoord - vec2(0.0, texel.y));

    vec3 dX1 = pR - worldPos;
    vec3 dX2 = worldPos - pL;
    vec3 dX = dot(dX1, dX1) < dot(dX2, dX2) ? dX1 : dX2;

    vec3 dY1 = pU - worldPos;
    vec3 dY2 = worldPos - pD;
    vec3 dY = dot(dY1, dY1) < dot(dY2, dY2) ? dY1 : dY2;

    vec3 worldNormal = normalize(cross(dX, dY));
    if (worldNormal.y < 0.0) worldNormal = -worldNormal;

    float slopeMask = smoothstep(0.55, 0.85, worldNormal.y);
    if (slopeMask <= 0.001 || length(viewPos) < 0.05) {
        fragColor = sceneColor;
        return;
    }

    vec2 puddleUV = worldPos.xz * (0.05 / max(Scale, 0.1));
    float n = fbm(puddleUV);

    float threshold = mix(0.75, 0.2, clamp(Density, 0.0, 1.0));
    float puddleMask = smoothstep(threshold, threshold + 0.12, n) * slopeMask;

    if (puddleMask <= 0.001) {
        fragColor = sceneColor;
        return;
    }

    vec3 waterNormalWorld = vec3(0.0, 1.0, 0.0);
    float waveHeight = 0.0;

    if (UseRain > 0.5) {
        float drops = rainRipples(worldPos.xz, Time);
        waterNormalWorld.xz += vec2(drops * 0.05);
        waveHeight += abs(drops);
    }

    if (UseWaves > 0.5) {
        float wave1 = sin(worldPos.x * 4.0 + Time * 2.8) * 0.015;
        float wave2 = cos(worldPos.z * 4.0 + Time * 2.2) * 0.015;
        waterNormalWorld.xz += vec2(wave1, wave2);
        waveHeight += abs(wave1 + wave2);
    }

    waterNormalWorld = normalize(waterNormalWorld);

    vec3 waterNormalView = normalize((ViewMat * vec4(waterNormalWorld, 0.0)).xyz);
    vec3 wetGroundColor = sceneColor.rgb * 0.35;

    vec3 viewDirView = normalize(viewPos);
    vec3 reflDirView = reflect(viewDirView, waterNormalView);

    vec4 ssr = traceSSR(viewPos, reflDirView);

    float lookDownFactor = smoothstep(-0.95, -0.6, viewDirView.y);
    vec2 fallbackUV = clamp(TexCoord + waterNormalWorld.xz * (0.04 * lookDownFactor), vec2(0.001), vec2(0.999));
    vec3 fallbackRefl = texture(Sampler0, fallbackUV).rgb;

    vec3 finalReflection = mix(fallbackRefl, ssr.rgb, ssr.a);

    if (UseWaves > 0.5 && UseWaveTheme > 0.5) {
        finalReflection = mix(finalReflection, WaveColor * 1.4, clamp(waveHeight * 1.5, 0.0, 0.4));
    }

    float NdotV = max(dot(-viewDirView, waterNormalView), 0.0);
    float fresnel = clamp(pow(1.0 - NdotV, 2.5), 0.25, 0.85);

    vec3 puddleColor = mix(wetGroundColor, finalReflection, fresnel);
    vec3 finalColor = mix(sceneColor.rgb, puddleColor, puddleMask * 0.85);

    fragColor = vec4(finalColor, sceneColor.a);
}
