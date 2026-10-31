#version 330

uniform sampler2D Sampler0; // поле силуэта (bloom-база + motion-trail): rgb = premult-цвет краёв, a = покрытие

// Комментарии к полям вынесены наружу: внутри блока их не переваривает конвертер GLSL у VulkanMod.
//   Time — секунды от старта (анимация мерцания/увода)
//   Speed — скорость анимации
//   Rise — высота языков вверх (доля экрана по высоте)
//   Decay — хвостовой слот (паддинг до 32 байт std140)
//   Sway — боковой увод языков
//   CamShiftX — хвостовой слот (паддинг)
//   CamShiftY — хвостовой слот (паддинг)
//   TexColor — >0.5 -> цвет пламени из краёв текстуры предмета, иначе из тинта
layout(std140) uniform EggFlame {
    float Time;
    float Speed;
    float Rise;
    float Decay;
    float Sway;
    float CamShiftX;
    float CamShiftY;
    float TexColor;
};

in vec2 texCoord0;
in vec4 tint;      // rgb = цвет огня, a = интенсивность

out vec4 fragColor;

uint mixBits(uint x) {
    x ^= x >> 16;
    x *= 0x7feb352du;
    x ^= x >> 15;
    x *= 0x846ca68bu;
    x ^= x >> 16;
    return x;
}

float hash(vec2 p) {
    uvec2 b = floatBitsToUint(p);
    return float(mixBits(b.x ^ mixBits(b.y + 0x9e3779b9u)) >> 8) * (1.0 / 16777216.0);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

const int STEPS = 12;
const float HEIGHT_SCALE = 0.4; // Rise задаётся слайдером 0.05..0.6; гасим в screen-UV до торч-высоты

// Пламя = тот же bloom-силуэт, только вытянутый ВВЕРХ. Для каждого пикселя собираем
// тепло из самого пикселя и точек НИЖЕ него: жар силуэта «всплывает» вверх в языки,
// а вес падает с высотой -> верх плавно гаснет. Лёгкий боковой увод и мерцание кончиков
// дают живость. Поле приходит из advect (bloom-база + motion-trail), а сами языки/цвет
// считаются заново каждый кадр, поэтому форма не «плавает» сама по себе.
// Узор завязан на gl_FragCoord (квадратные пиксели) — аспект не искажает.
void main() {
    float t = Time * Speed;
    vec2 fc = gl_FragCoord.xy;

    // Боковой увод: низкочастотный шум, прокручивается вверх (-t по Y).
    float sway = (noise(fc * 0.018 - vec2(0.0, t * 1.2)) - 0.5) * Sway;

    float heat = 0.0;
    vec3 colSum = vec3(0.0);
    float colW = 0.0;

    // Выборка текущего пикселя и точек ниже: жар поднимается ВВЕРХ. Увод растёт с высотой.
    for (int i = 0; i < STEPS; i++) {
        float f = float(i) / float(STEPS - 1); // 0 = сам пиксель, 1 = низ (исток)
        vec2 uv = texCoord0 - vec2(sway * f, f * Rise * HEIGHT_SCALE);
        vec4 s = texture(Sampler0, uv);
        heat = max(heat, s.a * (1.0 - f)); // дальше от истока -> слабее
        colSum += s.rgb;                   // premult-цвет краёв для усреднения
        colW += s.a;
    }
    if (heat <= 0.01) discard;

    // Мерцание кончиков: бегущий вверх шум. Плотное ядро держим стабильным (как bloom),
    // дрожат только слабые языки -> база не «дышит».
    float flick = 0.78 + 0.42 * noise(vec2(fc.x * 0.05, fc.y * 0.03 - t * 3.0));
    float core = smoothstep(0.5, 0.95, heat);
    heat = clamp(heat * mix(flick, 1.0, core), 0.0, 1.0);

    // Базовый цвет: тинт (Client/Static/Fade) или средний цвет ближайшей кромки предмета.
    vec3 col = TexColor > 0.5 ? colSum / max(colW, 0.001) : tint.rgb;

    // Огненный градиент: тёмный язык -> цвет -> белое горячее ядро.
    vec3 tip = col * 0.35;
    vec3 hot = mix(col, vec3(1.0), 0.7);
    vec3 fire = mix(tip, col, smoothstep(0.0, 0.4, heat));
    fire = mix(fire, hot, smoothstep(0.55, 1.0, heat));

    fragColor = vec4(fire, heat * tint.a);
}
