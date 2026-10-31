#version 330

uniform sampler2D Sampler0;

in vec2 texCoord0;
flat in float blurOffset;

out vec4 fragColor;

// Dual-Kawase DOWN c сохранением альфы (в отличие от общего blur_down):
// цепочка Ambience несёт в альфе premultiplied-вес блюр-зоны.
void main() {
    vec2 halfpixel = 0.5 / vec2(textureSize(Sampler0, 0));

    vec4 sum = texture(Sampler0, texCoord0) * 4.0;
    sum += texture(Sampler0, texCoord0 - halfpixel * blurOffset);
    sum += texture(Sampler0, texCoord0 + halfpixel * blurOffset);
    sum += texture(Sampler0, texCoord0 + vec2(halfpixel.x, -halfpixel.y) * blurOffset);
    sum += texture(Sampler0, texCoord0 - vec2(halfpixel.x, -halfpixel.y) * blurOffset);

    fragColor = sum / 8.0;
}
