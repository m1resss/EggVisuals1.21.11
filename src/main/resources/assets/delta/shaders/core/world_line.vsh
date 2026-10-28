#version 330

layout(std140) uniform LineData {
    mat4 ProjView;
    vec4 Params;
};

in vec3 Position;
in vec4 Color;
in vec3 Normal;
in float LineWidth;

out vec4 vertexColor;

void main() {
    float width = abs(LineWidth);
    float side = sign(LineWidth);

    vec4 clip1 = ProjView * vec4(Position, 1.0);
    vec4 clip2 = ProjView * vec4(Position + Normal, 1.0);

    vec3 ndc1 = clip1.xyz / clip1.w;
    vec3 ndc2 = clip2.xyz / clip2.w;

    vec2 screenDir = (ndc2.xy - ndc1.xy) * Params.xy;
    float len = length(screenDir);
    vec2 perp = vec2(0.0);
    if (len > 1e-7) {
        screenDir /= len;
        perp = vec2(-screenDir.y, screenDir.x) * (width * side) / Params.xy;
    }

    gl_Position = vec4((ndc1 + vec3(perp, 0.0)) * clip1.w, clip1.w);
    vertexColor = Color;
}
