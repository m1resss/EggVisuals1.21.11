#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;
in float LineWidth;

out vec4 vertexColor;
out vec2 quadCoord;
out float glowWidth;     // px (device), 0 = выкл
out float glowStrength;  // 0..1
out float outlineWidth;  // px (device), полуширина кольца, 0 = выкл

const float WIDTH_SCALE = 16.0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    quadCoord = UV0;                       // 0..1 — UV по всей SDF-текстуре
    glowWidth = LineWidth;
    glowStrength = Normal.y;
    outlineWidth = float(UV2.x) / WIDTH_SCALE;
}
