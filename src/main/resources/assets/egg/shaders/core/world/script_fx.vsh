#version 330

// Вершинник для шейдеров скриптов: квад уже собран на CPU лицом к камере, здесь остаётся
// перевести его в клип-пространство и протащить координату внутри квада (uv 0..1) и цвет.
// UBO-блоки объявлены вручную, как в остальных шейдерах проекта (см. core/world/neon.vsh).

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
in vec2 UV0;
in vec4 Color;

out vec2 uv;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    uv = UV0;
    vertexColor = Color * ColorModulator;
}
