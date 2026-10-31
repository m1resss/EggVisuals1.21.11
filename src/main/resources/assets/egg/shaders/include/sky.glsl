layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

layout(std140) uniform SadnesSky {
    float Time;
    int Variant;
    float Brightness;
    float Opacity;
    int Quality;
    float SkyR;
    float SkyG;
    float SkyB;
    mat4 PrevViewProj;
    float ScaleX;
    float ScaleY;
    float OffsetX;
    float OffsetY;
    int JitterX;
    int JitterY;
    float OriginTop;
    float HistoryValid;
    float PixelScale;
    float SkyPad0;
    float SkyPad1;
    float SkyPad2;
};

in vec3 skyDir;
out vec4 fragColor;

// Угловой размер пикселя (радианы), задаётся в main до расчёта цвета. По нему звёзды и сияние гасят детали
// мельче пикселя: иначе при повороте камеры они мерцают и рябят муаром.
float pixelAngle;

// Time приходит из CustomSkyRenderer завёрнутым по 1000с. Любая НЕпериодичная функция
// времени (линейный снос домена шума, свободный sin) даёт резкий скачок картинки на
// перезапуске. Поэтому: сносы — по окружности (drift2/drift3), частоты sin — кратны 2π/WRAP.
const float WRAP = 1000.0;
const float TAU = 6.2831853;

// Локально выглядит как линейный снос со скоростью spd, но замкнут по периоду WRAP.
vec2 drift2(float spd) {
    float a = Time * (TAU / WRAP);
    return vec2(cos(a), sin(a)) * (spd * (WRAP / TAU));
}

vec3 drift3(float spd) {
    float a = Time * (TAU / WRAP);
    return vec3(cos(a), sin(a), sin(a + a) * 0.7) * (spd * (WRAP / TAU));
}

// НЕ noise3(): noise1..4 — зарезервированные встроенные имена GLSL (noise3 → vec3).
// Пользовательская перегрузка с другим типом возврата (float) отвергается строгими
// компиляторами (Intel HD): "overloaded functions must have the same return type".
float valueNoise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = hash31(i + vec3(0.0, 0.0, 0.0));
    float n100 = hash31(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash31(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash31(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash31(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash31(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash31(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash31(i + vec3(1.0, 1.0, 1.0));
    float x00 = mix(n000, n100, f.x);
    float x10 = mix(n010, n110, f.x);
    float x01 = mix(n001, n101, f.x);
    float x11 = mix(n011, n111, f.x);
    return mix(mix(x00, x10, f.y), mix(x01, x11, f.y), f.z);
}

float valueNoise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float v00 = hash21(i);
    float v10 = hash21(i + vec2(1.0, 0.0));
    float v01 = hash21(i + vec2(0.0, 1.0));
    float v11 = hash21(i + vec2(1.0, 1.0));
    return mix(mix(v00, v10, f.x), mix(v01, v11, f.x), f.y);
}

float fbm(vec3 p) {
    float v = 0.0;
    float a = 0.5;
    int octaves = clamp(Quality, 1, 4) + 3;
    for (int i = 0; i < 7; i++) {
        if (i >= octaves) break;
        v += a * valueNoise3(p);
        p *= 2.02;
        a *= 0.5;
    }
    return v;
}

// Урезанные октавы для горячих путей чёрной дыры: младшие октавы там субпиксельные.
// Стартовые амплитуды подобраны так, чтобы сумма (и среднее) совпадали с полным fbm.
float fbm4(vec3 p) {
    float v = 0.0;
    float a = 0.52;
    for (int i = 0; i < 4; i++) {
        v += a * valueNoise3(p);
        p *= 2.02;
        a *= 0.5;
    }
    return v;
}

float fbm3(vec3 p) {
    float v = 0.0;
    float a = 0.54;
    for (int i = 0; i < 3; i++) {
        v += a * valueNoise3(p);
        p *= 2.02;
        a *= 0.5;
    }
    return v;
}

// Поле занавесей сияния: 3 октавы 2D-шума с медленным периодическим дрейфом.
// Все слои высоты семплируют ОДНО поле -> вертикальная экструзия без швов.
// footprint — шаг выборки кадра в единицах p: октава мельче шага плавно заменяется своим средним (у горизонта
// поле сжимается, и мелкие октавы давали муар). slope — оценка наклона оставшегося поля на единицу p.
float curtainNoise(vec2 p, float footprint, inout float slope) {
    const mat2 CURL = mat2(0.8, -0.6, 0.6, 0.8);
    float v = 0.0;
    float a = 0.55;
    float frequency = 1.0;
    slope = 0.0;
    vec2 q = p + drift2(0.045);
    int octaves = clamp(Quality, 1, 4) + 1;
    for (int i = 0; i < 5; i++) {
        if (i >= octaves) break;
        float detail = 1.0 - smoothstep(0.35, 0.8, footprint * frequency);
        if (detail <= 0.0) {
            // Эта и все следующие октавы мельче шага: вместо них их среднее 0.5 с остатком амплитуд.
            v += a * (1.0 - exp2(float(i - octaves)));
            break;
        }
        v += a * mix(0.5, valueNoise2(q), detail);
        slope += a * frequency * detail * 0.5;         // средний наклон value-шума ~0.5 на ячейку
        q = CURL * q * 2.1 + drift2(0.015);
        frequency *= 2.1;
        a *= 0.5;
    }
    return v;
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float dc = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * dc + e)), dc / (q.x + e), q.x);
}

// Звёзды: редкая сетка ячеек по направлению, мерцание во времени. Возвращает ЦВЕТ —
// тугое ядро + мягкое гало, лёгкий холодный/тёплый оттенок по хешу ячейки.
// Частота мерцания квантуется к кратной 2π/WRAP -> нет скачка фазы на перезапуске Time.
vec3 starField(vec3 d, float density, float twinkle) {
    vec3 g = d * density;
    vec3 id = floor(g);
    float h = hash31(id);
    if (h < 0.95) return vec3(0.0);
    vec3 f = fract(g) - 0.5;
    float r = length(f);
    // Ядро не меньше пикселя: субпиксельная звезда то попадает в выборку, то нет и мерцает при повороте
    // камеры. Яркость ядра не снижается — звезда остаётся чёткой точкой в пиксель.
    float core = smoothstep(max(0.085, pixelAngle * density), 0.0, r);
    float halo = 0.22 * smoothstep(0.32, 0.02, r);          // мягкое гало вокруг ярких звёзд
    float w = floor(twinkle * (WRAP / TAU) + 0.5) * (TAU / WRAP);
    float tw = 0.55 + 0.45 * sin(Time * w + h * 60.0);
    float bright = (h - 0.95) / 0.05;                        // редкость ячейки -> яркость
    float ch = hash21(id.xy + id.z * 1.7);                   // оттенок: холодный..тёплый
    vec3 tint = mix(vec3(0.72, 0.83, 1.0), vec3(1.0, 0.90, 0.75), ch);
    return tint * (core + halo) * tw * bright;
}

// --- Чёрная дыра (Variant 3): горизонт = 1, фотонная сфера = 1.5, камера в 12.5 от центра ---
// Камера дальше -> тень компактнее; диск широкий -> кольца доминируют в кадре.
const float BH_RI = 2.0;   // внутренний край диска (ISCO)
const float BH_RO = 9.5;   // внешний край диска

// Фон дальнего космоса по (уже линзированному) направлению луча.
vec3 bhBackground(vec3 bd, vec3 accent) {
    float dep = clamp(bd.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 space = mix(vec3(0.0035, 0.006, 0.010), vec3(0.001, 0.002, 0.004), dep);
    float hz = fbm3(bd * 1.7 + drift3(0.02));
    space += hsv2rgb(vec3(accent.x, min(accent.y, 0.5), 0.028)) * smoothstep(0.62, 1.0, hz);
    space += starField(bd, 70.0, 3.0);
    space += starField(bd, 30.0, 1.2) * 0.6;
    return space;
}

// Эмиссия тонкого слоя аккреционного диска в точке (ax, ay) базиса плоскости; tau — оптическая
// толщина: гасит линзированные дуги и фон ЗА этим пересечением (отсюда читаемая глубина).
// orb — направление движения материи, rd — направление фотона: доплер-буст делает
// приближающуюся сторону ярче и белее, уходящую — тусклее и краснее.
// Волокна: fbm на окружности (cos/sin угла) -> бесшовно по углу; спиральный сдвиг фазы по log(r)
// имитирует дифференциальное вращение. Слои крутятся жёстко с ЦЕЛЫМ числом оборотов на WRAP,
// поэтому картинка непрерывна на перезапуске Time.
vec3 bhDisk(float ax, float ay, vec3 orb, vec3 rd, inout float tau) {
    tau = 0.0;
    float rc = sqrt(ax * ax + ay * ay);
    if (rc < BH_RI || rc > BH_RO * 1.05) return vec3(0.0);
    float aa = atan(ay, ax);
    float lg = log(rc);
    float spin = Time * (32.0 * TAU / WRAP);
    float ph1 = aa + spin + lg * 4.5;
    float ph2 = aa + spin * 2.0 + lg * 8.0;
    float n1 = fbm4(vec3(cos(ph1) * 2.0, sin(ph1) * 2.0, lg * 7.0));
    float n2 = fbm3(vec3(cos(ph2) * 4.0, sin(ph2) * 4.0, lg * 16.0 + 7.0));
    float dens = pow(max(1.55 * n1 + 0.70 * n2 - 0.55, 0.001), 1.35); // широкие волокна с тёмными прожилками
    // Рваная внешняя кромка: радиус гуляет по углу и вращается с диском; затухание завершается
    // ДО жёсткого rc-гарда выше, чтобы у диска не оставалось видимого среза
    vec2 rimc = vec2(cos(aa + spin), sin(aa + spin));
    float rag = valueNoise2(rimc * 2.2) * 0.65 + valueNoise2(rimc * 5.0 + vec2(3.7)) * 0.35;
    float roEff = BH_RO * (0.70 + 0.28 * rag);
    float shape = smoothstep(BH_RI, BH_RI + 0.10, rc) * smoothstep(roEff, roEff - 2.2, rc);
    if (shape < 0.004) return vec3(0.0);
    float tn = (rc - BH_RI) / (BH_RO - BH_RI);
    float fallo = mix(0.30, 1.0, pow(BH_RI / rc, 1.6));    // светимость падает наружу
    float dop = -dot(orb, rd);
    float beam = pow(clamp(1.0 + 0.75 * dop, 0.45, 2.1), 1.5);
    float hot = smoothstep(1.05, 1.9, dens * beam);        // перегретые сгустки выгорают в белый
    vec3 cw = mix(vec3(1.05, 0.97, 0.86), vec3(1.0, 0.62, 0.22), smoothstep(0.0, 0.5, tn));
    cw = mix(cw, vec3(0.48, 0.17, 0.06), smoothstep(0.5, 1.0, tn)); // золото -> янтарь -> ржавый уголь
    cw = mix(cw, vec3(1.0, 0.99, 0.97), min(clamp(0.4 * (beam - 1.0), 0.0, 0.55) + hot * 0.6, 1.0));
    float inGlow = exp(-(rc - BH_RI) * 2.2);               // раскалённый внутренний обод
    tau = (1.5 * dens + 0.5 * inGlow) * shape;
    return (cw * dens * (0.55 + 0.50 * hot) + vec3(1.0, 0.94, 0.80) * inGlow) * (shape * beam * fallo);
}

// fbm с LOD: октава мельче шага пикселя (footprint — пиксель в единицах p, по самой сжатой оси) плавно
// заменяется своим средним, иначе мелкий узор рябит муаром при повороте камеры.
float fbmLod(vec3 p, float footprint) {
    float v = 0.0;
    float a = 0.5;
    int octaves = clamp(Quality, 1, 4) + 3;
    for (int i = 0; i < 7; i++) {
        if (i >= octaves) break;
        float detail = 1.0 - smoothstep(0.3, 0.7, footprint);
        if (detail <= 0.0) {
            v += a * (1.0 - exp2(float(i - octaves)));   // среднее 0.5 × остаток амплитуд
            break;
        }
        v += a * mix(0.5, valueNoise3(p), detail);
        p *= 2.02;
        footprint *= 2.02;
        a *= 0.5;
    }
    return v;
}

// --- Ночное небо (Variant 6); его фон и звёзды переиспользует планета (Variant 5) ---

// Цвет звезды по температуре t 0..1: голубовато-белые .. белые .. жёлтые .. оранжевые.
vec3 starTint(float t) {
    vec3 c = mix(vec3(0.66, 0.77, 1.0), vec3(1.0, 0.97, 0.93), smoothstep(0.0, 0.3, t));
    c = mix(c, vec3(1.0, 0.86, 0.64), smoothstep(0.45, 0.8, t));
    return mix(c, vec3(1.0, 0.68, 0.42), smoothstep(0.86, 1.0, t));
}

// Координаты звёздной сетки: грань куба + равноугольные координаты на ней (≈ радианы от центра грани).
// Ячейки двумерные -> звезда со смещением и гало целиком внутри своей ячейки (3D-ячейки режут звезду
// сферой: гало без ядра висят пятнами). atan выравнивает плотность к углам грани.
vec3 starCoords(vec3 d) {
    vec3 a = abs(d);
    if (a.x >= a.y && a.x >= a.z) return vec3(d.x > 0.0 ? 0.0 : 1.0, atan(d.y / a.x), atan(d.z / a.x));
    if (a.y >= a.z) return vec3(d.y > 0.0 ? 2.0 : 3.0, atan(d.x / a.y), atan(d.z / a.y));
    return vec3(d.z > 0.0 ? 4.0 : 5.0, atan(d.x / a.z), atan(d.y / a.z));
}

// Слой звёзд: в ячейке с вероятностью chance сидит звезда. Поток по степенному закону (много тусклых, мало
// ярких), gain — поток типичной звезды слоя. Ядро не уже ~пикселя с сохранением потока: субпиксельная
// звезда не мерцает при повороте, а тускнеет. Гало у ярких — в пределах ячейки (центр смещён не дальше 0.2).
vec3 starLayer(vec3 sc, float density, float chance, float gain) {
    vec2 g = sc.yz * density;
    vec3 key = vec3(floor(g), sc.x * 57.0 + density);
    float h = hash31(key);
    if (h >= chance) return vec3(0.0);
    float u = h / chance;                                    // равномерно 0..1 у занятых ячеек
    vec2 center = 0.3 + 0.4 * vec2(hash31(key + 13.7), hash31(key + 29.3));
    float cellR = length(fract(g) - center);                 // расстояние в долях ячейки
    float r = cellR / density;                               // угловое, рад
    float flux = gain * min(pow(u + 0.004, -0.6), 14.0);
    const float CORE = 0.0007;                               // собственный радиус ядра, рад
    float sigma = max(CORE, 0.6 * pixelAngle);
    float core = (CORE * CORE) / (sigma * sigma) * exp(-r * r / (sigma * sigma));
    float halo = 0.05 * pow(max(1.0 - cellR / 0.3, 0.0), 3.0);
    float w = floor((1.5 + 3.5 * fract(u * 7.31)) * (WRAP / TAU) + 0.5) * (TAU / WRAP);
    float tw = 1.0 + 0.28 * sin(Time * w + u * 91.0);        // мерцание, частота кратна 2π/WRAP
    return starTint(hash31(key + 41.9)) * (flux * (core + halo) * tw);
}

// Фон: тёмный зенит, чуть светлее к горизонту; над горизонтом деликатное свечение атмосферы (airglow):
// зеленоватый нижний слой и лиловый выше, медленно волнуются. Цвет настройки слегка тонирует верхний слой.
vec3 nightBase(vec3 d, vec3 accent) {
    float alt = d.y;
    vec3 col = mix(vec3(0.015, 0.020, 0.032), vec3(0.0035, 0.005, 0.011), sqrt(clamp(alt, 0.0, 1.0)));
    col = mix(col, vec3(0.004, 0.005, 0.008), smoothstep(0.0, -0.35, alt));
    float wave = valueNoise3(d * vec3(4.0, 1.5, 4.0) + drift3(0.004));
    float low = (alt - 0.06) / 0.05;                         // pow с отрицательным основанием не определён
    float high = (alt - 0.16) / 0.09;
    float green = exp(-low * low) * (0.55 + 0.9 * wave);
    float upper = exp(-high * high) * (0.8 + 0.4 * wave);
    vec3 violet = mix(vec3(0.45, 0.22, 0.55), hsv2rgb(vec3(accent.x, 0.6, 0.6)), 0.45);
    return col + vec3(0.07, 0.20, 0.11) * (0.12 * green) + violet * (0.035 * upper);
}

// Млечный Путь: полоса у наклонённой галактической плоскости (по dot(d, GAL_N) — без швов), тёплый балдж ядра
// в одной её части, звёздные облака и тёмные пылевые прожилки, вытянутые вдоль полосы (шум сжат поперёк).
// stars — насколько здесь густо неразрешённых звёзд: слои звёздной пыли гуще в светлых облаках, реже в пыли.
const vec3 GAL_N = vec3(0.7292, -0.4354, -0.5279);    // нормаль галактической плоскости
const vec3 GAL_C = vec3(-0.4891, 0.2079, -0.8471);    // на ядро: ~12° над горизонтом
const vec3 GAL_B = vec3(0.4786, 0.8759, -0.0613);     // вдоль полосы от ядра вверх (~60° к горизонту)

vec3 milkyWay(vec3 d, vec3 accent, inout float stars) {
    stars = 0.0;
    float lat = dot(d, GAL_N);
    float toCore = dot(d, GAL_C);
    float nearCore = 0.5 + 0.5 * toCore;
    float width = mix(0.12, 0.24, nearCore * nearCore);      // диск раздувается к центру галактики
    float disk = exp(-lat * lat / (width * width));
    float bulge = exp(-(1.0 - toCore) * 18.0 - lat * lat * 24.0);
    vec3 tone = mix(vec3(0.76, 0.84, 1.0), vec3(1.0, 0.84, 0.64), smoothstep(0.45, 0.97, nearCore));
    // Широкое рассеянное свечение вокруг полосы; дальше от неё шум не нужен
    vec3 halo = tone * (exp(-lat * lat / (6.0 * width * width)) * (0.02 + 0.03 * nearCore));
    if (disk + bulge < 0.004) return halo;

    // Шум сжат поперёк полосы: облака втрое, пыль вшестеро — тянутся вдоль неё
    vec3 q = d * 2.4 + GAL_N * (lat * 5.0);
    float fp = pixelAngle * 7.4;
    float clouds = fbmLod(q, fp);
    float clumps = smoothstep(0.32, 0.78, clouds);               // звёздные облака
    // Пыль: рваные тёмные облака (порог fbm, искривлённого облаками), тоньше звёздного диска, и Великий
    // Разлом — тёмная жила по центру полосы по одну сторону от ядра
    float dustN = fbmLod(d * 3.2 + GAL_N * (lat * 16.0 + clouds - 0.5) + vec3(7.3, 1.7, 4.1), pixelAngle * 19.2);
    float dustDisk = exp(-lat * lat / (0.36 * width * width));
    // Пыль видна силуэтом на ярких звёздных облаках; балдж просвечивает сквозь неё
    float patches = smoothstep(0.52, 0.7, dustN) * dustDisk * (0.35 + 0.65 * clumps) * (1.0 - 0.5 * bulge);
    float riftLat = (lat - 0.012 + (clouds - 0.5) * 0.07) / mix(0.014, 0.034, nearCore);
    float rift = exp(-riftLat * riftLat) * smoothstep(-0.05, 0.25, dot(d, GAL_B)) * smoothstep(-0.3, 0.5, toCore);
    float dust = min(max(patches, rift * (0.15 + 0.75 * smoothstep(0.38, 0.62, dustN))), 1.0);

    float glow = disk * (0.2 + 0.8 * nearCore * nearCore) * (0.2 + 1.5 * clumps) + bulge * (1.5 + 1.5 * clumps);
    // Пыль гасит синее сильнее красного: полупрозрачные края прожилок чуть буреют
    vec3 col = (tone * (glow * 0.32) + halo) * exp(-vec3(1.6, 1.8, 2.05) * dust);
    // Эмиссионные туманности вдоль полосы — оттенок цвета настройки, светятся у кромок пыли, а не под ней
    float neb = smoothstep(0.52, 0.72, fbm3(q * 1.3 + vec3(19.1, 3.3, 8.7))) * disk * (1.0 - 0.7 * dust);
    col += hsv2rgb(vec3(accent.x, clamp(accent.y * 1.2, 0.45, 0.9), 1.0)) * (neb * 0.10);
    stars = (disk * (0.3 + 0.9 * clumps) + bulge) * exp(-2.5 * dust);
    return col;
}

// Метеор: раз в period секунд с вероятностью ~2/3 вспыхивает короткая черта в случайном месте верхней
// полусферы. Слот = floor(Time / period), period делит WRAP нацело -> периодично; вспышка живёт первые
// LIFE секунд слота, поэтому на стыке WRAP метеора нет. Ширина аналитическая: не уже пикселя, яркость сохранена.
vec3 meteor(vec3 d, float period, float seed) {
    const float LIFE = 0.8;
    float slot = floor(Time / period);
    float age = Time - slot * period;
    if (age > LIFE || hash21(vec2(slot, seed)) > 0.65) return vec3(0.0);
    float az = hash21(vec2(slot + 0.37, seed + 1.0)) * TAU;
    float el = mix(0.3, 1.1, hash21(vec2(slot + 0.71, seed + 2.0)));
    vec3 p0 = vec3(cos(az) * cos(el), sin(el), sin(az) * cos(el));
    if (dot(d, p0) < 0.95) return vec3(0.0);                 // путь ~17° — дальше считать нечего
    vec3 east = vec3(-sin(az), 0.0, cos(az));
    vec3 down = cross(p0, east);                             // касательная к горизонту
    float tilt = (hash21(vec2(slot + 0.13, seed + 3.0)) - 0.5) * 2.4;
    vec3 dir = east * sin(tilt) + down * cos(tilt);          // летит преимущественно вниз
    float s = age / LIFE;
    float head = 0.3 * s;                                    // пройденная дуга, рад
    float tail = max(head - 0.12, 0.0);
    float along = dot(d, dir);
    float across = dot(d, cross(p0, dir));
    float width = max(0.0005, 0.7 * pixelAngle);
    float line = exp(-across * across / (width * width)) * (0.0005 / width);
    float soft = max(pixelAngle, 0.001);
    float streak = smoothstep(tail - soft, tail + soft, along) * (1.0 - smoothstep(head - soft, head + soft, along));
    float fade = pow(clamp((along - tail) / max(head - tail, soft), 0.0, 1.0), 1.5);  // ярче к голове
    float life = smoothstep(0.0, 0.12, s) * (1.0 - smoothstep(0.55, 1.0, s));
    return vec3(0.85, 1.0, 0.92) * (line * streak * (0.15 + 0.85 * fade) * life * 2.2);
}

// Ночное небо: фон и airglow, Млечный Путь, четыре слоя звёзд (яркие с гало .. звёздная пыль в полосе), метеоры.
// У горизонта атмосфера гасит звёзды и полосу.
vec3 nightSky(vec3 d, vec3 accent) {
    float stars = 0.0;
    vec3 galaxy = milkyWay(d, accent, stars);
    vec3 sc = starCoords(d);
    vec3 field = starLayer(sc, 40.0, 0.10, 0.45)
               + starLayer(sc, 95.0, 0.14, 0.14)
               + starLayer(sc, 150.0, 0.06 + 0.3 * stars, 0.12);
    // Звёздная пыль: ячейки в пару пикселей; мельче — растворяется в свечении полосы (иначе рябь)
    float dustLod = 1.0 - smoothstep(0.4, 0.7, pixelAngle * 270.0);
    if (dustLod > 0.0 && stars > 0.02) field += starLayer(sc, 270.0, 0.5 * min(stars, 1.0), 0.07) * dustLod;
    float extinction = mix(0.3, 1.0, smoothstep(-0.02, 0.3, d.y));
    return nightBase(d, accent) + (galaxy + field) * extinction + meteor(d, 5.0, 0.0) + meteor(d, 8.0, 7.0);
}

// --- Планета с кольцами (Variant 5): газовый гигант радиуса 1, кольца в плоскости экватора ---
// Диск строится в картинной плоскости (гномоническая проекция вокруг PL_F), лучи к планете параллельны:
// вид как на телеснимке Сатурна. При честной перспективе (R/D ≈ 0.3) кольцо радиуса 2.4R почти упирается
// в наблюдателя — задняя дуга целиком прячется за диском, передняя раздувается на пол-неба.
const vec3 PL_F = vec3(0.5727, 0.4540, 0.6826);    // на центр планеты: ~27° над горизонтом
const vec3 PL_R = vec3(-0.7660, 0.0, 0.6428);      // базис картинной плоскости: вправо
const vec3 PL_U = vec3(-0.2918, 0.8910, -0.3478);  // и вверх
const vec3 PL_N = vec3(-0.2557, 0.6094, -0.7505);  // ось вращения = нормаль колец: раскрыты на ~22°, завалены вбок
const vec3 PL_B1 = vec3(0.8188, -0.2762, -0.5033); // базис плоскости экватора
const vec3 PL_B2 = vec3(-0.5140, -0.7432, -0.4283);
const vec3 PL_S = vec3(-0.9748, -0.1043, -0.1971); // на солнце: фаза ~42° -> планета почти полная, терминатор слева
const float PL_TAN = 0.30;                          // тангенс углового радиуса диска (~16.7°)
const vec3 STORM = vec3(0.593, 0.7472, 0.30);      // штормовое пятно во вращающейся системе (x, y, sin широты)
const vec3 STORM_E = vec3(-0.7833, 0.6216, 0.0);   // касательная вдоль долготы в центре пятна

// Доля пикселя шириной w внутри интервала [a, b] по r: бокс-фильтр ступеньки, края колец без лесенки.
float ringSpan(float r, float a, float b, float w) {
    return clamp((r - a) / w + 0.5, 0.0, 1.0) - clamp((r - b) / w + 0.5, 0.0, 1.0);
}

// Оптическая толщина колец на радиусе r (в радиусах планеты), fp — шаг пикселя по r. Зоны как у Сатурна:
// тусклое C, плотное B, щель Кассини, A с щелью Энке, узкое F. Тонкие колечки — 1D-шум, октавы мельче
// пикселя заменяются средним (иначе муар). detail — рисунок колечек 0..1 для яркости.
float ringTau(float r, float fp, inout float detail) {
    detail = 0.5;
    float w = max(fp, 0.002);
    float tau = 0.10 * ringSpan(r, 1.35, 1.56, w)
              + mix(0.9, 2.4, smoothstep(1.56, 1.85, r)) * ringSpan(r, 1.56, 1.95, w)
              + 0.04 * ringSpan(r, 1.95, 2.03, w)
              + 0.60 * ringSpan(r, 2.03, 2.30, w)
              - 0.55 * ringSpan(r, 2.215, 2.228, w)
              + 0.40 * ringSpan(r, 2.37, 2.385, w);
    if (tau < 0.001) return 0.0;
    float v = 0.0;
    float a = 0.5;
    float f = 23.0;
    for (int i = 0; i < 4; i++) {
        float lod = 1.0 - smoothstep(0.25, 0.6, f * fp);
        v += a * mix(0.5, valueNoise2(vec2(r * f, 1.7 * float(i))), lod);
        f *= 2.7;
        a *= 0.55;
    }
    detail = v;
    return tau * (0.15 + 1.7 * v);                           // среднее множителя ≈ 1
}

// Порядок слоёв: фон со звёздами -> задняя дуга колец -> диск (сглаженный лимб) -> гало атмосферы -> передняя дуга.
vec3 planetSky(vec3 d, vec3 accent) {
    vec3 sc = starCoords(d);
    vec3 col = nightBase(d, accent) + (starLayer(sc, 40.0, 0.10, 0.45) + starLayer(sc, 95.0, 0.14, 0.14)
            + starLayer(sc, 150.0, 0.08, 0.12)) * mix(0.3, 1.0, smoothstep(-0.02, 0.3, d.y));
    float cosA = dot(d, PL_F);
    if (cosA < 0.55) return col;                             // дальше колец (~36°) и гало планеты нет
    vec2 q = vec2(dot(d, PL_R), dot(d, PL_U)) / (cosA * PL_TAN);
    float pxq = pixelAngle / (PL_TAN * cosA * cosA);         // шаг пикселя в радиусах планеты
    float rho = length(q);
    float cover = clamp((1.0 - rho) / pxq + 0.5, 0.0, 1.0);  // доля пикселя под диском — сглаженный лимб

    float hue = accent.x;
    float sat = clamp(accent.y, 0.3, 0.9);
    vec3 atmo = hsv2rgb(vec3(fract(hue + 0.02), sat * 0.55, 1.0));
    float sunN = dot(PL_S, PL_N);

    // Кольца: пересечение луча с плоскостью экватора. z — глубина точки вдоль луча (меньше = ближе к нам)
    float fn = dot(PL_F, PL_N);
    vec2 an = vec2(dot(PL_R, PL_N), dot(PL_U, PL_N));
    float z = -dot(q, an) / fn;
    vec3 p = PL_R * q.x + PL_U * q.y + PL_F * z;
    float r = length(p);
    float ringFp = length(q - an * (z / fn)) / max(r, 1.0e-3) * pxq;  // |grad r| · шаг пикселя
    float detail = 0.5;
    float tau = r > 1.3 && r < 2.45 ? ringTau(r, ringFp, detail) : 0.0;
    vec3 ringE = vec3(0.0);
    float ringT = 1.0;
    if (tau > 0.0) {
        float viewMu = abs(fn);
        float sunMu = abs(sunN);
        ringT = exp(-tau / viewMu);
        // Однократное рассеяние слоя: тонкие места тусклы, плотное B светится почти как диск
        float lit = (1.0 - exp(-tau * (1.0 / sunMu + 1.0 / viewMu))) * sunMu / (sunMu + viewMu) * 1.8;
        // Тень планеты — цилиндр за диском по лучу от солнца. Полутень шире с удалением от планеты (солнце не точка,
        // край размывает атмосфера); у края свет проходит сквозь атмосферу и краснеет. В самой тени кольца слабо
        // подсвечивает дневная сторона планеты: узор колец остаётся виден, тень не читается как вырез.
        float behind = -dot(p, PL_S);
        float pen = max(0.012 + 0.025 * behind, ringFp);
        float sunlit = behind > 0.0 ? smoothstep(1.0 - pen, 1.0 + pen, length(p + PL_S * behind)) : 1.0;
        vec3 shade = pow(vec3(sunlit), vec3(1.0, 1.15, 1.35)) + atmo * (0.1 * (1.0 - sunlit));
        vec3 ringCol = hsv2rgb(vec3(fract(hue + 0.04 * (detail - 0.5)), sat * mix(0.4, 0.3, smoothstep(1.6, 2.0, r)), 0.8));
        ringCol *= mix(0.55, 1.0, smoothstep(1.45, 1.7, r)) * (0.45 + 1.1 * detail);  // C тусклее и серее
        ringE = ringCol * lit * shade;
    }
    bool ringFront = z < -sqrt(max(1.0 - rho * rho, 0.0));
    if (!ringFront) col = col * ringT + ringE;               // задняя дуга — под диском

    if (cover > 0.0) {
        vec2 qd = q / max(rho, 1.0);
        float mu = sqrt(max(1.0 - dot(qd, qd), 0.0));        // косинус угла обзора поверхности
        vec3 n = PL_R * qd.x + PL_U * qd.y - PL_F * mu;
        float lat = dot(n, PL_N);
        float spin = Time * (6.0 * TAU / WRAP);              // 6 оборотов за WRAP
        float x = dot(n, PL_B1);
        float y = dot(n, PL_B2);
        vec3 m = vec3(x * cos(spin) - y * sin(spin), x * sin(spin) + y * cos(spin), lat);
        float fpS = pxq / max(mu, 0.08);                     // шаг пикселя по поверхности: у лимба растёт

        // Пояса: турбулентный fbm, вытянутый вдоль долготы, ломает границы; ширина и тон поясов — 1D-шум широты
        float warp = valueNoise3(vec3(m.xy * 1.7, lat * 5.0) + vec3(11.0, 3.0, 7.0));   // крупные извивы поясов
        float turb = fbmLod(vec3(m.xy * 2.6, lat * 13.0 + warp * 1.5) + vec3(4.1, 9.3, 2.7), fpS * 13.0);
        float latW = lat + ((turb - 0.5) * 0.14 + (warp - 0.5) * 0.05) * (1.0 - abs(lat));
        float lod2 = 1.0 - smoothstep(0.25, 0.6, fpS * 23.0);
        float tone = valueNoise2(vec2(latW * 8.0, 0.5)) * 0.65 + mix(0.5, valueNoise2(vec2(latW * 23.0, 4.5)), lod2) * 0.35;
        vec3 cLight = hsv2rgb(vec3(fract(hue + 0.015), sat * 0.3, 0.98));
        vec3 cMid = hsv2rgb(vec3(hue, sat * 0.58, 0.82));
        vec3 cDark = hsv2rgb(vec3(fract(hue - 0.035), sat * 0.85, 0.52));
        vec3 body = mix(cDark, cMid, smoothstep(0.25, 0.5, tone));
        body = mix(body, cLight, smoothstep(0.5, 0.75, tone));
        // Вихри и фестоны: мелкий почти изотропный fbm, закрученный турбулентностью поясов
        float eddies = fbmLod(vec3(m.xy * 7.0, lat * 20.0) + vec3(turb * 2.5), fpS * 20.0);
        body *= 0.72 + 0.3 * turb + 0.3 * eddies;
        body = mix(body, cDark * 0.8, smoothstep(0.7, 0.95, abs(lat)));   // тёмные полярные шапки

        // Штормовое пятно: овал с закрученными полосами и светлым воротником
        float ex = dot(m - STORM, STORM_E) / 0.20;
        float ey = (lat - STORM.z) / 0.085;
        float ell = ex * ex + ey * ey + step(dot(m, STORM), 0.0) * 9.0;   // обратная сторона — мимо
        if (ell < 2.0) {
            float swirl = 0.5 + 0.5 * sin(atan(ey, ex + 1.0e-4) * 2.0 + sqrt(ell) * 5.0 + turb * 6.0);
            vec3 stormCol = mix(hsv2rgb(vec3(fract(hue - 0.05), min(sat * 1.05, 1.0), 0.62)),
                    hsv2rgb(vec3(fract(hue - 0.03), sat * 0.55, 0.88)), 0.25 + 0.35 * swirl);
            body = mix(body, cLight, smoothstep(1.7, 1.1, ell) * 0.6);
            body = mix(body, stormCol, smoothstep(1.0, 0.55, ell));
        }

        // Свет: мягкий терминатор, потемнение к лимбу, тень колец на южном полушарии
        float ndl = dot(n, PL_S);
        float light = pow(clamp((ndl + 0.05) / 1.05, 0.0, 1.0), 0.7) * mix(0.5, 1.0, sqrt(mu));
        float tS = -lat / sunN;
        if (tS > 0.0) {
            float shadowDetail = 0.0;
            float tauS = ringTau(length(n + PL_S * tS), fpS / abs(sunN), shadowDetail);
            light *= mix(1.0, exp(-tauS / abs(sunN)), 0.9);
        }
        vec3 planet = body * light;
        // Атмосферный ободок у края, ярче на освещённой стороне; за терминатором — слабые сумерки
        planet += atmo * (pow(1.0 - mu, 3.0) * smoothstep(-0.25, 0.4, ndl) * 0.45);
        planet += atmo * (0.02 * exp(-max(-ndl, 0.0) * 12.0));
        col = mix(col, planet, cover);
    }
    // Гало атмосферы снаружи лимба, со стороны солнца
    if (rho > 1.0) {
        vec2 qn = q / rho;
        float limbLit = smoothstep(-0.3, 0.5, dot(PL_R * qn.x + PL_U * qn.y, PL_S));
        col += atmo * (exp(-(rho - 1.0) * 45.0) * 0.22 * limbLit);
    }
    if (ringFront) col = col * ringT + ringE;                // передняя дуга ложится поверх диска
    return col;
}

// --- Звездопад (Variant 7): густое небо мелких звёзд, метеорный поток из радианта, эмиссионная туманность ---
const vec3 SF_RAD = vec3(0.2514, 0.8988, -0.3591);  // радиант потока: высоко, метеоры «падают» во все стороны
const vec3 SF_RA = vec3(-0.8192, 0.0, -0.5736);     // базис, перпендикулярный радианту
const vec3 SF_RB = vec3(-0.5155, 0.4384, 0.7362);
const vec3 SF_NEB = vec3(0.8297, 0.4695, -0.302);   // центр туманности: ~28° над горизонтом, в 42° от радианта
const vec3 SF_NE1 = vec3(0.342, 0.0, 0.9397);       // её картинная плоскость: вправо
const vec3 SF_NE2 = vec3(-0.4412, 0.8829, 0.1606);  // и вверх

// Яркие звёзды как starLayer, но у самых ярких — тонкий крестик лучиков. Лучик не уже пикселя и гаснет к 0.3 ячейки:
// звезда целиком в своей ячейке.
vec3 sparkleStars(vec3 sc, float density, float chance, float gain) {
    vec2 g = sc.yz * density;
    vec3 key = vec3(floor(g), sc.x * 57.0 + density);
    float h = hash31(key);
    if (h >= chance) return vec3(0.0);
    float u = h / chance;                                    // равномерно 0..1 у занятых ячеек
    vec2 center = 0.3 + 0.4 * vec2(hash31(key + 13.7), hash31(key + 29.3));
    vec2 f = fract(g) - center;                              // от центра звезды, доли ячейки
    float cellR = length(f);
    float r = cellR / density;
    float flux = gain * min(pow(u + 0.004, -0.6), 14.0);
    const float CORE = 0.0006;
    float sigma = max(CORE, 0.6 * pixelAngle);
    float core = (CORE * CORE) / (sigma * sigma) * exp(-r * r / (sigma * sigma));
    float haloR = max(1.0 - cellR / 0.3, 0.0);
    float halo = 0.05 * haloR * haloR * haloR;
    float cellPx = pixelAngle * density;                     // пиксель в долях ячейки
    float spark = smoothstep(4.0, 11.0, flux / gain) * smoothstep(0.05, 0.03, cellPx);
    if (spark > 0.0) {
        float rayW = max(0.012, 0.6 * cellPx);
        vec2 a = abs(f);
        vec2 fall = pow(max(1.0 - a / 0.3, 0.0), vec2(4.0));
        float rays = exp(-a.y * a.y / (rayW * rayW)) * fall.x + exp(-a.x * a.x / (rayW * rayW)) * fall.y;
        halo += spark * 0.5 * rays * (0.012 / rayW);
    }
    float tw = floor((1.5 + 3.5 * fract(u * 7.31)) * (WRAP / TAU) + 0.5) * (TAU / WRAP);
    float twinkle = 1.0 + 0.35 * sin(Time * tw + u * 91.0);  // частота кратна 2π/WRAP
    return starTint(hash31(key + 41.9)) * (flux * (core + halo) * twinkle);
}

// Звёздная пыль: мелкие звёзды без гало и мерцания — гауссово ядро не уже пикселя и оттенок. Ячейки тут в пару
// пикселей и почти каждый пиксель проходит весь расчёт, поэтому случайные числа выводятся из одного хеша.
vec3 dustStars(vec3 sc, float density, float chance, float gain) {
    vec2 g = sc.yz * density;
    float h = hash31(vec3(floor(g), sc.x * 57.0 + density));
    if (h >= chance) return vec3(0.0);
    float u = h / chance;
    vec2 f = fract(g) - 0.3 - 0.4 * fract(vec2(u * 37.1, u * 91.7));
    const float CORE = 0.0006;
    float sigma2 = max(CORE * CORE, 0.36 * pixelAngle * pixelAngle);
    float core = (CORE * CORE) / sigma2 * exp(-dot(f, f) / (density * density * sigma2));
    return mix(vec3(0.74, 0.84, 1.0), vec3(1.0, 0.9, 0.76), fract(u * 13.3)) * (gain * (0.5 + 1.5 * u * u) * core);
}

// Метеоры одного слоя потока: следы расходятся от радианта по большим кругам. Небо вокруг радианта поделено по
// азимуту phi на секторы; за слот времени (period делит WRAP нацело) в секторе пролетает не больше одного метеора
// и держится в средней половине сектора — пиксель считает только свой сектор, и десятки метеоров стоят как один.
// Метеор живёт внутри слота, поэтому на стыке WRAP его нет. theta — угол от радианта.
vec3 showerLayer(float theta, float phi, vec3 trail, float sectors, float period, float seed) {
    float sector = floor(phi * (sectors / TAU));
    float slot = floor(Time / period);
    vec2 key = vec2(sector * 1.37 + seed, slot * 0.71 + seed * 3.1);
    if (hash21(key) > 0.9) return vec3(0.0);                 // в этом слоте сектор пуст
    float life = mix(0.45, 1.0, hash21(key + 0.31));
    float age = Time - slot * period - hash21(key + 0.57) * (period - life);
    if (age < 0.0 || age > life) return vec3(0.0);
    float across = sin(theta) * sin(phi - (sector + 0.25 + 0.5 * hash21(key + 0.83)) * (TAU / sectors));
    float width = max(0.0006, 0.75 * pixelAngle);
    if (abs(across) > 6.0 * width) return vec3(0.0);
    // Старт равномерно по площади неба в 10°..110° от радианта; у радианта метеор летит почти на нас — след короче
    float start = acos(mix(0.985, -0.35, hash21(key + 1.17)));
    float path = mix(0.08, 0.32, hash21(key + 1.41)) * (0.25 + 0.75 * sin(start));
    float s = age / life;
    float head = start + path * (1.0 - (1.0 - s) * (1.0 - s) * 0.3);   // к концу чуть тормозит
    float tail = max(start, head - 0.55 * path);
    float soft = max(pixelAngle, 0.001);
    float line = exp(-across * across / (width * width)) * (0.0006 / width);
    float streak = smoothstep(tail - soft, tail + soft, theta) * (1.0 - smoothstep(head - soft, head + soft, theta));
    float toHead = pow(clamp((theta - tail) / max(head - tail, soft), 0.0, 1.0), 1.5);
    float dh = theta - head;
    float glowR = 2.2 * width;
    float glow = exp(-(dh * dh + across * across) / (glowR * glowR)) * (0.0006 / width);
    float mag = (0.55 + 2.6 * pow(hash21(key + 1.93), 6.0)) * smoothstep(0.0, 0.12, s) * (1.0 - smoothstep(0.55, 1.0, s));
    vec3 headCol = vec3(0.86, 0.95, 1.0);
    return (mix(trail, headCol, toHead) * (line * streak * (0.12 + 0.88 * toHead)) + headCol * (0.6 * glow)) * (mag * 2.2);
}

vec3 starfallMeteors(vec3 d, vec3 accent) {
    float up = smoothstep(0.0, 0.12, d.y);                   // сгорают в атмосфере — только над горизонтом
    float theta = acos(clamp(dot(d, SF_RAD), -1.0, 1.0));
    if (up <= 0.0 || theta > 2.3) return vec3(0.0);          // дальше start + path никто не долетает
    float phi = atan(dot(d, SF_RB), dot(d, SF_RA));
    vec3 trail = mix(vec3(0.62, 1.0, 0.78), hsv2rgb(vec3(accent.x, 0.55, 1.0)), 0.5);   // зелёное свечение следа
    vec3 m = showerLayer(theta, phi, trail, 72.0, 2.5, 0.0) + showerLayer(theta, phi, trail, 40.0, 5.0, 17.0);
    return m * up;
}

// 2D fbm с LOD (как fbmLod) для карты туманности. Октавы повёрнуты — без осевой решётки value-шума.
float fbm2Lod(vec2 p, float footprint, int octaves) {
    const mat2 ROT = mat2(0.8, -0.6, 0.6, 0.8);
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 6; i++) {
        if (i >= octaves) break;
        float detail = 1.0 - smoothstep(0.3, 0.7, footprint);
        if (detail <= 0.0) {
            v += a * (1.0 - exp2(float(i - octaves)));       // среднее 0.5 × остаток амплитуд
            break;
        }
        v += a * mix(0.5, valueNoise2(p), detail);
        p = ROT * p * 2.03;
        footprint *= 2.03;
        a *= 0.5;
    }
    return v;
}

// Гребнёвый мультифрактал с LOD: сумма гребней 1 - |2n - 1| — тонкие ветвящиеся волокна газа. Октава взвешена
// предыдущей, поэтому мелкие волокна растут на крупных, а не рассыпаны зерном; мельче пикселя — их среднее.
float ridged2Lod(vec2 p, float footprint, int octaves) {
    const mat2 ROT = mat2(0.8, -0.6, 0.6, 0.8);
    float v = 0.0;
    float a = 0.6;
    float prev = 1.0;
    for (int i = 0; i < 6; i++) {
        if (i >= octaves) break;
        float detail = 1.0 - smoothstep(0.3, 0.7, footprint);
        float n = 1.0 - abs(2.0 * valueNoise2(p) - 1.0);
        n *= n;
        v += a * prev * mix(0.4, n, detail);
        if (detail <= 0.0) break;
        prev = clamp(n * 1.8, 0.0, 1.0);
        p = ROT * p * 2.07;
        footprint *= 2.07;
        a *= 0.5;
    }
    return v;
}

// Эмиссионная туманность вокруг SF_NEB: светящееся ядро со скоплением молодых звёзд, от него рваные крылья газа,
// прошитые тонкими волокнами, поперёк — тёмные пылевые прожилки с подсвеченными кромками. Считается в картинной
// плоскости у центра (≈ радианы). Газ — соседние оттенки цвета настройки, у ядра светлее. trans — пропускание пыли.
vec3 emissionNebula(vec3 d, vec3 accent, inout vec3 trans) {
    trans = vec3(1.0);
    float w = dot(d, SF_NEB);
    if (w < 0.5) return vec3(0.0);                           // дальше 60° газа нет
    vec2 uv = vec2(dot(d, SF_NE1), dot(d, SF_NE2)) / w;
    // Очертание: вытянутое наклонное облако. Край рвёт warp (сдвиг до ~0.15), поэтому отсев до шума — с запасом
    vec2 e = mat2(0.87, 0.5, -0.5, 0.87) * uv;
    if (e.x * e.x + e.y * e.y * 2.25 > 1.1) return vec3(0.0);
    float fp = pixelAngle / (w * w) * 3.0;                   // шаг пикселя в единицах p
    vec2 p = uv * 3.0;
    vec2 warp = vec2(fbm2Lod(p + vec2(1.7, 9.2), fp, 3), fbm2Lod(p + vec2(8.3, 2.8), fp, 3)) - 0.5;
    e += mat2(0.87, 0.5, -0.5, 0.87) * (warp * 0.3);
    e.y *= 1.5;
    float shape = exp(-dot(e, e) / 0.16);
    if (shape < 0.02) return vec3(0.0);
    int octaves = clamp(Quality, 1, 4) + 2;
    vec2 q = p + warp * 1.6;
    float fq = fp * 1.8;                                     // warp растягивает домен — шаг в q крупнее
    float gas = fbm2Lod(q * 1.5 + vec2(2.9, 5.1), fq * 1.5, octaves);
    float wisps = ridged2Lod(q * 3.2 + vec2(3.3, 6.1), fq * 3.2, octaves);
    float dustN = fbm2Lod(q * 1.7 + vec2(7.1, 0.4), fq * 1.7, 3) + (wisps - 0.3) * 0.12;   // рваные края пыли
    float dust = smoothstep(0.5, 0.63, dustN) * smoothstep(0.03, 0.35, shape);
    // Кромка пыли, подсвеченная скоплением: тонкая полоса у границы облака пыли
    float rim = smoothstep(0.43, 0.5, dustN) * (1.0 - smoothstep(0.5, 0.56, dustN)) * shape;
    vec2 cc = uv - vec2(0.04, -0.03);
    float r2 = dot(cc, cc);
    float core = exp(-r2 / 0.004);
    float heat = exp(-r2 / 0.05);                            // у горячих звёзд газ светлее и холоднее тоном
    float glow = shape * (0.1 + 1.4 * gas * gas) * (0.55 + 0.9 * wisps);
    float fil = shape * wisps * wisps * (0.3 + gas);
    float sat = clamp(accent.y * 1.4, 0.65, 0.92);
    vec3 outer = hsv2rgb(vec3(fract(accent.x + 0.07 * (2.0 * smoothstep(-0.2, 0.2, warp.x) - 1.0)), sat, 1.0));
    vec3 inner = hsv2rgb(vec3(fract(accent.x - 0.06), sat * 0.7, 1.0));
    vec3 gasCol = mix(outer, inner, heat);
    vec3 col = gasCol * (glow * 0.4 + fil * 0.9) + vec3(1.0, 0.97, 0.94) * (core * 0.15);
    trans = exp(-vec3(1.0, 1.3, 1.7) * (dust * 3.0));
    col = col * mix(vec3(1.0), trans, 0.9) + mix(gasCol, vec3(1.0), 0.4) * (rim * (0.1 + 0.6 * heat));
    col = vec3(1.0) - exp(-col * 1.8);                       // мягкое плечо: у ядра цвет не выгорает в плоский белый
    // Скопление в ядре: несколько горячих звёзд с лучиками поверх пыли
    if (r2 < 0.02) {
        float sigma = max(0.0006, 0.6 * pixelAngle);
        float rayW = max(0.0004, 0.6 * pixelAngle);
        float cluster = 0.0;
        for (int i = 0; i < 8; i++) {
            float fi = float(i);
            vec2 dv = cc - (vec2(hash21(vec2(fi, 3.7)), hash21(vec2(fi, 8.1))) - 0.5) * 0.16;
            vec2 a = abs(dv);
            vec2 fall = pow(max(1.0 - a / 0.016, 0.0), vec2(4.0));
            float rays = (exp(-a.y * a.y / (rayW * rayW)) * fall.x + exp(-a.x * a.x / (rayW * rayW)) * fall.y) * (0.0004 / rayW);
            float star = (0.0006 * 0.0006) / (sigma * sigma) * exp(-dot(dv, dv) / (sigma * sigma));
            cluster += (0.6 + 2.0 * hash21(vec2(fi, 5.3))) * (star + 0.25 * rays + 0.05 * exp(-dot(dv, dv) / 0.00003));
        }
        col += vec3(0.8, 0.9, 1.0) * cluster;
    }
    return col * (0.35 + 0.65 * accent.z);
}

vec3 starfallSky(vec3 d, vec3 accent) {
    vec3 sc = starCoords(d);
    // Облака звёзд: пыль гуще в одних местах неба, реже в других. Гасится яркостью, а не шансом: переменный порог
    // шанса резал бы звёзды пополам
    float dense = smoothstep(0.25, 0.75, valueNoise3(d * 2.6 + vec3(4.4, 8.1, 2.9)));
    float dustLod = 1.0 - smoothstep(0.4, 0.7, pixelAngle * 300.0);
    vec3 stars = sparkleStars(sc, 26.0, 0.10, 0.45) + sparkleStars(sc, 70.0, 0.18, 0.15)
               + dustStars(sc, 140.0, 0.30, 0.09) * (0.5 + 0.8 * dense)
               + dustStars(sc, 300.0, 0.45, 0.06) * (dense * dustLod);
    vec3 trans = vec3(0.0);
    vec3 nebula = emissionNebula(d, accent, trans);
    float extinction = mix(0.3, 1.0, smoothstep(-0.02, 0.3, d.y));
    return nightBase(d, accent) + (stars * trans + nebula) * extinction + starfallMeteors(d, accent);
}

// --- Звёздное поле (Variant 2): лунная ночь — глубокое синее небо, дымка туманности над куполом, звёзды, луна ---
const vec3 MN_DIR = vec3(-0.8164, 0.5592, -0.144);  // луна: ~34° над горизонтом
const vec3 MN_E1 = vec3(0.1736, 0.0, -0.9848);      // картинная плоскость диска: вправо
const vec3 MN_E2 = vec3(0.5507, 0.829, 0.0971);     // и вверх
const float MN_R = 0.036;                           // угловой радиус диска, рад: крупнее настоящей, как луна в игре

// Кинематографичный тон (кривая ACES): глубже тени, мягкое плечо у ярких — луна и ореол не выгорают в плоский белый.
vec3 filmicTone(vec3 col) {
    vec3 x = col * col * 2.6;                                // из гаммы в линейное ≈ квадрат, плюс экспозиция
    x = (x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14);
    return sqrt(clamp(x, 0.0, 1.0));
}

// Небо светлее к горизонту (подсветка атмосферы), зенит — тёмно-синий; дымка туманности лежит на куполе как
// высокий слой: стереографическая проекция неба на плоскость сжимает узор к горизонту, и он читается в перспективе.
// Луна с морями и ореолом рассеянного света, звёзды гаснут в её подсветке и у горизонта. Цвет — от цвета настройки.
vec3 moonlitSky(vec3 d, vec3 accent) {
    float hue = accent.x;
    float sat = clamp(accent.y * 1.5, 0.45, 0.85);
    float up = max(d.y, 0.0);
    vec3 zenith = hsv2rgb(vec3(fract(hue + 0.03), sat, 0.075));
    vec3 horizon = hsv2rgb(vec3(fract(hue + 0.01), sat * 0.75, 0.17));
    vec3 col = mix(zenith, horizon, pow(1.0 - up, 4.0));
    col *= mix(1.0, 0.5, smoothstep(0.0, -0.3, d.y));        // ниже горизонта — земная тень

    // Дымка: крупные облака × средние × мелкие клочья; гуще на средней высоте, тает к зениту и у горизонта
    vec2 plane = d.xz / (abs(d.y) + 1.0);
    float fp = pixelAngle / (abs(d.y) + 1.0);                // шаг пикселя в единицах плоскости
    float base = fbm2Lod(plane * 2.4 + vec2(3.1, 7.4), fp * 2.4, 3);
    float mid = fbm2Lod(plane * 6.0 + vec2(8.2, 1.3), fp * 6.0, 3);
    float fine = fbm2Lod(plane * 26.0 + vec2(1.9, 5.6), fp * 26.0, 3);
    float haze = (0.25 + smoothstep(0.25, 0.72, base)) * smoothstep(0.15, 0.75, mid) * (0.2 + 1.3 * fine);
    haze *= smoothstep(-0.02, 0.2, d.y) * (1.0 - 0.55 * up);
    vec3 hazeThin = hsv2rgb(vec3(fract(hue - 0.02), sat * 0.85, 1.0));
    vec3 hazeDense = hsv2rgb(vec3(fract(hue - 0.06), sat * 0.8, 1.0));      // плотные клочья — в соседний оттенок
    col += mix(hazeThin, hazeDense, smoothstep(0.45, 0.8, mid)) * (haze * 0.1 + haze * haze * 0.07);

    // Луна: ореол рассеяния — широкая подсветка неба и дымки, ближе к диску ярче
    float ang = acos(clamp(dot(d, MN_DIR), -1.0, 1.0));
    float glow = 0.05 * exp(-ang * 2.5) + 0.1 * exp(-ang * 12.0) + 0.25 * exp(-ang * 55.0);
    col += mix(horizon * 3.0, vec3(0.85, 0.9, 1.0), 0.5) * glow;

    // Звёзды: густая пыль мелких и редкие яркие; тонут в подсветке луны и плотной дымки. К горизонту гаснут как
    // сквозь толщу воздуха: блеск падает с воздушной массой (≈ 1/высоты) плавно от самого зенита, тусклые пропадают
    // раньше ярких — густота редеет постепенно, без полосы, где звёзды обрываются все разом.
    vec3 sc = starCoords(d);
    float dustLod = 1.0 - smoothstep(0.4, 0.7, pixelAngle * 300.0);
    vec3 stars = sparkleStars(sc, 26.0, 0.10, 0.45) + sparkleStars(sc, 70.0, 0.2, 0.15)
               + dustStars(sc, 140.0, 0.45, 0.13) + dustStars(sc, 300.0, 0.5, 0.08) * dustLod;
    float airExtinction = exp(-0.2 * (1.0 / max(d.y, 0.02) - 1.0));
    float starVis = airExtinction * (1.0 - min(glow * 6.0, 0.85)) * (1.0 - 0.5 * min(haze * 2.0, 1.0));
    col += stars * starVis + meteor(d, 5.0, 3.0) + meteor(d, 8.0, 11.0);

    if (ang < MN_R * 1.6) {
        vec2 mp = vec2(dot(d, MN_E1), dot(d, MN_E2)) / MN_R;  // диск в радиусах: -1..1
        float cover = clamp((MN_R - ang) / pixelAngle + 0.5, 0.0, 1.0);
        float mu = sqrt(max(1.0 - dot(mp, mp), 0.0));
        float seas = fbm2Lod(mp * 1.9 + vec2(4.7, 1.3), pixelAngle / MN_R * 1.9, 5);
        float craters = fbm2Lod(mp * 7.0 + vec2(2.2, 9.1), pixelAngle / MN_R * 7.0, 3);
        float albedo = mix(0.42, 1.0, smoothstep(0.34, 0.62, seas)) * (0.8 + 0.4 * craters);
        vec3 moon = vec3(1.0, 0.97, 0.9) * albedo * (0.55 + 0.45 * pow(mu, 0.5)) * 0.9;
        col = mix(col, moon, cover);
    }
    return filmicTone(col);
}

// Цвет неба по направлению d. Всё считается ТОЛЬКО от 3D-направления -> паттерн непрерывен по всей сфере,
// без швов на гранях/меридиане. accent — HSV цвета настройки, ign — сдвиг марша этой выборки 0..1.
vec3 skyRadiance(vec3 d, vec3 accent, float ign) {
    vec3 col = vec3(0.0);
    if (Variant == 1) {
        // Nebula — космические облака: второй fbm-слой искривляется первым (domain warp,
        // живые завихрения), светящиеся прожилки по гребням, тёмные пылевые полосы поверх.
        float n1 = fbm(d * 2.4 + drift3(0.035));
        float n2 = fbm(d * 4.8 + vec3(n1 * 0.9) - drift3(0.02));
        float dens = smoothstep(0.32, 0.92, n1 * 0.62 + n2 * 0.52);
        float ridge = 1.0 - abs(2.0 * n2 - 1.0);
        float hue = accent.x + 0.09 * (n1 - 0.5);
        float sat = clamp(accent.y * (0.8 + 0.4 * n2), 0.0, 1.0);
        vec3 cloud = hsv2rgb(vec3(fract(hue), sat, 1.0));
        vec3 core = hsv2rgb(vec3(fract(hue + 0.07), sat * 0.5, 1.0));
        float lum = 0.35 + 0.65 * accent.z;
        col = (cloud * dens * 0.8 + core * pow(ridge, 3.0) * dens * 0.7) * lum;
        float dust = smoothstep(0.5, 0.85, fbm(d * 6.5 + vec3(2.7)));
        col *= 1.0 - 0.5 * dust * dens;
        col += cloud * 0.018 + vec3(0.004, 0.005, 0.012);
        col += starField(d, 95.0, 4.0);
        col += starField(d, 42.0, 1.6) * 0.6;
    } else if (Variant == 3) {
        // Чёрная дыра (Gargantua, Interstellar). Kick-drift-интегратор с адаптивным шагом:
        // мелкий у фотонной сферы -> резкое фотонное кольцо и чистый край тени. Пересечения
        // плоскости диска дают несколько линзированных образов (дуги над/под тенью), причём
        // передний край диска ЗАСЛОНЯЕТ их через transmittance — от этого читается глубина.
        // Объём добавляют «атмосфера» диска (свечение при пролёте сквозь толщину) и тёплый
        // ореол у фотонной сферы, подсвечивающий край тени.
        vec3 bhDir = normalize(vec3(0.0, 0.06, 1.0));
        float ang = acos(clamp(dot(d, bhDir), -1.0, 1.0));

        if (ang > 1.24) {
            // Дальше прицельный параметр > 11.8: эмиссии там ровно 0, а звёзды в поясе
            // блендинга уже сведены к прямому направлению — марш был бы работой вхолостую
            col = bhBackground(d, accent);
        } else {
            // ПЛОСКАЯ ГЕОДЕЗИЧЕСКАЯ. Траектория фотона лежит в плоскости (e1,e2) через центр,
            // поэтому вместо 3D-позиции мелкими шагами интегрируем скалярное уравнение
            // Шварцшильда u'' = 1.5u^2 - u (u = 1/r) по углу φ: шаг по φ вдали от дыры покрывает
            // огромный кусок пути -> в разы меньше шагов, а состояние — скаляры вместо vec3
            // (ниже register pressure -> выше occupancy). Пересечения плоскости диска — заранее
            // известные углы (нули синусоиды высоты) φC + kπ, без проверок знака на каждом шаге.
            vec3 dn = normalize(vec3(0.0, 1.0, -0.18));    // нормаль диска: вид почти ребром, чуть сверху
            vec3 du = normalize(cross(dn, bhDir));         // ортонормированный базис плоскости
            vec3 dv = cross(dn, du);
            vec3 e1 = -bhDir;                              // радиальный орт камеры, r0 = 12.5
            float ct = dot(d, e1);
            float st = sqrt(max(1.0 - ct * ct, 1.0e-6));
            float b = 12.5 * st;                           // прицельный параметр луча
            vec3 e2 = (d - e1 * ct) * (1.0 / st);
            float c1 = dot(e1, dn), c2 = dot(e2, dn);      // высота над диском: (c1·cosφ + c2·sinφ)·r
            float d1u = dot(e1, du), d2u = dot(e2, du);    // координаты в базисе диска — тоже скалярно
            float d1v = dot(e1, dv), d2v = dot(e2, dv);
            float spin = Time * (32.0 * TAU / WRAP);       // вращение атмосферы: целое число оборотов на WRAP
            float spc = cos(spin);
            float sps = sin(spin);

            float u = 0.08;                                // = 1/12.5
            float v = -ct / max(b, 0.05);                  // du/dφ из геометрии старта
            float phi = 0.0, cs = 1.0, sn = 0.0;           // (cs,sn) = (cosφ,sinφ), ведём инкрементально
            float uMax = u;
            vec3 acc = vec3(0.0);
            vec3 trans = vec3(1.0);                        // пропускание по каналам: диск и пыль гасят то, что позади
            bool captured = b < 0.05;                      // луч почти в центр — гарантированный захват
            // Лучи с b ниже критического (2.598) обречены упасть под горизонт: их путевое
            // свечение (атмосфера/ореол) гасим, иначе оно рисует мутную вуаль ПОВЕРХ тени.
            // Плавно по b: у самого края тени ободок и дымка кольца сохраняются без шва.
            float doom = smoothstep(2.45, 2.75, b);

            // Первый угол пересечения плоскости диска (нуль высоты), дальше каждые π
            float cAmp = sqrt(c1 * c1 + c2 * c2);
            float nextC = 1.0e9;                           // траектория почти в плоскости диска — без пересечений
            if (cAmp > 0.02) {
                float phiC = atan(c2, c1) + 1.5707963;
                nextC = phiC - floor(phiC / 3.14159265) * 3.14159265;
                if (nextC < 1.0e-3) nextC += 3.14159265;
            }
            float prevU = u, prevV = v, prevPhi = 0.0;

            int marchSteps = 64 + clamp(Quality, 1, 4) * 48;
            for (int i = 0; i < 256; i++) {
                if (i >= marchSteps) break;
                if (captured) break;
                if (u > 1.0) { captured = true; break; }   // под горизонтом -> тень
                if (u < 0.0831 && v < 0.0) break;          // r > 12 и движемся наружу: всё интересное позади
                uMax = max(uMax, u);
                float r = 1.0 / u;
                float hgt = (c1 * cs + c2 * sn) * r;
                float rcy2 = r * r - hgt * hgt;
                float h = clamp(0.30 / (1.0 + 7.0 * u), 0.045, 0.20);
                if (rcy2 < 145.0 && abs(hgt) < 3.0) h = min(h, 0.125); // плотнее сквозь слой диска
                h *= 0.85 + 0.30 * ign;                    // джиттер шага против бандинга

                // Объёмная атмосфера: дешёвые огибающие ДО шума, 3D-дым и пыль только где видно
                if (doom > 0.003 && rcy2 > 1.44 && rcy2 < 152.5) { // (1.2)^2 .. (BH_RO*1.30)^2
                    float rcy = sqrt(rcy2);
                    float tr = (rcy - BH_RI) / (BH_RO - BH_RI);
                    float radf = exp(-max(tr, 0.0) * 2.0) * smoothstep(1.30, 0.80, tr) * smoothstep(1.2, 2.2, rcy);
                    float thick = 0.30 + 1.50 * max(tr, 0.0);
                    float coreEnv = exp(-hgt * hgt / (thick * thick));
                    float haloEnv = exp(-hgt * hgt / (thick * thick * 9.0));
                    if (radf * (coreEnv + 0.25 * haloEnv) > 0.01) {
                        float ds = h * r * sqrt(1.0 + v * v * r * r);  // длина дуги этого шага
                        float pu = (cs * d1u + sn * d2u) * (r / rcy);  // единичные координаты в плоскости
                        float pv = (cs * d1v + sn * d2v) * (r / rcy);
                        vec2 ur = vec2(pu * spc - pv * sps, pu * sps + pv * spc);
                        vec3 q = vec3(ur * 2.6, log(rcy) * 5.0 + hgt * 2.2);
                        float sm = valueNoise3(q) * 0.65;
                        // Вторая октава — только в плотном ядре; +0.175 = её среднее (стык LOD без сдвига)
                        if (radf * coreEnv > 0.02) sm += valueNoise3(q * 2.6 + vec3(9.7, 3.1, 17.0)) * 0.35;
                        else sm += 0.175;
                        float hh = hgt + (sm - 0.5) * 0.5;        // дым волнит поверхность атмосферы
                        float core = exp(-hh * hh / (thick * thick));
                        float halo = exp(-hh * hh / (thick * thick * 9.0)) * 0.25;
                        float puff = 0.30 + 1.30 * sm * sm;       // дымные клубы, нелинейно для контраста
                        vec3 gc = mix(vec3(1.0, 0.80, 0.52), vec3(0.95, 0.44, 0.16), smoothstep(0.0, 0.55, tr));
                        gc = mix(gc, vec3(0.55, 0.22, 0.11), smoothstep(0.55, 1.2, tr));
                        acc += (gc * core + mix(gc, vec3(0.55, 0.34, 0.26), 0.5) * halo)
                                * (puff * radf * 0.34 * ds * doom) * trans;
                        // Пыль: плотнейшие клубы гасят свет позади, синий канал сильнее
                        float dust = smoothstep(0.60, 0.92, sm) * core * radf * doom;
                        trans *= exp(-vec3(0.9, 1.4, 2.0) * (dust * 1.6 * ds));
                    }
                }
                if (r < 2.4 && doom > 0.003)               // ореол у фотонной сферы — подсветка края тени
                    acc += vec3(1.0, 0.90, 0.74) * (exp(-(r - 1.5) * (r - 1.5) * 5.0) * 0.045 * doom
                            * (h * r * sqrt(1.0 + v * v * r * r))) * trans;

                // RK2 (midpoint) для u'' = u·(1.5u - 1)
                prevU = u; prevV = v; prevPhi = phi;
                float um = u + v * (h * 0.5);
                float vm = v + u * (1.5 * u - 1.0) * (h * 0.5);
                u += vm * h;
                v += um * (1.5 * um - 1.0) * h;
                phi += h;
                // Инкрементальный поворот (cs,sn) на h: полиномы вместо cos/sin + ренормализация
                float ch = 1.0 - 0.5 * h * h;
                float sh = h * (1.0 - h * h * (1.0 / 6.0));
                float ncs = cs * ch - sn * sh;
                sn = sn * ch + cs * sh;
                cs = ncs;
                float rn = 1.5 - 0.5 * (cs * cs + sn * sn);
                cs *= rn;
                sn *= rn;

                if (phi >= nextC) {                        // на этом шаге прошли плоскость диска
                    float tc = (nextC - prevPhi) / max(phi - prevPhi, 1.0e-5);
                    float uc = mix(prevU, u, tc);
                    float rc = 1.0 / uc;
                    if (rc > BH_RI && rc < BH_RO * 1.05) {
                        float cosC = cos(nextC), sinC = sin(nextC);
                        float ax = rc * (cosC * d1u + sinC * d2u);
                        float ay = rc * (cosC * d1v + sinC * d2v);
                        vec3 er = e1 * cosC + e2 * sinC;   // радиальный/трансверсальный орты в точке
                        vec3 ep = e2 * cosC - e1 * sinC;
                        vec3 cp = er * rc;
                        vec3 rd = normalize(ep * uc - er * mix(prevV, v, tc)); // направление фотона
                        float tau = 0.0;
                        vec3 e = bhDisk(ax, ay, normalize(cross(dn, cp)), rd, tau);
                        acc += e * trans;
                        trans *= exp(-tau);
                    }
                    nextC += 3.14159265;
                }
                if (max(max(trans.x, trans.y), trans.z) < 0.02) break; // впереди плотно — дальше не видно
            }

            // Фон по линзированному направлению (искривлённые звёзды у тени); к границе конуса
            // стягиваем к прямому направлению — бесшовный стык с внешней веткой
            vec3 fer = e1 * cs + e2 * sn;
            vec3 fep = e2 * cs - e1 * sn;
            vec3 bd = normalize(mix(normalize(fep * u - fer * v), d, smoothstep(0.95, 1.24, ang)));
            col = acc;
            if (!captured) {
                col += bhBackground(bd, accent) * trans;
                // Фотонное кольцо: лучи, скользнувшие у фотонной сферы
                float minR = 1.0 / uMax;
                float ring = exp(-(minR - 1.5) * (minR - 1.5) * 60.0);
                col += vec3(1.0, 0.97, 0.90) * (ring * 1.1) * trans;
            }
        }
        col = vec3(1.0) - exp(-col * 2.0);                 // filmic: горячее выгорает в белое, стык веток без шва
    } else if (Variant == 4) {
        // Aurora — северное сияние. Занавеси = вертикальная экструзия одного 2D-поля:
        // луч пересекает слои высот, каждый семплирует то же поле на своей высоте ->
        // объёмные лучи-складки со сходом в зените, без швов и резких границ.
        float upness = clamp(d.y * 0.5 + 0.5, 0.0, 1.0);
        col = mix(vec3(0.016, 0.022, 0.046), vec3(0.001, 0.002, 0.008), upness);
        col += starField(d, 110.0, 4.0) * 0.9;
        col += starField(d, 50.0, 1.6) * 0.55;

        float horizon = smoothstep(0.015, 0.15, d.y);   // плавное гашение к горизонту
        float y = max(d.y, 0.05);
        vec2 ground = d.xz / y;                          // проекция направления на плоскость высоты 1
        float groundStep = pixelAngle / (y * y);         // шаг пикселя на этой плоскости: к горизонту растёт как 1/y²
        float distFade = exp(-0.045 * dot(ground, ground)); // дальние занавеси растворяются в дымке
        // Пятна покрытия медленно плывут по небу; floor — слабое сияние есть всегда
        float patches = smoothstep(0.24, 0.58, valueNoise2(ground * 0.16 + drift2(0.012)));
        float vis = horizon * distFade * (0.35 + 0.65 * patches);
        if (vis > 0.004) {
            vec3 aur = vec3(0.0);
            float sat = min(accent.y * 1.35, 1.0);
            // Сглаживание: у горизонта волокна мягче (координаты слоёв там сжимаются -> без ряби),
            // а джиттер высоты слоя по IGN рассыпает ступени марша в гладкий градиент
            float soft = mix(5.0, 12.0, distFade);
            int layerCount = clamp(Quality, 1, 4) * 8;
            float layerReach = length(ground) * 2.2 / float(layerCount); // сдвиг точки поля между слоями / fi
            for (int i = 0; i < 32; i++) {
                if (i >= layerCount) break;
                float fi = (float(i) + 0.5 + (ign - 0.5) * 0.9) / float(layerCount);
                float h = 1.0 + fi * fi * 2.2;           // высоты слоёв, гуще у нижней кромки
                // лёгкое покачивание, растущее с высотой; частоты кратны 2π/WRAP
                vec2 sway = vec2(sin(Time * 0.13195), cos(Time * 0.11310)) * (0.35 * fi);
                // Шаг выборки в единицах поля — по экрану (соседний пиксель) и вдоль луча: соседний слой сдвигает
                // точку поля на ground·Δh/2, Δh = 4.4·fi/layerCount. Детали мельче шага слои не разрешают, они давали
                // только зерно; у нижней кромки слои часты, и мелкие волокна там остаются.
                float footprint = max(groundStep * (h * 0.5), layerReach * fi);
                float slope = 0.0;
                float n = curtainNoise(ground * (h * 0.5) + sway, footprint, slope);
                // Волокно на изолинии поля + широкое мягкое гало вокруг. Волокно не уже шага выборки
                // (наклон поля × шаг), расширенное тускнеет — яркость занавеси сохраняется.
                float edge = min(soft, 1.0 / max(slope * footprint, 1.0e-4));
                float fil = exp(-edge * abs(n - 0.5)) * (edge / soft) + 0.3 * exp(-2.5 * abs(n - 0.5));
                // низ занавеси — цвет настройки, кверху оттенок уплывает (как кислород/азот в реальном сиянии)
                vec3 lc = hsv2rgb(vec3(fract(accent.x + fi * 0.16), sat, 1.0));
                aur += lc * fil * (1.0 - 0.65 * fi);
            }
            col += aur * (accent.z * 0.26 * 16.0 / float(layerCount)) * vis;
        }
    } else if (Variant == 5) {
        col = planetSky(d, accent);
    } else if (Variant == 6) {
        col = nightSky(d, accent);
    } else if (Variant == 7) {
        col = starfallSky(d, accent);
    } else {
        col = moonlitSky(d, accent);
    }
    return max(col, vec3(0.0));
}

void main() {
    // Настраиваемый цвет неба (skyColor -> ColorModulator) задаёт оттенок/насыщенность/яркость паттернов
    vec3 accent = rgb2hsv(vec3(SkyR, SkyG, SkyB));
    // IGN (interleaved gradient noise): распределён равномернее белого шума ->
    // гладкий дизеринг и джиттер марша без видимых скоплений зерна
    vec2 screenCoord = PixelScale < 0.75 ? floor(gl_FragCoord.xy) * 2.0 + vec2(JitterX, JitterY) + 0.5 : gl_FragCoord.xy;
    float ign = fract(52.9829189 * fract(dot(screenCoord, vec2(0.06711056, 0.00583715))));
    // Производные — до всех ветвлений. skyDir линеен по пикселю, поэтому skyDir + dFdx·x — точный луч подпикселя.
    vec3 d = normalize(skyDir);
    vec3 dirDx = dFdx(skyDir) * PixelScale;
    vec3 dirDy = dFdy(skyDir) * PixelScale;
    pixelAngle = max(length(dFdx(d)), length(dFdy(d))) * PixelScale;

    // x4 — адаптивный суперсэмплинг. Базовая выборка в центре пикселя; где соседние пиксели расходятся больше чем
    // на ~5/255 (кромки, фотонное кольцо, дуги диска, звёзды), добавляются выборки повёрнутой сеткой (RGSS):
    // ±p, затем ±p, повёрнутые на 90°. Чёрной дыре хватает одной: её марш самый дорогой, а одна выборка уже
    // снимает лесенку края тени и зерно диска. Ночному небу тоже одной: звёзды там гауссовы не уже пикселя,
    // а звёздная пыль в полосе поднимает контраст почти везде — четыре выборки удвоили бы цену. Сиянию, Звёздному полю
    // и Звездопаду суперсэмплинг не нужен — они сглажены аналитически (LOD шума, ширина волокна, звёзды и метеоры не уже
    // пикселя, край луны по доле пикселя).
    // Сдвиги марша выборок разнесены по [0, 1) — их ступени усредняются в гладкий градиент.
    int extra = Quality < 4 || Variant == 2 || Variant == 4 || Variant == 7 ? 0 : (Variant == 3 || Variant == 6 ? 1 : 4);
    vec3 col = vec3(0.0);
    vec2 offset = vec2(0.0);
    vec2 rgss = vec2(0.125, 0.375);
    if (extra == 0) col = skyRadiance(d, accent, ign);
    else for (int i = 0; i < 5; i++) {
        col += skyRadiance(normalize(skyDir + dirDx * offset.x + dirDy * offset.y), accent,
                fract(ign + float(i) / float(extra + 1)));
        if (i == 0) {
            // Первая итерация идёт у всех пикселей квада — производные здесь определены.
            // Контраст по тому, что попадёт в кадр: пересвет выше 1 не рябит.
            vec3 contrast = fwidth(min(col * max(Brightness, 0.0), vec3(1.0)));
            if (max(max(contrast.r, contrast.g), contrast.b) <= 0.02) extra = 0;
        }
        if (i >= extra) break;
        offset = i % 2 == 0 ? rgss : -rgss;
        if (i % 2 == 1) rgss = vec2(rgss.y, -rgss.x);    // следующая пара RGSS — поворот на 90°
    }
    col /= float(extra + 1);
    // Дизеринг ~1 LSB против бандинга на плавных тёмных градиентах.
    // Opacity < 1 просвечивает текстурный скайбокс, нарисованный в слоте неба.
    fragColor = vec4(col * max(Brightness, 0.0) + (ign - 0.5) * (1.0 / 255.0), Opacity);
}
