#version 330

// Ленты молний маркера цели. Вершинник — общий с AA-линиями (core/world/line.vsh): он
// расширяет сегмент в screen-space и отдаёт знаковое пиксельное расстояние до оси (edgePx)
// и полуширину ленты (halfPx). Здесь по этому сечению рисуем раскалённую нить: тонкое
// добела-белое ядро постоянной пиксельной ширины + мягкое цветное гало на всю ширину ленты.
in vec4 vertexColor;
in float edgePx;   // знаковое расстояние от оси ленты до фрагмента, в пикселях
in float halfPx;   // половина толщины ленты, в пикселях

out vec4 fragColor;

// Ширина ядра в пикселях (сигма гаусса) — абсолютная, поэтому нить одинаковой толщины на
// «core»-проходе и не раздувается на широком «halo»-проходе.
const float CORE_SIGMA = 1.7;

void main() {
    fragColor = vec4(0.0);

    float d = abs(edgePx);
    // Плавное покрытие у истинного края ленты (антиалиас как в line.fsh) — гасит запас-геометрию.
    float coverage = clamp(halfPx - d + 0.5, 0.0, 1.0);
    if (coverage <= 0.0) discard;

    // Ядро живёт только на тонких лентах: на широком гало-проходе (halfPx велик) coreW → 0,
    // остаётся чистое мягкое свечение без яркой нити.
    float coreW = smoothstep(10.0, 5.0, halfPx);
    float core  = exp(-(d * d) / (2.0 * CORE_SIGMA * CORE_SIGMA)) * coreW;
    float halo  = pow(clamp(1.0 - d / max(halfPx, 0.001), 0.0, 1.0), 2.2);

    // Центр нити уводим в добела, края держат цвет молнии.
    vec3 col = mix(vertexColor.rgb, vec3(1.0), core);
    // Аддитивный бленд (SRC_ALPHA, ONE): вклад = col * a, пересечения нитей копятся к белому.
    float a = clamp((core * 1.4 + halo * 0.55) * coverage * vertexColor.a, 0.0, 1.0);
    if (a <= 0.003) discard;

    fragColor = vec4(col, a);
}
