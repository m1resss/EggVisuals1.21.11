#version 330

in vec3 tint;
in float alpha;
in float edgePx;
in float halfPx;
in float along;
in float heightAboveTop;

out vec4 fragColor;

// Полоса капли: сглаженный край как у AA-линии, яркая голова внизу и тающий след. Всё ниже верха столба
// (земля, крыша, вода) отрезано — капля обрывается ровно на поверхности. Выход premultiplied.
void main() {
    fragColor = vec4(0.0);
    if (heightAboveTop < 0.0) discard;
    float d = abs(edgePx);
    float coverage = clamp(halfPx - d + 0.5, 0.0, 1.0);
    float core = 1.0 - d / (halfPx + 1.0);
    float body = smoothstep(0.0, 0.12, along) * (1.0 - smoothstep(0.3, 1.0, along));
    float a = alpha * coverage * core * body;
    if (a <= 0.002) discard;
    fragColor = vec4(tint * a, a);
}
