#version 330

// UBO-блоки объявлены вручную (как в остальных шейдерах проекта), а не через #moj_import.
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

layout(std140) uniform Projection {
    mat4 ProjMat;
};

// Нужен только ScreenSize — для расширения линии в screen-space. Порядок полей 1:1 с
// ванильным globals.glsl, иначе смещения std140 разъедутся.
layout(std140) uniform Globals {
    ivec3 CameraBlockPos;
    vec3 CameraOffset;
    vec2 ScreenSize;
    float GlintAlpha;
    float GameTime;
    int MenuBlurRadius;
    int UseRgss;
};

in vec3 Position;
in vec4 Color;
in vec3 Normal;
in float LineWidth;

out vec4 vertexColor;
out float edgePx;   // знаковое расстояние от оси линии до вершины, в пикселях
out float halfPx;   // половина толщины линии, в пикселях

// Запас геометрии за «истинным» краем под антиалиасинг (в пикселях).
const float FEATHER = 1.0;

const float VIEW_SHRINK = 1.0 - (1.0 / 256.0);
const mat4 VIEW_SCALE = mat4(
    VIEW_SHRINK, 0.0, 0.0, 0.0,
    0.0, VIEW_SHRINK, 0.0, 0.0,
    0.0, 0.0, VIEW_SHRINK, 0.0,
    0.0, 0.0, 0.0, 1.0
);

// Расширение линии в screen-space как у vanilla rendertype_lines, но квад шире «истинной»
// толщины на FEATHER пикселей, а во фрагмент идёт знаковое пиксельное расстояние до оси
// (edgePx) — по нему фрагмент аналитически считает покрытие края (сглаживание без MSAA).
void main() {
    vec4 startClip = ProjMat * VIEW_SCALE * ModelViewMat * vec4(Position, 1.0);
    vec4 endClip   = ProjMat * VIEW_SCALE * ModelViewMat * vec4(Position + Normal, 1.0);

    vec3 ndcStart = startClip.xyz / startClip.w;
    vec3 ndcEnd   = endClip.xyz / endClip.w;

    vec2 dir = normalize((ndcEnd.xy - ndcStart.xy) * ScreenSize);
    vec2 perp = vec2(-dir.y, dir.x);

    halfPx = LineWidth * 0.5;
    float extentPx = halfPx + FEATHER;

    // offset в NDC: (2*extentPx)/ScreenSize NDC == extentPx пикселей по экрану.
    vec2 offset = perp * (2.0 * extentPx) / ScreenSize;
    if (offset.x < 0.0) offset *= -1.0;

    float side = (gl_VertexID % 2 == 0) ? 1.0 : -1.0;
    edgePx = side * extentPx;

    vec3 ndc = ndcStart + side * vec3(offset, 0.0);
    gl_Position = vec4(ndc * startClip.w, startClip.w);

    vertexColor = Color * ColorModulator;
}
