#version 330

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};
layout(std140) uniform EggWeatherPost {
    float NearPlane;
    float FarPlane;
    float WetAmount;
    float DarkAmount;
    vec4 WetTint4;
    float ReflectAmount;
    float PuddleAmount;
    float GlintAmount;
    float Frost;
    float TerrainValid;
    float SnowGeometry;
    float FadeEnd;
    float CoverRadius;
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
};

#define WetTint WetTint4.xyz
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

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

float camPitchCos;
float camPitchSin;
float camYawCos;
float camYawSin;

vec3 toWorld(vec3 viewVector) {
    vec3 pitched = vec3(
            viewVector.x,
            viewVector.y * camPitchCos + viewVector.z * camPitchSin,
            viewVector.z * camPitchCos - viewVector.y * camPitchSin
    );
    return vec3(
            pitched.x * camYawCos + pitched.z * camYawSin,
            pitched.y,
            pitched.z * camYawCos - pitched.x * camYawSin
    );
}

float linearDepth(float depth) {
    float ndc = depth * 2.0 - 1.0;
    return 2.0 * NearPlane * FarPlane / (FarPlane + NearPlane - ndc * (FarPlane - NearPlane));
}

vec3 viewOffsetAt(vec2 uv, float depth) {
    vec2 ndc = uv * 2.0 - 1.0;
    vec3 ray = vec3(ndc.x * Aspect * TanHalfFov, ndc.y * TanHalfFov, -1.0);
    return toWorld(ray * linearDepth(depth));
}

float luminance(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

const float SNOW_DUNE_DEPTH = 0.11;

const float SNOW_BUMP = 3.4;

float snowDune(vec2 world) {

    return (valueNoise2(world * 0.09) * 0.85 + valueNoise2(world * 0.31) * 0.15) * SNOW_DUNE_DEPTH;
}

float snowMicro(vec2 world) {
    return valueNoise2(world * 2.9 + 47.3) * 0.55 + valueNoise2(world * 6.1 + 11.9) * 0.45;
}

float fresnel(float cosTheta) {
    float base = 1.0 - clamp(cosTheta, 0.0, 1.0);
    float square = base * base;
    return 0.02 + 0.98 * square * square * base;
}

void main() {
    float depth = texture(InSampler, texCoord).r;

    if (depth >= 0.999999 || WetAmount <= 0.001) {
        fragColor = vec4(0.0);
        return;
    }

    camPitchCos = cos(SkyPitch);
    camPitchSin = sin(SkyPitch);
    camYawCos = cos(SkyYaw);
    camYawSin = sin(SkyYaw);

    vec2 ndc = texCoord * 2.0 - 1.0;
    vec3 ray = vec3(ndc.x * Aspect * TanHalfFov, ndc.y * TanHalfFov, -1.0);
    float viewZ = linearDepth(depth);

    vec3 offset = toWorld(ray * viewZ);
    vec3 viewDir = normalize(offset);
    vec3 worldPos = CameraPosition + offset;

    float depthLeft = texture(InSampler, texCoord - vec2(oneTexel.x, 0.0)).r;
    float depthRight = texture(InSampler, texCoord + vec2(oneTexel.x, 0.0)).r;
    float depthDown = texture(InSampler, texCoord - vec2(0.0, oneTexel.y)).r;
    float depthUp = texture(InSampler, texCoord + vec2(0.0, oneTexel.y)).r;

    float zLeft = linearDepth(depthLeft);
    float zRight = linearDepth(depthRight);
    float zDown = linearDepth(depthDown);
    float zUp = linearDepth(depthUp);

    vec3 alongX = abs(zRight - viewZ) < abs(viewZ - zLeft)
            ? viewOffsetAt(texCoord + vec2(oneTexel.x, 0.0), depthRight) - offset
            : offset - viewOffsetAt(texCoord - vec2(oneTexel.x, 0.0), depthLeft);
    vec3 alongY = abs(zUp - viewZ) < abs(viewZ - zDown)
            ? viewOffsetAt(texCoord + vec2(0.0, oneTexel.y), depthUp) - offset
            : offset - viewOffsetAt(texCoord - vec2(0.0, oneTexel.y), depthDown);

    vec3 normal = cross(alongX, alongY);
    float normalLength = length(normal);
    if (normalLength < 1.0e-9) {
        fragColor = vec4(0.0);
        return;
    }
    normal /= normalLength;
    normal *= -sign(dot(normal, viewDir));

    float curvature = abs(zLeft + zRight - 2.0 * viewZ) + abs(zDown + zUp - 2.0 * viewZ);
    float edgeMask = 1.0 - smoothstep(viewZ * 0.006, viewZ * 0.030, curvature);

    float flatness = 1.0 - smoothstep(0.35, 1.10, length(alongX) + length(alongY));

    float distanceMask = 1.0 - smoothstep(FadeEnd * 0.5, FadeEnd, viewZ);

    float amount = WetAmount * edgeMask * flatness * distanceMask * (1.0 - 0.40 * Flash);
    if (amount <= 0.002) {
        fragColor = vec4(0.0);
        return;
    }

    float upness = clamp(normal.y, 0.0, 1.0);

    if (Frost > 0.5) {

        float coverFade = CoverRadius > 0.5
                ? smoothstep(CoverRadius * 0.70, CoverRadius, viewZ)
                : 1.0;
        float onTerrain = 1.0;
        if (TerrainValid > 0.5) {
            float terrainRaw = texture(TerrainDepthSampler, texCoord).r;
            float terrainViewZ = terrainRaw >= 0.999999 ? FarPlane : linearDepth(terrainRaw);
            float gap = terrainViewZ - viewZ;
            onTerrain = 1.0 - smoothstep(0.04 + viewZ * 0.012, 0.12 + viewZ * 0.030, gap);
        }

        float blur = smoothstep(18.0, 55.0, viewZ);
        float layLow = mix(0.22, -0.35, blur);
        float layHigh = mix(0.72, 1.25, blur);
        float upFactor = smoothstep(layLow, layHigh, upness);

        float lay = upFactor * coverFade * onTerrain;

        float rime = 0.34 * (1.0 - upFactor) * coverFade * onTerrain;

        float yFract = fract(worldPos.y);
        float gridDist = min(min(yFract, 1.0 - yFract), abs(yFract - 0.5));
        float jitter = clamp(fwidth(worldPos.y), 0.0, 0.5);
        float slack = 0.02 + jitter * 2.0;
        float onBlock = 1.0 - smoothstep(slack, slack + 0.05 + jitter, gridDist);
        onBlock = mix(onBlock, 1.0, smoothstep(22.0, 48.0, viewZ));
        lay *= onBlock;
        rime *= onBlock;

        if (lay + rime <= 0.0) {
            fragColor = vec4(0.0);
            return;
        }

        float snowDetail = 1.0 - smoothstep(26.0, 58.0, viewZ);
        float slopeX = 0.0;
        float slopeZ = 0.0;

        if (snowDetail > 0.0) {
            float d = 0.5;
            float heightLeft = snowDune(worldPos.xz - vec2(d, 0.0));
            float heightRight = snowDune(worldPos.xz + vec2(d, 0.0));
            float heightDown = snowDune(worldPos.xz - vec2(0.0, d));
            float heightUp = snowDune(worldPos.xz + vec2(0.0, d));
            float bump = SNOW_BUMP * snowDetail;
            slopeX = (heightLeft - heightRight) * bump;
            slopeZ = (heightDown - heightUp) * bump;

            float nearby = (1.0 - smoothstep(6.0, 18.0, viewZ)) * snowDetail;
            if (nearby > 0.001) {
                float md = 0.16;

                float microBump = 0.38 * nearby;
                slopeX += (snowMicro(worldPos.xz - vec2(md, 0.0)) - snowMicro(worldPos.xz + vec2(md, 0.0))) * microBump;
                slopeZ += (snowMicro(worldPos.xz - vec2(0.0, md)) - snowMicro(worldPos.xz + vec2(0.0, md))) * microBump;
            }
        }

        vec3 duneNormal = normalize(vec3(slopeX, 1.0, slopeZ));

        duneNormal = normalize(mix(normal, duneNormal, lay));

        vec3 albedo = vec3(0.955, 0.965, 1.00);
        float wrap = clamp((dot(duneNormal, normalize(SunDir)) + 0.35) / 1.35, 0.0, 1.0);
        vec3 skyAmbient = SkyZenith * 0.55 + SkyHorizon * 0.45;

        vec3 ambient = skyAmbient * 0.80 + 0.10;
        float skyView = 0.62 + 0.38 * max(0.0, duneNormal.y);

        float occlusion = 0.0;
        if (snowDetail > 0.002) {
            float aoRadius = 1.4;
            vec2 uvRadius = aoRadius / (max(viewZ, 1.0) * TanHalfFov) * vec2(0.5 / Aspect, 0.5);
            float spin = hash21(worldPos.xz) * 6.2831;
            float cs = cos(spin), sn = sin(spin);
            for (int i = 0; i < 8; i++) {
                float ang = float(i) * 0.7854;
                vec2 dir = vec2(cos(ang), sin(ang));
                dir = vec2(dir.x * cs - dir.y * sn, dir.x * sn + dir.y * cs);
                float reach = 0.35 + 0.65 * fract(float(i) * 0.618 + spin);
                vec2 uv2 = texCoord + dir * uvRadius * reach;
                float d2 = texture(InSampler, uv2).r;
                if (d2 >= 0.999999) continue;
                vec3 dv = viewOffsetAt(uv2, d2) - offset;
                float dist = length(dv);

                float toward = dot(dv / max(dist, 1.0e-4), normal);
                occlusion += clamp(toward - 0.08, 0.0, 1.0)
                        * clamp(1.0 - dist / (aoRadius * 2.0), 0.0, 1.0);
            }
            occlusion = clamp(occlusion * 0.275, 0.0, 1.0);
        }
        float shade = 1.0 - occlusion * 0.72 * snowDetail;

        vec3 snow = albedo * (ambient * skyView * shade + SunColor * wrap * 0.34 * (0.45 + 0.55 * shade));

        float cover = amount * clamp(lay + rime, 0.0, 1.0) * 0.965;
        fragColor = vec4(snow * cover, cover);
        return;
    }

    float perBlock = 0.80 + 0.40 * valueNoise2(worldPos.xz * 0.41 + vec2(13.7, 5.3));

    float patchNoise = valueNoise2(worldPos.xz * 0.34) * 0.60 + valueNoise2(worldPos.xz * 0.87) * 0.40;
    vec3 reflected = reflect(viewDir, normal);

    float faceWeight = mix(0.35, 1.0, upness);

    float coverage = mix(1.0, smoothstep(0.55, 0.72, patchNoise), PuddleAmount);
    float puddle = coverage * upness;
    float wet = amount * faceWeight * perBlock * (1.0 + puddle * 0.9);

    float darken = min(0.66, DarkAmount * wet);

    float skyward = smoothstep(-0.05, 0.45, reflected.y);

    float spread = mix(0.72, 0.90, PuddleAmount);
    float sheen = ((1.0 - spread) + spread * fresnel(dot(-viewDir, normal)))
            * skyward * ReflectAmount * mix(0.40, 1.0, PuddleAmount);

    float sheenAmount = sheen * wet * mix(0.12, 1.0, puddle);

    if (sheenAmount <= 0.002) {
        fragColor = vec4(0.0, 0.0, 0.0, darken);
        return;
    }

    float specularity = mix(0.30, 0.85, PuddleAmount);
    vec3 skyDir = normalize(mix(normal, reflected, specularity));
    vec3 skyColor = weatherSkyReflect(skyDir);

    vec3 light = min(WetTint * skyColor * sheenAmount * (0.55 + GlintAmount * 0.25), vec3(0.30));

    fragColor = vec4(light, darken);
}
