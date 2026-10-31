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

in vec3 Position;   // позиция диска относительно камеры
in vec2 UV0;        // x = фаза времени, y = сила искажения
in vec4 Color;      // оттенок «стекла» (a = сила окраски)

out vec2 params;
out vec4 tint;
out vec2 flatPos;   // горизонтальные координаты для процедурного шума ряби

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    params = UV0;
    tint = Color;
    flatPos = Position.xz;
}
