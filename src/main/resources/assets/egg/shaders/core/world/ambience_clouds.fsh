#version 330

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

layout(std140) uniform SadnesAmbiencePost {
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
    int FogMode;
    float FogSpeed;
    float ScreenOriginTop;
    float CloudCamX;
    float CloudCamZ;
    float CloudBottom;
    float CloudThickness;
    float CloudThreshold;
    float CloudExtinction;
    float CloudDistance;
    float CloudLift;
    float CloudR;
    float CloudG;
    float CloudB;
    float CloudA;
    float CloudNight;
};

out vec4 fragColor;

// Объёмные кучевые облака: луч из камеры маршится через слой [CloudBottom - CloudLift, CloudBottom + CloudThickness + CloudLift]
// (высоты относительно камеры) и обрывается на рельефе по depth кадра. Sampler1 - общая LUT: RG - карта облаков
// на всех ярусах сразу (по ней же крупные зоны погоды), BA - белый шум для 3D value-noise кромки (приём IQ).
// Sampler2 - карты трёх ярусов высоты (RGB, те же UV): каждое облако целиком лежит на своём ярусе, ярусы
// складываются объединением, поэтому облака висят на разной высоте, но ни одно не режется по столбцу.
// Свет - затухание к солнцу по нескольким выборкам, многократное рассеяние октавами и небесная подсветка.
// Размеры и упаковку LUT пишет CloudNoiseLut; стенд внешнего вида собирает .claude_tmp/tool/clouds_proto_build.py
// прямо из этого файла. Всё менять синхронно.

// Размер LUT в текселях: по нему тайлятся карты и белый шум кромки.
const float LUT_SIZE = 512.0;
// Желаемый шаг марша в блоках; число шагов ограничено качеством.
const float STEP_BLOCKS = 6.0;
// Высота ярусов (R, G, B карты ярусов) в долях CloudLift.
const vec3 LEVELS = vec3(0.0, 1.0, -1.0);
// Самая широкая часть облака в долях толщины слоя: выше - купол, ниже - скруглённое днище.
const float BASE_LEVEL = 0.28;
// Мягкость края в единицах формы.
const float EDGE_SOFTNESS = 0.08;
// Сила клубов и размер крупного клуба (блоки; степень двойки - шум тайлится без шва на свёртке камеры).
const float DETAIL_STRENGTH = 0.6;
const float DETAIL_BLOCKS = 32.0;
// Среднее |2n - 1| value-шума: клубы смещают кромку вокруг нуля, облако в среднем не толстеет и не худеет.
const float BILLOW_MEAN = 0.3;

float linearZ(float depth) {
    float ndcZ = depth * 2.0 - 1.0;
    return 2.0 * Near * Far / (Far + Near - ndcZ * (Far - Near));
}

vec3 viewToWorld(vec3 direction) {
    vec3 up = vec3(UpViewX, UpViewY, UpViewZ);
    vec3 axisX = vec3(AxisXx, AxisXy, AxisXz);
    vec3 axisZ = vec3(AxisZx, AxisZy, AxisZz);
    return normalize(vec3(dot(direction, axisX), dot(direction, up), dot(direction, axisZ)));
}

vec3 pixelViewDirection(vec2 nativeUv) {
    float ndcY = mix(nativeUv.y * 2.0 - 1.0, 1.0 - nativeUv.y * 2.0, ScreenOriginTop);
    vec2 ndc = vec2(nativeUv.x * 2.0 - 1.0, ndcY);
    return normalize(vec3(ndc / vec2(Proj00, Proj11), -1.0));
}

vec3 sunWorldDirection() {
    return viewToWorld(vec3(SunDirX, SunDirY, SunDirZ));
}

// Слой вдвое меньше кадра: из 2x2 пикселей depth берём самый дальний, чтобы облака не обрывались
// у силуэтов рельефа; лишнее поверх рельефа отсекает композит по полному depth.
float farthestDepth(ivec2 texel, ivec2 limit) {
    float depth = texelFetch(Sampler0, min(texel, limit), 0).r;
    depth = max(depth, texelFetch(Sampler0, min(texel + ivec2(1, 0), limit), 0).r);
    depth = max(depth, texelFetch(Sampler0, min(texel + ivec2(0, 1), limit), 0).r);
    return max(depth, texelFetch(Sampler0, min(texel + ivec2(1, 1), limit), 0).r);
}

float lutHeight(vec2 tileUv) {
    vec2 heightBytes = textureLod(Sampler1, tileUv, 0.0).rg;
    return dot(heightBytes, vec2(65280.0, 255.0)) / 65535.0;
}

float valueNoise3(vec3 x) {
    vec3 cell = floor(x);
    vec3 blend = fract(x);
    blend = blend * blend * (3.0 - 2.0 * blend);
    vec2 uv = cell.xy + vec2(37.0, 17.0) * cell.z + blend.xy;
    vec2 slices = textureLod(Sampler1, (uv + 0.5) / LUT_SIZE, 0.0).ba;
    return mix(slices.x, slices.y, blend.z);
}

// UV карт. Карта повёрнута на atan(3/4) целочисленной матрицей: её ячейки не выстраиваются вдоль осей мира,
// а период 8192 / 5 блоков делит свёртку камеры 65536, так что шва нет. Тексель - 3.2 блока.
vec2 domeUv(vec2 cloudXZ) {
    return mat2(4.0, 3.0, -3.0, 4.0) * cloudXZ / 8192.0;
}

// Карты ярусов в точке. Контур по карте почти круглый: карту сдвигает плавный шум до 24 блоков, и край облака
// идёт неровными лопастями. Оба канала BA в одной выборке - независимые смещения по X и Z; ячейка 64 блока
// (сдвиг меньше ячейки - карта не складывается), шум тайлится через 512 * 64 = 32768 блоков - делит свёртку камеры.
vec3 levelMap(vec2 cloudXZ, bool warped) {
    vec2 warp = warped ? textureLod(Sampler1, (cloudXZ / 64.0 + 0.5) / LUT_SIZE, 0.0).ba - 0.5 : vec2(0.0);
    return textureLod(Sampler2, domeUv(cloudXZ + warp * 48.0), 0.0).rgb;
}

// Зоны погоды в пару километров: где крупная карта облаков высокая, облака гуще, где пусто - прояснения.
// Небо не заполняется равномерно, а повтор тайла карты теряется за разной густотой. Сдвиг порога
// от +0.105 до -0.195 - поэтому марш пропускает точки, где купол ниже порога больше чем на 0.195.
float groupShift(vec2 cloudXZ) {
    return (0.35 - lutHeight(cloudXZ / 32768.0)) * 0.3;
}

// Форма одного яруса: x > 0 внутри облака, y - сила купола в точке (0 - просвет, туда кромка не дорисовывается).
vec2 domeShape(float source, float heightBlocks, float threshold) {
    float height = heightBlocks / CloudThickness;
    // Степень < 1 поднимает слабые купола: облако выше и круче по бокам, а не тонкий блин со срезанным дном.
    float dome = pow(clamp((source - threshold) / (1.0 - threshold), 0.0, 1.0), 0.7);
    // Днище - по квадрату глубины: к краю облака оно быстро поднимается, и снизу облако пузатое, а не срезано
    // плоскостью. Глубже всего днище под сильным куполом, у кромки - почти у основания.
    float below = (BASE_LEVEL - height) / BASE_LEVEL;
    float extent = height >= BASE_LEVEL ? (height - BASE_LEVEL) / (1.0 - BASE_LEVEL) : below * below;
    return vec2(dome - extent, dome);
}

// Объединение ярусов: берётся ярус, в котором точка глубже всего внутри облака.
vec2 cloudShape(vec3 levels, float heightBlocks, float threshold) {
    vec2 first = domeShape(levels.r, heightBlocks - LEVELS.x * CloudLift, threshold);
    vec2 second = domeShape(levels.g, heightBlocks - LEVELS.y * CloudLift, threshold);
    vec2 third = domeShape(levels.b, heightBlocks - LEVELS.z * CloudLift, threshold);
    vec2 best = first.x >= second.x ? first : second;
    return best.x >= third.x ? best : third;
}

// Плотность в точке. Клубы - billow-шум |2n - 1|: круглые выпуклости и острые складки между ними, октавы вдвое
// мельче («цветная капуста»). stepBlocks - шаг марша: клуб мельче шага сводится к среднему, а кромка не тоньше
// шага, иначе редкие выборки у горизонта рвут облако в гребёнку и слоёную кашу.
float cloudDensity(vec2 cloudXZ, vec2 shape, float heightBlocks, float stepBlocks, int octaves) {
    if (shape.y <= 0.0) return 0.0;
    float edge = max(EDGE_SOFTNESS, stepBlocks / ((1.0 - BASE_LEVEL) * CloudThickness));
    float lower = 0.5 * (EDGE_SOFTNESS - edge);
    // Клубы - на куполе; к днищу они слабеют: низ настоящих кучевых глаже, а шум на почти горизонтальном днище
    // в перспективе тянется в мазки к горизонту.
    float height = heightBlocks / CloudThickness;
    float reach = DETAIL_STRENGTH * min(shape.y * 3.0, 1.0) * mix(0.2, 1.0, smoothstep(BASE_LEVEL - 0.1, BASE_LEVEL + 0.2, height));
    if (shape.x + 0.7 * reach < lower) return 0.0;
    vec3 point = vec3(cloudXZ, heightBlocks) / DETAIL_BLOCKS;
    float size = DETAIL_BLOCKS;
    float weight = 0.5;
    float billows = 0.0;
    for (int i = 0; i < 4; i++) {
        if (i >= octaves) break;
        float resolved = 1.0 - smoothstep(0.8 * size, 2.0 * size, stepBlocks);
        if (resolved <= 0.0) break;
        billows += weight * resolved * (abs(valueNoise3(point) * 2.0 - 1.0) - BILLOW_MEAN);
        point = point * 2.03 + vec3(19.0, 7.0, 3.0);
        size *= 0.5;
        weight *= 0.55;
    }
    return smoothstep(lower, lower + edge, shape.x + billows * reach);
}

// Свет берёт карту без сдвига контура: лишняя выборка в цикле света дороже, чем сдвиг тени в пару десятков блоков.
float lightDensity(vec3 point, vec2 cameraXZ, float threshold) {
    vec2 cloudXZ = cameraXZ + point.xz;
    return smoothstep(0.0, EDGE_SOFTNESS, cloudShape(levelMap(cloudXZ, false), point.y - CloudBottom, threshold).x);
}

// Плотность с клубами для ближних выборок света: клубы отбрасывают тени друг на друга, и складки между ними
// темнеют - без этого «цветная капуста» освещена как гладкий купол.
float detailedLightDensity(vec3 point, vec2 cameraXZ, float threshold, int octaves) {
    vec2 cloudXZ = cameraXZ + point.xz;
    float heightBlocks = point.y - CloudBottom;
    return cloudDensity(cloudXZ, cloudShape(levelMap(cloudXZ, true), heightBlocks, threshold), heightBlocks, 0.0, octaves);
}

float henyeyGreenstein(float cosTheta, float g) {
    float g2 = g * g;
    return (1.0 - g2) / pow(max(1.0 + g2 - 2.0 * g * cosTheta, 0.0001), 1.5);
}

float cloudPhase(float cosTheta, float g) {
    return mix(henyeyGreenstein(cosTheta, g), henyeyGreenstein(cosTheta, -0.3 * g), 0.3);
}

vec4 marchClouds(vec3 direction, float sceneDistance, vec2 fragCoord) {
    float bottom = CloudBottom - CloudLift;
    float top = CloudBottom + CloudThickness + CloudLift;
    float tEnter = 0.0;
    float tExit = CloudDistance;
    if (abs(direction.y) > 0.00001) {
        float tBottom = bottom / direction.y;
        float tTop = top / direction.y;
        tEnter = max(min(tBottom, tTop), 0.0);
        tExit = min(max(tBottom, tTop), CloudDistance);
    } else if (bottom > 0.0 || top < 0.0) {
        return vec4(0.0);
    }
    tExit = min(tExit, sceneDistance);
    if (tExit <= tEnter) return vec4(0.0);

    int quality = clamp(ShaderQuality, 1, 4);
    int maxSteps = quality == 1 ? 24 : (quality == 2 ? 40 : (quality == 3 ? 56 : 72));
    int steps = clamp(int((tExit - tEnter) / STEP_BLOCKS), maxSteps / 2, maxSteps);
    int lightSteps = quality == 1 ? 2 : (quality == 2 ? 3 : 4);
    int octaves = quality == 1 ? 2 : (quality == 4 ? 4 : 3);
    int detailedLight = quality == 4 ? 2 : (quality == 3 ? 1 : 0);

    // Ночью светит луна напротив солнца; оба источника гаснут у горизонта, так что смена бесшовна.
    // Под ночным шейдером неба (CloudNight) облака освещены как ночью при любом времени суток.
    vec3 sun = sunWorldDirection();
    float day = smoothstep(-0.05, 0.12, sun.y) * (1.0 - CloudNight);
    float night = max(1.0 - smoothstep(-0.25, -0.05, sun.y), CloudNight);
    vec3 lightDirection = sun.y >= -0.05 ? sun : -sun;
    vec3 sunTint = mix(vec3(1.0, 0.52, 0.24), vec3(1.0, 0.97, 0.92), smoothstep(0.02, 0.4, sun.y));
    // Под ночным шейдером неба луна ярче: облака читаются серебристыми силуэтами на звёздах, а не тонут в черноте.
    vec3 lightColor = sunTint * day + vec3(0.42, 0.52, 0.78) * night * mix(0.16, 0.45, CloudNight);
    // Цвета неба в UBO итоговые: своё небо и туман Ambience ночью не темнеют, поэтому яркость
    // небесной подсветки и дымки дали, уходящей в цвет горизонта, ведём по солнцу.
    vec3 zenith = vec3(SkyZenithR, SkyZenithG, SkyZenithB);
    vec3 horizon = vec3(SkyHorizonR, SkyHorizonG, SkyHorizonB);
    float skyLight = mix(0.08, 1.0, day);

    float cosTheta = dot(direction, lightDirection);
    float phaseDirect = cloudPhase(cosTheta, 0.6);
    float phaseSoft = cloudPhase(cosTheta, 0.3);
    float phaseWide = cloudPhase(cosTheta, 0.1);

    float threshold = CloudThreshold;
    float extinction = CloudExtinction;
    float lightScale = CloudThickness / 96.0;
    vec2 cameraXZ = vec2(CloudCamX, CloudCamZ);
    // Шаги распределены равномерно по sqrt(t): у камеры гуще, вдали реже; сдвиг по R2 прячет слои без полос.
    float jitter = fract(dot(fragCoord, vec2(0.7548776662, 0.5698402910)));
    float rootEnter = sqrt(tEnter);
    float rootExit = sqrt(tExit);
    float rootStep = (rootExit - rootEnter) / float(steps);
    // Кромку облака крупный шаг ловит с ошибкой до шага - на плоском дне это штриховка. Поэтому на входе в облако
    // шаг откатывается назад и четыре выборки идёт втрое мельче: кромка ложится точно, а глубь облака, где
    // ошибка шага уже не видна, снова идёт крупно.
    float root = rootEnter + jitter * rootStep;
    bool refined = false;
    int refinedLeft = 0;
    bool outside = true;
    // Плотность между выборками считается линейной (трапеция): вклад входа в облако почти не зависит от того, где
    // выпала выборка, и сдвиг шагов соседних пикселей не рисует на кромках и днищах штриховку.
    float previousT = tEnter;
    float previousDensity = 0.0;
    vec3 lastColor = vec3(0.0);
    bool previousLit = false;

    vec3 luminance = vec3(0.0);
    float transmittance = 1.0;
    for (int i = 0; i < 144; i++) {
        if (i >= steps * 2 || root >= rootExit) break;
        float current = root;
        float stepRoot = refined ? rootStep * 0.35 : rootStep;
        root += stepRoot;
        float t = current * current;
        vec3 offset = direction * t;
        vec2 cloudXZ = cameraXZ + offset.xz;
        vec3 levels = levelMap(cloudXZ, true);
        float density = 0.0;
        float localThreshold = threshold;
        float heightBlocks = offset.y - CloudBottom;
        if (max(levels.r, max(levels.g, levels.b)) > max(threshold - 0.195, 0.02)) {
            localThreshold = max(threshold + groupShift(cloudXZ), 0.02);
            float fade = 1.0 - smoothstep(CloudDistance * 0.5, CloudDistance, t);
            vec2 shape = cloudShape(levels, heightBlocks, localThreshold);
            density = cloudDensity(cloudXZ, shape, heightBlocks, 2.0 * current * rootStep, octaves) * fade;
        }
        if (refined && --refinedLeft <= 0) refined = false;
        float segment = max(t - previousT, 0.0);
        float segmentDensity = 0.5 * (density + previousDensity);
        if (density <= 0.001) {
            outside = true;
            // Выход из облака: хвост отрезка до первой пустой выборки - цветом последней освещённой.
            if (segmentDensity > 0.0005) {
                float exitTransmittance = exp(-segmentDensity * extinction * segment);
                luminance += transmittance * (1.0 - exitTransmittance) * lastColor;
                transmittance *= exitTransmittance;
            }
            previousT = t;
            previousDensity = density;
            previousLit = false;
            continue;
        }
        // Мельчить есть смысл только на входе снаружи и там, где шаг длиннее кромки. Прошлая выборка (пустая, перед
        // облаком) остаётся началом отрезка.
        bool entering = outside && !refined;
        outside = false;
        if (entering && quality > 1 && transmittance > 0.5 && 2.0 * current * rootStep > 4.0) {
            refined = true;
            refinedLeft = 4;
            root = current - rootStep * 0.65;
            continue;
        }
        previousT = t;
        previousDensity = density;

        float lightDepth = 0.0;
        float previous = 0.0;
        for (int j = 0; j < 4; j++) {
            if (j >= lightSteps) break;
            float lightDistance = 9.0 * pow(2.3, float(j)) * lightScale;
            vec3 lightPoint = offset + lightDirection * lightDistance;
            float lightSample = j < detailedLight
                    ? detailedLightDensity(lightPoint, cameraXZ, localThreshold, 2)
                    : lightDensity(lightPoint, cameraXZ, localThreshold);
            lightDepth += lightSample * (lightDistance - previous);
            previous = lightDistance;
        }
        float opticalDepth = lightDepth * extinction;
        // «Порошок»: тонкий внешний слой клуба ещё не набрал рассеянного света, поэтому складки между клубами и их
        // кромки темнее - объём читается. Заметно, когда солнце за спиной и видна освещённая сторона.
        float powder = mix(1.0, 1.0 - exp(-2.0 * opticalDepth - 1.5 * density), 0.8 * smoothstep(0.4, -0.5, cosTheta));
        // Октавы многократного рассеяния: свет, прошедший глубже, - слабее затухает и шире расходится; тень облака
        // от этого светлая и мягкая, а не серая плита.
        vec3 sunLight = lightColor * powder * (exp(-opticalDepth) * phaseDirect
                + 0.6 * exp(-opticalDepth * 0.35) * phaseSoft
                + 0.35 * exp(-opticalDepth * 0.12) * phaseWide);
        // Небо светит сверху: низ облака темнее верха, а середина днища под толщей облака темнее его краёв -
        // тень не ровная плита. Затенение сверху - одна выборка карты на 16 блоков выше.
        float heightFraction = clamp((heightBlocks + CloudLift) / (CloudThickness + 2.0 * CloudLift), 0.0, 1.0);
        float skyVisible = 1.0 - 0.55 * lightDensity(offset + vec3(0.0, 16.0 * lightScale, 0.0), cameraXZ, localThreshold);
        vec3 ambient = mix(horizon * 0.75, zenith, sqrt(heightFraction)) * skyVisible * mix(0.7, 1.2, CloudNight) * skyLight;
        vec3 sampleColor = mix(sunLight + ambient, horizon * skyLight, (1.0 - exp(-t / 1400.0)) * 0.7);

        // Цвет отрезка - среднее концов (на входе в облако - цвет первой выборки внутри).
        vec3 segmentColor = previousLit ? 0.5 * (lastColor + sampleColor) : sampleColor;
        lastColor = sampleColor;
        previousLit = true;
        float stepTransmittance = exp(-segmentDensity * extinction * segment);
        luminance += transmittance * (1.0 - stepTransmittance) * segmentColor;
        transmittance *= stepTransmittance;
        if (transmittance < 0.015) {
            transmittance = 0.0;
            break;
        }
    }
    // Premultiplied: пересвет у солнца гасится общим множителем, чтобы не сбивать оттенок по каналам.
    float alpha = 1.0 - transmittance;
    vec3 color = luminance * vec3(CloudR, CloudG, CloudB);
    float peak = max(max(color.r, color.g), max(color.b, alpha));
    return vec4(color * (alpha / max(peak, 0.0001)), alpha) * CloudA;
}

void main() {
    fragColor = vec4(0.0);

    ivec2 depthSize = textureSize(Sampler0, 0);
    ivec2 layerSize = max(depthSize / 2, ivec2(1));
    vec3 viewDirection = pixelViewDirection(gl_FragCoord.xy / vec2(layerSize));
    float depth = farthestDepth(ivec2(gl_FragCoord.xy) * 2, depthSize - 1);
    float sceneDistance = depth >= 0.99999 ? 1.0e9 : linearZ(depth) / max(-viewDirection.z, 0.0001);
    fragColor = marchClouds(viewToWorld(viewDirection), sceneDistance, gl_FragCoord.xy);
}
