#version 330

uniform sampler2D Sampler0;

in vec2 texCoord0;

out vec4 fragColor;

// Резолв силуэта для пламени: premultiplied-цвет модели в rgb + покрытие в alpha.
// В отличие от glow_mask (grayscale-покрытие) сохраняет цвет краёв текстуры предмета —
// он несётся через весь flame-конвейер (blur -> advect -> composite, режим Texture).
void main() {
    vec4 silh = texture(Sampler0, texCoord0);
    fragColor = vec4(silh.rgb * silh.a, silh.a);
}
