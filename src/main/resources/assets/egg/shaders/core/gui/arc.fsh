#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 quadCoord;
in vec2 arcAngles;
in vec2 arcShimmer;
in float thicknessPx;

out vec4 fragColor;

const float TAU = 6.28318531;
const float PI = 3.14159265;

vec3 linearToSrgb(vec3 c) {
    c = clamp(c, 0.0, 1.0);
    return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(0.0031308, c));
}

// Угол точки от 12 часов по часовой стрелке (экранный Y смотрит вниз).
float clockAngle(vec2 p) {
    return atan(p.x, -p.y);
}

// SDF дуги-капсулы: внутри сектора — расстояние до окружности, снаружи — до ближайшего
// торца, что даёт круглые концы. Честный SDF (|grad| ~ 1), поэтому сглаживание берётся
// L2-нормой градиента и не «плывёт» ни на торцах, ни на дуге.
float sdArc(vec2 p, float start, float sweep, float radius, float halfThickness) {
    float ring = abs(length(p) - radius) - halfThickness;
    if (sweep >= TAU - 0.0001) return ring;

    float angle = mod(clockAngle(p) - start, TAU);
    if (angle <= sweep) return ring;

    vec2 head = vec2(sin(start), -cos(start)) * radius;
    vec2 tail = vec2(sin(start + sweep), -cos(start + sweep)) * radius;
    return min(length(p - head), length(p - tail)) - halfThickness;
}

// Сектор кольца с прямыми радиальными торцами (радиальные меню): кольцо режется по границам
// угла, дистанция до среза берётся по длине дуги — AA не плывёт на любом радиусе.
float sdArcSector(vec2 p, float start, float sweep, float radius, float halfThickness) {
    float ring = abs(length(p) - radius) - halfThickness;
    if (sweep >= TAU - 0.0001) return ring;

    float halfSweep = sweep * 0.5;
    float rel = mod(clockAngle(p) - start - halfSweep + PI, TAU) - PI;
    float cut = (abs(rel) - halfSweep) * max(length(p), 0.0001);
    return max(ring, cut);
}

void main() {
    fragColor = vec4(0.0);

    // Размер квада выводим из производных локальных координат — передавать его не нужно.
    vec2 uvWidth = max(fwidth(quadCoord), vec2(0.000001));
    vec2 rectSize = 1.0 / uvWidth;
    vec2 halfSize = rectSize * 0.5;
    vec2 pos = quadCoord * rectSize - halfSize;

    float sweep = clamp(arcAngles.y, 0.0, TAU);
    if (sweep <= 0.0001) discard;

    // Отрицательная толщина кодирует сектор с прямыми торцами вместо круглых концов.
    bool flatCaps = thicknessPx < 0.0;
    float halfThickness = max(abs(thicknessPx), 0.0001) * 0.5;
    float radius = max(min(halfSize.x, halfSize.y) - halfThickness, 0.0001);

    float d = flatCaps
        ? sdArcSector(pos, arcAngles.x, sweep, radius, halfThickness)
        : sdArc(pos, arcAngles.x, sweep, radius, halfThickness);
    float w = max(length(vec2(dFdx(d), dFdy(d))), 1e-6);
    float coverage = clamp(0.5 - d / w, 0.0, 1.0);
    if (coverage <= 0.0) discard;

    vec3 rgb = linearToSrgb(vertexColor.rgb);
    float alpha = vertexColor.a;

    // Переливание: по дуге бежит светлый гребень — «живой» индикатор того, что время ещё идёт.
    float strength = clamp(arcShimmer.y, 0.0, 1.0);
    if (strength > 0.0) {
        float along = clamp(mod(clockAngle(pos) - arcAngles.x, TAU) / sweep, 0.0, 1.0);
        // Расстояние до гребня по кольцевой координате: 0 в гребне, 1 в противофазе.
        float wave = abs(fract(along - arcShimmer.x + 0.5) - 0.5) * 2.0;
        float crest = pow(1.0 - wave, 8.0);
        rgb = mix(rgb, vec3(1.0), crest * strength * 0.6);
        alpha = min(alpha * (1.0 + crest * strength * 0.4), 1.0);
    }

    fragColor = vec4(rgb, alpha * coverage) * ColorModulator;
    if (fragColor.a <= 0.0005) discard;
}
