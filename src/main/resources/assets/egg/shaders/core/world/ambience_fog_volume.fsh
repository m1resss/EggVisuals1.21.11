#version 330

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

layout(std140) uniform SadnesAmbiencePost {
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

layout(std140) uniform SadnesFog {
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

#moj_import <sadnes:ambience_fog.glsl>

// Объёмный туман, вдвое мельче кадра: луч из камеры идёт через слой до поверхности (Sampler0 - depth кадра),
// в каждой точке плотность слоя умножается на плывущий по ветру 3D-шум (Sampler1 - LUT шума облаков, BA),
// а прямой свет гасят столбы карты высот вокруг камеры (Sampler2 - карта осадков, RG): кроны, крыши и склоны
// прорезают в тумане лучи. Дальше конца марша слой досчитывается точным интегралом без шума.
// Выход premultiplied: rgb - рассеянный свет, a - непрозрачность. Композит - core/world/ambience_fog.

// Ячейка шума, блоки: банки тумана крупные, октавы вдвое и вчетверо мельче. Тайл LUT - 512 ячеек,
// так что период шума 65536 совпадает со свёрткой камеры и сноса, а по высоте - с перебором срезов.
const float NOISE_CELL = 128.0;
const float LUT_SIZE = 512.0;
const float HEIGHT_BIAS = 32768.0;

float fogLutNoise(vec3 p) {
    vec3 cell = floor(p);
    vec3 blend = fract(p);
    blend = blend * blend * (3.0 - 2.0 * blend);
    vec2 uv = cell.xz + vec2(37.0, 17.0) * cell.y + blend.xz;
    vec2 slices = textureLod(Sampler1, (uv + 0.5) / LUT_SIZE, 0.0).ba;
    return mix(slices.x, slices.y, blend.y);
}

// Множитель плотности слоя: клубы и просветы со средним около единицы. Ближе к камере - больше октав;
// лёгкий сдвиг низкой октавой рвёт ровные ячейки value-noise.
float fogBanks(vec3 position, float distance, int octaves) {
    if (NoiseShare <= 0.0) return 1.0;
    vec3 world = vec3(NoiseCamX + position.x - DriftX, NoiseCamY + position.y + DriftY, NoiseCamZ + position.z - DriftZ);
    vec3 p = world / NOISE_CELL;
    p.xz += (fogLutNoise(p * 0.5 + vec3(3.0, 7.0, 1.0)) - 0.5) * 1.2;
    float value = fogLutNoise(p) * 0.55;
    float weight = 0.55;
    int detail = distance > 180.0 ? 1 : octaves;
    if (detail > 1) {
        value += fogLutNoise(p * 2.0 + vec3(11.0, 5.0, 19.0)) * 0.3;
        weight += 0.3;
    }
    if (detail > 2) {
        value += fogLutNoise(p * 4.0 + vec3(7.0, 13.0, 3.0)) * 0.15;
        weight += 0.15;
    }
    float banks = smoothstep(0.3, 0.64, value / weight);
    return mix(1.0, banks * 1.7, NoiseShare);
}

// Верх столба карты высот относительно камеры; вне карты - глубоко внизу, там тени не считаются.
float fogColumnTop(vec2 relativeXZ) {
    vec2 texel = floor(relativeXZ - vec2(MapOriginX, MapOriginZ));
    if (MapSize <= 0.0 || texel.x < 0.0 || texel.y < 0.0 || texel.x >= MapSize || texel.y >= MapSize) return -1.0e6;
    vec2 heightBytes = texelFetch(Sampler2, ivec2(texel), 0).rg;
    return dot(heightBytes, vec2(65280.0, 255.0)) - HEIGHT_BIAS - NoiseCamY;
}

float fogOpen(float columnTop, float height) {
    return 1.0 - smoothstep(-0.6, 0.6, columnTop - height);
}

// Доля прямого света: точка под своим столбом в тени, дальше - шаги к светилу мимо соседних столбов.
float fogSunlight(vec3 position, float open, vec3 lightDirection, int steps) {
    float lit = open;
    float reach = 1.5;
    for (int k = 0; k < 4; k++) {
        if (k >= steps || lit <= 0.01) break;
        vec3 probe = position + lightDirection * reach;
        lit *= fogOpen(fogColumnTop(probe.xz), probe.y);
        reach = reach * 2.0 + 1.5;
    }
    return mix(1.0, lit, ShaftShare);
}

vec4 marchFog(vec3 direction, float sceneDistance, vec2 fragCoord) {
    vec3 lightDirection = vec3(0.0);
    vec3 sunColor = vec3(0.0);
    float skyLevel = 0.0;
    float phase = 0.0;
    fogLighting(direction, lightDirection, sunColor, skyLevel, phase);
    vec3 albedo = vec3(FogR, FogG, FogB);

    // Туман проявляется плавно от половины дистанции начала.
    float rampStart = LayerStart * 0.5;
    float nearStart = rampStart;
    float end = min(sceneDistance, MarchDistance);
    // Выше потолка слоя плотность меньше процента: марш идёт только внутри слоя, дымку над ним
    // досчитывает интеграл. Камера над потолком - луч вниз начинается у потолка, остальные мимо слоя.
    float ceiling = LayerBaseY + 4.6 / LayerFalloff;
    if (direction.y > 0.001) end = min(end, max(ceiling, 0.0) / direction.y);
    if (ceiling < 0.0) {
        if (direction.y < -0.001) nearStart = max(nearStart, ceiling / direction.y);
        else end = nearStart;
    }

    int quality = clamp(VolumeQuality, 1, 4);
    int steps = quality == 1 ? 10 : (quality == 2 ? 16 : (quality == 3 ? 24 : 32));
    int octaves = quality == 1 ? 1 : (quality == 4 ? 3 : 2);
    int shadowSteps = MapSize > 0.0 && ShaftShare > 0.0 ? quality : 0;
    float jitter = fract(52.9829189 * fract(dot(fragCoord, vec2(0.06711056, 0.00583715))));

    vec3 scatter = vec3(0.0);
    float transmittance = 1.0;
    if (end > nearStart) {
        // Шаги равномерны по sqrt(t): у камеры, где видна структура, гуще; сдвиг по IGN прячет слои.
        float rootStart = sqrt(nearStart);
        float rootStep = (sqrt(end) - rootStart) / float(steps);
        for (int i = 0; i < 32; i++) {
            if (i >= steps) break;
            float root = rootStart + (float(i) + jitter) * rootStep;
            float t = root * root;
            float stepLength = 2.0 * root * rootStep;
            vec3 position = direction * t;
            float height = position.y - LayerBaseY;
            float ramp = LayerStart > 0.0 ? smoothstep(rampStart, LayerStart, t) : 1.0;
            float layer = height <= 0.0 ? 1.0 : exp(-LayerFalloff * height);
            float haze = HazeShare * (height <= 0.0 ? 1.0 : exp(-HazeFalloff * height));
            float density = LayerDensity * ramp * (layer * fogBanks(position, t, octaves) + haze);
            if (density <= 0.000005) continue;

            float columnTop = fogColumnTop(position.xz);
            float open = fogOpen(columnTop, position.y);
            float sunlight = shadowSteps > 0 ? fogSunlight(position, open, lightDirection, shadowSteps) : 1.0;
            sunlight *= exp(-fogSunDepth(height, lightDirection.y) * FOG_SELF_SHADOW);
            // Под кронами и крышами неба почти не видно - там туман темнее.
            vec3 light = albedo * skyLevel * mix(0.35, 1.0, open) + sunColor * phase * sunlight + fogFlash(position);

            float stepTransmittance = exp(-density * stepLength);
            scatter += transmittance * (1.0 - stepTransmittance) * light;
            transmittance *= stepTransmittance;
            if (transmittance < 0.01) break;
        }
    }

    float tailStart = max(end, nearStart);
    if (transmittance >= 0.01 && sceneDistance > tailStart) {
        float tailDepth = LayerDensity * fogColumnIntegral(-LayerBaseY, direction.y, max(tailStart, LayerStart), sceneDistance);
        vec3 position = direction * tailStart;
        float sunlight = exp(-fogSunDepth(position.y - LayerBaseY, lightDirection.y) * FOG_SELF_SHADOW);
        vec3 light = albedo * skyLevel + sunColor * phase * sunlight + fogFlash(position);
        float tailTransmittance = exp(-tailDepth);
        scatter += transmittance * (1.0 - tailTransmittance) * light;
        transmittance *= tailTransmittance;
    }

    float alpha = min(1.0 - transmittance, FOG_ALPHA_MAX);
    return vec4(scatter, alpha);
}

void main() {
    fragColor = vec4(0.0);

    ivec2 depthSize = textureSize(Sampler0, 0);
    ivec2 layerSize = max(depthSize / 2, ivec2(1));
    vec3 viewDirection = fogPixelDirection(gl_FragCoord.xy / vec2(layerSize));
    // Глубину берём у того же пикселя, что и композит при растяжке слоя: первый в блоке 2x2.
    float depth = texelFetch(Sampler0, min(ivec2(gl_FragCoord.xy) * 2, depthSize - 1), 0).r;
    float sceneDistance = fogSceneDistance(depth, viewDirection);
    vec4 fog = marchFog(fogViewToWorld(viewDirection), sceneDistance, gl_FragCoord.xy);
    if (fog.a <= 0.0001) return;
    fragColor = fogDithered(fog, gl_FragCoord.xy);
}
