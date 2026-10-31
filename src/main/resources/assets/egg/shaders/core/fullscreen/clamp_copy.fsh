#version 330

uniform sampler2D Sampler0;

// Комментарии к полям вынесены наружу: внутри блока их не переваривает конвертер GLSL у VulkanMod.
//   Bounds — minU, minV, maxU, maxV (framebuffer-UV, v-вверх)
//   Radius — радиус угла в framebuffer px
layout(std140) uniform EggBlurRect {
    vec4 Bounds;
    float Radius;
};

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    // Проекция сэмпла на СКРУГЛЁННЫЙ рект (в px, углы круглые): за ректом и в вырезах
    // углов берём ближайшую точку на скруглённом крае → блюр не тянет фон снаружи.
    vec2 sizePx = vec2(textureSize(Sampler0, 0));
    vec2 pMin = Bounds.xy * sizePx;
    vec2 pMax = Bounds.zw * sizePx;
    vec2 center = (pMin + pMax) * 0.5;
    vec2 halfSize = (pMax - pMin) * 0.5;

    vec2 p = texCoord0 * sizePx - center;
    float r = min(Radius, min(halfSize.x, halfSize.y));
    vec2 inner = max(halfSize - r, vec2(0.0));
    vec2 q = clamp(p, -inner, inner);
    vec2 d = p - q;
    float len = length(d);
    if (len > r) d *= r / len;

    vec2 uv = (center + q + d) / sizePx;
    fragColor = vec4(texture(Sampler0, uv).rgb, 1.0);
}
