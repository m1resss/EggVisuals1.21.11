#version 330

in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    vec2 uv = vec2(mod(texCoord.x, 2.0), texCoord.y);
    float mode = floor(texCoord.x * 0.5);
    if (mode < 0.5) {
        fragColor = vertexColor;
    } else if (mode < 1.5) {
        float y = uv.y;
        float bend = vertexColor.r * 2.0 - 1.0;
        float split = vertexColor.g * 2.0 - 1.0;
        float center = 0.49 + (bend * 0.19 + sin(y * 8.2 + split) * 0.055) * y;
        float bulb = smoothstep(-0.05, 0.23, y) * (1.0 - smoothstep(0.30, 0.95, y));
        float width = (0.28 * pow(max(0.0, 1.0 - y), 0.72) + 0.17 * bulb)
                * smoothstep(-0.005, 0.082, y);
        float edge = width - abs(uv.x - center);
        float branch = (0.112 - abs(uv.x - 0.48 - split * 0.31 + y * split * 0.15))
                * smoothstep(0.11, 0.27, y) * (1.0 - smoothstep(0.4, 0.69, y));
        edge = max(edge, branch - 0.020);
        float mask = smoothstep(-0.029, 0.074, edge) * smoothstep(0.0, 0.09, y);
        float inner = smoothstep(0.022, 0.215, edge) * (1.0 - smoothstep(0.48, 0.88, y));
        float core = smoothstep(0.31, 0.78, inner);
        vec3 flame = mix(vec3(0.93, 0.17, 0.017), vec3(1.0, 0.63, 0.19), inner);
        flame = mix(flame, vec3(1.0, 0.96, 0.70), core);
        fragColor = vec4(flame, mask * vertexColor.a * mix(0.72, 1.0, core));
    } else {
        float r = length(uv - 0.5) * 2.0;
        float halo = exp(-5.2 * r * r) * (1.0 - smoothstep(0.56, 1.0, r));
        fragColor = vec4(vertexColor.rgb, vertexColor.a * halo);
    }
    if (fragColor.a < 0.005) discard;
}
