#version 330

in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    vec2 p = texCoord * 2.0 - 1.0;
    float ridge = p.y - p.x * 0.22;
    float edge = max(abs(p.x), abs(p.x * 0.52) + abs(ridge) * 0.95);
    float coverage = 1.0 - smoothstep(0.92, 0.92 + max(fwidth(edge), 0.08), edge);
    float facet = smoothstep(-0.18, 0.10, ridge);
    float shine = (1.0 - smoothstep(0.04, 0.23, abs(ridge))) * (1.0 - abs(p.x));
    vec3 steel = mix(vec3(0.36, 0.40, 0.45), vec3(0.77, 0.83, 0.89), facet);
    steel = mix(steel, vec3(0.96, 0.98, 1.0), shine * 0.65);
    fragColor = vec4(steel * vertexColor.rgb, coverage * vertexColor.a);
}
