#version 330

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

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

layout(std140) uniform EggWeather {
    float BoxSize;
    float InnerFade;
    float OriginAX;
    float OriginAY;
    float OriginAZ;
    float OriginBX;
    float OriginBY;
    float OriginBZ;
    float OriginCX;
    float OriginCY;
    float OriginCZ;
    float FallX;
    float FallY;
    float FallZ;
    float StreakLength;
    float ParticleSize;
    float Opacity;
    float Time;
    float MapOriginX;
    float MapOriginZ;
    float MapCamY;
    float MapSize;
    float LightR;
    float LightG;
    float LightB;
    float Flash;
    float WeatherFogStart;
    float WeatherFogDensity;
    float Ripples;
    float SnowCover;
    float Sway;
    int DefaultType;
    int Quality;
    float Pad2;
    float Pad3;
    float Pad4;
};

out vec4 fragColor;

// Осадки на поверхностях мира, по depth кадра (Sampler0): круги от капель на воде и мокрой земле в дождь,
// снежный покров в снегопад. Карта осадков (Sampler1, её пишет WeatherMap) пускает эффект только на открытый
// верх столба — под крышей, на стенах и на мобах ничего нет. Sampler2 — копия кадра: вода преломляет её
// по наклону ряби, снег берёт из неё освещённость места. Выход premultiplied поверх кадра.

const float HEIGHT_BIAS = 32768.0;
const int TYPE_RAIN = 1;
const int TYPE_SNOW = 2;
// Период свёрнутых мировых XZ (FogCamX/Z, AmbiencePostFx): кратен ячейкам ряби и масштабам шума покрова.
const float WORLD_PERIOD = 4096.0;
// Дальше круги мельче пикселя; ещё и карта осадков кончается.
const float RIPPLE_DISTANCE = 40.0;
const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

float linearZ(float depth) {
    float ndcZ = depth * 2.0 - 1.0;
    return 2.0 * Near * Far / (Far + Near - ndcZ * (Far - Near));
}

vec3 pixelViewDirection(vec2 nativeUv) {
    float ndcY = mix(nativeUv.y * 2.0 - 1.0, 1.0 - nativeUv.y * 2.0, ScreenOriginTop);
    vec2 ndc = vec2(nativeUv.x * 2.0 - 1.0, ndcY);
    return normalize(vec3(ndc / vec2(Proj00, Proj11), -1.0));
}

vec3 viewToWorld(vec3 direction) {
    vec3 up = vec3(UpViewX, UpViewY, UpViewZ);
    vec3 axisX = vec3(AxisXx, AxisXy, AxisXz);
    vec3 axisZ = vec3(AxisZx, AxisZy, AxisZz);
    return vec3(dot(direction, axisX), dot(direction, up), dot(direction, axisZ));
}

// Позиция поверхности в пикселе относительно камеры, в мировых осях.
vec3 surfacePosition(ivec2 pixel, vec2 frame) {
    float depth = texelFetch(Sampler0, pixel, 0).r;
    vec3 viewDirection = pixelViewDirection((vec2(pixel) + 0.5) / frame);
    return viewToWorld(viewDirection) * (linearZ(depth) / max(-viewDirection.z, 0.0001));
}

// Нормаль грани по соседним пикселям: с каждой оси берём соседа ближе по глубине, чтобы на силуэте
// разность не перепрыгивала на фон. Развёрнута к камере.
vec3 surfaceNormal(ivec2 pixel, ivec2 limit, vec2 frame, vec3 center) {
    vec3 right = surfacePosition(min(pixel + ivec2(1, 0), limit), frame) - center;
    vec3 left = center - surfacePosition(max(pixel - ivec2(1, 0), ivec2(0)), frame);
    vec3 up = surfacePosition(min(pixel + ivec2(0, 1), limit), frame) - center;
    vec3 down = center - surfacePosition(max(pixel - ivec2(0, 1), ivec2(0)), frame);
    float rightLength = dot(right, right);
    float leftLength = dot(left, left);
    float upLength = dot(up, up);
    float downLength = dot(down, down);
    vec3 dx = (rightLength > 0.0 && (rightLength < leftLength || leftLength <= 0.0)) ? right : left;
    vec3 dy = (upLength > 0.0 && (upLength < downLength || downLength <= 0.0)) ? up : down;
    vec3 normal = cross(dx, dy);
    float normalLength = length(normal);
    if (normalLength < 1.0e-8) return vec3(0.0);
    normal /= normalLength;
    return dot(normal, center) > 0.0 ? -normal : normal;
}

vec4 mapTexel(vec2 relativeXZ) {
    ivec2 texel = ivec2(floor(relativeXZ - vec2(MapOriginX, MapOriginZ)));
    int size = int(MapSize);
    if (texel.x < 0 || texel.y < 0 || texel.x >= size || texel.y >= size) return vec4(-1.0);
    return texelFetch(Sampler1, texel, 0);
}

float columnTop(vec4 texel) {
    return floor(dot(texel.rg, vec2(65280.0, 255.0)) + 0.5) - HEIGHT_BIAS - MapCamY;
}

int columnFlags(vec4 texel) {
    return int(texel.b * 255.0 + 0.5);
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}

float valueNoisePeriodic(vec2 p, float period) {
    vec2 cell = floor(p);
    vec2 blend = fract(p);
    blend = blend * blend * (3.0 - 2.0 * blend);
    float v00 = hash12(mod(cell, period));
    float v10 = hash12(mod(cell + vec2(1.0, 0.0), period));
    float v01 = hash12(mod(cell + vec2(0.0, 1.0), period));
    float v11 = hash12(mod(cell + vec2(1.0, 1.0), period));
    return mix(mix(v00, v10, blend.x), mix(v01, v11, blend.x), blend.y);
}

// Круги одного слоя: в каждой ячейке раз за цикл падает капля в случайную точку, от неё бежит кольцо с парой
// гребней и гаснет. Часть ячеек молчит — густота кругов идёт за силой дождя. Частота цикла кратна 1/1024 Гц
// (время свёрнуто в 1024 с), радиус кольца не больше ячейки — хватает соседей 3x3.
// Возвращает градиент высоты воды (xy) и силу волнения (z).
vec3 rippleField(vec2 world, float cellSize, float rate, float maxRadius, float seed) {
    vec2 cell = floor(world / cellSize);
    float cells = WORLD_PERIOD / cellSize;
    float width = 0.09 * maxRadius + 0.015;
    float crest = 6.2831853 / (1.7 * width);
    vec2 gradient = vec2(0.0);
    float energy = 0.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 neighbour = cell + vec2(float(x), float(y));
            vec2 id = mod(neighbour, cells);
            float spawn = hash12(id + seed);
            if (spawn > Ripples) continue;
            float cycle = Time * rate + spawn * 17.0;
            float drop = mod(floor(cycle), rate * 1024.0);
            float life = fract(cycle);
            vec2 center = (neighbour + 0.15 + 0.7 * hash22(id + drop * 0.137 + seed)) * cellSize;
            vec2 offset = world - center;
            float d = length(offset);
            float wave = d - life * maxRadius;
            if (abs(wave) > 3.0 * width) continue;
            float envelope = exp(-wave * wave / (width * width)) * (1.0 - life) * (1.0 - life) * smoothstep(0.0, 0.06, life);
            float slope = envelope * (crest * cos(wave * crest) - 2.0 * wave / (width * width) * sin(wave * crest));
            gradient += slope * offset / max(d, 0.0001);
            energy += envelope;
        }
    }
    return vec3(gradient, energy);
}

vec4 rippleLayer(vec2 world, vec2 uv, vec3 viewDirection, vec3 direction, float dist, bool water) {
    vec3 field = rippleField(world, water ? 0.9 : 0.6, 1.25, water ? 0.8 : 0.4, 3.0);
    if (Quality >= 2) field += rippleField(world + 17.0, water ? 0.55 : 0.4, 1.75, water ? 0.5 : 0.26, 11.0);
    if (field.z <= 0.002) return vec4(0.0);

    // Наклон воды; на суше это мелкие брызги по мокрой поверхности — слабее и короче.
    vec2 slope = field.xy * (water ? 0.006 : 0.0035);
    // Преломление: мировой сдвиг по наклону переводится в экранный через проекцию.
    vec3 offsetView = slope.x * vec3(AxisXx, AxisXy, AxisXz) + slope.y * vec3(AxisZx, AxisZy, AxisZz);
    vec2 offsetNdc = offsetView.xy * vec2(Proj00, Proj11) * 0.3 / max(dist * -viewDirection.z, 0.5);
    vec2 offsetUv = vec2(offsetNdc.x, mix(offsetNdc.y, -offsetNdc.y, ScreenOriginTop)) * 0.5;
    vec3 refracted = textureLod(Sampler2, uv + offsetUv, 0.0).rgb;

    // Гребень, наклонённый от камеры, отражает небо и светлеет; наклонённый к ней — темнеет.
    vec2 toCamera = normalize(-direction.xz + vec2(0.0001));
    float facing = dot(slope, toCamera) * 10.0;
    vec3 sky = mix(vec3(SkyHorizonR, SkyHorizonG, SkyHorizonB), vec3(SkyZenithR, SkyZenithG, SkyZenithB), 0.4)
            * max(dot(vec3(LightR, LightG, LightB), LUMA) * 1.4, 0.15);
    vec3 color = refracted * (1.0 - 0.3 * clamp(-facing, 0.0, 1.0)) + sky * 0.5 * clamp(facing, 0.0, 1.0);

    float fade = 1.0 - smoothstep(0.6 * RIPPLE_DISTANCE, RIPPLE_DISTANCE, dist);
    float alpha = clamp(field.z * 2.0, 0.0, 1.0) * fade * (water ? 1.0 : 0.6);
    return vec4(color * alpha, alpha);
}

vec4 snowLayer(vec2 world, ivec2 pixel, vec3 normal) {
    vec3 scene = texelFetch(Sampler2, pixel, 0).rgb;
    // Пятна покрова: при малой доле снег лежит островками, при полной — сплошь. Масштабы шума делят период свёртки.
    float patches = valueNoisePeriodic(world * 0.375, WORLD_PERIOD * 0.375) * 0.65
            + valueNoisePeriodic(world * 1.875 + 7.0, WORLD_PERIOD * 1.875) * 0.35;
    float threshold = 1.1 - SnowCover * 1.25;
    float amount = smoothstep(threshold - 0.12, threshold + 0.12, patches) * smoothstep(0.7, 0.92, normal.y);
    if (amount <= 0.002) return vec4(0.0);

    // Освещённость места — из яркости кадра (факелы, тени), но не темнее общего света неба.
    float light = clamp(max(dot(scene, LUMA) * 2.3, dot(vec3(LightR, LightG, LightB), LUMA) * 0.8), 0.0, 1.0);
    vec3 snow = mix(vec3(0.8, 0.86, 0.98), vec3(0.97, 0.98, 1.0), light) * light;

    float alpha = amount * 0.92;
    return vec4(snow * alpha, alpha);
}

void main() {
    fragColor = vec4(0.0);

    ivec2 frameSize = textureSize(Sampler0, 0);
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    float depth = texelFetch(Sampler0, pixel, 0).r;
    if (depth >= 0.99999) return;
    vec2 frame = vec2(frameSize);
    vec2 uv = gl_FragCoord.xy / frame;
    vec3 viewDirection = pixelViewDirection(uv);
    float dist = linearZ(depth) / max(-viewDirection.z, 0.0001);
    vec3 direction = viewToWorld(viewDirection);
    vec3 position = direction * dist;

    vec4 texel = mapTexel(position.xz);
    bool mapped = texel.a >= 0.0;
    int flags = mapped ? columnFlags(texel) : DefaultType;
    int type = flags % 4;
    bool water = flags >= 4;
    bool rain = type == TYPE_RAIN && Ripples > 0.0 && dist < RIPPLE_DISTANCE;
    bool snow = type == TYPE_SNOW && SnowCover > 0.0 && !water;
    if (!rain && !snow) return;
    // Осадки ложатся только на открытый верх столба.
    if (mapped) {
        float top = columnTop(texel);
        if (position.y < top - 0.7 || position.y > top + 0.1) return;
    }
    vec3 normal = surfaceNormal(pixel, frameSize - 1, frame, position);
    if (normal.y < 0.7) return;

    vec2 world = vec2(FogCamX, FogCamZ) + position.xz;
    fragColor = rain ? rippleLayer(world, uv, viewDirection, direction, dist, water)
            : snowLayer(world, pixel, normal);
}
