#version 330

in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    vec2 p = texCoord * 2.0 - 1.0;
    float d = max(max(abs(p.y) - 0.77, abs(p.x) - 0.97), abs(p.x + p.y * 0.20) + abs(p.y) * 0.76 - 1.12);
    float aa = clamp(fwidth(d) * 0.72, 0.008, 0.32);
    float alpha = (1.0 - smoothstep(-aa, aa, d)) * vertexColor.a;
    if (alpha < 0.008) discard;
    float rim = smoothstep(-0.25 - aa, -0.045, d);
    float striation = 0.5 + 0.5 * cos((p.y + p.x * 0.43) * 16.0);
    float facet = smoothstep(-0.14, 0.07, p.y + p.x * 0.36);
    vec3 cool = vertexColor.rgb * vec3(0.72, 0.89, 1.0);
    vec3 warm = mix(vertexColor.rgb, vec3(1.0, 0.90, 0.76), 0.24);
    vec3 pearl = mix(cool, warm, facet);
    pearl += striation * min(0.10, 0.10 / max(fwidth(p.y) * 5.0, 1.0));
    pearl = mix(pearl, vec3(0.92, 0.96, 1.0), rim * 0.58);
    fragColor = vec4(pearl, alpha);
}
