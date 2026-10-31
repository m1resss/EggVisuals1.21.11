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
out vec4 radiusData;
out vec3 slimeParams;  // x — ховер 0..1, y — курсор по ширине 0..1, z — режим
out vec3 slimeSplash;  // x — возраст всплеска 0..1, y — его место по ширине, z — наклон -1..1
out float slimeTime;   // секунды (со сдвигом фазы конкретной кнопки)

const float RADIUS_SCALE = 16.0;
const float PACK = 10000.0;

vec3 srgbToLinear(vec3 c) {
    c = clamp(c, 0.0, 1.0);
    return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(0.04045, c));
}

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = vec4(srgbToLinear(Color.rgb), Color.a);
    quadCoord = UV0;
    // Скругление у всех фигур этого шейдера однородное (кнопки, капсулы, панели), поэтому под
    // радиус хватает ОДНОГО канала, а три освободившихся ушли под всплеск и наклон поверхности.
    radiusData = vec4(float(UV1.x)) / RADIUS_SCALE;
    slimeParams = Normal;
    slimeSplash = vec3(float(UV1.y) / PACK, float(UV2.x) / PACK, float(UV2.y) / (PACK * 0.5) - 1.0);
    slimeTime = LineWidth;
}
