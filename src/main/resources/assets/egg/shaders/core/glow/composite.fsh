#version 330

uniform sampler2D Sampler0; // размытое покрытие силуэта
uniform sampler2D Sampler1; // резкое покрытие (alpha силуэта)

in vec2 texCoord0;
in vec4 tint;

out vec4 fragColor;

// Мягкое свечение по краю: из размытого покрытия вычитаем резкое — остаётся
// только ореол СНАРУЖИ модели. Тинтуем и отдаём с аддитивным блендом.
void main() {
    float blurred = texture(Sampler0, texCoord0).r;
    float sharp = texture(Sampler1, texCoord0).a;
    float edge = clamp(blurred - sharp, 0.0, 1.0);
    float a = edge * tint.a;
    if (a <= 0.001) discard;
    fragColor = vec4(tint.rgb, a);
}
