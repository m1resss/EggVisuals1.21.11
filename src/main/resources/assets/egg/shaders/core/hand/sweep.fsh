#version 330

uniform sampler2D Sampler0; // размытое покрытие силуэта
uniform sampler2D Sampler1; // резкое покрытие (alpha силуэта)

// Делим UBO с пламенем — слоты переиспользованы под параметры полосы.
// Ось задаётся в аспект-корректных UV: x умножен на Aspect, иначе направление косит с окном.
// Комментарии к полям вынесены наружу: внутри блока их не переваривает конвертер GLSL у VulkanMod.
//   Progress — положение переднего края вдоль оси: 0 — начало предмета, 1 — конец
//   BandWidth — длина светящегося хвоста в долях оси
//   Intensity — сила подсветки (анимация наведения)
//   SharpFlip — 1.0, если Sampler1 лежит в нативной ориентации бэкенда (VulkanMod): его V переворачиваем
layout(std140) uniform EggFlame {
    float Progress;
    float BandWidth;
    float AxisX;
    float AxisY;
    float OriginX;
    float OriginY;
    float Aspect;
    float Intensity;
    float SharpFlip;
};

in vec2 texCoord0;
in vec4 tint;       // акцент темы: им светится подложка и ореол, полоса остаётся белой

out vec4 fragColor;

void main() {
    vec2 sharpCoord = vec2(texCoord0.x, mix(texCoord0.y, 1.0 - texCoord0.y, SharpFlip));
    float sharp = texture(Sampler1, sharpCoord).a;
    float blurred = texture(Sampler0, texCoord0).r;
    if (sharp + blurred <= 0.002) discard;

    // Координата вдоль собственной оси предмета, а не вдоль экрана: полоса идёт по клинку.
    // Ось приходит уже поделённой на размах, так что деления здесь нет.
    vec2 q = vec2(texCoord0.x * Aspect, texCoord0.y);
    float s = dot(q - vec2(OriginX, OriginY), vec2(AxisX, AxisY));

    // Профиль намеренно несимметричный: резкая передняя кромка и длинный затухающий хвост позади.
    // Получается протирка, а не блик — все прежние переливы клиента симметричны и мягки с обеих сторон.
    // lead ОТСЕКАЕТ всё впереди фронта, tail тянет свет назад.
    float lead = 1.0 - smoothstep(Progress - 0.004, Progress + 0.012, s);
    float behind = clamp((Progress - s) / max(BandWidth, 1e-4), 0.0, 1.0);
    float tail = 1.0 - behind;
    tail = tail * tail * (3.0 - 2.0 * tail);              // мягче простого квадрата, без ступеньки
    float band = lead * tail;
    // Два подсвета у самого фронта: широкий тёплый ореол и поверх него тонкая зеркальная кромка.
    // Именно она читается как скользящий по предмету свет, а не как заливка.
    float core = lead * (1.0 - smoothstep(0.0, 0.26, behind));
    float edge = lead * (1.0 - smoothstep(0.0, 0.10, behind));

    // Ровная подложка держит предмет подсвеченным всё время наведения: иначе в паузе между
    // проходами эффект выглядел бы то включённым, то выключенным.
    float outer = clamp(blurred - sharp, 0.0, 1.0);
    // Суммы подобраны так, чтобы на самом фронте альфа не переваливала за 1: бленд аддитивный,
    // и всё сверх единицы выжигает предмет в белое пятно.
    // Фронт намеренно уходит за единицу: бленд аддитивный, и пересвет на самой кромке — это и есть
    // читаемый блик. Вне кромки сумма остаётся под единицей, чтобы предмет не заливало белым.
    float a = sharp * (0.18 + band * 0.55 + core * 0.30 + edge * 0.35)
            + outer * (0.14 + band * 0.35 + core * 0.35 + edge * 0.45);
    a *= Intensity * tint.a;
    if (a <= 0.002) discard;

    // Подложка и ореол — цветом клиента, полоса выбеливается к фронту: получается блик по
    // акцентному свечению, а не отдельная белая плёнка поверх предмета.
    vec3 col = mix(tint.rgb, vec3(1.0), clamp(band * 0.75 + edge, 0.0, 1.0));
    fragColor = vec4(col, a);
}
