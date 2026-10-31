#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 quadCoord;
in float glowWidth;
in float glowStrength;
in float outlineWidth;

out vec4 fragColor;

void main() {
    fragColor = vec4(0.0);
    vec4 fill = vertexColor * ColorModulator;

    // Знаковая дистанция из SDF: >0.5 внутри. Расстояние до контура в ПИКСЕЛЯХ кадра.
    // НЕ через m/|grad(m)|: на медиальной оси (скелете) вогнутых мест градиент SDF обнуляется,
    // m/|grad| взрывается → тёмная «прорезь» в глоу. Берём СТАБИЛЬНУЮ производную UV (она не
    // схлопывается) и линейный масштаб SDF. Коэф 0.1875 = 2*range_store/STORE = 2*(144/3)/512
    // (см. LogoTexture: SDF_RANGE=144, SUPERSAMPLE=3, STORE=512). Совпадает со старым масштабом
    // вдали от скелета — вид глоу прежний, но без артефакта.
    float m = texture(Sampler0, quadCoord).a - 0.5;
    vec2 duvx = dFdx(quadCoord);
    vec2 duvy = dFdy(quadCoord);
    float uvPerPx = sqrt(max(0.5 * (dot(duvx, duvx) + dot(duvy, duvy)), 1e-12));
    float dpx = m * 0.1875 / uvPerPx;           // px, >0 внутри лого

    // Чёткая заливка (1px AA-полоса на грани).
    float fillA = clamp(dpx + 0.5, 0.0, 1.0) * fill.a;

    // Компоновка alpha-over, сзади вперёд: glow -> заливка -> кант.
    vec3 col = fill.rgb;
    float alpha = 0.0;

    if (glowStrength > 0.0) {
        float t = clamp(1.0 + dpx / max(glowWidth, 1e-3), 0.0, 1.0); // 1 у грани -> 0 на -glowWidth
        alpha = glowStrength * t * t * fill.a;                       // ореол цветом заливки
    }

    alpha = fillA + alpha * (1.0 - fillA);

    if (outlineWidth > 0.0) {
        float ring = 1.0 - smoothstep(outlineWidth - 0.75, outlineWidth + 0.75, abs(dpx));
        float outA = ring * fill.a;
        vec3 outlineColor = mix(fill.rgb, vec3(1.0), 0.65);          // светлый кант (rim light)
        col = mix(col, outlineColor, outA);
        alpha = outA + alpha * (1.0 - outA);
    }

    fragColor = vec4(col, alpha);
    if (fragColor.a <= 0.0005) discard;
}
