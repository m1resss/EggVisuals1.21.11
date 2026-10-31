#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;   // 4x1 data-текстура: контрольные точки P0..P3, нормированные в квад (16-бит на ось)

in vec4 vertexColor;
in vec2 quadCoord;
in float bodyThick;
in float glowRadius;
in float phase;

out vec4 fragColor;

const int STEPS = 40;

// Распаковка контрольной точки i: x в R(hi)/G(lo), y в B(hi)/A(lo) → нормированные [0,1] в квад.
vec2 decode(int i) {
    vec4 t = texelFetch(Sampler0, ivec2(i, 0), 0);
    float nx = (t.r * 65280.0 + t.g * 255.0) / 65535.0;
    float ny = (t.b * 65280.0 + t.a * 255.0) / 65535.0;
    return vec2(nx, ny);
}

vec2 bezier(vec2 a, vec2 b, vec2 c, vec2 d, float t) {
    float u = 1.0 - t;
    return u * u * u * a + 3.0 * u * u * t * b + 3.0 * u * t * t * c + t * t * t * d;
}

// Расстояние от точки p до отрезка a→b.
float segDist(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-6), 0.0, 1.0);
    return length(pa - ba * h);
}

void main() {
    // Размер квада в пикселях кадра выводим из производной UV — координаты не зависят от размера bbox.
    vec2 bboxPx = 1.0 / max(fwidth(quadCoord), vec2(1e-6));
    vec2 p = quadCoord * bboxPx;

    vec2 P0 = decode(0) * bboxPx;
    vec2 P1 = decode(1) * bboxPx;
    vec2 P2 = decode(2) * bboxPx;
    vec2 P3 = decode(3) * bboxPx;

    // Дистанция до кривой: аналитический Безье, разбитый на STEPS отрезков, min-distance до ломаной.
    float d = 1.0e9;
    vec2 prev = P0;
    for (int i = 1; i <= STEPS; i++) {
        vec2 cur = bezier(P0, P1, P2, P3, float(i) / float(STEPS));
        d = min(d, segDist(p, prev, cur));
        prev = cur;
    }

    vec4 base = vertexColor * ColorModulator;
    float aa = 0.75;
    float bodyHalf = bodyThick * 0.5;

    float bodyA = 1.0 - smoothstep(bodyHalf - aa, bodyHalf + aa, d);

    float pulse = 0.85 + 0.15 * sin(phase * 6.2831853);
    float glowA = 0.0;
    if (glowRadius > 0.0) {
        float g = clamp(1.0 - max(d - bodyHalf, 0.0) / glowRadius, 0.0, 1.0);
        glowA = g * g * 0.55 * pulse;
    }

    // Тело поверх ореола, один цвет: alpha-over.
    float coverage = bodyA + glowA * (1.0 - bodyA);
    fragColor = vec4(base.rgb, base.a * coverage);
    if (fragColor.a <= 0.0005) discard;
}
