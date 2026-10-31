#version 330

in vec3 tint;
in float alpha;
in vec2 flakeUv;
in float starShape;

out vec4 fragColor;

// Снежинка: мелкие и дальние — мягкий диск, крупные вблизи — шестилучевая звезда с отростками.
// Лучи складываются в один сектор (60 градусов) и рисуются расстоянием до отрезков со сглаживанием по пикселю.
const float SECTOR = 1.0471976;

float segmentDistance(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h);
}

float crystal(vec2 uv, float r, float aa) {
    float angle = r > 0.0001 ? atan(uv.y, uv.x) : 0.0;
    float local = mod(angle + 0.5 * SECTOR, SECTOR) - 0.5 * SECTOR;
    vec2 q = vec2(r * cos(local), abs(r * sin(local)));
    float d = segmentDistance(q, vec2(0.0), vec2(0.88, 0.0));
    d = min(d, segmentDistance(q, vec2(0.36, 0.0), vec2(0.56, 0.2)));
    d = min(d, segmentDistance(q, vec2(0.6, 0.0), vec2(0.74, 0.13)));
    float lines = 1.0 - smoothstep(0.055 - aa, 0.055 + aa, d);
    float core = 1.0 - smoothstep(0.1, 0.2, r);
    return max(lines, core);
}

void main() {
    fragColor = vec4(0.0);
    float r = length(flakeUv);
    // Производная — до ветвлений и discard: в неоднородном потоке управления она не определена.
    float aa = max(fwidth(r), 0.001);
    if (r >= 1.0) discard;
    float disc = 1.0 - smoothstep(0.1, 1.0, r);
    disc *= disc;
    float shape = disc;
    if (starShape > 0.0) shape = mix(disc, min(crystal(flakeUv, r, aa) + disc * 0.3, 1.0), starShape);
    float a = alpha * shape;
    if (a <= 0.002) discard;
    fragColor = vec4(tint * a, a);
}
