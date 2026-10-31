#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0; // LUT кривой затухания тени (Nx1), сэмплим по X в [0,1]

in vec4 vertexColor;
in vec2 quadCoord;
in vec4 radiusData;
in float valueData; // spread тени в пикселях (полуширина ореола за кромкой панели)

out vec4 fragColor;

// Скругление углов — та же SDF, что и в rounded_gui/shape_texture_gui (знак радиуса = режим:
// >=0 дуга окружности, <0 squircle/непрерывная кривизна суперэллипса).
const float SQUIRCLE_N = 4.0;

float roundedBox(vec2 pos, vec2 b, vec4 r) {
    vec2 signPos = step(vec2(0.0), pos);
    vec2 radius = mix(r.zw, r.xy, signPos.x);
    float rSigned = radius.y * (1.0 - signPos.y) + radius.x * signPos.y;
    float rVal = abs(rSigned);
    vec2 q = abs(pos) - b + rVal;
    float qMax = max(q.x, q.y);
    vec2 qClamped = max(q, vec2(0.0));
    float corner = rSigned < 0.0
        ? pow(pow(qClamped.x, SQUIRCLE_N) + pow(qClamped.y, SQUIRCLE_N), 1.0 / SQUIRCLE_N)
        : length(qClamped);
    return min(qMax, 0.0) + corner - rVal;
}

void main() {
    // Квад расширен на spread; восстанавливаем его размер из fwidth и вычитаем spread,
    // чтобы d=0 пришёлся на кромку самой панели: d<0 внутри, d>0 в зоне ореола.
    vec2 uvWidth = max(fwidth(quadCoord), vec2(0.000001));
    vec2 rectSize = 1.0 / uvWidth;
    vec2 halfSize = rectSize * 0.5;
    vec2 pos = quadCoord * rectSize - halfSize;

    float spread = max(valueData, 0.0001);
    float d = roundedBox(pos, halfSize - spread, radiusData);
    float aa = fwidth(d) * 0.75;

    // Тень живёт строго за кромкой панели (d>0); внутри (d<0) прозрачна — не лезет на блюр и
    // не даёт цветного ободка по краю заливки (переход AA-шириной вокруг d=0).
    float insideMask = smoothstep(-aa, aa, d);
    // t=0 у кромки панели → сэмплим LUT в 1.0 (полный цвет тени), t=1 у внешнего края → 0.
    // Кривую затухания (любой Easing) выбираем в коде и запекаем в Sampler0.
    float t = clamp(d / spread, 0.0, 1.0);
    float falloff = texture(Sampler0, vec2(1.0 - t, 0.5)).r;

    vec4 color = vertexColor * ColorModulator;
    fragColor = vec4(color.rgb, color.a * falloff * insideMask);
    if (fragColor.a <= 0.0005) discard;
}
