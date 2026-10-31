#version 330

// 3D-чамс: рендер самой геометрии модели (entity-формат), а не экранного силуэта.
// Вершины приходят уже в view-space (MatrixStack мира запекает поворот камеры),
// поэтому viewPos/viewNormal дают честный френель. TextureMat сюда кладётся НЕ
// текстурной матрицей, а ЯКОРЕМ КОНКРЕТНОЙ СУЩНОСТИ (view -> локальные координаты
// относительно её ног): узор приклеен к мобу, едет вместе с ним, у каждого свой,
// и не дрожит от float-точности на больших мировых координатах.
// invariant: chams/outline.vsh считает позицию тем же выражением и полагается на
// побитовое совпадение глубины (EQUAL против prepass) — не убирать.

invariant gl_Position;

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
in vec3 Normal;

out vec4 vertexColor;
out vec2 texCoord0;
out vec3 viewPos;
out vec3 viewNormal;
out vec3 localPos;

void main() {
    vec4 pos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * pos;
    viewPos = pos.xyz;
    viewNormal = mat3(ModelViewMat) * Normal;
    localPos = (TextureMat * vec4(Position, 1.0)).xyz;
    vertexColor = Color * ColorModulator;
    texCoord0 = UV0;
}
