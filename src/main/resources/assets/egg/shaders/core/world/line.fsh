#version 330

in vec4 vertexColor;
in float edgePx;
in float halfPx;

out vec4 fragColor;

// Аналитическое покрытие края: линия плотная внутри halfPx и за ~1px плавно гаснет — это и
// есть сглаживание без MSAA (квад в vsh расширен на FEATHER, чтобы рамп уместился в геометрию).
void main() {
    fragColor = vec4(0.0);
    float coverage = clamp(halfPx - abs(edgePx) + 0.5, 0.0, 1.0);
    float a = vertexColor.a * coverage;
    if (a <= 0.0005) discard;
    fragColor = vec4(vertexColor.rgb, a);
}
