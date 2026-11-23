#version 330

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};
layout(std140) uniform EggWeatherPost {
    float NearPlane;
    float FarPlane;
    float WetAmount;
    float ReflectAmount;
    float PuddleAmount;
    float GlintAmount;
    float Frost;
    float MistAmount;
    vec4 MistTint4;
    float MistBase;
    float MistHeight;
    vec2 MistWind;
    float TerrainValid;
    float FadeEnd;
    float CoverRadius;
    float RainAmount;
    float time;
    vec4 CameraPosition4;
    float SkyPitch;
    float SkyYaw;
    float TanHalfFov;
    float Aspect;
    vec4 SkyZenith4;
    vec4 SkyHorizon4;
    vec4 SkyNadir4;
    vec4 SunDir4;
    vec4 SunColor4;
    float SunSize;
    float SunGlare;
    float CloudCover;
    float CloudDark;
    float CloudSpeed;
    float CloudScale;
    vec4 CloudLit4;
    vec4 CloudShadow4;
    float StarAmount;
    float BandAmount;
    float AuroraAmount;
    float MeteorAmount;
    float HazeAmount;
    float FrostAmount;
    vec4 HazeColor4;
    vec4 GlowDir4;
    float GlowFocus;
    float Flash;
    vec4 FlashColor4;

    float WorldSaturation;
    float FogEnvironmentalStart;
    float FogEnvironmentalEnd;
    float FogRenderDistanceStart;
    float FogRenderDistanceEnd;
    vec4 WorldGrade;
    vec4 FogColor;
};

#define MistTint MistTint4.xyz
#define CameraPosition CameraPosition4.xyz
#define SkyZenith SkyZenith4.xyz
#define SkyHorizon SkyHorizon4.xyz
#define SkyNadir SkyNadir4.xyz
#define SunDir SunDir4.xyz
#define SunColor SunColor4.xyz
#define CloudLit CloudLit4.xyz
#define CloudShadow CloudShadow4.xyz
#define HazeColor HazeColor4.xyz
#define GlowDir GlowDir4.xyz
#define FlashColor FlashColor4.xyz

#moj_import <egg:weather_sky_color.glsl>

uniform sampler2D InSampler;

uniform sampler2D TerrainDepthSampler;
uniform sampler2D GlossSampler;
uniform sampler2D DepthSampler;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

const int STEPS = 22;
const int REFINE = 4;

float camPitchCos;
float camPitchSin;
float camYawCos;
float camYawSin;

vec3 toWorld(vec3 v) {
    float pc = camPitchCos;
    float ps = camPitchSin;
    vec3 p = vec3(v.x, v.y * pc + v.z * ps, v.z * pc - v.y * ps);
    float yc = camYawCos;
    float ys = camYawSin;
    return vec3(p.x * yc + p.z * ys, p.y, p.z * yc - p.x * ys);
}

vec3 toView(vec3 v) {
    float yc = camYawCos;
    float ys = camYawSin;
    vec3 p = vec3(v.x * yc - v.z * ys, v.y, v.z * yc + v.x * ys);
    float pc = camPitchCos;
    float ps = camPitchSin;
    return vec3(p.x, p.y * pc - p.z * ps, p.z * pc + p.y * ps);
}

float linearDepth(float depth) {
    float ndc = depth * 2.0 - 1.0;
    return 2.0 * NearPlane * FarPlane / (FarPlane + NearPlane - ndc * (FarPlane - NearPlane));
}

float zenithFogValue(float vertexDistance, float fogStart, float fogEnd) {
    if (vertexDistance <= fogStart) {
        return 0.0;
    } else if (vertexDistance >= fogEnd) {
        return 1.0;
    }
    return (vertexDistance - fogStart) / (fogEnd - fogStart);
}

vec3 zenithGrade(vec3 color) {
    float amount = max(WorldSaturation, 0.0);
    float luma = dot(color, vec3(0.299, 0.587, 0.114));
    float boost = max(amount - 1.0, 0.0);
    vec3 saturated = mix(vec3(luma), color, amount);
    saturated = clamp(saturated * (1.0 + boost * 0.35), 0.0, 1.0);
    return clamp(saturated * WorldGrade.rgb * WorldGrade.a, 0.0, 1.0);
}

vec3 gradeScene(vec3 color, vec2 uv, float depth) {
    if (depth >= 0.999999 || (WorldSaturation == 1.0 && WorldGrade == vec4(1.0))) {
        return color;
    }
    vec2 ndc = uv * 2.0 - 1.0;
    vec3 view = vec3(ndc.x * Aspect * TanHalfFov, ndc.y * TanHalfFov, -1.0) * linearDepth(depth);

    vec3 offset = toWorld(view);
    float spherical = length(offset);
    float cylindrical = max(length(offset.xz), abs(offset.y));
    float fog = max(zenithFogValue(spherical, FogEnvironmentalStart, FogEnvironmentalEnd),
            zenithFogValue(cylindrical, FogRenderDistanceStart, FogRenderDistanceEnd)) * FogColor.a;
    if (fog >= 0.999) {
        return color;
    }
    vec3 lit = clamp((color - FogColor.rgb * fog) / (1.0 - fog), 0.0, 1.0);
    return mix(zenithGrade(lit), FogColor.rgb, fog);
}

vec3 sceneAt(vec2 uv) {
    return gradeScene(texture(InSampler, uv).rgb, uv, texture(DepthSampler, uv).r);
}

vec2 project(vec3 view) {
    vec2 ndc = vec2(view.x / (-view.z * Aspect * TanHalfFov), view.y / (-view.z * TanHalfFov));
    return ndc * 0.5 + 0.5;
}

vec3 viewAt(vec2 uv, float depth) {
    vec2 ndc = uv * 2.0 - 1.0;
    return vec3(ndc.x * Aspect * TanHalfFov, ndc.y * TanHalfFov, -1.0) * linearDepth(depth);
}

float fresnel(float cosTheta) {
    float base = 1.0 - clamp(cosTheta, 0.0, 1.0);
    float sq = base * base;
    return 0.02 + 0.98 * sq * sq * base;
}

vec2 ripple(vec2 world, float time) {

    vec2 slope = vec2(0.0);
    float scale = 1.9;
    vec2 cell = floor(world * scale);
    for (int ox = -1; ox <= 1; ox++) {
        for (int oz = -1; oz <= 1; oz++) {
            vec2 c = cell + vec2(float(ox), float(oz));
            float seed = hash21(c);

            float phase = fract(time * 0.9 + seed);
            vec2 center = (c + 0.35 + 0.3 * vec2(seed, fract(seed * 7.3))) / scale;
            vec2 delta = world - center;
            float dist = length(delta);
            float wave = exp(-dist * 7.0) * exp(-phase * 3.5) * sin((dist - phase * 0.55) * 46.0);
            slope += normalize(delta + 1.0e-5) * wave;
        }
    }

    return slope * 0.13;
}

const vec3 MIST_A0 = vec3(0.0, 1.7, 2.3);
const vec3 MIST_B0 = vec3(0.0);
const vec3 MIST_WEIGHTS = vec3(1.0, 0.50, 0.25);

vec3 mistArgA(vec3 q) {
    return vec3(q.x * 0.30 + q.z * 0.17, q.x * 0.71 - q.z * 0.53, (q.x + q.z) * 1.31);
}

vec3 mistArgB(vec3 q) {
    return vec3(q.z * 0.23 - q.y * 0.41, q.y * 0.63 + q.x * 0.29, q.y * 1.11 - q.z * 0.87);
}

vec3 applyMist(vec3 color, vec3 rayEnd) {
    if (MistAmount <= 0.002) {
        return color;
    }
    vec3 delta = rayEnd - CameraPosition;
    float distance = length(delta);
    if (distance < 0.05) {
        return color;
    }
    float reach = min(distance, 110.0);
    vec3 direction = delta / distance;

    float jitter = fract(sin(dot(texCoord, vec2(12.9898, 78.233))) * 43758.5453);
    float stepLength = reach / 16.0;
    float sum = 0.0;

    float sumCap = 3.0 / (stepLength * MistAmount * 0.085);

    float invHeight = 1.0 / max(MistHeight, 0.25);
    vec3 windShift = vec3(MistWind.x * time * 0.55, 0.0, MistWind.y * time * 0.55);
    vec3 firstPos = CameraPosition + direction * (jitter * stepLength);
    vec3 q0 = (firstPos - windShift) * vec3(1.0, 1.7, 1.0) * 0.34;
    vec3 dq = direction * stepLength * vec3(1.0, 1.7, 1.0) * 0.34;
    vec3 angleA = mistArgA(q0) + MIST_A0;
    vec3 angleB = mistArgB(q0) + MIST_B0;
    vec3 sinA = sin(angleA);
    vec3 cosA = cos(angleA);
    vec3 sinB = sin(angleB);
    vec3 cosB = cos(angleB);
    vec3 stepA = mistArgA(dq);
    vec3 stepB = mistArgB(dq);
    vec3 stepSinA = sin(stepA);
    vec3 stepCosA = cos(stepA);
    vec3 stepSinB = sin(stepB);
    vec3 stepCosB = cos(stepB);

    for (int i = 0; i < 16; i++) {
        float sampleY = firstPos.y + direction.y * (float(i) * stepLength);
        float h = (sampleY - MistBase) * invHeight;
        if ((direction.y > 0.0 && h > 3.2) || (direction.y < 0.0 && h < -1.25)) {
            break;
        }

        float band = exp(-max(h, 0.0) * 2.1) * smoothstep(-1.2, 0.05, h);
        if (band >= 0.002) {
            float wisp = clamp(dot(sinA * sinB, MIST_WEIGHTS) * 0.35 + 0.5, 0.0, 1.0);

            wisp = smoothstep(0.34, 0.92, wisp) * 0.85 + 0.15;
            sum += band * wisp;
        }
        if (sum >= sumCap) {
            break;
        }

        vec3 nextSinA = sinA * stepCosA + cosA * stepSinA;
        cosA = cosA * stepCosA - sinA * stepSinA;
        sinA = nextSinA;
        vec3 nextSinB = sinB * stepCosB + cosB * stepSinB;
        cosB = cosB * stepCosB - sinB * stepSinB;
        sinB = nextSinB;
    }

    float optical = sum * stepLength * MistAmount * 0.085;
    return mix(color, MistTint, clamp(1.0 - exp(-optical), 0.0, 0.94));
}

void main() {
    camPitchCos = cos(SkyPitch);
    camPitchSin = sin(SkyPitch);
    camYawCos = cos(SkyYaw);
    camYawSin = sin(SkyYaw);

    vec4 source = texture(InSampler, texCoord);
    float depth = texture(DepthSampler, texCoord).r;

    source.rgb = gradeScene(source.rgb, texCoord, depth);
    if (depth >= 0.999999) {

        vec2 ndcSky = texCoord * 2.0 - 1.0;
        vec3 skyRay = normalize(toWorld(vec3(ndcSky.x * Aspect * TanHalfFov, ndcSky.y * TanHalfFov, -1.0)));
        fragColor = vec4(clamp(applyMist(source.rgb, CameraPosition + skyRay * 110.0), 0.0, 1.0),
                source.a);
        return;
    }

    vec4 gloss = texture(GlossSampler, texCoord);
    vec3 color = source.rgb * (1.0 - gloss.a) + gloss.rgb;

    float viewZ = linearDepth(depth);

    vec3 posV = viewAt(texCoord, depth);
    vec3 mistHit = CameraPosition + toWorld(posV);

    if (WetAmount <= 0.01 || viewZ > FadeEnd || (Frost <= 0.5 && ReflectAmount <= 0.01)) {
        fragColor = vec4(clamp(applyMist(color, mistHit), 0.0, 1.0), source.a);
        return;
    }

    vec3 viewDir = normalize(posV);

    float depthLeft = texture(DepthSampler, texCoord - vec2(oneTexel.x, 0.0)).r;
    float depthRight = texture(DepthSampler, texCoord + vec2(oneTexel.x, 0.0)).r;
    float depthDown = texture(DepthSampler, texCoord - vec2(0.0, oneTexel.y)).r;
    float depthUp = texture(DepthSampler, texCoord + vec2(0.0, oneTexel.y)).r;

    float zLeft = linearDepth(depthLeft);
    float zRight = linearDepth(depthRight);
    float zDown = linearDepth(depthDown);
    float zUp = linearDepth(depthUp);

    vec3 alongX = abs(zRight - viewZ) < abs(viewZ - zLeft)
            ? viewAt(texCoord + vec2(oneTexel.x, 0.0), depthRight) - posV
            : posV - viewAt(texCoord - vec2(oneTexel.x, 0.0), depthLeft);
    vec3 alongY = abs(zUp - viewZ) < abs(viewZ - zDown)
            ? viewAt(texCoord + vec2(0.0, oneTexel.y), depthUp) - posV
            : posV - viewAt(texCoord - vec2(0.0, oneTexel.y), depthDown);

    vec3 normalV = normalize(cross(alongX, alongY));
    normalV *= -sign(dot(normalV, viewDir));

    vec3 worldNormal = toWorld(normalV);
    vec3 worldPos = mistHit;

    if (TerrainValid > 0.5) {
        float terrainRaw = texture(TerrainDepthSampler, texCoord).r;
        if (terrainRaw < 0.999999) {
            float terrainViewZ = linearDepth(terrainRaw);
            if (terrainViewZ - viewZ > 0.04 + viewZ * 0.012) {
                fragColor = vec4(clamp(applyMist(color, mistHit), 0.0, 1.0), source.a);
                return;
            }
        }
    }

    float normalBlur = smoothstep(18.0, 55.0, viewZ);
    float upness = smoothstep(mix(0.86, 0.30, normalBlur), mix(0.99, 1.30, normalBlur),
            worldNormal.y);
    if (upness <= 0.002) {
        fragColor = vec4(clamp(applyMist(color, mistHit), 0.0, 1.0), source.a);
        return;
    }

    float curvature = abs(zLeft + zRight - 2.0 * viewZ) + abs(zDown + zUp - 2.0 * viewZ);
    float edgeMask = 1.0 - smoothstep(viewZ * 0.006, viewZ * 0.030, curvature);

    if (Frost > 0.5) {

        float coverFade = CoverRadius > 0.5
                ? smoothstep(CoverRadius * 0.70, CoverRadius, viewZ)
                : 1.0;
        float lay = smoothstep(mix(0.22, -0.35, normalBlur), mix(0.72, 1.25, normalBlur),
                worldNormal.y) * edgeMask * coverFade;

        float yFract = fract(worldPos.y);
        float gridDist = min(min(yFract, 1.0 - yFract), abs(yFract - 0.5));
        float jitter = clamp(fwidth(worldPos.y), 0.0, 0.5);
        float onBlock = 1.0 - smoothstep(0.02 + jitter * 2.0, 0.07 + jitter * 3.0, gridDist);
        lay *= mix(onBlock, 1.0, smoothstep(22.0, 48.0, viewZ));
        if (lay <= 0.002) {
            fragColor = vec4(clamp(applyMist(color, mistHit), 0.0, 1.0), source.a);
            return;
        }

        vec3 sunDir = normalize(SunDir);

        vec3 viewW = toWorld(viewDir);
        float snowNear = 1.0 - smoothstep(FadeEnd * 0.6, FadeEnd, viewZ);

        float retro = pow(max(dot(-viewW, sunDir), 0.0), 2.0);
        color += SunColor * retro * lay * WetAmount * snowNear * 0.13;

        float grazing = pow(1.0 - abs(dot(viewW, worldNormal)), 4.0);
        vec3 skyAbove = weatherSkyReflect(vec3(0.0, 1.0, 0.0));
        color += skyAbove * grazing * lay * WetAmount * snowNear * 0.16;

        vec3 cell = floor(worldPos * 34.0);
        float grain = hash21(cell.xz + cell.y * 31.7);

        float twinkle = 0.5 + 0.5 * sin(time * 1.6 + grain * 6.2831);
        float spark = smoothstep(0.93, 1.0, grain) * smoothstep(0.62, 1.0, twinkle);

        float mirror = max(dot(reflect(viewW, worldNormal), sunDir), 0.0);
        spark *= 0.35 + 0.65 * pow(mirror, 6.0);

        spark *= 1.0 - smoothstep(14.0, 34.0, viewZ);

        if (spark > 0.001) {

            vec3 skyLight = skyAbove;
            color += (skyLight * 0.55 + SunColor * 0.45) * spark * lay * WetAmount * 0.40;
        }
        fragColor = vec4(clamp(applyMist(color, mistHit), 0.0, 1.0), source.a);
        return;
    }

    float patchNoise = valueNoise2(worldPos.xz * 0.34) * 0.60 + valueNoise2(worldPos.xz * 0.87) * 0.40;
    float coverage = mix(1.0, smoothstep(0.55, 0.72, patchNoise), PuddleAmount);
    float puddle = coverage * upness * edgeMask;
    puddle *= 1.0 - smoothstep(FadeEnd * 0.5, FadeEnd, viewZ);
    puddle *= WetAmount;
    if (puddle <= 0.01) {
        fragColor = vec4(clamp(applyMist(color, mistHit), 0.0, 1.0), source.a);
        return;
    }

    float roughness = mix(0.24, 0.08, PuddleAmount);

    vec2 slope = RainAmount > 0.001
            ? ripple(worldPos.xz, time) * RainAmount * (1.0 + roughness)
            : vec2(0.0);
    vec3 rippled = normalize(toView(normalize(worldNormal + vec3(slope.x, 0.0, slope.y))));

    float wave = min(length(slope) * 6.0, 1.0);

    vec3 rayDir = reflect(viewDir, rippled);

    float spread = mix(0.72, 0.90, PuddleAmount);
    float level = mix(0.40, 0.88, PuddleAmount);
    float fres = ((1.0 - spread) + spread * fresnel(dot(-viewDir, rippled))) * level;

    float budget = fres * ReflectAmount * puddle * (1.0 - roughness * 0.55) * (1.0 - 0.40 * Flash);
    if (budget <= 0.012) {
        fragColor = vec4(clamp(applyMist(color, mistHit), 0.0, 1.0), source.a);
        return;
    }

    vec3 reflection = vec3(0.0);
    float hit = 0.0;
    vec3 position = posV + rippled * 0.05;
    float step = max(0.12, viewZ * 0.035);

    for (int i = 0; i < STEPS; i++) {
        position += rayDir * step;
        step *= 1.28;
        if (position.z > -NearPlane) {
            break;
        }
        vec2 uv = project(position);
        if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
            break;
        }
        float sampled = texture(DepthSampler, uv).r;

        if (sampled >= 0.999999) {
            break;
        }
        float sceneDepth = linearDepth(sampled);
        float rayDepth = -position.z;
        float diff = rayDepth - sceneDepth;

        if (diff > 0.0 && diff < step * 2.5 + 0.6) {

            vec3 back = position;
            vec3 forward = position - rayDir * step / 1.28;
            for (int r = 0; r < REFINE; r++) {
                vec3 mid = (back + forward) * 0.5;
                vec2 midUv = project(mid);
                float midScene = linearDepth(texture(DepthSampler, midUv).r);
                if (-mid.z > midScene) {
                    back = mid;
                } else {
                    forward = mid;
                }
            }
            uv = project(back);

            float spread = roughness * 0.010;
            reflection = sceneAt(uv) * 0.40
                    + sceneAt(uv + vec2(spread, 0.0)) * 0.15
                    + sceneAt(uv - vec2(spread, 0.0)) * 0.15
                    + sceneAt(uv + vec2(0.0, spread)) * 0.15
                    + sceneAt(uv - vec2(0.0, spread)) * 0.15;

            vec2 border = smoothstep(vec2(0.0), vec2(0.12), uv)
                    * (1.0 - smoothstep(vec2(0.88), vec2(1.0), uv));
            hit = border.x * border.y;
            break;
        }
    }

    float specularity = mix(0.30, 0.85, PuddleAmount);
    vec3 skyDir = normalize(mix(toWorld(rippled), toWorld(rayDir), specularity));
    vec3 skyColor = weatherSkyReflect(skyDir);
    reflection = mix(skyColor, reflection, hit);

    float sunAlign = max(dot(skyDir, normalize(SunDir)), 0.0);
    reflection += SunColor * pow(sunAlign, 40.0) * GlintAmount * (1.0 - hit * 0.7);

    float mirror = clamp(fres * ReflectAmount * puddle * (0.75 + 0.35 * hit)
            * (1.0 - roughness * 0.55) * (1.0 - 0.40 * Flash), 0.0, 0.94);
    mirror *= mix(0.5, 1.0, hit);
    color = mix(color, reflection, mirror);

    color += skyColor * wave * puddle * RainAmount * 0.10 * (1.0 - 0.40 * Flash);

    fragColor = vec4(clamp(applyMist(color, mistHit), 0.0, 1.0), source.a);
}
