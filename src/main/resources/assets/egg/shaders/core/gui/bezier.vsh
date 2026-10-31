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
in float LineWidth;

out vec4 vertexColor;
out vec2 quadCoord;
out float bodyThick;    // px (device)
out float glowRadius;   // px (device)
out float phase;        // 0..1 — пульс/дыхание линии

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    quadCoord = UV0;
    bodyThick = LineWidth;
    glowRadius = float(UV1.x) / 16.0;
    phase = float(UV1.y) / 1000.0;
}
