#version 330

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

layout(std140) uniform EggAmbiencePost {
    float Proj00;
    float Proj11;
    float Near;
    float Far;
    float BlurStart;
    float BlurEnd;
    float BloomThreshold;
    float BloomIntensity;
    float UpViewX;
    float UpViewY;
    float UpViewZ;
    float ReflStrength;
    float ReflDistance;
    float SkyZenithR;
    float SkyZenithG;
    float SkyZenithB;
    float SkyHorizonR;
    float SkyHorizonG;
    float SkyHorizonB;
    float SunDirX;
    float SunDirY;
    float SunDirZ;
    float AxisXx;
    float AxisXy;
    float AxisXz;
    float AxisZx;
    float AxisZy;
    float AxisZz;
    float CamX;
    float CamZ;
    float WaveTime;
    float FogR;
    float FogG;
    float FogB;
    float FogDensity;
    float FogStart;
    float FogHeight;
    float FogFalloff;
    float FogNoise;
    float FogCamX;
    float FogCamY;
    float FogCamZ;
    float Pad0;
    float Pad1;
    int ShaderQuality;
    int FogMode;
    float FogSpeed;
    float ScreenOriginTop;
};

layout(std140) uniform EggFog {
    float LayerDensity;
    float LayerStart;
    float LayerBaseY;
    float LayerFalloff;
    float HazeShare;
    float HazeFalloff;
    float NoiseShare;
    float MarchDistance;
    float DriftX;
    float DriftY;
    float DriftZ;
    float NoiseCamX;
    float NoiseCamY;
    float NoiseCamZ;
    float SunShare;
    float AmbientShare;
    float ShaftShare;
    float MapOriginX;
    float MapOriginZ;
    float MapSize;
    float FlashX;
    float FlashY;
    float FlashZ;
    float FlashPower;
    float FlashHeight;
    int VolumeMode;
    int VolumeQuality;
    float FogPad0;
    float FogPad1;
    float FogPad2;
    float FogPad3;
    float FogPad4;
};

out vec4 fragColor;

#moj_import <egg:ambience_fog.glsl>

// Туман поверх кадра на полном разрешении (Sampler0 - depth кадра). Дистанционный и приземный режимы
// считаются здесь точно. Объёмный марш идёт вдвое мельче (Sampler1): его слой растягивается с учётом
// глубины - из четырёх соседних текселей берутся те, чья глубина совпадает с пикселем; где совпадения нет
// (тонкая трава, края листвы), остаётся точный слой без шума, иначе дальний туман лёг бы ореолом на ближний край.
// Здесь же лучи света: марш по экрану от пикселя к солнцу по маске неба - за кронами, крышами и склонами
// сияние рвётся на полосы.

const int VOLUME_MODE = 2;
// Яркость лучей относительно прямого света тумана.
const float FOG_RAY_GAIN = 1.8;

vec4 upsampledVolume(vec4 analytic, float pixelZ, ivec2 depthSize) {
    ivec2 layerSize = textureSize(Sampler1, 0);
    vec2 layerPosition = gl_FragCoord.xy * 0.5 - 0.5;
    ivec2 base = ivec2(floor(layerPosition));
    vec2 blend = layerPosition - vec2(base);
    float tolerance = pixelZ * 0.04 + 0.4;
    vec4 sum = vec4(0.0);
    float weights = 0.0;
    float bestMatch = 0.0;
    for (int j = 0; j < 2; j++) {
        for (int i = 0; i < 2; i++) {
            ivec2 texel = clamp(base + ivec2(i, j), ivec2(0), layerSize - 1);
            float sampleZ = fogLinearZ(texelFetch(Sampler0, min(texel * 2, depthSize - 1), 0).r);
            float difference = (sampleZ - pixelZ) / tolerance;
            float match = exp(-difference * difference);
            float bilinear = (i == 0 ? 1.0 - blend.x : blend.x) * (j == 0 ? 1.0 - blend.y : blend.y);
            float weight = (bilinear + 0.02) * match;
            sum += texelFetch(Sampler1, texel, 0) * weight;
            weights += weight;
            bestMatch = max(bestMatch, match);
        }
    }
    if (weights <= 0.00001) return analytic;
    return mix(analytic, sum / weights, smoothstep(0.05, 0.5, bestMatch));
}

// Доля открытого неба на пути к солнцу по экрану: 1 - путь чист, 0 - солнце закрыто. Ближние выборки весомее,
// поэтому полосы держатся у самих силуэтов. Солнце за спиной или за кадром лучей не даёт.
float fogSunRays(vec2 nativeUv, ivec2 depthSize) {
    vec3 sunView = vec3(SunDirX, SunDirY, SunDirZ);
    if (ShaftShare <= 0.0 || sunView.z > -0.08) return 0.0;
    vec2 sunNdc = vec2(sunView.x / -sunView.z * Proj00, sunView.y / -sunView.z * Proj11);
    vec2 sunUv = vec2(sunNdc.x * 0.5 + 0.5, mix(sunNdc.y * 0.5 + 0.5, 0.5 - sunNdc.y * 0.5, ScreenOriginTop));
    int steps = clamp(VolumeQuality, 1, 4) * 6 + 6;
    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    float open = 0.0;
    float weights = 0.0;
    for (int i = 0; i < 30; i++) {
        if (i >= steps) break;
        float along = (float(i) + jitter) / float(steps);
        vec2 uv = mix(nativeUv, sunUv, along);
        if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) continue;
        float weight = pow(1.0 - along, 2.5);
        open += weight * step(0.99999, texelFetch(Sampler0, ivec2(uv * vec2(depthSize)), 0).r);
        weights += weight;
    }
    return weights > 0.0 ? open / weights : 0.0;
}

void main() {
    fragColor = vec4(0.0);

    ivec2 depthSize = textureSize(Sampler0, 0);
    vec3 viewDirection = fogPixelDirection(gl_FragCoord.xy / vec2(depthSize));
    float depth = texelFetch(Sampler0, ivec2(gl_FragCoord.xy), 0).r;
    bool sky = depth >= 0.99999;
    float sceneDistance = fogSceneDistance(depth, viewDirection);
    vec3 direction = fogViewToWorld(viewDirection);

    vec3 lightDirection = vec3(0.0);
    vec3 sunColor = vec3(0.0);
    float skyLevel = 0.0;
    float phase = 0.0;
    fogLighting(direction, lightDirection, sunColor, skyLevel, phase);

    vec4 fog = fogAnalytic(direction, sceneDistance, sky, lightDirection, sunColor, skyLevel, phase);
    if (VolumeMode == VOLUME_MODE) fog = upsampledVolume(fog, fogLinearZ(depth), depthSize);
    if (fog.a <= 0.0001) return;
    // Лучи складываются с уже набранным светом: чем плотнее туман на луче, тем заметнее сияние.
    float rays = fogSunRays(gl_FragCoord.xy / vec2(depthSize), depthSize) * ShaftShare;
    fog.rgb += sunColor * (phase * rays * fog.a * FOG_RAY_GAIN);
    fragColor = fogDithered(fog, gl_FragCoord.xy);
}
