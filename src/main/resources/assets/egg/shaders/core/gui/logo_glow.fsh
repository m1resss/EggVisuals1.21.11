#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec4 vertexColor;
in vec2 texCoord;
in float energy;
in float flash;
in float phase;

out vec4 fragColor;

// Основное лого из света: полая «S» — светлый кант по контуру, мягкий ореол, тёмное полупрозрачное тело
// акцента с диагональными шёлковыми струями. След мыши гонит по букве кольца ряби, резкое движение
// вдали сносит струи по ходу мыши и подсвечивает задний край. Внутренняя дистанция SDF — только через
// smoothstep с плато до 0.024 (гребень перекладин 0.031–0.0335), ореол держит плато в просветах буквы.
const float SDF_SCALE = 0.1875;
const float TAU = 6.2831853;
// Волновое число ряби: длина волны 0.065 UV квадрата лого.
const float RING_K = 96.7;

vec2 decodePos(int i) {
    vec4 t = texelFetch(Sampler1, ivec2(i, 0), 0);
    return vec2(t.r * 65280.0 + t.g * 255.0, t.b * 65280.0 + t.a * 255.0) / 65535.0 * 2.0 - 0.5;
}

// Хэши без sin: на больших координатах sin-хэш теряет точность и зерно слипается в узор.
uint mixBits(uint x) {
    x ^= x >> 16;
    x *= 0x7feb352du;
    x ^= x >> 15;
    x *= 0x846ca68bu;
    x ^= x >> 16;
    return x;
}

vec2 hash22(vec2 p) {
    uvec2 b = floatBitsToUint(p);
    uint h = mixBits(b.x ^ mixBits(b.y + 0x9e3779b9u));
    return vec2(float(h >> 8), float(mixBits(h) >> 8)) * (1.0 / 16777216.0);
}

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

// Градиентный шум с квинтикой: x — значение, yz — производная. C2-гладкий, без заломов.
vec3 gradNoise(vec2 x) {
    vec2 i = floor(x);
    vec2 f = fract(x);
    vec2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    vec2 du = 30.0 * f * f * (f * (f - 2.0) + 1.0);
    vec2 ga = hash22(i) * 2.0 - 1.0;
    vec2 gb = hash22(i + vec2(1.0, 0.0)) * 2.0 - 1.0;
    vec2 gc = hash22(i + vec2(0.0, 1.0)) * 2.0 - 1.0;
    vec2 gd = hash22(i + vec2(1.0, 1.0)) * 2.0 - 1.0;
    float va = dot(ga, f);
    float vb = dot(gb, f - vec2(1.0, 0.0));
    float vc = dot(gc, f - vec2(0.0, 1.0));
    float vd = dot(gd, f - vec2(1.0, 1.0));
    float k = va - vb - vc + vd;
    float value = va + u.x * (vb - va) + u.y * (vc - va) + u.x * u.y * k;
    vec2 deriv = ga + u.x * (gb - ga) + u.y * (gc - ga) + u.x * u.y * (ga - gb - gc + gd)
            + du * (u.yx * k + vec2(vb, vc) - va);
    return vec3(value, deriv);
}

float valueNoise(vec2 x) {
    vec2 i = floor(x);
    vec2 f = fract(x);
    vec2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    float a = hash22(i).x;
    float b = hash22(i + vec2(1.0, 0.0)).x;
    float c = hash22(i + vec2(0.0, 1.0)).x;
    float d = hash22(i + vec2(1.0, 1.0)).x;
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float dither(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z) - 0.5;
}

// Гауссова капсула вдоль отрезка ab (σ = 0.02 UV): квадрат расстояния гладкий, маска без изломов.
float capsule(vec2 p, vec2 a, vec2 b) {
    vec2 ab = b - a;
    float t = clamp(dot(p - a, ab) / dot(ab, ab), 0.0, 1.0);
    vec2 off = p - a - ab * t;
    return exp(-dot(off, off) * 2500.0);
}

// Нить на фазе t: номер меняется во впадине, где яркость ровно 0, — стык невидим.
// resolve уводит нить в среднее (35/128 · 0.85), когда её шаг мельче ~3 px экрана.
float threadLine(float t, float salt, float resolve) {
    float raw = 0.5 + 0.5 * cos(TAU * t);
    raw *= raw;
    raw *= raw;
    float pick = hash11(floor(t + 0.5) * 1.37 + salt);
    return mix(0.2324, raw * (0.2 + 1.5 * pick * pick), resolve);
}

void main() {
    // Первый тап и производные — до discard. px — UV квадрата на пиксель кадра.
    float d0 = (0.5 - texture(Sampler0, texCoord).a) * SDF_SCALE;
    vec2 duvx = dFdx(texCoord);
    vec2 duvy = dFdy(texCoord);
    float px = sqrt(max(0.5 * (dot(duvx, duvx) + dot(duvy, duvy)), 1e-12));
    // Ореол гаснет к 0.038 UV, рябь сдвигает кромку не больше чем на 0.006: дальше 0.046 фрагмент пустой
    // (поле квада M = 0.085 с запасом).
    if (d0 >= 0.046) discard;

    vec4 mouse = texelFetch(Sampler1, ivec2(28, 0), 0);
    float spd = mouse.r;
    vec2 windDir = mouse.ba * 2.0 - 1.0;
    windDir /= max(length(windDir), 1e-3);

    // След мыши: у точки следа гребёнка сдвигает струи по ходу, от неё расходится пакет ряби.
    // Пакеты сводятся максимумом неотрицательного профиля гребня: соседние не складываются в засветку
    // и не гасят друг друга в серые соты. refraction — насколько кольца и гребёнка сжимают нити на экране.
    // Гребень узкий (h³); на GUI scale 1 широкий (профиль h): узкий в 2 пикселя рассыпается на бусины.
    float ringSharp = smoothstep(10.0, 16.0, 0.065 / px);
    vec2 p = texCoord - 0.5;
    vec2 comb = vec2(0.0);
    vec2 warp = vec2(0.0);
    vec2 slope = vec2(0.0);
    float crest = 0.0;
    float packet = 0.0;
    float refraction = 0.0;
    for (int i = 0; i < 12; i++) {
        vec4 imp = texelFetch(Sampler1, ivec2(16 + i, 0), 0);
        if (imp.r <= 0.0) continue;
        vec2 q = texCoord - decodePos(i);
        float r = length(q);
        float age = imp.g;
        // Кольцо расходится как по воде, с замедлением; с возрастом пакет раскрывается из одного гребня в 2–3.
        float span = 0.036 + 0.026 * age;
        float x = r - (0.014 + 0.17 * sqrt(age));
        // Вне пакета и вне гребёнки вклад меньше 2e-3 — пропускаем.
        if (abs(x) > span * 2.6 && r > 0.08) continue;
        float life = 1.0 - age;
        vec2 dir = imp.ba * 2.0 - 1.0;
        dir /= max(length(dir), 1e-3);
        vec2 radial = q / (r + 0.003);
        float combWeight = exp(-r * r / 0.0010) * imp.r * life * life;
        comb += dir * combWeight;
        // Точки следа идут через ~длину волны: дуги поперёк следа от соседних точек складываются в лесенку,
        // поэтому их приглушаем, но не гасим — боковые дуги ведут кольцо, а целиком оно всё ещё читается.
        float side = radial.x * dir.y - radial.y * dir.x;
        float amp = imp.r * life * (1.0 - 0.5 * age) * smoothstep(0.0, 0.1, age) * (0.15 + 0.85 * side * side)
                * smoothstep(0.0, 0.02, r);
        float env = exp(-x * x / (span * span)) * amp;
        float ripple = cos(x * RING_K);
        float h = 0.5 + 0.5 * ripple;
        crest = max(crest, env * h * mix(1.0, h * h, ringSharp));
        packet += env;
        slope -= radial * sin(x * RING_K) * env;
        warp += radial * ripple * env * 0.0025;
        float bend = 0.003 * smoothstep(0.01, 0.035, r);
        p += radial * ripple * env * bend;
        // Производная сдвига: bend·K от колец и ~27·0.015 от гаусса гребёнки.
        refraction += env * bend * RING_K + combWeight * 0.4;
    }
    p -= comb * 0.010 / (1.0 + length(comb));
    float ringResolve = smoothstep(3.0, 6.0, 0.065 / px);
    crest = ringResolve * (1.0 - exp(-2.2 * crest));
    // Впадины — не отдельные ямы каждого кольца (их пересечения дают тёмные соты), а общее гладкое
    // затемнение пакета между гребнями: сумма огибающих не имеет изломов.
    float dip = (1.0 - exp(-1.5 * packet)) * (1.0 - crest) * ringResolve;
    slope = slope / (1.0 + 0.6 * length(slope)) * ringResolve;
    // Под пакетом ряби струи и отлив приглушаются к среднему — кольца читаются поверх узора.
    float calm = 0.4 * (1.0 - exp(-2.0 * packet)) * ringResolve;

    // Кромка колышется от ряби: мягкий кламп до 0.006 UV и гашение к полю квада.
    warp *= 0.006 / sqrt(0.006 * 0.006 + dot(warp, warp));
    warp *= 1.0 - smoothstep(0.05, 0.08, d0);
    float d = (0.5 - textureLod(Sampler0, texCoord + warp, 0.0).a) * SDF_SCALE;

    // Течение: наклон струй медленно покачивается, смещения октав ходят по кругам с несоизмеримыми периодами.
    // Резкое движение мыши доворачивает струи к ходу и сносит их: вдоль себя полностью, поперёк — на 0.3,
    // иначе при кружении мышью узор поперёк струй стробит. Снос — только от скорости: при развороте мыши
    // windDir переворачивается за кадр, а windDir·spd непрерывен.
    float tilt = 0.42 + 0.12 * sin(phase * 0.071);
    vec2 along0 = vec2(cos(tilt), sin(tilt));
    // dot·cross = sin(2θ)/2: доворот гладкий при любом повороте направления, без скачка на перпендикуляре.
    tilt += 0.2 * spd * dot(along0, windDir) * (along0.x * windDir.y - along0.y * windDir.x);
    vec2 alongDir = vec2(cos(tilt), sin(tilt));
    vec2 across = vec2(-alongDir.y, alongDir.x);
    vec2 windShift = windDir * (spd * 0.062);
    vec2 flowP = p - alongDir * dot(windShift, alongDir) - across * (0.3 * dot(windShift, across));
    float turb = 1.0 + 0.12 * energy;
    vec2 drift1 = 1.7 * vec2(cos(phase * 0.043), sin(phase * 0.043));
    vec2 drift2 = 1.3 * vec2(sin(phase * 0.067 + 1.3), cos(phase * 0.059));
    vec3 n1 = gradNoise(flowP * 6.0 + drift1);
    vec3 n2 = gradNoise(flowP * 14.0 + drift2 + 7.3);
    float a1 = 0.028 * turb;
    float a2 = 0.0065 * turb;
    float psi = dot(flowP, across) + a1 * n1.x + a2 * n2.x;
    // Шаг линии тока на пиксель кадра.
    float psiPx = length(across + a1 * 6.0 * n1.yz + a2 * 14.0 * n2.yz) * px;

    // Курсор над окном чуть подсвечивает свет под собой, быстрым ведением — сильнее.
    vec2 toCursor = texCoord - decodePos(12);
    float lens = exp(-dot(toCursor, toCursor) / 0.0012) * mouse.g * (0.12 + 0.25 * spd);

    // Пока скорость мыши набирается или спадает, узор сносит на пиксели за кадр: тонкие нити и мелкий отлив
    // смазываются к среднему (spd·(1−spd) — ноль в покое и на ровном ходу), иначе они стробят.
    float windBlur = 3.2 * spd * (1.0 - spd);

    // Нити: два семейства (второе в золотое сечение гуще); мельче ~3 px уходят в среднее.
    float thread = 0.0;
    for (int f = 0; f < 2; f++) {
        float threadK = f == 0 ? 820.0 : 1330.0;
        float resolve = (1.0 - smoothstep(0.19, 0.40, psiPx * (1.0 + min(refraction, 2.0)) * threadK / TAU))
                * (1.0 - 0.85 * windBlur);
        thread += threadLine(psi * threadK / TAU + float(f) * 0.37, 24.0 + float(f) * 57.0, resolve) * (f == 0 ? 0.65 : 0.35);
    }
    thread = max(0.2324 + (thread - 0.2324) * (1.0 + 0.35 * energy), 0.0);
    thread = mix(thread, 0.2324, calm);

    // Шёлковый отлив: шум в координатах (вдоль струи, номер линии тока) — длинные полосы по току.
    // На GUI scale 1 мелкая октава (шаг 0.0114) уходит в среднее, крупная теряет контраст, но не рисунок.
    float fine = smoothstep(1.2, 2.6, 0.0114 / psiPx) * (1.0 - windBlur);
    float coarse = smoothstep(2.2, 4.5, 0.026 / psiPx);
    vec2 sheenCoord = vec2(dot(flowP, alongDir) * 5.0 - phase * 0.34, psi * 38.0);
    float sheenFine = valueNoise(sheenCoord * vec2(2.1, 2.3) + vec2(3.1, 5.7) - vec2(phase * 0.35, 0.0));
    float sheen = valueNoise(sheenCoord) * 0.65 + mix(0.5, sheenFine, fine) * 0.35;
    sheen = smoothstep(mix(0.05, 0.25, coarse), mix(1.05, 0.85, coarse), sheen);
    sheen = mix(sheen, 0.45, max(0.45 * windBlur, calm));

    // Инерция: свет отстаёт от мыши и набегает на задний по ходу край буквы.
    float slosh = clamp(-dot(texCoord - vec2(0.5, 0.497), windDir) * 6.0, -1.0, 1.0) * spd;

    float breathe = 0.9 + 0.1 * sin(phase * 1.1);
    float inner = smoothstep(0.0, 0.024, -d);
    float body = clamp(0.5 - d / (px * 1.25), 0.0, 1.0);

    // Тело: тёмное полупрозрачное, струи и отлив поднимают его яркость; ряби в плотности тела нет.
    float glowLevel = (0.26 + 0.36 * sheen + 0.34 * thread * (0.4 + 0.6 * sheen) + 0.08 * inner)
            * breathe * (1.0 + 0.30 * energy) * (1.0 + 0.22 * slosh);
    glowLevel = min(glowLevel, 0.92);
    float bodyA = body * (glowLevel + (0.95 - glowLevel) * (1.0 - exp(-1.4 * lens)));

    // Впадины ряби уходят в глубокий акцент без потери альфы — тело между гребнями тёмное, не серое.
    vec3 base = vertexColor.rgb;
    float lightness = thread * (0.45 + 0.55 * sheen) + 0.35 * sheen + 0.2 * max(slosh, 0.0);
    vec3 bodyRgb = mix(base * 0.62, base, smoothstep(0.05, 0.75, lightness));
    bodyRgb = mix(bodyRgb, vec3(1.0), min(0.24 * smoothstep(0.7, 1.3, lightness) + 0.08 * flash, 0.3));
    bodyRgb = mix(bodyRgb, base * 0.55, dip * 0.5);

    // Кант — профиль пробы exp(−|d|/w). Хвост снаружи гаснет к 0.013 (медиальная ось просветов от 0.014),
    // внутри — к 0.024 (гребень перекладин 0.031): за этими дистанциями кант ровно ноль.
    float rimTail = d > 0.0 ? 1.0 - smoothstep(0.006, 0.013, d) : 1.0 - smoothstep(0.012, 0.024, -d);
    float rim = exp(-abs(d) / max(px * 1.4, 0.0009)) * rimTail
            * (0.5 + 0.2 * sheen + 0.3 * flash + 0.3 * crest) * (1.0 - 0.4 * inner) * (1.0 + 0.2 * slosh);
    rim = min(rim, 1.0);
    vec3 rimRgb = mix(base, vec3(1.0), 0.45);

    // Ореол снаружи буквы — сдержанный. Ближняя часть гаснет к 0.012 — раньше медиальной оси просветов «S»
    // (от 0.014), поэтому в просветах излома нет. Дальняя часть (до 0.038) в просветах и их устьях плавно
    // выключается гладкой маской в координатах буквы (гауссовы капсулы вдоль осей просветов). Плоской заливки
    // просветов нет: капсулы выходили за устья, и там висело светлое пятно. На GUI scale 1 ореол ослаблен.
    float scaleOne = smoothstep(0.0045, 0.0072, px);
    float haloFar = 1.0 - smoothstep(0.0, 0.038, d);
    float haloNear = 1.0 - smoothstep(0.0, 0.012, d);
    float counter = clamp(capsule(texCoord, vec2(0.480, 0.438), vec2(0.700, 0.474))
            + capsule(texCoord, vec2(0.530, 0.551), vec2(0.380, 0.523)), 0.0, 1.0);
    float haloShape = haloNear * haloNear * 0.14 + haloFar * haloFar * 0.17 * (1.0 - counter);
    float halo = (1.0 - body) * (1.0 - 0.45 * scaleOne) * haloShape * (1.0 + 0.6 * flash + 0.4 * energy + 0.12 * spd);

    vec3 premul = base * halo;
    float cover = halo;
    premul = bodyRgb * bodyA + premul * (1.0 - bodyA);
    cover = bodyA + cover * (1.0 - bodyA);

    // Гребни ряби — слой света поверх тела, тоном между телом и кантом (белого не больше 0.3),
    // склон к свету сверху-слева ярче.
    float glint = max(dot(slope, vec2(-0.55, -0.83)), 0.0);
    float wakeA = body * min(0.7 * crest + 0.13 * glint * crest, 0.72);
    vec3 wakeRgb = mix(base, vec3(1.0), 0.22 + 0.08 * crest);
    premul = wakeRgb * wakeA + premul * (1.0 - wakeA);
    cover = wakeA + cover * (1.0 - wakeA);
    premul = rimRgb * rim + premul * (1.0 - rim);
    cover = rim + cover * (1.0 - rim);

    float alpha = cover * (1.0 + 0.5 * flash) * (1.0 + 0.25 * energy) * vertexColor.a;
    alpha += dither(gl_FragCoord.xy) * (0.25 / 255.0);
    fragColor = vec4(premul / max(cover, 1e-4), clamp(alpha, 0.0, 1.0)) * ColorModulator;
    if (fragColor.a <= 0.0005) discard;
}
