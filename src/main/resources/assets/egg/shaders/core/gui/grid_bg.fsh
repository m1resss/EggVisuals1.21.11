#version 330

// Геометрическая сетка для главного меню: тонкие линии + узлы на пересечениях,
// лёгкий параллакс от мыши и медленный дрейф, радиальная виньетка. Адаптируется
// под тему: тёмная — светящиеся акцентные линии по тёмному фону; светлая — тёмные
// линии по светлому фону. Непрозрачный фон (без бленда) — рисуется первым.
layout(std140) uniform GridBg {
    float Time;
    float Aspect;
    float MouseX;
    float MouseY;
    float AccentR;
    float AccentG;
    float AccentB;
    float Alpha;
    float BaseR;
    float BaseG;
    float BaseB;
    float Light;
};

in vec2 texCoord0;
out vec4 fragColor;

// Покрытие линии сетки [0..1] с антиалиасингом через производные.
float gridCoverage(vec2 g, float thickness) {
    vec2 d = abs(fract(g - 0.5) - 0.5) / max(fwidth(g), vec2(1e-5));
    float line = min(d.x, d.y);
    return 1.0 - min(line / thickness, 1.0);
}

void main() {
    fragColor = vec4(0.0);

    vec2 uv = texCoord0;
    vec2 p = vec2(uv.x * Aspect, uv.y);

    vec2 parallax = vec2(MouseX, -MouseY) * 0.02;
    vec2 drift = vec2(Time * 0.0040, Time * 0.0026);
    vec2 gp = p + parallax + drift;

    vec3 accent = vec3(AccentR, AccentG, AccentB);
    vec3 base = vec3(BaseR, BaseG, BaseB);

    float fine = gridCoverage(gp * 26.0, 1.3);
    float coarse = gridCoverage(gp * 6.5, 1.5);
    vec2 gg = gp * 6.5;
    vec2 toInt = min(fract(gg), 1.0 - fract(gg));
    float node = 1.0 - smoothstep(0.0, 0.10, length(toInt));

    vec3 col;
    if (Light > 0.5) {
        // светлая тема: тёмные тонкие линии по светлому фону, акцентные узлы
        float ink = clamp(fine * 0.30 + coarse * 0.55, 0.0, 1.0);
        col = mix(base, base * 0.78, ink);
        col = mix(col, accent, node * 0.30);
    } else {
        // тёмная тема: светящиеся акцентные линии по тёмному фону
        col = base;
        col += accent * fine * 0.09;
        col += accent * coarse * 0.16;
        col += accent * node * 0.22;
        float sheen = sin((uv.x + uv.y) * 2.2 - Time * 0.12) * 0.5 + 0.5;
        col += accent * pow(sheen, 8.0) * 0.05;
    }

    // радиальная виньетка (в светлой теме почти незаметная)
    vec2 vc = vec2((uv.x - 0.5) * Aspect, uv.y - 0.5);
    float vig = smoothstep(0.95, 0.30, length(vc));
    float vigMin = Light > 0.5 ? 0.94 : 0.30;
    col *= mix(vigMin, 1.0, vig);

    col *= Alpha;
    fragColor = vec4(col, 1.0);
}
