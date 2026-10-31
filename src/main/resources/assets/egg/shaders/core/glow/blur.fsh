#version 330

uniform sampler2D Sampler0;

in vec2 texCoord0;
flat in float blurOffset;

out vec4 fragColor;

const int MAX_TAPS = 48;

// Сепарабельный гауссов блюр. Направление кодируется ЗНАКОМ blurOffset: >= 0 — горизонталь,
// < 0 — вертикаль. Так H и V — ОДИН pipeline/шейдер (GLOW_BLUR), без отдельного UBO: H-проход
// зовётся с +радиусом, V-проход с −радиусом. Радиус = |blurOffset|.
//
// Линейно-сэмплируемый: пара соседних текселей (i, i+1) берётся одним bilinear-сэмплом во
// взвешенной точке между ними — вдвое меньше выборок при идентичной форме ядра. Требует
// LINEAR-сэмплера (все вызовы GLOW_BLUR его дают). Блюрит все каналы (rgba).
void main() {
    vec2 px = 1.0 / vec2(textureSize(Sampler0, 0));
    vec2 dir = blurOffset >= 0.0 ? vec2(px.x, 0.0) : vec2(0.0, px.y);

    float sigma = max(abs(blurOffset) * 1.5, 0.5);
    int radius = min(MAX_TAPS, int(ceil(sigma * 2.0)));
    float twoSigmaSq = 2.0 * sigma * sigma;

    vec4 sum = texture(Sampler0, texCoord0);
    float weightSum = 1.0;
    for (int i = 1; i <= MAX_TAPS; i += 2) {
        if (i > radius) break;
        float w1 = exp(-float(i * i) / twoSigmaSq);
        float wPair = w1;
        float off = float(i);
        if (i + 1 <= radius) {
            float w2 = exp(-float((i + 1) * (i + 1)) / twoSigmaSq);
            wPair = w1 + w2;
            off = (float(i) * w1 + float(i + 1) * w2) / wPair;
        }
        vec2 step = dir * off;
        sum += wPair * (texture(Sampler0, texCoord0 + step) + texture(Sampler0, texCoord0 - step));
        weightSum += 2.0 * wPair;
    }
    fragColor = sum / weightSum;
}
