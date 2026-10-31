#version 330

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 tint;
out vec4 fragColor;

void main() {
    ivec2 size = textureSize(Sampler0, 0);
    ivec2 pixel = clamp(ivec2(texCoord0 * vec2(size)), ivec2(0), size - 1);
    vec4 center = texelFetch(Sampler0, pixel, 0);
    bool covered = center.a > 0.002;
    vec3 color = center.rgb;
    float edge = 0.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec4 neighbor = texelFetch(Sampler0, clamp(pixel + ivec2(x, y), ivec2(0), size - 1), 0);
            if (abs(neighbor.a - center.a) < 0.002) continue;
            edge += (x == 0 || y == 0) ? 1.0 : 0.5;
            if (!covered) color = neighbor.rgb;
        }
    }
    float outline = clamp(edge * 0.5, 0.0, 1.0);
    float alpha = ((covered ? 0.28 : 0.0) + outline * 0.68) * tint.a;
    if (alpha <= 0.001) discard;
    fragColor = vec4(clamp(color + outline * 0.08, 0.0, 1.0), clamp(alpha, 0.0, 1.0));
}
