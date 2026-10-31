#version 330

// Линии по кромкам КАЖДОЙ грани части модели + небольшой глов. Расстояние до
// кромки грани приходит интерполяцией «углов квада» (cornerUv из UV2) и
// переводится в пиксели экрана через fwidth — толщина линии не зависит от
// размера грани и дистанции. Рисуется ПОВЕРХ заливки цветового прохода
// альфа-блендом; глубина EQUAL против prepass — линии ровно на переднем слое,
// сквозь стены как весь чамс. Scale расширяет линию и глов. Cutout по alpha
// скина — прозрачные куски частей линий не дают.

uniform sampler2D Sampler0;

layout(std140) uniform SadnesChams {
    float Time;
    float Speed;
    float Scale;
    float Type;
};

in vec4 vertexColor;
in vec2 texCoord0;
in vec2 cornerUv;

out vec4 fragColor;

void main() {
    if (texture(Sampler0, texCoord0).a < 0.1) discard;

    vec2 fw = max(fwidth(cornerUv), vec2(1.0e-6));
    float dx = min(cornerUv.x, 1.0 - cornerUv.x) / fw.x;
    float dy = min(cornerUv.y, 1.0 - cornerUv.y) / fw.y;
    float d = min(dx, dy) / max(Scale, 0.1);

    // Тонкое ядро ~1 px с мягким краем + экспоненциальный глов на несколько px.
    float core = 1.0 - smoothstep(0.6, 1.4, d);
    float glow = exp(-max(d - 1.0, 0.0) * 0.35) * 0.45;
    float alpha = clamp(core + glow, 0.0, 1.0) * vertexColor.a;
    // Интерьер грани (далеко от кромок) не блендим вовсе — дёшево и без грязи.
    if (alpha < 0.004) discard;

    // Ядро чуть высветляется — линия читается «нежной», глов остаётся тинтом.
    vec3 col = mix(vertexColor.rgb, vec3(1.0), core * 0.2);
    fragColor = vec4(col, alpha);
}
