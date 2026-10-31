#version 330

uniform sampler2D Sampler0; // прошлое поле (rgb = premult-цвет краёв, a = интенсивность)
uniform sampler2D Sampler1; // свежий seed — размытое покрытие силуэта этого кадра

// Комментарии к полям вынесены наружу: внутри блока их не переваривает конвертер GLSL у VulkanMod.
//   Time — не используется здесь (нужно композиту)
//   Speed — -//-
//   Rise — -//-
//   Decay — множитель затухания за кадр (0..1) = exp(-damping * dt)
//   Sway — -//-
//   CamShiftX — сдвиг прошлого поля по дельте поворота камеры (UV) -> след при повороте
//   TexColor — -//-
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

out vec4 fragColor;

// Лёгкая симметричная диффузия (5 тапов) — сглаживает поле и затягивает разрывы между
// позициями руки в соседних кадрах в непрерывный след; симметрия = без сдвига/глитча.
vec4 sampleDiffused(vec2 uv, vec2 texel) {
    vec4 s = texture(Sampler0, clamp(uv, 0.0, 1.0)) * 0.4;
    s += texture(Sampler0, clamp(uv + vec2(texel.x, 0.0), 0.0, 1.0)) * 0.15;
    s += texture(Sampler0, clamp(uv - vec2(texel.x, 0.0), 0.0, 1.0)) * 0.15;
    s += texture(Sampler0, clamp(uv + vec2(0.0, texel.y), 0.0, 1.0)) * 0.15;
    s += texture(Sampler0, clamp(uv - vec2(0.0, texel.y), 0.0, 1.0)) * 0.15;
    return s;
}

// Motion-trail: прошлое поле затухает (без подъёма) и сливается со свежим seed через max.
// Доп. сдвиг выборки прошлого поля на CamShift = дельту поворота камеры даёт ИНЕРЦИОННЫЙ
// след при повороте: накопленный огонь «отстаёт» от взгляда и тянется шлейфом. Неподвижный
// взгляд + рука -> поле == seed (лишнего следа нет, форма как у обычного bloom). Подъёма в
// обратной связи НЕТ — он живёт в композите, поэтому плюмаж не «уплывает» сам по себе.
// Floor: после затухания вычитаем малую константу, привязанную к (1-Decay) -> хвост следа
// гарантированно доходит до НУЛЯ (буфер очищается), а не висит вечно экспонентой.
void main() {
    vec2 texel = 1.5 / vec2(textureSize(Sampler0, 0));
    vec2 src = texCoord0 + vec2(CamShiftX, CamShiftY);
    vec4 prev = sampleDiffused(src, texel) * Decay;
    prev = max(prev - (1.0 - Decay) * 0.2, vec4(0.0));

    vec4 seed = texture(Sampler1, texCoord0);
    fragColor = max(prev, seed);
}
