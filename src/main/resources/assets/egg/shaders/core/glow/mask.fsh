#version 330

uniform sampler2D Sampler0;

in vec2 texCoord0;

out vec4 fragColor;

// Резолв силуэта: alpha-покрытие захваченной текстуры -> в RGB (blur читает .r).
// Покрытие дублируется и в alpha — превью-композит читает резкое покрытие из .a
// собственной маски (нельзя сэмплить целевую текстуру, в которую сам же пишет).
// В мировом композите Sampler1 = сырой capture, поэтому на ваниль это не влияет.
void main() {
    float coverage = texture(Sampler0, texCoord0).a;
    fragColor = vec4(vec3(coverage), coverage);
}
