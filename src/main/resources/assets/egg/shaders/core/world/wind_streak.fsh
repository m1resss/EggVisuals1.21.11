#version 330

in vec4 vertexColor;
in float edgePx;
in float halfPx;

out vec4 fragColor;

// Поток ветра: вершинник общий с AA-линиями (core/world/line) и отдаёт пиксельное расстояние до оси ленты.
// Вместо плотной линии с резким краем — гауссов профиль по всей ширине: полоса выглядит размытой струёй воздуха.
void main() {
    fragColor = vec4(0.0);
    float d = abs(edgePx) / max(halfPx, 0.001);
    float a = vertexColor.a * exp(-3.0 * d * d);
    if (a <= 0.002) discard;
    fragColor = vec4(vertexColor.rgb, a);
}
