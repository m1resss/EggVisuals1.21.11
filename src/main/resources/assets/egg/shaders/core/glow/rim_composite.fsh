#version 330

uniform sampler2D Sampler0; // размытое покрытие силуэта
uniform sampler2D Sampler1; // резкое покрытие (alpha силуэта)

// Делим UBO с пламенем. Time/Speed — бегущий блик. CamShiftX/Y переиспользованы как trail-вектор:
// trail=0 → обычный Rimlight; trail≠0 → весь glow меча дублируется хвостом назад (Spring при прыжке).
layout(std140) uniform SadnesFlame {
    float Time;
    float Speed;
    float Rise;
    float Decay;
    float Sway;
    float CamShiftX;
    float CamShiftY;
    float TexColor;
    float SharpFlip;
};

in vec2 texCoord0;
in vec4 tint;

out vec4 fragColor;

void main() {
    float t = Time * Speed;
    float sweep = pow(max(sin((texCoord0.x + texCoord0.y) * 9.0 - t * 2.2), 0.0), 6.0);

    vec2 trail = vec2(CamShiftX, CamShiftY);

    // Дубликат меча: смазываем ВЕСЬ glow (контур + наружный bloom) вдоль trail. Копии комбинируем по
    // max — каждый «дубликат» светится сам по себе (а не суммируется в кашу). При trail=0 все тапы в
    // одной точке → чистый Rimlight; при trail≠0 (прыжок) сзади тянутся ghost-копии меча.
    float glow = 0.0;
    for (int i = 0; i < 6; i++) {
        float f = float(i) / 5.0;
        vec2 uv = texCoord0 - trail * f;
        float b = texture(Sampler0, uv).r;
        float s = texture(Sampler1, vec2(uv.x, mix(uv.y, 1.0 - uv.y, SharpFlip))).a;
        float band = 1.0 - abs(2.0 * b - 1.0);
        // Тонкая ровная полоса по кромке: плавный профиль без зазубрин, но не слишком узкий —
        // иначе выступы силуэта торчат «зубиками».
        band = smoothstep(0.25, 0.95, band);
        float outer = clamp(b - s, 0.0, 1.0);
        // Немного наружного ореола сглаживает выступы, контур по острию остаётся ведущим.
        float g = (outer * 0.4 + band * 0.85) * (1.0 - f * 0.7); // дальние копии слабее
        glow = max(glow, g);
    }

    // Бегущий блик — по несмазанному контуру, поверх.
    float b0 = texture(Sampler0, texCoord0).r;
    float band0 = 1.0 - abs(2.0 * b0 - 1.0);
    band0 = band0 * band0;
    glow += band0 * 1.0 * sweep;

    vec3 col = tint.rgb + vec3(0.5 * sweep);
    float a = glow * tint.a;
    if (a <= 0.001) discard;
    fragColor = vec4(col, a);
}
