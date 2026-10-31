#version 330

uniform sampler2D Sampler0; // размытое покрытие силуэта цели (GL-ориентация)
uniform sampler2D Sampler1; // сырая капча цели: rgb — цвет модели, a — покрытие (нативная ориентация)

// Оболочка стиля «Разряд»: цель просвечивает скан. Тело перекрашено в цвет разряда по яркости
// самой модели (детали скина читаются, но уже «в синем»), по кромке тлеет контур, а по телу
// вверх-вниз ходит фронт заряда: живая рваная кромка, за которой тело налито светом. Заряд
// перетекает, а не переключается: каждая точка светится по тому, как давно через неё прошёл фронт.
// Поэтому свет плавно густеет к кромке, мягко остывает позади, у разворота сам собирается к фронту
// и так же плавно утекает обратно. На фронте рисунок тела сбоит — тонкие ломти скина съезжают
// вбок; в верхней и нижней точке кромка мягко разгорается.
//
// Делим UBO с пламенем, слоты переиспользованы. Описания полей вынесены над блоком — внутри его
// не переваривает конвертер GLSL у VulkanMod:
//   Time — секунды; Flash — вспышка удара 0..1
//   CenterX/CenterY — центр цели в UV кадра (V вверх); Scale — сколько V кадра приходится на блок
//   Aspect — ширина кадра к высоте
//   BandV — V кадра, на котором сейчас фронт скана; BandDir — скорость скана -1..1 (плюс — вверх)
//   SharpFlip — 1.0, если Sampler1 лежит в нативной ориентации бэкенда (VulkanMod): его V переворачиваем
//   ScanPhase — фаза скана 0..2π: 0 — фронт в нижней точке разворота, π — в верхней
//   ScanSpan — V кадра от нижней точки разворота до верхней; со знаком: камера сверху видит их и наоборот
layout(std140) uniform SadnesFlame {
    float Time;
    float Flash;
    float CenterX;
    float CenterY;
    float Scale;
    float Aspect;
    float BandV;
    float BandDir;
    float SharpFlip;
    float ScanPhase;
    float ScanSpan;
};

in vec2 texCoord0;
in vec4 tint;       // rgb — цвет разряда, a — альфа настройки, умноженная на появление

out vec4 fragColor;

const float PI = 3.14159265;
const float TAU = 6.2831853;

uint mixBits(uint x) {
    x ^= x >> 16;
    x *= 0x7feb352du;
    x ^= x >> 15;
    x *= 0x846ca68bu;
    x ^= x >> 16;
    return x;
}

float hash(vec2 p) {
    uvec2 b = floatBitsToUint(p);
    return float(mixBits(b.x ^ mixBits(b.y + 0x9e3779b9u)) >> 8) * (1.0 / 16777216.0);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

// Затухание с круглой вершиной: у нуля — гаусс, дальше — экспонента. Гребень заряда у кромки
// выходит мягким, без излома, а хвост остывает как обычно.
float falloff(float x, float knee, float decay) {
    return exp(-x * x / ((x + knee) * decay));
}

void main() {
    // Всё меряем в блоках: p — от центра цели, band — по вертикали экрана от фронта скана.
    // Кромка не дуга, а живая рваная линия: две бегущие волны плюс мелкий треск.
    float scale = max(Scale, 1e-4);
    vec2 p = vec2((texCoord0.x - CenterX) * Aspect, texCoord0.y - CenterY) / scale;
    float edgeShape = 0.028 * sin(p.x * 7.0 + Time * 4.0)
            + 0.016 * sin(p.x * 19.0 - Time * 9.0)
            + 0.02 * (vnoise(vec2(p.x * 28.0, Time * 9.0)) - 0.5);
    float band = (texCoord0.y - BandV) / scale - edgeShape;

    // Сбой на фронте: ломти по 3 см съезжают вбок, набор перещёлкивается 20 раз в секунду. Сдвигаем
    // только рисунок скина, а покрытие берём на месте: сдвинутое покрытие вылезало прямоугольниками
    // за контур (на плоской голове особенно) и читалось как баг рендера, а не как помеха скана.
    float slice = floor(p.y * 32.0);
    float frame = floor(Time * 20.0);
    float glitchZone = exp(-band * band / 0.006);
    float glitch = step(0.55, hash(vec2(slice, frame))) * (hash(vec2(slice + 17.0, frame)) - 0.5) * 0.16 * glitchZone;
    vec2 sharpCoord = vec2(texCoord0.x, mix(texCoord0.y, 1.0 - texCoord0.y, SharpFlip));

    float sharp = texture(Sampler1, sharpCoord).a;
    vec3 skin = texture(Sampler1, sharpCoord + vec2(glitch * scale / Aspect, 0.0)).rgb;
    float blurred = texture(Sampler0, texCoord0).r;
    if (sharp + blurred <= 0.002 || tint.a <= 0.002) discard;

    // Заряд. travel — где точка лежит на ходу скана: 0 — нижний разворот, 1 — верхний. Считаем от
    // фронта, чтобы гребень заряда шёл ровно по кромке вместе с её рваной формой. Фронт пересекает
    // точку дважды за цикл — на подъёме в фазе crossUp и на спуске в TAU - crossUp; свет гаснет со
    // временем от последнего пересечения и чуть загорается перед следующим. Ход гармонический, так
    // что у разворота, где фронт тормозит, хвост сам поджимается к кромке, а после разворота тянется
    // уже с другой стороны — без скачка. За точками разворота (макушка, пятки) заряд гаснет по
    // расстоянию.
    float span = ScanSpan < 0.0 ? min(ScanSpan, -0.25 * scale) : max(ScanSpan, 0.25 * scale);
    float travel = 0.5 - 0.5 * cos(ScanPhase) + band * scale / span;
    float crossUp = acos(1.0 - 2.0 * clamp(travel, 0.0, 1.0));
    float since = min(mod(ScanPhase - crossUp, TAU), mod(ScanPhase + crossUp, TAU));
    float until = min(mod(crossUp - ScanPhase, TAU), mod(-crossUp - ScanPhase, TAU));
    float beyond = max(max(-travel, travel - 1.0), 0.0) * abs(span) / scale;
    float charge = max(falloff(since, 0.06, 0.6), falloff(until, 0.04, 0.1)) * exp(-beyond / 0.08);
    // Ядро — тот же заряд, стянутый к кромке: плотный свет прямо у фронта вместо отдельной полосы.
    float core = charge * charge * charge;
    float crest = exp(-band * band / 0.0016);
    float near = exp(-band * band / 0.008);
    // Разворот: кромка разгорается и гаснет плавно, на всё время торможения фронта.
    float ping = 1.0 - smoothstep(0.0, 0.5, abs(BandDir));
    // Заливка не гладкая: по ней тянутся светлые струи, как ток. Их ход привязан к ходу фронта —
    // на развороте они тормозят вместе с ним, а не перескакивают.
    float flow = (PI - abs(ScanPhase - PI)) * 0.95;
    float streaks = 0.75 + 0.25 * vnoise(vec2(p.x * 11.0 + Time * 0.3, p.y * 3.0 - flow));

    float rim = 1.0 - abs(2.0 * blurred - 1.0);
    rim = smoothstep(0.35, 0.95, rim);
    float outer = clamp(blurred - sharp, 0.0, 1.0);

    // Голографические строки по телу, привязанные к цели. Вдали сливаются в муар — там их гасим.
    float rows = (0.5 + 0.5 * sin(p.y * 120.0 + Time * 3.0)) * smoothstep(0.02, 0.06, Scale);

    vec3 accent = tint.rgb;
    vec3 hot = mix(accent, vec3(1.0), 0.45);

    // Перекраска тела: монохром в цвет разряда по яркости модели. Непрозрачность неполная —
    // исходная модель просвечивает, и фигура выглядит налитой светом, а не залитой краской.
    // Где заряд, тело залито плотнее.
    float lum = dot(skin, vec3(0.299, 0.587, 0.114));
    vec3 body = mix(accent * (0.3 + 1.05 * lum), vec3(1.0), 0.55 * Flash);
    float bodyA = sharp * (0.36 + 0.3 * charge + 0.3 * Flash) * (1.0 - 0.14 * rows) * tint.a;

    // Свечение поверх перекраски. Постоянное — ореол и контур — нарочно тихое: главный здесь заряд.
    // Заливка — цветом разряда, ядро и кромка — светлее, но не белые; у кромки свет чуть
    // выплёскивается за силуэт.
    vec3 light = accent * outer * (0.18 + 0.3 * near + 0.6 * Flash)
            + hot * rim * (0.12 + 0.35 * near + 0.6 * Flash)
            + accent * charge * streaks * sharp * 0.75
            + mix(accent, hot, 0.5) * core * sharp * 0.4
            + hot * crest * (sharp + 0.4 * outer) * (0.7 + 0.5 * ping + Flash);
    light *= tint.a;

    // Премультиплицированный вывод под бленд (ONE, ONE_MINUS_SRC_ALPHA): перекраска ложится
    // поверх кадра с альфой bodyA, а свечение с нулевой альфой просто прибавляет свет.
    vec3 col = body * bodyA + light;
    if (bodyA + max(col.r, max(col.g, col.b)) <= 0.002) discard;
    fragColor = vec4(col, bodyA);
}
