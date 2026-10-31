#version 330

uniform sampler2D Sampler0;   // размытая копия кадра (сцена под кольцом)

in vec2 params;   // x = фаза времени, y = сила искажения
in vec4 tint;
in vec2 flatPos;

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

// Гладкий value-шум для ряби
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
    fragColor = vec4(0.0);

    // Экранные UV пикселя диска: размытая сцена того же размера, что кадр.
    vec2 screenUv = gl_FragCoord.xy / vec2(textureSize(Sampler0, 0));

    float time = params.x;
    float strength = params.y;

    // Две волны шума по разным направлениям/времени → бегущая рябь (distortion)
    float nx = vnoise(flatPos * 7.0 + vec2(time, time * 0.6));
    float ny = vnoise(flatPos * 7.0 + vec2(-time * 0.7, time * 1.1));
    vec2 offset = (vec2(nx, ny) - 0.5) * strength;

    // Сэмпл размытой сцены со смещением = blur + distortion внутри диска
    vec3 col = texture(Sampler0, clamp(screenUv + offset, 0.0, 1.0)).rgb;

    // Лёгкая окраска «стекла» оттенком кольца (фикс. сила — tint.a занят под альфу)
    col = mix(col, tint.rgb, 0.12);

    // Альфа = fade кольца (несётся в tint.a): стекло плавно проявляется и гаснет вместе с кольцом.
    // При blend TRANSLUCENT диск смешивается с резкой сценой — fade=0 эффекта нет, fade=1 полное стекло.
    fragColor = vec4(col, clamp(tint.a, 0.0, 1.0));
}
