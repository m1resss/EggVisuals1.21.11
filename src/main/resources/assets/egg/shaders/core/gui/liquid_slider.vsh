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
out vec3 sliderMotion;  // x — позиция капли (0..1, за краями — перелёт), y — скорость -1..1, z — возраст волны 0..1
out vec4 sliderState;   // x — ховер 0..1, y — зажатие 0..1, z — место волны, w — возраст импульса нажатия (1 = нет)
out float sliderTime;   // секунды

const float PACK = 10000.0;

vec3 srgbToLinear(vec3 c) {
    c = clamp(c, 0.0, 1.0);
    return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(0.04045, c));
}

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = vec4(srgbToLinear(Color.rgb), Color.a);
    quadCoord = UV0;
    // Позиция и место волны пакуются как (v + 0.5) / 2 — запас по полдорожки в обе стороны под
    // перелёт; скорость — как v * 0.5 + 0.5. Шаг 1/10000 — сотые доли пикселя.
    sliderMotion = vec3(float(UV1.y) / PACK * 2.0 - 0.5,
                        float(UV2.x) / (PACK * 0.5) - 1.0,
                        float(UV1.x) / PACK);
    // Нормаль — байт: BufferBuilder отбрасывает дробь (int)(f * 127). Для ховера, зажатия и
    // затухающего импульса ошибка ≤ 1/127 незаметна; 1.0 доезжает ровно, отрицательных значений нет.
    sliderState = vec4(clamp(Normal.xy, 0.0, 1.0), float(UV2.y) / PACK * 2.0 - 0.5, clamp(Normal.z, 0.0, 1.0));
    sliderTime = LineWidth;
}
