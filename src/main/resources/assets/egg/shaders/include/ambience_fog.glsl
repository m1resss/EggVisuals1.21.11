// Общая модель тумана Ambience: слой на полном разрешении (core/world/ambience_fog) и объёмный марш
// (core/world/ambience_fog_volume). Восстановление луча из depth, профиль плотности по высоте с точным
// интегралом вдоль луча и свет: небо, солнце или луна по фазовой функции, вспышки молний.
// Блоки SadnesAmbiencePost и SadnesFog объявляет подключающий шейдер - до импорта.
// Высоты здесь - над основанием слоя, позиции - относительно камеры.

const float FOG_ALPHA_MAX = 0.985;
// Доля прямого света в рассеянии тумана: вдали от солнца он светится почти своим цветом,
// против солнца фазовая функция даёт яркое сияние и лучи.
const float FOG_SUN_SCATTER = 0.12;
// Какую долю тени от слоя тумана получает прямой свет: рассеяние вперёд подсвечивает толщу.
const float FOG_SELF_SHADOW = 0.35;

float fogLinearZ(float depth) {
    float ndcZ = depth * 2.0 - 1.0;
    return 2.0 * Near * Far / (Far + Near - ndcZ * (Far - Near));
}

vec3 fogViewToWorld(vec3 direction) {
    vec3 up = vec3(UpViewX, UpViewY, UpViewZ);
    vec3 axisX = vec3(AxisXx, AxisXy, AxisXz);
    vec3 axisZ = vec3(AxisZx, AxisZy, AxisZz);
    return normalize(vec3(dot(direction, axisX), dot(direction, up), dot(direction, axisZ)));
}

vec3 fogPixelDirection(vec2 nativeUv) {
    float ndcY = mix(nativeUv.y * 2.0 - 1.0, 1.0 - nativeUv.y * 2.0, ScreenOriginTop);
    vec2 ndc = vec2(nativeUv.x * 2.0 - 1.0, ndcY);
    return normalize(vec3(ndc / vec2(Proj00, Proj11), -1.0));
}

// Дистанция до поверхности вдоль луча; небо стоит на дальней плоскости.
float fogSceneDistance(float depth, vec3 viewDirection) {
    if (depth >= 0.99999) return Far;
    return fogLinearZ(depth) / max(-viewDirection.z, 0.0001);
}

// Относительная плотность на высоте над основанием: ниже основания слой полон, выше редеет,
// над ним тянется дымка дали. Равномерный туман (LayerFalloff = 0) одинаков везде.
float fogProfile(float height) {
    if (LayerFalloff <= 0.0) return 1.0;
    if (height <= 0.0) return 1.0 + HazeShare;
    return exp(-LayerFalloff * height) + HazeShare * exp(-HazeFalloff * height);
}

// Интеграл exp(-k * h) вдоль отрезка [s1, s2], где h = h0 + dy * s и отрезок целиком выше основания.
// Через значения на концах: обе экспоненты не больше единицы, переполнения нет при любом наклоне.
float fogExpSegment(float k, float h0, float dy, float s1, float s2) {
    float first = exp(-k * (h0 + dy * s1));
    float last = exp(-k * (h0 + dy * s2));
    float kd = k * dy;
    if (abs(kd * (s2 - s1)) < 0.001) return 0.5 * (first + last) * (s2 - s1);
    return (first - last) / kd;
}

float fogProfileSegment(float h0, float dy, float s1, float s2) {
    if (s2 <= s1) return 0.0;
    if (h0 + dy * 0.5 * (s1 + s2) <= 0.0) return (s2 - s1) * (1.0 + HazeShare);
    return fogExpSegment(LayerFalloff, h0, dy, s1, s2) + HazeShare * fogExpSegment(HazeFalloff, h0, dy, s1, s2);
}

// Интеграл профиля вдоль луча h = h0 + dy * s на [s1, s2]: отрезок делится там, где луч пересекает основание.
float fogColumnIntegral(float h0, float dy, float s1, float s2) {
    if (s2 <= s1) return 0.0;
    if (LayerFalloff <= 0.0) return s2 - s1;
    float crossing = abs(dy) > 0.000001 ? -h0 / dy : s1;
    float split = clamp(crossing, s1, s2);
    return fogProfileSegment(h0, dy, s1, split) + fogProfileSegment(h0, dy, split, s2);
}

// Оптическая толща слоя над точкой по пути к светилу: низ толстого слоя получает меньше прямого света.
float fogSunDepth(float height, float lightY) {
    if (LayerFalloff <= 0.0 || lightY <= 0.02) return 0.0;
    float below = max(-height, 0.0) * (1.0 + HazeShare);
    float above = max(height, 0.0);
    float layer = exp(-LayerFalloff * above) / LayerFalloff + HazeShare * exp(-HazeFalloff * above) / HazeFalloff;
    return LayerDensity * (below + layer) / lightY;
}

float fogHenyeyGreenstein(float cosTheta, float g) {
    float g2 = g * g;
    return (1.0 - g2) / pow(max(1.0 + g2 - 2.0 * g * cosTheta, 0.0001), 1.5);
}

vec3 fogSunDirection() {
    return fogViewToWorld(vec3(SunDirX, SunDirY, SunDirZ));
}

// Свет кадра для луча: направление на светило (ночью - луна напротив солнца), его цвет с учётом погоды
// и пещер, уровень неба и фазовая функция. Днём туман светится своим цветом, ночью почти гаснет.
void fogLighting(vec3 direction, inout vec3 lightDirection, inout vec3 sunColor, inout float skyLevel, inout float phase) {
    vec3 sun = fogSunDirection();
    float day = smoothstep(-0.05, 0.12, sun.y);
    float night = 1.0 - smoothstep(-0.25, -0.05, sun.y);
    lightDirection = sun.y >= -0.05 ? sun : -sun;
    vec3 tint = mix(vec3(1.0, 0.5, 0.24), vec3(1.0, 0.95, 0.88), smoothstep(0.02, 0.45, sun.y));
    sunColor = (tint * day + vec3(0.46, 0.56, 0.82) * night * 0.12) * SunShare * FOG_SUN_SCATTER;
    skyLevel = mix(0.06, 1.0, day) * AmbientShare;
    float cosTheta = dot(direction, lightDirection);
    phase = mix(fogHenyeyGreenstein(cosTheta, 0.2), fogHenyeyGreenstein(cosTheta, 0.6), 0.55);
}

// Свечение тумана у канала молнии: канал - вертикальный отрезок над точкой удара.
vec3 fogFlash(vec3 position) {
    if (FlashPower <= 0.0) return vec3(0.0);
    vec2 across = position.xz - vec2(FlashX, FlashZ);
    float along = clamp(position.y, FlashY, FlashY + FlashHeight) - position.y;
    float distanceSq = dot(across, across) + along * along;
    return vec3(0.72, 0.78, 1.0) * (FlashPower * 2.5 / (1.0 + distanceSq / 1600.0));
}

// Туман без шума и теней: точный интеграл слоя вдоль луча, свет - в точке, где рассеяние набирается сильнее всего.
vec4 fogAnalytic(vec3 direction, float sceneDistance, bool sky,
                 vec3 lightDirection, vec3 sunColor, float skyLevel, float phase) {
    if (sceneDistance <= LayerStart) return vec4(0.0);
    float depth;
    if (sky && LayerFalloff <= 0.0) {
        // Равномерный туман закрывает небо у горизонта и быстро редеет к зениту, как ванильный:
        // дальняя плоскость дала бы белое небо целиком.
        depth = 3.0 * exp(-max(direction.y, 0.0) * 4.5);
    } else {
        depth = LayerDensity * fogColumnIntegral(-LayerBaseY, direction.y, LayerStart, sceneDistance);
    }
    float alpha = min(1.0 - exp(-depth), FOG_ALPHA_MAX);
    if (alpha <= 0.0001) return vec4(0.0);

    float probe = LayerStart + min(sceneDistance - LayerStart, 1.0 / max(LayerDensity, 0.0001)) * 0.5;
    vec3 position = direction * probe;
    float shadow = exp(-fogSunDepth(position.y - LayerBaseY, lightDirection.y) * FOG_SELF_SHADOW);
    vec3 albedo = vec3(FogR, FogG, FogB);
    vec3 light = albedo * skyLevel + sunColor * phase * shadow + fogFlash(position);
    return vec4(light * alpha, alpha);
}

// Premultiplied-выход с дизерингом: плавные градиенты дымки не ложатся ступенями в 8-битный кадр.
vec4 fogDithered(vec4 fog, vec2 fragCoord) {
    float dither = fract(52.9829189 * fract(dot(fragCoord, vec2(0.06711056, 0.00583715))));
    return vec4(max(fog.rgb + (dither - 0.5) * (max(fog.a, 0.02) / 255.0), vec3(0.0)), fog.a);
}
