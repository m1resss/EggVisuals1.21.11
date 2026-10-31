#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;   // 64x1 data-текстура: точки кривой, нормированные в bbox (16-бит на ось)

in vec4 vertexColor;
in vec2 quadCoord;
in float bodyThick;
in float spineThick;
in float glowRadius;
in float knotRadius;
in float phase;

out vec4 fragColor;

const int N = 64;

// Распаковка точки i: x в R(hi)/G(lo), y в B(hi)/A(lo) → нормированные [0,1] координаты в bbox.
vec2 decode(int i) {
    vec4 t = texelFetch(Sampler0, ivec2(i, 0), 0);
    float nx = (t.r * 65280.0 + t.g * 255.0) / 65535.0;
    float ny = (t.b * 65280.0 + t.a * 255.0) / 65535.0;
    return vec2(nx, ny);
}

// Расстояние от точки p до отрезка a→b; h — параметр проекции вдоль отрезка (0..1).
float segDist(vec2 p, vec2 a, vec2 b, inout float h) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    h = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-6), 0.0, 1.0);
    return length(pa - ba * h);
}

void main() {
    fragColor = vec4(0.0);

    // Размер квада в пикселях кадра выводим из производной UV — координаты не зависят от размера bbox.
    vec2 bboxPx = 1.0 / max(fwidth(quadCoord), vec2(1e-6));
    vec2 p = quadCoord * bboxPx;

    // Дистанция до ломаной по 64 точкам + положение вдоль кривой (для градиента к концу).
    float d = 1.0e9;
    float arc = 0.0;
    vec2 prev = decode(0) * bboxPx;
    for (int i = 1; i < N; i++) {
        vec2 cur = decode(i) * bboxPx;
        float h = 0.0;
        float dd = segDist(p, prev, cur, h);
        if (dd < d) {
            d = dd;
            arc = (float(i - 1) + h) / float(N - 1);
        }
        prev = cur;
    }
    vec2 endP = decode(N - 1) * bboxPx;
    float dEnd = length(p - endP);

    vec4 base = vertexColor * ColorModulator;
    float aa = 0.75;
    float bodyHalf = bodyThick * 0.5;
    float spineHalf = spineThick * 0.5;

    float bodyA = 1.0 - smoothstep(bodyHalf - aa, bodyHalf + aa, d);
    float spineA = 1.0 - smoothstep(spineHalf - aa, spineHalf + aa, d);

    float pulse = 0.8 + 0.2 * sin(phase * 6.2831853);
    float glowA = 0.0;
    if (glowRadius > 0.0) {
        float g = clamp(1.0 - max(d - bodyHalf, 0.0) / glowRadius, 0.0, 1.0);
        glowA = g * g * 0.5 * pulse;
    }
    float knotA = knotRadius > 0.0 ? (1.0 - smoothstep(knotRadius - aa, knotRadius + aa, dEnd)) : 0.0;

    // Тело светлеет к концу нити (активный «хватающий» кончик ярче), жилка по центру — светлая.
    vec3 bodyCol = mix(base.rgb * 0.85, mix(base.rgb, vec3(1.0), 0.25), arc);
    vec3 spineCol = mix(bodyCol, vec3(1.0), 0.55);
    vec3 glowCol = base.rgb;
    vec3 knotCol = mix(base.rgb, vec3(1.0), 0.7);

    // Alpha-over, сзади вперёд: glow → тело → жилка → узелок.
    vec3 col = glowCol;
    float alpha = glowA * base.a;
    float ba = bodyA * base.a;
    col = mix(col, bodyCol, ba);
    alpha = ba + alpha * (1.0 - ba);
    float sa = spineA * base.a;
    col = mix(col, spineCol, sa);
    alpha = sa + alpha * (1.0 - sa);
    float ka = knotA * base.a;
    col = mix(col, knotCol, ka);
    alpha = ka + alpha * (1.0 - ka);

    fragColor = vec4(col, alpha);
    if (fragColor.a <= 0.0005) discard;
}
