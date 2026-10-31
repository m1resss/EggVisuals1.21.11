#version 330

// Стили 3D-чамса по Type из UBO SadnesChams: 0 = Flat, 1 = Glass, 2 = Blur,
// 3 = Cosmic, 4 = Ghost, 5 = Outline (тут только его полупрозрачная заливка —
// саму кайму частей рисует отдельный проход chams/outline поверх). Sampler0 —
// родная текстура модели: alpha даёт cutout (прозрачные части скина не
// заливаются), luma — объём. Sampler1 — сцена БЕЗ сущностей (копия кадра до их
// рендера, см. capturePreEntityScene): Glass преломляет её, Ghost показывает
// сквозь тело, Blur размывает то, что ЗА моделькой (морозная версия в Sampler2),
// Outline подмешивает к ней заливку. Копия делается только когда какой-то батч
// кадра её требует — стили без сцены её не сэмплят (гейтинг). Sampler3 —
// тайлящаяся LUT value-noise fbm (ChamsNoiseLut) для Cosmic: одна выборка вместо
// ~20 вычислений noise на пиксель. localPos — координаты относительно сущности:
// узоры приклеены к мобу и не плывут при движении. Альфа (Intensity,
// vertexColor.a) растворяет модель ЦЕЛИКОМ: пиксель пишется заменой как
// mix(сцена позади, стиль, alpha) — при низкой альфе сквозь модель видно мир,
// а не ванильную модельку под заливкой.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
// Та же сцена, но размытая dual-filter цепочкой (для Blur); иначе = Sampler1.
uniform sampler2D Sampler2;
// Тайлящаяся noise-LUT (REPEAT-сэмплер), RG-пак 16 бит.
uniform sampler2D Sampler3;

layout(std140) uniform SadnesChams {
    float Time;
    float Speed;
    float Scale;
    float Type;
};

in vec4 vertexColor;
in vec2 texCoord0;
in vec3 viewPos;
in vec3 viewNormal;
in vec3 localPos;

out vec4 fragColor;

// fbm из LUT: 16.0 — период запечённого тайла в юнитах (ChamsNoiseLut.PERIOD),
// REPEAT-сэмплер тайлит бесшовно. RG-пак (R — старший байт, G — младший) даёт
// 16-битную точность — 8 бит бандят на pow-математике нэбулы; линейная
// фильтрация пака корректна, значение линейно по (R, G).
float fbmLut(vec2 p) {
    vec2 v = texture(Sampler3, p / 16.0).rg;
    return dot(v, vec2(65280.0, 255.0)) / 65535.0;
}

// Проекция на грань по доминантной оси нормали (модели MC — боксы, швы прячутся
// на рёбрах); тем же свитчем проецируется вектор взгляда для параллакса.
vec2 faceProject(vec3 p, vec3 fn, bool topFace) {
    return topFace ? p.xz : (fn.x > fn.z ? p.zy : p.xy);
}

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    if (tex.a < 0.1) discard;

    int type = int(Type + 0.5);
    vec2 sceneUv = gl_FragCoord.xy / vec2(textureSize(Sampler1, 0));
    vec3 col;

    if (type == 0) {
        // Flat: ровная заливка одним цветом — минимальный путь, без нормалей и сцены.
        col = vertexColor.rgb;
    } else if (type == 5) {
        // Outline: лёгкая полупрозрачная заливка тела — 0.28 примеси тинта к сцене
        // (финальный mix по Intensity растворяет её дальше: итог = 0.28 * Intensity).
        // Кайму каждой части рисует проход chams/outline ПОВЕРХ этой заливки.
        col = mix(texture(Sampler1, sceneUv).rgb, vertexColor.rgb, 0.28);
    } else {
        vec3 n = normalize(viewNormal);
        vec3 v = normalize(-viewPos);
        float ndv = clamp(abs(dot(n, v)), 0.0, 1.0);

        if (type == 1) {
            // Glass: сквозь модель видно тонированный мир позади (без сущностей);
            // нормали дают преломление (Scale = сила), кромка — белый френель + спекуляр.
            float fresnel = pow(1.0 - ndv, 2.5);
            vec2 bend = n.xy * 0.018 * Scale;
            vec3 scene = texture(Sampler1, clamp(sceneUv + bend, 0.0, 1.0)).rgb;
            vec3 r = reflect(-v, n);
            float spec = pow(max(dot(r, normalize(vec3(0.35, 0.75, 0.55))), 0.0), 48.0);
            col = scene * mix(vec3(1.0), vertexColor.rgb, 0.75);
            col += vec3(fresnel * 0.35 + spec * 0.5);
        } else if (type == 2) {
            // Blur: морозное стекло — модель прозрачна, а то, что ЗА ней, заранее
            // размыто dual-filter цепочкой в Sampler2 (Scale = сила, бандинга нет).
            float fresnel = pow(1.0 - ndv, 2.5);
            vec3 frosted = texture(Sampler2, sceneUv).rgb;
            col = frosted * mix(vec3(1.0), vertexColor.rgb, 0.35);
            col += vec3(fresnel * 0.15);
        } else if (type == 3) {
            // Cosmic: объёмная туманность в ЛОКАЛЬНЫХ координатах сущности — узор у
            // каждого моба свой и едет вместе с ним, камера на него не влияет.
            // Domain-warp fbm (из LUT) даёт дымные вихри; внутренний слой чуть
            // сдвигается по взгляду (параллакс) — псевдо-глубина вместо «обоев».
            float fresnel = pow(1.0 - ndv, 2.5);
            float lum = dot(tex.rgb, vec3(0.299, 0.587, 0.114));
            float t = Time * Speed;
            vec3 fn = abs(normalize(cross(dFdx(localPos), dFdy(localPos))));
            bool topFace = fn.y > max(fn.x, fn.z);
            vec2 p = faceProject(localPos, fn, topFace) * 2.0 * Scale;

            vec2 drift = vec2(t * 0.12, -t * 0.08);
            vec2 warp = vec2(fbmLut(p * 1.4 + drift * 2.0), fbmLut(p * 1.4 - drift)) * 1.6;
            float nearLayer = fbmLut(p + warp + drift);
            vec2 par = faceProject(v, fn, topFace) * 0.35;
            float deepLayer = fbmLut(p * 0.55 + warp * 0.5 - par + drift * 0.6);
            float density = pow(clamp(nearLayer * 0.65 + deepLayer * 0.55, 0.0, 1.0), 1.7);

            vec3 deep = vec3(0.02, 0.01, 0.10);
            vec3 dusk = vec3(0.16, 0.05, 0.42);
            vec3 rose = vec3(0.78, 0.22, 0.62);
            vec3 glow = vec3(0.45, 0.75, 1.00);
            col = mix(deep, dusk, smoothstep(0.05, 0.55, density));
            col = mix(col, rose, smoothstep(0.45, 0.85, density));
            col += glow * pow(max(density - 0.75, 0.0) * 4.0, 2.0) * 0.6;

            col *= 0.45 + 0.55 * lum;
            col = mix(col, col * vertexColor.rgb, 0.45);
            col += vertexColor.rgb * fresnel * 0.5;
        } else {
            // Ghost: тело прозрачно (мир позади), кромка светится тинтом по френелю.
            // Scale = ширина кромки (больше — шире); лёгкий тинт-налёт держит силуэт.
            float rim = pow(1.0 - ndv, 2.5 / max(Scale, 0.25));
            vec3 scene = texture(Sampler1, sceneUv).rgb;
            col = scene * mix(vec3(1.0), vertexColor.rgb, 0.15);
            col += vertexColor.rgb * rim;
        }
    }

    // Replace вместо бленда: альфа гасит модель и шейдер одновременно, замешивая
    // пиксель к миру позади сущности (за стеной «позади» = сама стена). При полной
    // альфе сцену не читаем — при гейтинге она может быть вовсе не скопирована.
    if (vertexColor.a >= 0.999) {
        fragColor = vec4(col, 1.0);
    } else {
        fragColor = vec4(mix(texture(Sampler1, sceneUv).rgb, col, vertexColor.a), 1.0);
    }
}
