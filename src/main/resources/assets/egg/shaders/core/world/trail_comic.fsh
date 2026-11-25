#version 330

in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    float u = texCoord.x;
    float v = texCoord.y;
    float opacity = vertexColor.a;
    vec3 ink = vec3(0.063, 0.053, 0.084);

    if (v >= 2.0) {
        float side = abs((v - 2.5) * 2.0);
        float outer = 1.0 - smoothstep(0.77, 1.0, side);
        float inner = 1.0 - smoothstep(0.36, 0.61, side);
        fragColor = vec4(mix(ink, mix(vertexColor.rgb, vec3(1.0, 0.95, 0.79), 0.60), inner), opacity * outer);
        return;
    }

    vec3 fill = mix(vertexColor.rgb, vec3(1.0, 0.95, 0.81), 0.13);
    vec3 shade = fill * vec3(0.54, 0.53, 0.67);
    float aa = clamp(fwidth(v) * 0.66, 0.008, 0.075);
    float outer = smoothstep(0.0, aa * 1.6, min(v, 1.0 - v));
    float interior = smoothstep(0.107 - aa, 0.107 + aa, min(v, 1.0 - v));
    float cream = 1.0 - smoothstep(0.206 - aa, 0.206 + aa, v);
    float shaded = smoothstep(0.55 - aa, 0.55 + aa, v);
    vec3 color = mix(fill, shade, shaded);

    vec2 cells = vec2(u * 14.0, v * 6.4);
    cells.x += mod(floor(cells.y), 2.0) * 0.5;
    float dotDist = length(fract(cells) - 0.5);
    float soften = clamp(fwidth(cells.x) * 0.48 + fwidth(cells.y) * 0.26, 0.035, 0.25);
    float dots = 1.0 - smoothstep(0.245 - soften, 0.245 + soften, dotDist);
    color = mix(color, ink, dots * shaded * 0.92);
    color = mix(color, vec3(1.0, 0.95, 0.78), cream * 0.82);
    fragColor = vec4(mix(ink, color, interior), opacity * outer);
}
