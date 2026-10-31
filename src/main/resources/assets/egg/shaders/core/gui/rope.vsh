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
out float spineThick;   // px (device)
out float glowRadius;   // px (device)
out float knotRadius;   // px (device)
out float phase;        // 0..1 — пульс/анимация

const float SCALE16 = 16.0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    quadCoord = UV0;
    bodyThick = LineWidth;
    spineThick = float(UV1.x) / SCALE16;
    glowRadius = float(UV1.y) / SCALE16;
    knotRadius = float(UV2.x) / SCALE16;
    phase = float(UV2.y) / 1000.0;   // 0..1, целочисленный канал (без квантизации NORMAL-байта)
}
