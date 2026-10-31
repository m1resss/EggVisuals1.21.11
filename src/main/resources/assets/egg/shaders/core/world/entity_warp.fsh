#version 330

// Искажение пространства вокруг сущностей. Sampler1 — РАЗМЫТАЯ маска силуэтов: размытие здесь не
// украшение, а способ выпустить эффект за контур, чтобы гнулась и полоска фона вокруг цели, а не
// только её пиксели с резким обрезом по краю модели.
//
// Sampler0 — РЕЗКАЯ копия кадра. Первая версия брала размытую (наследие матового стекла из
// disk_glass) и подменяла сущность размытой кашей: цель превращалась в размазанное пятно вместо
// того, чтобы преломляться. Смещая резкую копию, мы двигаем картинку, а не растворяем её.

uniform sampler2D Sampler0;   // резкая копия кадра
uniform sampler2D Sampler1;   // размытая маска силуэтов, a = вес

in vec2 texCoord0;
in vec4 tint;                 // rgb — оттенок ауры, a — сила искажения

out vec4 fragColor;

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
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

void main() {
    float mask = texture(Sampler1, texCoord0).a;
    if (mask <= 0.004 || tint.a <= 0.002) discard;   // мир за пределами ореола не трогаем вовсе

    vec2 size = vec2(textureSize(Sampler0, 0));
    // Низкая частота: крупные плавные завихрения читаются как ведёт пространство, а мелкая рябь на
    // таком масштабе выглядит шумом по модели. Узор прокручивается самой силой импульса, поэтому
    // отдельная uniform под время не нужна — пока удар затухает, искажение ещё и ползёт.
    vec2 p = gl_FragCoord.xy / size.y * 3.2 + vec2(tint.a * 29.0, tint.a * -17.0);
    float nx = vnoise(p);
    float ny = vnoise(p + vec2(19.3, 7.1));
    vec2 offset = (vec2(nx, ny) - 0.5) * tint.a * mask;

    vec3 col = texture(Sampler0, clamp(texCoord0 + offset, 0.0, 1.0)).rgb;
    col = mix(col, tint.rgb, 0.07 * mask);

    // Альфа = вес маски: в середине силуэта подмена полная, к краю ореола сходит на нет. Так как
    // сэмплится резкая копия, при нулевом смещении результат совпадает с кадром — шва не видно.
    fragColor = vec4(col, clamp(mask * 1.35, 0.0, 1.0));
}
