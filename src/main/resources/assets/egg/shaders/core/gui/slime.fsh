#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 quadCoord;
in vec4 radiusData;
in vec3 slimeParams;
in vec3 slimeSplash;
in float slimeTime;

uniform sampler2D Sampler0;   // SDF-текстура лого (LogoTexture): alpha 0.5 — контур буквы

out vec4 fragColor;

// Скругление маски — та же функция, что в core/gui/rounded: знак радиуса несёт режим
// (>=0 дуга окружности, <0 squircle), поэтому маска жижи совпадает с формой подложки.
const float SQUIRCLE_N = 4.0;

// Настройка внешнего вида. Уровень и разлёт — в долях высоты/ширины кнопки, поэтому не
// зависят от её размера и от ховер-зума: кнопка растёт — жижа растёт вместе с ней.
const float REST_LEVEL = 0.36;    // сколько высоты кнопки занимает вода в покое
const float HOVER_LEVEL = 0.74;   // ... и под курсором
const float REST_SPREAD = 0.46;   // полуширина лужи в покое (доля ширины кнопки)
const float HOVER_SPREAD = 0.95;  // ... и под курсором
// След курсора: под ним поверхность вспухает горбом, а вокруг слегка проседает — масса как бы
// перетекает под палец. Именно эта пара и читается как слизь, а не как поднявшийся уровень.
const float DRAG_LIFT = 0.26;     // высота горба (доля высоты кнопки)
const float DRAG_WIDTH = 0.17;    // его ширина (доля ширины кнопки)
// Всплеск от клика: по поверхности расходится затухающее кольцо от точки нажатия.
const float SPLASH_LIFT = 0.30;   // высота гребня
const float SPLASH_WIDTH = 0.10;  // его толщина (доля ширины)
const float SPLASH_SPEED = 0.85;  // как далеко кольцо уходит за свою жизнь
// Инерция: на быстром движении курсора поверхность заваливается против него, как в стакане.
const float TILT_AMP = 0.16;
// Верхний слой: та же вода, нарисованная ПОВЕРХ подписи, чтобы текст, который уровень накрыл,
// просвечивал сквозь неё, а не лежал на поверхности. Плотность низкая — иначе подпись не прочесть.
const float OVERLAY_ALPHA = 0.42;
const float INSET_PX = 1.0;       // поджатие маски в пикселях КАДРА: жижа не лезет на кромку подложки

// Метрики SDF-лого (см. LogoTexture): расстояние в долях стороны квадрата = (alpha - 0.5) * 0.1875,
// где 0.1875 = 2 * (SDF_RANGE / SUPERSAMPLE) / STORE. Видимая буква занимает лишь центр квадрата —
// в logo.svg большие поля, те же доли использует раскладка главного меню.
const float LOGO_SDF_SCALE = 0.1875;
const float GLYPH_HALF_W = 0.164;
const float GLYPH_HALF_H = 0.138;

float roundedBox(vec2 pos, vec2 b, vec4 r) {
    vec2 signPos = step(vec2(0.0), pos);
    vec2 radius = mix(r.zw, r.xy, signPos.x);
    float rSigned = radius.y * (1.0 - signPos.y) + radius.x * signPos.y;
    float rVal = abs(rSigned);
    vec2 q = abs(pos) - b + rVal;
    float qMax = max(q.x, q.y);
    vec2 qClamped = max(q, vec2(0.0));
    float corner = rSigned < 0.0
        ? pow(pow(qClamped.x, SQUIRCLE_N) + pow(qClamped.y, SQUIRCLE_N), 1.0 / SQUIRCLE_N)
        : length(qClamped);
    return min(qMax, 0.0) + corner - rVal;
}

vec3 linearToSrgb(vec3 c) {
    c = clamp(c, 0.0, 1.0);
    return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(0.0031308, c));
}

// Пологие градиенты низкой альфы на тёмном фоне — худший случай для 8 бит на канал: ровный спад
// ложится видимыми полосами. Разбиваем их шумом в четверть младшего разряда: полосы исчезают,
// а само зерно на таком уровне неразличимо.
float dither(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453) - 0.5;
}

// Полиномиальный smooth-min: две поверхности сливаются перемычкой вместо стыка — именно это
// читается как «слайм», когда капля отрывается от лужи и втягивается обратно.
float smoothMin(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

// Поле капли-лого в единицах высоты кнопки, отрицательное внутри буквы. p — вектор от центра
// капли, box — сторона SDF-квадрата, angle — поворот капли.
float logoField(vec2 p, float box, float angle) {
    float c = cos(angle), s = sin(angle);
    vec2 rot = vec2(c * p.x + s * p.y, -s * p.x + c * p.y);
    vec2 local = rot / box;   // -0.5..0.5 внутри квадрата
    vec2 uv = clamp(local + 0.5, 0.0, 1.0);
    // Шаг производной берём ДО любых ветвлений: ниже стоит ранний выход, а внутри него неявные
    // производные уже не определены.
    vec2 duv = fwidth(uv);
    // Запас SDF — всего ±0.094 стороны, дальше поле упирается в полку и smooth-min растянул бы
    // вокруг буквы прямоугольный ореол. Продолжаем его расстоянием до bbox видимой буквы: буква
    // лежит ВНУТРИ bbox, поэтому max корректен как пересечение и полку срезает.
    vec2 q = abs(local) - vec2(GLYPH_HALF_W, GLYPH_HALF_H);
    float bbox = (min(max(q.x, q.y), 0.0) + length(max(q, vec2(0.0)))) * box;
    // За полкой max всё равно вернёт bbox — выборку текстуры там можно не делать. На большой
    // панели это снимает почти все сэмплы: буква занимает лишь малую долю её площади.
    if (bbox > LOGO_SDF_SCALE * box * 0.5) return bbox;
    // Глиф занимает в SDF-текстуре ~141 тексель, а на экране бывает 15–25 пикселей: это
    // минификация в 6–10 раз, мипмапов у текстуры нет, и ОДНА выборка на пиксель просто
    // прореживает поле. Соседние пиксели читают тексели через 6–10 штук, из-за чего контур
    // рвётся ступеньками, а при движении набор попавших текселей меняется — отсюда «кипение».
    // Четыре выборки по повёрнутой сетке усредняют поле в пределах пикселя и это снимают.
    // textureLod, а не texture: выборка живёт внутри ветки режима, где неявный LOD не определён;
    // уровень всё равно нулевой — мипмапов нет.
    float m = 0.25 * (
          textureLod(Sampler0, uv + duv * vec2(-0.375,  0.125), 0.0).a
        + textureLod(Sampler0, uv + duv * vec2( 0.125,  0.375), 0.0).a
        + textureLod(Sampler0, uv + duv * vec2( 0.375, -0.125), 0.0).a
        + textureLod(Sampler0, uv + duv * vec2(-0.125, -0.375), 0.0).a) - 0.5;
    float sdf = -m * LOGO_SDF_SCALE * box;
    return max(sdf, bbox);
}

// Высота поверхности воды в точке x (0..1 вдоль кнопки), в координатах quadCoord.y.
// Считается и для текущего фрагмента, и для центра каждой капли — иначе капли срезало бы по
// локальному уровню и они выглядели бы скошенными.
float surfaceAt(float x, float center, float spread, float hover, float cursor,
                float splashAge, float splashX, float tilt, float t) {
    float envelope = 1.0 - smoothstep(spread * 0.25, spread, abs(x - center));
    float wave = 0.55 * sin(x * 9.4 + t * 1.15)
               + 0.30 * sin(x * 16.1 - t * 0.87)
               + 0.15 * sin(x * 25.3 + t * 1.63);
    float level = mix(REST_LEVEL, HOVER_LEVEL, hover) * envelope;
    // Горб под курсором минус широкий пологий провал вокруг: масса не появляется из ниоткуда, а
    // стягивается с боков — поэтому поверхность ведёт себя вязко, а не просто вспучивается.
    // Прибавляется ПОСЛЕ огибающей лужи: иначе у краёв кнопки, где огибающая нулевая, курсор
    // ничего бы не поднимал.
    float drag = (x - cursor) / DRAG_WIDTH;
    float pull = exp(-drag * drag) - 0.30 * exp(-drag * drag * 0.16);
    level += DRAG_LIFT * hover * pull;
    // Наклон против движения курсора: жидкость по инерции отстаёт от него, а не бежит впереди.
    level -= TILT_AMP * tilt * (x - 0.5) * 2.0;
    // Кольцо всплеска: гребень уходит от точки клика и гаснет к концу жизни.
    if (splashAge > 0.0 && splashAge < 1.0) {
        float front = (abs(x - splashX) - splashAge * SPLASH_SPEED) / SPLASH_WIDTH;
        level += SPLASH_LIFT * exp(-front * front) * (1.0 - splashAge);
    }
    float amplitude = (0.030 + 0.070 * hover) * envelope;
    return 1.0 - level + wave * amplitude;
}

void main() {
    fragColor = vec4(0.0);

    // Размер квада выводим из производных локальных координат — передавать его не нужно, и он
    // автоматически учитывает и gui-scale, и масштаб матрицы (ховер-зум кнопки).
    vec2 uvWidth = max(fwidth(quadCoord), vec2(0.000001));
    vec2 rectSize = 1.0 / uvWidth;
    vec2 halfSize = rectSize * 0.5;
    vec2 pos = quadCoord * rectSize - halfSize;

    float hover = clamp(slimeParams.x, 0.0, 1.0);
    // Плотность в шейдер не едет: её уже вмешали в альфу вершинного цвета, а канал отдан курсору.
    float cursor = clamp(slimeParams.y, 0.0, 1.0);
    float splashAge = slimeSplash.x;
    float splashX = slimeSplash.y;
    float tilt = slimeSplash.z;
    // 0 — вода с каплями, 0.25 — без них, 1 — слой поверх текста.
    float mode = clamp(slimeParams.z, 0.0, 1.0);
    bool overlay = mode > 0.875;          // тот же расчёт воды, но слоем поверх текста
    float t = slimeTime;

    // Маска — форма фигуры, поджатая на пиксель кадра: иначе жижа лезла бы на сглаженную кромку подложки.
    float shape = roundedBox(pos, halfSize, radiusData) + INSET_PX;
    float shapeWidth = max(length(vec2(dFdx(shape), dFdy(shape))), 1e-6);
    float mask = clamp(0.5 - shape / shapeWidth, 0.0, 1.0);
    if (mask <= 0.0) discard;
    float u = clamp(quadCoord.x, 0.0, 1.0);
    float v = clamp(quadCoord.y, 0.0, 1.0);
    // Поле живёт в ЕДИНИЦАХ ВЫСОТЫ фигуры: по X делим на пропорции, иначе на длинной кнопке
    // буквы растянуло бы поперёк. Пропорция от зума не зависит.
    float aspect = rectSize.x / max(rectSize.y, 0.0001);

    // Центр лужи дрейфует вдоль фигуры.
    float center = 0.5 + 0.16 * sin(t * 0.23) + 0.07 * sin(t * 0.41 + 1.7);
    // Длины ниже живут в тех же единицах, что и field, — то есть в долях ВЫСОТЫ фигуры.
    float spread = mix(REST_SPREAD, HOVER_SPREAD, hover);
    float field = surfaceAt(u, center, spread, hover, cursor, splashAge, splashX, tilt, t) - v;

    // Капли-лого есть только у SLIME. У WATER их нет: на мелких плашках аккаунтов буква
    // выходит мельче собственной кромки и читается шумом, а не логотипом.
    if (mode < 0.125) {
        // Три капли всплывают над лужей, покачиваются и втягиваются обратно. smoothMin сливает
        // их с телом перемычкой — буква ВЫТЕКАЕТ из воды, а не летает поверх неё. Коэффициент
        // слияния держим маленьким: на большом буква оплывает и перестаёт читаться.
        float box = 1.15 + 0.55 * hover;   // сторона SDF-квадрата в единицах высоты кнопки
        for (int i = 0; i < 3; i++) {
            float index = float(i);
            float blobX = center + spread * 0.72 * sin(t * (0.53 + 0.17 * index) + index * 2.4);
            float rise = 0.5 + 0.5 * sin(t * (0.71 + 0.13 * index) + index * 1.3);
            float blobY = surfaceAt(blobX, center, spread, hover, cursor, splashAge, splashX, tilt, t)
                    - rise * (0.14 + 0.34 * hover);
            float angle = 0.35 * sin(t * (0.44 + 0.11 * index) + index * 1.9);
            vec2 rel = vec2((u - blobX) * aspect, v - blobY);
            field = smoothMin(field, logoField(rel, box * (0.82 + 0.18 * index), angle),
                    0.06 + 0.06 * hover);
        }
    }
    float edge = 0.060 + 0.030 * hover;    // мягкость кромки поля
    float hazeLen = 0.22 + 0.18 * hover;   // длина затухания дымки наружу
    float rimLen = 0.050 + 0.020 * hover;  // ширина светлого гребня по кромке
    float depthLen = 0.55;                 // на какой глубине масса дотемняется до дна

    // Пиксельный пол сглаживания. Ширину пикселя кадра В ЕДИНИЦАХ ПОЛЯ берём из производных
    // САМОГО поля, а не из размера фигуры: после smooth-min, аспекта и «дыхания» контура градиент
    // поля уже не единичный, и оценка «пиксель = 1/высота» врала бы. Именно этот пол не даёт
    // переходу стать уже пикселя на мелких буквах — там, где иначе контур идёт ступеньками, и в
    // движении они читаются как ползающие точки. Долевые значения из веток задают художественную
    // мягкость и побеждают, когда фигура крупная.
    float fieldPx = max(length(vec2(dFdx(field), dFdy(field))), 1e-6);
    edge = max(edge, fieldPx * 2.0);
    rimLen = max(rimLen, fieldPx * 1.5);

    // Кромка намеренно мягкая (не пиксельное AA): масса читается как подсвеченная жижа, а не как
    // залитая фигура. Наружу от поля уходит дымка, по самой кромке идёт светлый мениск.
    float body = 1.0 - smoothstep(-edge, edge, field);
    float haze = exp(-max(field, 0.0) / hazeLen) * (1.0 - body);
    float rim = exp(-abs(field) / rimLen);

    vec3 base = linearToSrgb(vertexColor.rgb);
    float depth = clamp(-field / depthLen, 0.0, 1.0);
    vec3 rgb = mix(base, base * 0.42, depth);               // ко дну темнее — объём массы
    rgb = mix(rgb, mix(base, vec3(1.0), 0.55), rim * 0.8);  // светлый гребень по поверхности

    float alpha = vertexColor.a
        * (body * (0.50 + 0.28 * hover)
         + haze * (0.20 + 0.20 * hover)
         + rim * (0.22 + 0.18 * hover));

    if (overlay) alpha *= OVERLAY_ALPHA;
    alpha += dither(gl_FragCoord.xy) * (0.25 / 255.0);

    fragColor = vec4(rgb, clamp(alpha, 0.0, 1.0) * mask) * ColorModulator;
    if (fragColor.a <= 0.0005) discard;
}
