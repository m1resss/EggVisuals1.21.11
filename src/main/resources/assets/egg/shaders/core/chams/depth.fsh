#version 330

// Depth-prepass 3D-чамса: пишет ТОЛЬКО глубину (цвет замаскирован colorWrite=false).
// Поэтому дорогой стиль (нэбула/стекло/мороз) тут не нужен — считаем лишь cutout-discard
// по alpha текстуры модели, чтобы прозрачные части скина не давали глубину. Делит vsh с
// ENTITY_CHAMS (тот же вершинный формат и varyings), цветовую математику не трогает.
uniform sampler2D Sampler0;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    if (texture(Sampler0, texCoord0).a < 0.1) discard;
    fragColor = vec4(0.0);
}
