#version 330

// Неоновая трубка глифа «Бесконечность». Лента строится на CPU в плоскости, обращённой к камере,
// поэтому вершиннику остаётся только протащить сечение во фрагмент: UV0.x — знаковая координата
// поперёк ленты (0 на оси, ±1 на геометрической кромке), UV0.y — накал ядра (1 — трубка с добела
// раскалённой нитью, 0 — только цвет, для прохода в offscreen-bloom).
// UBO-блоки объявлены вручную, как в остальных шейдерах проекта (см. core/particle/fluff.vsh).

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

out vec4 vertexColor;
out vec2 tube;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color * ColorModulator;
    tube = UV0;
}
