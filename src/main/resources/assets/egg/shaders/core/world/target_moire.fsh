#version 330

layout(std140) uniform MoireView {
    vec4 View;
};

in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;

float thread(float frequency) {
    float pixel = fwidth(frequency);
    float distanceToThread = abs(fract(frequency + 0.5) - 0.5);
    return (1.0 - smoothstep(0.037, 0.075 + pixel * 0.56, distanceToThread))
        * (1.0 - smoothstep(0.27, 0.63, pixel));
}

void main() {
    float side = texCoord.x > 1.5 ? -1.0 : 1.0;
    vec2 uv = vec2(texCoord.x - (side < 0.0 ? 2.0 : 0.0), texCoord.y);
    vec2 p = uv - 0.5;
    float view = side * dot(View.xy, vec2(0.83, 0.56));
    float bend = sin(p.y * 5.0 + p.x * 3.4 + view * 1.8) * 0.45;
    float shift = view * 2.05 + sin(View.z * 1.35 + side) * 0.34;
    float a = p.x * 13.0 + p.y * 8.5 + bend;
    float b = p.x * 11.2 + p.y * (6.1 + view * 1.4) - bend + shift;
    float crossA = p.y * 14.0 - p.x * 5.5;
    float crossB = p.y * 12.0 - p.x * (7.0 + view) + bend - shift * 0.55;
    float meshA = max(thread(a), 0.43 * thread(crossA));
    float meshB = max(thread(b), 0.39 * thread(crossB));
    float beat = 0.5 + 0.5 * cos((a - b) * 6.2831853);
    float strength = (meshA * 0.50 + meshB * 0.38) * (0.36 + beat * 0.64);
    float edgeX = smoothstep(0.0, 0.16, uv.x) * (1.0 - smoothstep(0.83, 1.0, uv.x));
    float edgeY = smoothstep(0.0, 0.20, uv.y) * (1.0 - smoothstep(0.76, 1.0, uv.y));
    vec3 color = mix(vertexColor.rgb, vec3(0.86, 0.92, 1.0), 0.28 + beat * 0.43);
    fragColor = vec4(color, strength * edgeX * edgeY * vertexColor.a);
}
