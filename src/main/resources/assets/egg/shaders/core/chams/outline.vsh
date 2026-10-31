#version 330

// Кайма Outline-чамса: ТА ЖЕ геометрия, что у цветового прохода — позиция считается
// идентично color.vsh и объявлена invariant, поэтому depth-тест EQUAL против
// prepass-глубины сходится бит-в-бит. В атрибуте света (UV2, чамсам освещение не
// нужно) лежат «углы квада» (0,0)/(1,0)/(1,1)/(0,1) по порядку обхода (см.
// ModelChamsRenderer.buildOutlineVbo): их интерполяция даёт fsh позицию внутри
// грани — по ней рисуются линии по кромке КАЖДОЙ грани части модели.

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
in ivec2 UV2;

out vec4 vertexColor;
out vec2 texCoord0;
out vec2 cornerUv;

void main() {
    vec4 pos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * pos;
    vertexColor = Color * ColorModulator;
    texCoord0 = UV0;
    cornerUv = vec2(UV2);
}
