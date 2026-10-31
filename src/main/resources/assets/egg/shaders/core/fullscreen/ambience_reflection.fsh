#version 330

uniform sampler2D Sampler0; // цвет сцены ДО прозрачного слоя — источник отражения (без самой воды)
uniform sampler2D Sampler1; // depth мира вместе с прозрачным слоем
uniform sampler2D Sampler2; // depth одной непрозрачной геометрии (до прозрачного слоя)

layout(std140) uniform EggAmbiencePost {
    float Proj00;
    float Proj11;
    float Near;
    float Far;
    float BlurStart;
    float BlurEnd;
    float BloomThreshold;
    float BloomIntensity;
    float UpViewX;
    float UpViewY;
    float UpViewZ;
    float ReflStrength;
    float ReflDistance;
    float SkyZenithR;
    float SkyZenithG;
    float SkyZenithB;
    float SkyHorizonR;
    float SkyHorizonG;
    float SkyHorizonB;
    float SunDirX;
    float SunDirY;
    float SunDirZ;
    float AxisXx;
    float AxisXy;
    float AxisXz;
    float AxisZx;
    float AxisZy;
    float AxisZz;
    float CamX;
    float CamZ;
    float WaveTime;
    float FogR;
    float FogG;
    float FogB;
    float FogDensity;
    float FogStart;
    float FogHeight;
    float FogFalloff;
    float FogNoise;
    float FogCamX;
    float FogCamY;
    float FogCamZ;
    float Pad0;
    float Pad1;
    int ShaderQuality;
};

in vec2 texCoord0;
flat in float blurOffset;

out vec4 fragColor;

// Волновые векторы заданы целыми в единицах 2π/16: поле ряби периодично с шагом 16 блоков,
// ровно в этот период свёрнуты CamX/CamZ — далеко от нуля рябь не рассыпается и шва нет.
const float WAVE_UNIT = 0.3926991;

float linearZ(float depth) {
    float ndcZ = depth * 2.0 - 1.0;
    return 2.0 * Near * Far / (Far + Near - ndcZ * (Far - Near));
}

// Обратное к linearZ: глубина буфера для точки на расстоянии z вдоль взгляда.
float bufferDepth(float z) {
    float ndcZ = (Far + Near - 2.0 * Near * Far / z) / (Far - Near);
    return ndcZ * 0.5 + 0.5;
}

// Позиция во view-пространстве (-Z вперёд) из UV и depth через диагональ проекции.
vec3 viewPos(vec2 uv, float depth) {
    float z = linearZ(depth);
    vec2 ndc = uv * 2.0 - 1.0;
    return vec3(ndc / vec2(Proj00, Proj11) * z, -z);
}

vec2 toUv(vec3 pos) {
    vec2 ndc = pos.xy * vec2(Proj00, Proj11) / -pos.z;
    return ndc * 0.5 + 0.5;
}

vec3 toScreen(vec3 pos) {
    return vec3(toUv(pos), bufferDepth(-pos.z));
}

// Дешёвый хэш для джиттера старта луча — прячет ступени марша в шум.
float dither(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

// Одна бегущая волна: возвращает вклад в наклон поверхности (dH/dx, dH/dz) от
// H = amp*sin(k·world + speed*t). Градиент аналитический, без конечных разностей.
vec2 waveSlope(vec2 world, vec2 wavevector, float amplitude, float speed) {
    vec2 k = wavevector * WAVE_UNIT;
    return k * (amplitude * cos(dot(world, k) + WaveTime * speed));
}

// Аналитическое небо по направлению луча: за пределами кадра экранных данных нет,
// но небо бесконечно далеко — его цвет считается для ЛЮБОГО направления. Градиент
// горизонт→зенит из реально отрендеренных цветов кадра.
vec3 skyFallback(vec3 ray, vec3 up) {
    float elev = clamp(dot(ray, up), -1.0, 1.0);
    vec3 zenith = vec3(SkyZenithR, SkyZenithG, SkyZenithB);
    vec3 horizon = vec3(SkyHorizonR, SkyHorizonG, SkyHorizonB);
    vec3 sky = mix(horizon, zenith, pow(clamp(elev, 0.0, 1.0), 0.6));
    // Ниже горизонта — приглушённый цвет горизонта (отражение дымки).
    return mix(horizon * 0.7, sky, smoothstep(-0.08, 0.04, elev));
}

// Марш отражения в экранном пространстве по схеме шейдерпаков: шаг нормируется так, чтобы
// за выбранное качеством число шагов дойти ровно до края кадра — луч покрывается равномерно, без
// подбора экспоненты. Шаг, на котором луч ушёл за поверхность, уточняется делением пополам до
// точки входа, и попадание засчитывается, только если там луч лежит на самой поверхности. Иначе
// луч прошёл ЗА объектом, висящим над землёй (крона, ник над головой), и марш идёт дальше: такое
// «попадание» залило бы разрыв под объектом растянутыми полосами его кромки.
// hitUv — точка попадания, exitUv — где луч ушёл с экрана при промахе.
bool raytrace(vec3 origin, vec3 ray, float jitter, inout vec2 hitUv, inout vec2 exitUv) {
    hitUv = texCoord0;
    exitUv = texCoord0;

    vec3 start = toScreen(origin);
    vec3 ahead = origin + ray;
    if (ahead.z > -Near) return false; // луч почти сразу уходит за камеру

    vec3 offset = normalize(toScreen(ahead) - start);
    // Параметрическая длина до границы кадра по каждой оси; ближайшая — предел марша.
    vec3 toBorder = (step(0.0, offset) - start) / offset;
    int marchSteps = clamp(ShaderQuality, 1, 4) * 12;
    int refineSteps = clamp(ShaderQuality, 1, 4) + 3;
    offset *= min(min(toBorder.x, toBorder.y), toBorder.z) / float(marchSteps);
    float stepPixels = max(length(offset.xy * vec2(textureSize(Sampler1, 0))), 1.0);

    vec3 probe = start + offset * (1.0 + jitter);
    bool wasBehind = false;
    for (int i = 0; i < 48; i++) {
        if (i >= marchSteps) break;
        if (probe.x < 0.0 || probe.x > 1.0 || probe.y < 0.0 || probe.y > 1.0 || probe.z > 1.0) break;
        exitUv = probe.xy;
        float scene = texture(Sampler1, probe.xy).r;
        bool behind = probe.z > scene;
        if (behind) {
            // На сколько блоков луч может уйти за видимую поверхность и ещё считаться попавшим в неё:
            // своя толщина плюс сдвиг луча по глубине за пару пикселей — вдали и под скользящим углом
            // соседние пиксели расходятся по глубине на блоки.
            float rayZ = linearZ(probe.z);
            float stepSpan = abs(rayZ - linearZ(probe.z - offset.z));
            float thickness = 0.35 + rayZ * 0.01 + stepSpan / stepPixels * 2.0;
            // Первый шаг за поверхностью у удаляющегося луча уточняем делением пополам до точки
            // входа. Луч, приближающийся к камере, сам подходит к поверхности — ближе всего к ней
            // текущая точка; уже шедший за поверхностью луч проверяем там же, без уточнения.
            vec3 entry = probe;
            float entryScene = scene;
            if (!wasBehind && offset.z > 0.0) {
                vec3 front = probe - offset;
                for (int j = 0; j < 7; j++) {
                    if (j >= refineSteps) break;
                    vec3 middle = (front + entry) * 0.5;
                    float middleScene = texture(Sampler1, middle.xy).r;
                    if (middle.z > middleScene) {
                        entry = middle;
                        entryScene = middleScene;
                    } else {
                        front = middle;
                    }
                }
            }
            if (linearZ(entry.z) - linearZ(entryScene) < thickness) {
                hitUv = clamp(entry.xy, 0.0, 1.0);
                return true;
            }
        }
        wasBehind = behind;
        probe += offset;
    }
    return false;
}

// Отражения мира. Вода, лёд и цветное стекло опознаются разницей глубин (прозрачный слой
// закрыл собой непрозрачную геометрию) — зеркалят в полную силу, горизонтальная вода
// получает бегущую рябь и солнечный блик. Остальные блоки отражают как мокрый блеск:
// та же трассировка, но без ряби и с приглушённым весом. Нормаль точная по оси грани.
// Отражается снимок сцены БЕЗ воды, промах уходит в небо.
// Выход premultiplied: rgb = цвет*вес, вес — в альфе.
void main() {
    fragColor = vec4(0.0);
    float depth = texture(Sampler1, texCoord0).r;
    vec3 pos = viewPos(texCoord0, depth);
    // Производные — до любых ветвлений: в дивергентном потоке они не определены.
    vec3 dx = dFdx(pos);
    vec3 dy = dFdy(pos);
    if (depth >= 0.9999) return; // небо не отражает

    // Материал: прозрачный слой перед непрозрачной геометрией — вода/лёд/стекло,
    // совпавшие глубины — обычный блок (отражает сама непрозрачная поверхность).
    float opaqueZ = linearZ(texture(Sampler2, texCoord0).r);
    float surfaceZ = linearZ(depth);
    bool water = opaqueZ - surfaceZ >= 0.05;

    // Кромка силуэта: производные через разрыв глубины укажут на чужую грань, а по краю
    // воды это была бы полоса с посторонним отражением. Дешевле пропустить такой пиксель.
    if (abs(dx.z) + abs(dy.z) > abs(pos.z) * 0.5) return;

    vec3 up = vec3(UpViewX, UpViewY, UpViewZ);
    vec3 axisX = vec3(AxisXx, AxisXy, AxisXz);
    vec3 axisZ = vec3(AxisZx, AxisZy, AxisZz);

    // Грани блоков осеориентированы, поэтому производные нужны лишь чтобы ВЫБРАТЬ ось —
    // сама нормаль точная. Шум производных на кромках в неё уже не проходит.
    vec3 rough = normalize(cross(dx, dy));
    if (dot(rough, pos) > 0.0) rough = -rough;
    float onUp = dot(rough, up);
    float onX = dot(rough, axisX);
    float onZ = dot(rough, axisZ);
    vec3 plane;
    if (abs(onUp) >= max(abs(onX), abs(onZ))) plane = up * sign(onUp);
    else if (abs(onX) >= abs(onZ)) plane = axisX * sign(onX);
    else plane = axisZ * sign(onZ);

    // Обычные блоки: потолки не блестят; вода зеркалит любой гранью (стекло — и стеной).
    if (!water && dot(plane, up) < -0.5) return;

    // Рябь: длинная зыбь плюс три мелкие волны с разных направлений, все — от МИРОВЫХ
    // координат пикселя, поэтому при движении камеры волны стоят на месте. Мелкие гасим
    // с дистанцией: на дальней воде их период мельче пикселя и они кипели бы муаром.
    vec3 normal = plane;
    if (water && dot(plane, up) > 0.5) {
        vec2 world = vec2(CamX + dot(pos, axisX), CamZ + dot(pos, axisZ));
        float fade = clamp(1.0 - surfaceZ / 48.0, 0.15, 1.0);
        vec2 slope = waveSlope(world, vec2(1.0, 1.0), 0.030, 0.9)
                   + waveSlope(world, vec2(3.0, -2.0), 0.020, 1.4) * fade
                   + waveSlope(world, vec2(-2.0, 5.0), 0.012, 1.9) * fade
                   + waveSlope(world, vec2(6.0, 3.0), 0.008, 2.6) * fade;
        normal = normalize(plane - axisX * slope.x - axisZ * slope.y);
    }

    vec3 view = normalize(pos);
    vec3 ray = reflect(view, normal);

    // Под скользящим углом рябь способна увести луч ПОД собственную поверхность — марш
    // тогда сразу «попадает» в соседнюю воду. Возвращаем луч обратно над плоскостью грани.
    float alongPlane = dot(ray, plane);
    if (alongPlane < 0.02) ray = normalize(ray + plane * (0.02 - alongPlane));

    // Старт смещён по нормали: иначе первый же сэмпл попадает в саму отражающую поверхность.
    vec2 hitUv = vec2(0.0);
    vec2 exitUv = vec2(0.0);
    bool hit = raytrace(pos + plane * 0.075, ray, dither(gl_FragCoord.xy), hitUv, exitUv);

    // Доля экранных данных в отражении; остаток добирает небо, поэтому отражение
    // непрерывно под любым углом и не обрывается на краях кадра.
    vec2 uv = hit ? hitUv : exitUv;
    float dataWeight;
    if (hit) {
        // Кромка кадра и настроенная дальность плавно передают эстафету небу.
        vec2 border = min(uv, 1.0 - uv);
        float hitDistance = length(viewPos(uv, texture(Sampler1, uv).r) - pos);
        dataWeight = smoothstep(0.0, 0.07, min(border.x, border.y))
                   * (1.0 - smoothstep(ReflDistance * 0.6, ReflDistance, hitDistance));
    } else {
        // Промах: если луч ушёл с экрана над небом, отрендеренное небо лучше аналитики.
        dataWeight = texture(Sampler1, uv).r >= 0.9999 ? 1.0 : 0.0;
    }
    vec3 color = mix(skyFallback(ray, up), texture(Sampler0, uv).rgb, dataWeight);

    // Солнечный блик поверх отражения: широкая доля вокруг направления на светило. Рябь
    // дробит его в бегущую дорожку — самый узнаваемый признак настоящей воды.
    if (water) {
        vec3 sunDir = vec3(SunDirX, SunDirY, SunDirZ);
        float sunDot = dot(ray, sunDir);
        color += vec3(1.0, 0.93, 0.78) * pow(max(sunDot, 0.0), 120.0) * 2.2
               + vec3(0.7, 0.78, 0.95) * pow(max(-sunDot, 0.0), 200.0) * 0.7;
    }

    // Шлик-френель воды: вблизи почти прозрачная, вдаль под скользящим углом — зеркало.
    // Обычные блоки — тот же закон, но приглушённый: мокрый блеск, а не зеркало.
    float cosI = clamp(dot(-view, normal), 0.0, 1.0);
    float weight = 0.04 + 0.96 * pow(1.0 - cosI, 4.0);
    if (!water) weight *= 0.45;

    fragColor = vec4(color * weight, weight);
}
