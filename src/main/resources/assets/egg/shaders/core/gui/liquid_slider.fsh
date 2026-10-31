#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 quadCoord;
in vec3 sliderMotion;
in vec4 sliderState;
in float sliderTime;

out vec4 fragColor;

// Все длины — в долях ВЫСОТЫ квада: виджет растёт вместе с gui-scale, а пропорции капли, желобка
// и шлейфа остаются прежними. Ширина дорожки выводится из аспекта квада.
// PAD обязан совпадать с раскладкой в Java: ширина квада = TRACK + 2 * PAD * h.
const float PAD = 0.45;        // от торца квада до крайнего уровня: место под каплю и шлейф
const float GROOVE = 0.085;    // полутолщина стеклянного желобка
const float FILL = 0.058;      // полутолщина воды в желобке
const float DOT_R = 0.040;     // засечка уровня
const float DROP_R = 0.195;    // радиус капли в покое
const float TRAIL = 0.50;      // длина шлейфа на полной скорости, доля длины дорожки
const float MERGE = 0.10;      // перемычка между водой и каплей (smooth-min)
const float VEL_SCALE = 3.0;   // Java отдаёт velocity = tanh(vel / 3), vel в дорожках/с
const float WALL = 0.08;       // на сколько капля заходит за крайний уровень, упираясь в него

vec3 linearToSrgb(vec3 c) {
    c = clamp(c, 0.0, 1.0);
    return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(0.0031308, c));
}

float hash(float n) {
    return fract(sin(n * 91.345) * 47453.5453);
}

float dither(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453) - 0.5;
}

float smoothMin(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

// Капсула вдоль оси X от a до b с полутолщиной r.
float capsule(vec2 p, float a, float b, float r) {
    return length(vec2(p.x - clamp(p.x, a, b), p.y)) - r;
}

// Приближённое расстояние до эллипса (градиентная оценка). В самом центре градиент вырождается
// (0/0) — там точка заведомо внутри, отдаём глубину по малой полуоси.
float ellipse(vec2 q, vec2 ab) {
    float k0 = length(q / ab);
    float k1 = length(q / (ab * ab));
    if (k1 < 1e-4) return -min(ab.x, ab.y);
    return k0 * (k0 - 1.0) / k1;
}

// Аналитический смаз капли за выдержку: центр идёт от 0 назад на L (s = 0 — сейчас, s = 1 — конец
// выдержки). Точка покрыта, пока |q.x + L·s| <= полуширина эллипса на её высоте, — это отрезок
// по s, и доля выдержки считается формулой, без выборок и без прошлого кадра.
// Вес 2(1-s) — у головы плотнее, к хвосту гаснет линейно (кубический вес съедал хвост на обычном драге).
float motionCoverage(vec2 q, vec2 ab, float L, float px) {
    float yy = q.y / ab.y;
    float halfW = ab.x * sqrt(max(1.0 - yy * yy, 0.0)) + px * 0.5;
    float yCov = clamp((ab.y - abs(q.y)) / px + 0.5, 0.0, 1.0);
    float s0 = (-q.x - halfW) / L;
    float s1 = (-q.x + halfW) / L;
    float lo = clamp(min(s0, s1), 0.0, 1.0);
    float hi = clamp(max(s0, s1), 0.0, 1.0);
    float a = 1.0 - lo;
    float b = 1.0 - hi;
    return (a * a - b * b) * yCov;
}

void over(inout vec4 acc, vec3 color, float alpha) {
    alpha = clamp(alpha, 0.0, 1.0);
    acc.rgb = color * alpha + acc.rgb * (1.0 - alpha);
    acc.a = alpha + acc.a * (1.0 - alpha);
}

void main() {
    fragColor = vec4(0.0);

    vec2 uvWidth = max(fwidth(quadCoord), vec2(0.000001));
    vec2 rectSize = 1.0 / uvWidth;
    float px = 1.0 / rectSize.y;
    float aspect = rectSize.x / rectSize.y;
    vec2 p = vec2(quadCoord.x * aspect, quadCoord.y - 0.5);

    float pos = sliderMotion.x;
    float velocity = clamp(sliderMotion.y, -1.0, 1.0);
    float waveAge = sliderMotion.z;
    float hover = sliderState.x;
    float press = sliderState.y;
    float waveAt = sliderState.z;
    float pulseAge = sliderState.w;
    float t = sliderTime;

    float trackL = PAD;
    float trackR = aspect - PAD;
    float len = trackR - trackL;
    float level = clamp(pos, 0.0, 1.0);
    // Упор в крайний уровень: за 0..1 капля почти не едет, а сплющивается о стенку.
    float overshoot = pos - level;
    float squash = min(abs(overshoot) * 4.0, 0.3);
    float dropX = trackL + level * len + sign(overshoot) * WALL * (1.0 - exp(-abs(overshoot) / 0.03));
    // Дорожка сама показывает уровень: вода «оживает» к «Хорошей», блики появляются к «Высокой».
    float rich = smoothstep(0.18, 0.5, level);
    float spark = smoothstep(0.62, 0.98, level);
    float speed = abs(velocity);
    float dir = velocity < 0.0 ? -1.0 : 1.0;
    // Импульс нажатия: вспышка ядра гаснет за ~0.1 с, ореол — за ~0.3 с.
    float pulseOn = step(pulseAge, 0.999);
    float flash = exp(-9.0 * pulseAge) * pulseOn;
    float bloom = exp(-6.0 * pulseAge) * (1.0 - pulseAge) * pulseOn;

    // Мягкое гашение у кромок квада: свечение и волны не обрезаются прямой линией.
    float edgeFade = smoothstep(0.0, 0.22, p.x) * smoothstep(0.0, 0.22, aspect - p.x)
                   * smoothstep(0.5, 0.28, abs(p.y));

    vec3 base = linearToSrgb(vertexColor.rgb);
    vec3 light = mix(base, vec3(1.0), 0.55);
    vec4 acc = vec4(0.0);

    // Капля: при зажатии сплющивается (как ползунок настроек), на скорости вытягивается вдоль хода,
    // об крайний уровень — сплющивается по X и вытягивается по Y.
    float r = DROP_R * (1.0 + 0.10 * hover);
    float stretch = min(speed * 1.2, 1.0);
    vec2 ab = vec2(r * (1.0 + 0.50 * stretch + 0.26 * press) * (1.0 - squash),
                   r * (1.0 - 0.18 * stretch - 0.20 * press) * (1.0 + 0.4 * squash));
    float center = dropX - dir * r * 0.275 * stretch - sign(overshoot) * r * squash * 0.5;
    vec2 q = p - vec2(center, 0.0);
    float drop = ellipse(q, ab);

    // Свечение вокруг капли: в покое еле заметно и только на «живых» уровнях, ховер и зажатие добавляют,
    // нажатие даёт короткий широкий ореол.
    float outside = max(drop, 0.0);
    float halo = exp(-outside / 0.17) * (0.05 * rich + 0.10 * hover + 0.20 * press);
    over(acc, base, halo * edgeFade);
    over(acc, light, exp(-outside / (0.16 + 0.22 * pulseAge)) * 0.50 * bloom * edgeFade);

    // Стеклянный желобок: чуть светлее подложки (нейтраль 10–15%), объём — тень по верху и кант по низу.
    float groove = capsule(p, trackL, trackR, GROOVE);
    float grooveCov = clamp(0.5 - groove / px, 0.0, 1.0);
    over(acc, mix(vec3(0.85), base, 0.12), grooveCov * (0.10 + 0.05 * hover));
    float inner = clamp(-groove / (GROOVE * 1.2), 0.0, 1.0);
    over(acc, vec3(0.0), grooveCov * (1.0 - step(0.0, p.y)) * (1.0 - inner) * 0.35);
    float lip = exp(-abs(groove) / (px * 1.3));
    over(acc, vec3(1.0), lip * step(0.0, p.y) * (0.12 + 0.08 * hover));

    // Пятно света в желобке под каплей: зажатие читается и в статике, без контуров поверх.
    float underDx = (p.x - dropX) / 0.95;
    float underDy = p.y / 0.16;
    float under = exp(-underDx * underDx) * (0.08 * hover + 0.30 * press + 0.25 * flash);
    over(acc, light, under * max(grooveCov, exp(-underDy * underDy) * 0.45) * edgeFade);

    // Огоньки нажатия: два коротких света разбегаются по желобку в обе стороны, только внутри него.
    if (pulseOn > 0.5) {
        float fade = (1.0 - pulseAge) * (1.0 - pulseAge) * (1.0 - pulseAge);
        float run = r + pulseAge * 0.9;
        float left = (p.x - (dropX - run)) / 0.07;
        float right = (p.x - (dropX + run)) / 0.07;
        float sparks = exp(-left * left) + exp(-right * right);
        over(acc, light, sparks * grooveCov * fade * 0.40);
    }

    // Засечки уровней, ещё не залитые водой. Ближайшая при зажатии обводится — туда встанет капля.
    float target = trackL + floor(level * 2.0 + 0.5) * 0.5 * len;
    for (int k = 0; k < 3; k++) {
        float dx = trackL + float(k) * 0.5 * len;
        float dotSdf = length(p - vec2(dx, 0.0)) - DOT_R;
        float unfilled = smoothstep(dropX - 0.02, dropX + 0.06, dx);
        over(acc, vec3(1.0), clamp(0.5 - dotSdf / px, 0.0, 1.0) * unfilled * (0.30 + 0.16 * hover));
    }
    // На быстрой перемотке ближайший уровень мелькает — кольцо проявляется, только когда капля притормозила.
    float targetAway = smoothstep(r * 0.9, r * 1.8, abs(target - dropX)) * (1.0 - smoothstep(0.15, 0.5, speed));
    float targetRing = abs(length(p - vec2(target, 0.0)) - (0.13 + 0.015 * sin(t * 7.0)));
    over(acc, base, press * targetAway * clamp(1.0 - (targetRing - px * 0.6) / px, 0.0, 1.0) * 0.6);

    // Вода в желобке до капли. У капли толще — масса подтягивается к ней, а smooth-min даёт
    // перемычку: капля не лежит поверх воды, а вытекает из неё.
    float nearDrop = exp(-max(dropX - p.x, 0.0) / 0.35);
    float fillR = FILL * (1.0 + (0.35 + 0.45 * press) * nearDrop);
    float fill = capsule(p, trackL, dropX, fillR);
    float liquid = smoothMin(fill, drop, MERGE);
    float liquidCov = clamp(0.5 - liquid / px, 0.0, 1.0);

    // Течение к капле: длинные мягкие полосы (частые на толщине воды в 2–3 пикселя читаются пунктиром).
    // Время не умножается на скорость: при t в сотни секунд любое изменение скорости дёргало бы полосы,
    // поэтому разгон течения берётся из пройденного каплей пути — он непрерывен.
    float flow = t * 0.9 + 0.5 * dropX + 0.8 * press;
    float bands = smoothstep(0.35, 1.0, sin((p.x - flow * 0.35) * 5.2) * 0.6 + sin((p.x - flow * 0.22) * 2.9 + 1.3) * 0.4);
    vec3 fillColor = base * (0.66 + 0.30 * nearDrop) + (light - base) * bands * rich * 0.45;
    // Блики на «Высокой»: редкая искра вспыхивает раз в несколько секунд и уплывает к капле.
    float cell = floor((p.x - t * 0.30) * 4.0);
    float cellX = (cell + 0.5) / 4.0 + t * 0.30;
    float glintDx = (p.x - cellX) / 0.07;
    float glint = pow(max(sin(t * 1.7 + hash(cell) * 6.2832), 0.0), 24.0) * exp(-glintDx * glintDx);
    fillColor = mix(fillColor, vec3(1.0), clamp(glint * spark * 0.75, 0.0, 0.7));

    // Тело капли: объём светом сверху, мениск по кромке, блик-линза. Зажатие и вспышка светлят ядро.
    vec2 n = q / ab;
    vec3 dropColor = mix(base * 0.85, mix(base, vec3(1.0), 0.30), smoothstep(0.7, -0.9, n.y));
    float core = 1.0 - clamp(length(n), 0.0, 1.0);
    dropColor = mix(dropColor, light, press * 0.35 * core);
    dropColor = mix(dropColor, vec3(1.0), flash * 0.65 * (0.35 + 0.65 * core));
    float inDrop = clamp(0.5 - drop / px, 0.0, 1.0);
    vec3 liquidColor = mix(fillColor, dropColor, smoothstep(0.02, -0.03, drop));

    // Моушен блюр: доля выдержки, которую капля провела над пикселем. Голова держит не меньше 65%
    // плотности — это ползунок, а не пуля.
    // Шлейф не длиннее пути от стенки: за крайний уровень капля не заходила, иначе хвост режется кромкой квада.
    float room = max(dir > 0.0 ? center - trackL + WALL : trackR + WALL - center, 0.0);
    float L = dir * min(abs(velocity) * TRAIL * len, room);
    float moving = smoothstep(px * 0.5, px * 3.0, abs(L));
    float smear = abs(L) > px * 0.25 ? motionCoverage(q, ab, L, px) : 0.0;
    float headKeep = 1.0 - 0.35 * stretch;
    float coverage = max(liquidCov * mix(1.0, headKeep, moving * inDrop), smear * moving);
    over(acc, liquidColor, coverage * (0.78 + 0.12 * hover + 0.10 * press));

    float rim = exp(-abs(liquid) / (px * 1.4)) * liquidCov;
    over(acc, light, rim * (0.22 + 0.18 * hover) * (1.0 - moving * 0.6));
    vec2 sp = (n - vec2(-0.32, -0.42)) * vec2(1.0, 1.4);
    float specular = exp(-dot(sp, sp) / 0.07);
    over(acc, vec3(1.0), specular * inDrop * (0.55 + 0.25 * hover) * (1.0 - 0.5 * moving));

    // Засечки поверх воды: залитые — светлые бусины; на скорости пройденная засечка коротко
    // вспыхивает гало (время с прохода = пройденный путь / скорость, без состояния).
    float realSpeed = max(atanh(min(speed, 0.995)) * VEL_SCALE * len, 1e-3);
    float fast = smoothstep(0.12, 0.35, speed);
    for (int k = 0; k < 3; k++) {
        float dx = trackL + float(k) * 0.5 * len;
        vec2 d = p - vec2(dx, 0.0);
        float dotSdf = length(d) - DOT_R * 0.85;
        float filled = 1.0 - smoothstep(dropX - 0.02, dropX + 0.06, dx);
        float hidden = smoothstep(r * 0.6, r * 1.2, abs(dx - center));
        over(acc, light, clamp(0.5 - dotSdf / px, 0.0, 1.0) * filled * hidden * 0.55);

        float passed = (dropX - dx) * dir;
        float since = max(passed, 0.0) / realSpeed;
        float tickFlash = step(0.0, passed) * exp(-since / 0.14) * fast;
        float haloD = length(d) / 0.10;
        over(acc, light, exp(-haloD * haloD) * tickFlash * hidden * 0.35);
    }

    // Волна приземления: капля встала на уровень — по желобку в обе стороны бегут два светлых гребня,
    // а над каплей расходится едва заметный ореол.
    if (waveAge > 0.0 && waveAge < 1.0) {
        float wx = trackL + waveAt * len;
        float fade = (1.0 - waveAge) * (1.0 - waveAge);
        float front = (abs(p.x - wx) - (r + waveAge * 1.6)) / (0.10 + 0.12 * waveAge);
        // По воде гребень ярче, по пустому стеклу — вдвое тише: там светлое пятно читалось призраком.
        float crest = exp(-front * front) * (liquidCov * 0.75 + grooveCov * (1.0 - liquidCov) * 0.35);
        over(acc, light, crest * fade);
        // Квадрат вручную: pow() с отрицательным основанием в GLSL не определён.
        float ringD = (length((p - vec2(wx, 0.0)) * vec2(1.0, 1.5)) - (r + waveAge * 0.35)) / 0.09;
        over(acc, base, exp(-ringD * ringD) * fade * 0.28 * edgeFade);
    }

    if (acc.a <= 0.0005) discard;
    float alpha = acc.a * vertexColor.a + dither(gl_FragCoord.xy) * (0.25 / 255.0);
    fragColor = vec4(acc.rgb / acc.a, clamp(alpha, 0.0, 1.0)) * ColorModulator;
    if (fragColor.a <= 0.0005) discard;
}
