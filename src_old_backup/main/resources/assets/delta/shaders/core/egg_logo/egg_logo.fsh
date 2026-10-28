#version 150

in vec2 TexCoord;
in vec4 VertexColor;

out vec4 fragColor;

uniform sampler2D Sampler0;
uniform vec2  uResolution;
uniform float uTime;
uniform vec2  uMouse;
uniform float uIntroPlayed;
uniform float uAlpha;
uniform float uThemeMode;
uniform float uBurstTime;

// =============================================================================
// КОНСТАНТЫ СЦЕНЫ
// =============================================================================
const float LOGO_BASE_SIZE    = 1.05;      // Базовый размер 3D-логотипа по центру
const float LOGO_DOCKED_SCALE = 0.48;      // Размер 3D-логотипа в доке (на месте даты/часов)
const float LOGO_DOCKED_Y     = 1.50;      // Высота позиции в доке
const vec3  COLOR_LOGO_FACE   = vec3(0.965, 0.975, 0.990); // Платиново-белый металлик
const vec3  COLOR_LOGO_SIDE   = vec3(0.240, 0.260, 0.310); // Тёмно-графитовые анодированные грани

mat3 rotX(float a) {
    float c = cos(a), s = sin(a);
    return mat3(1.0, 0.0, 0.0, 0.0, c, -s, 0.0, s, c);
}

mat3 rotY(float a) {
    float c = cos(a), s = sin(a);
    return mat3(c, 0.0, s, 0.0, 1.0, 0.0, -s, 0.0, c);
}

mat3 rotZ(float a) {
    float c = cos(a), s = sin(a);
    return mat3(c, -s, 0.0, s, c, 0.0, 0.0, 0.0, 1.0);
}

float easeInOutCubic(float t) {
    t = clamp(t, 0.0, 1.0);
    return t < 0.5 ? 4.0 * t * t * t : 1.0 - pow(-2.0 * t + 2.0, 3.0) * 0.5;
}

// -----------------------------------------------------------------------------
// 1. Процедурный 3D-логотип через экструзию Signed Distance Field (SDF) с фасками
// -----------------------------------------------------------------------------
float sdLogo2D(vec2 p) {
    vec2 uv = p * vec2(0.5, -0.5) + 0.5;
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        vec2 dEdge = max(abs(p) - 1.0, vec2(0.0));
        return length(dEdge) + 0.20;
    }
    float val = texture(Sampler0, uv).r;
    return (0.502 - val) * 0.375;
}

float sdLogo3D(vec3 p, float scale) {
    p /= scale;
    float d2d = sdLogo2D(p.xy);
    float h = 0.105;  // 3D-толщина по Z
    float r = 0.022;  // Радиус скругления/металлической фаски
    vec2 w = vec2(d2d, abs(p.z) - h);
    float d3d = min(max(w.x, w.y), 0.0) + length(max(w, 0.0)) - r;
    return d3d * scale;
}

// -----------------------------------------------------------------------------
// Интерактивная траектория и физика 3D-логотипа:
// 1) Плавный подлёт в камеру из глубины без рывков
// 2) На фоне: лёгкий фиксированный наклон + плавное вращение по горизонтали (Yaw)
// 3) При клике/пробеле: бешеная раскрутка (spin-up) -> мощная детонация -> подъём в док
// -----------------------------------------------------------------------------
void getLogoTransform(float t, float burstT, out vec3 pos, out mat3 rot, out float scale) {
    float tiltPitch = 0.18; // Лёгкий наклон вперёд
    float tiltRoll  = -0.06;

    if (uIntroPlayed > 0.5) {
        float breathe = sin(t * 1.5) * 0.025;
        pos = vec3(0.0, LOGO_DOCKED_Y + breathe, 0.0);
        scale = LOGO_DOCKED_SCALE;

        float pX = uMouse.y * 0.15 + sin(t * 1.2) * 0.03;
        float slowDockYaw = t * 0.85 + uMouse.x * 0.20;
        rot = rotY(slowDockYaw) * rotX(pX);
        return;
    }

    if (burstT < 0.0) {
        // До нажатия:
        if (t < 2.4) {
            // 1. Максимально плавный подлёт в камеру (Quintic Ease-Out децелерация)
            float pFly = clamp(t / 2.4, 0.0, 1.0);
            float eFly = 1.0 - pow(1.0 - pFly, 3.5);
            pos = vec3(0.0, mix(-0.25, 0.0, eFly), mix(-8.5, 0.0, eFly));
            scale = mix(0.18, LOGO_BASE_SIZE, eFly);

            // Плавное успокоение вращения при прилёте в центр
            float entrySpin = (1.0 - eFly) * 3.5;
            float yaw = t * 0.85 + entrySpin;
            rot = rotX(tiltPitch * eFly) * rotY(yaw) * rotZ(tiltRoll * eFly);
        } else {
            // 2. В ожидании: под небольшим углом плавно крутится по горизонтали
            pos = vec3(0.0, 0.0, 0.0);
            scale = LOGO_BASE_SIZE;

            float idleYaw = t * 0.85 + uMouse.x * 0.25;
            float idlePitch = tiltPitch + uMouse.y * 0.18;
            rot = rotX(idlePitch) * rotY(idleYaw) * rotZ(tiltRoll);
        }
    } else {
        // После нажатия Пробела или клика:
        if (burstT < 0.40) {
            // 3. Очень быстрая прокрутка по горизонтали с ускорением (Spin-Up)
            float pSpin = burstT / 0.40;
            float fastSpin = pSpin * pSpin * 36.0;
            float swell = sin(pSpin * 3.14159) * 0.16;

            scale = LOGO_BASE_SIZE * (1.0 + swell);
            pos = vec3(0.0, 0.0, 0.0);
            rot = rotX(tiltPitch * (1.0 - pSpin)) * rotY(fastSpin) * rotZ(tiltRoll * (1.0 - pSpin));
        } else if (burstT < 1.30) {
            // 4. Взрывной импульс детонации и физическая отдача (вращение плавно гасит бешеную скорость)
            float blastT = burstT - 0.40;
            float damp = exp(-blastT * 4.5) * cos(blastT * 18.0);
            pos = vec3(0.0, damp * 0.22, damp * 0.32);
            scale = mix(1.22, LOGO_BASE_SIZE, smoothstep(0.0, 0.35, blastT)) + damp * 0.08;

            float blastYaw = 36.0 + (1.0 - exp(-blastT * 3.5)) * 14.0 + blastT * 1.5;
            rot = rotY(blastYaw) * rotX(damp * 0.25);
        } else if (burstT < 2.80) {
            // 5. Плавный подъём наверх на позицию часов — вращение ПРОДОЛЖАЕТСЯ в спокойном медленном темпе
            float eAscent = easeInOutCubic((burstT - 1.30) / 1.50);
            pos = vec3(0.0, mix(0.0, LOGO_DOCKED_Y, eAscent), 0.0);
            scale = mix(LOGO_BASE_SIZE, LOGO_DOCKED_SCALE, eAscent);

            float ascentT = burstT - 1.30;
            float baseYaw13 = 36.0 + (1.0 - exp(-0.90 * 3.5)) * 14.0 + 0.90 * 1.5;
            float ascentYaw = baseYaw13 + ascentT * 1.1 + (uMouse.x * 0.20 * eAscent);
            float pitchAscent = uMouse.y * 0.15 * eAscent;
            rot = rotY(ascentYaw) * rotX(pitchAscent);
        } else {
            // 6. Завершено, покачивание в доке с непрерывным спокойным вращением
            float dockT = burstT - 2.80;
            pos = vec3(0.0, LOGO_DOCKED_Y + sin(dockT * 1.5) * 0.025, 0.0);
            scale = LOGO_DOCKED_SCALE;

            float baseYaw28 = 36.0 + (1.0 - exp(-0.90 * 3.5)) * 14.0 + 0.90 * 1.5 + 1.50 * 1.1;
            float continuousDockYaw = baseYaw28 + dockT * 0.85 + (uMouse.x * 0.20);
            rot = rotY(continuousDockYaw) * rotX(uMouse.y * 0.15);
        }
    }
}

float mapDist(vec3 p) {
    vec3 logoPos;
    mat3 logoRot;
    float logoScale;
    getLogoTransform(uTime, uBurstTime, logoPos, logoRot, logoScale);

    vec3 localLogo = transpose(logoRot) * (p - logoPos);
    return sdLogo3D(localLogo, logoScale);
}

vec3 calcNormal(vec3 p) {
    const vec2 e = vec2(1.0, -1.0) * 0.0018;
    return normalize(
        e.xyy * mapDist(p + e.xyy) +
        e.yyx * mapDist(p + e.yyx) +
        e.yxy * mapDist(p + e.yxy) +
        e.xxx * mapDist(p + e.xxx)
    );
}

float calcAO(vec3 p, vec3 n) {
    float occ = 0.0;
    float sca = 1.0;
    for (int i = 0; i < 5; i++) {
        float h = 0.02 + 0.12 * float(i) / 4.0;
        float d = mapDist(p + h * n);
        occ += (h - d) * sca;
        sca *= 0.85;
    }
    return clamp(1.0 - 2.2 * occ, 0.0, 1.0);
}

// =============================================================================
// ЖИВОЙ ПРОЦЕДУРНЫЙ 3D-ШЕЙДЕРНЫЙ ФОН: ТЁМНЫЙ ЖИДКИЙ ШЁЛК / ВОЛНЫ МЕТАЛЛА (БЕЗ PNG)
// Плавные трёхмерные волны, объёмные светотени, хроматические отблески и физическая рябь
// =============================================================================
vec3 renderProceduralLivingBackground(vec2 uv, float distCenter) {
    vec2 p = uv * 1.35 + uMouse * 0.08;
    float t = uTime * 0.28;

    // Многослойная симуляция струящихся жидких волн / ткани
    float w1 = sin(p.x * 2.1 + p.y * 1.3 + t);
    float w2 = cos(p.x * 3.2 - p.y * 2.4 - t * 0.8 + w1 * 0.9);
    float w3 = sin((p.x + p.y) * 4.4 + w2 * 0.7 + t * 0.6);
    float h = w1 * 0.42 + w2 * 0.35 + w3 * 0.23;

    // Честные трёхмерные нормали поверхности для светотени и бликов
    float eps = 0.02;
    float hX = sin((p.x + eps) * 2.1 + p.y * 1.3 + t) * 0.42 + cos((p.x + eps) * 3.2 - p.y * 2.4 - t * 0.8) * 0.35;
    float hY = sin(p.x * 2.1 + (p.y + eps) * 1.3 + t) * 0.42 + cos(p.x * 3.2 - (p.y + eps) * 2.4 - t * 0.8) * 0.35;
    vec3 norm = normalize(vec3((h - hX) / eps * 0.65, (h - hY) / eps * 0.65, 1.0));

    // Динамический студийный свет с параллаксом
    vec3 light1 = normalize(vec3(-0.45 + uMouse.x * 0.25, 0.75 + uMouse.y * 0.20, 0.85));
    vec3 light2 = normalize(vec3(0.65, -0.35, 0.60));

    float diff1 = max(dot(norm, light1), 0.0);
    float diff2 = max(dot(norm, light2), 0.0) * 0.45;

    vec3 viewDir = vec3(0.0, 0.0, 1.0);
    vec3 half1 = normalize(light1 + viewDir);
    float spec = pow(max(dot(norm, half1), 0.0), 22.0) * 0.60;
    float rim = pow(1.0 - max(dot(norm, viewDir), 0.0), 2.8) * 0.50;

    // Роскошная палитра: глубокий обсидиан, тёмная сталь, лунные гребни и платиновые блики
    vec3 colDeep  = vec3(0.045, 0.052, 0.068);
    vec3 colMid   = vec3(0.120, 0.138, 0.175);
    vec3 colCrest = vec3(0.245, 0.280, 0.350);
    vec3 colGleam = vec3(0.600, 0.660, 0.760);

    float vGrad = clamp(uv.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 color = mix(colDeep, colMid, vGrad);

    float waveIntensity = (h * 0.5 + 0.5);
    color = mix(color, colCrest, waveIntensity * (diff1 * 0.70 + diff2 + 0.30));
    color += colGleam * spec;
    color += mix(colMid, colGleam, 0.45) * rim;

    // Тонкий переливчатый хроматический шёлк в складках
    vec3 sheen = vec3(
        sin(h * 3.1415 + 0.0) * 0.025,
        sin(h * 3.1415 + 1.2) * 0.028,
        sin(h * 3.1415 + 2.4) * 0.038
    );
    color += sheen;

    // Мягкая кинематографичная виньетка
    float vignette = smoothstep(0.40, 1.85, distCenter);
    color = mix(color, color * 0.60, vignette);

    // Затенение под центральным логотипом
    float contactShadow = smoothstep(0.0, 1.30, length(uv - vec2(0.0, -0.05)));
    color = mix(color * 0.75, color, contactShadow);

    // Физическая рябь от взрыва логотипа
    if (uBurstTime >= 0.40 && uBurstTime < 2.5) {
        float blastT = uBurstTime - 0.40;
        float rippleR = blastT * 3.2;
        float dR = abs(distCenter - rippleR);
        float ripple = sin(dR * 28.0 - blastT * 14.0) * exp(-dR * 8.0) * exp(-blastT * 2.2);
        color += vec3(0.28, 0.34, 0.45) * ripple * 0.9;
    }

    return color;
}

void main() {
    vec2 uv = (gl_FragCoord.xy - 0.5 * uResolution.xy) / min(uResolution.x, uResolution.y);
    float distCenter = length(uv);

    vec3 finalColor = renderProceduralLivingBackground(uv, distCenter);

    // =========================================================================
    // RAYMARCHING ДЛЯ 3D-ЛОГОТИПА
    // =========================================================================
    vec3 ro = vec3(uMouse.x * 0.15, 0.45 + uMouse.y * 0.10, 6.2);
    vec3 ta = vec3(0.0, 0.30, 0.0);
    vec3 ww = normalize(ta - ro);
    vec3 uu = normalize(cross(ww, vec3(0.0, 1.0, 0.0)));
    vec3 vv = cross(uu, ww);
    vec3 rd = normalize(uv.x * uu + uv.y * vv + 1.75 * ww);

    float t = 0.1;
    float tMax = 18.0;
    bool hit = false;

    for (int i = 0; i < 76; i++) {
        vec3 p = ro + rd * t;
        float d = mapDist(p);
        if (d < 0.0012) {
            hit = true;
            break;
        }
        t += d * 0.90;
        if (t > tMax) break;
    }

    if (hit) {
        vec3 p = ro + rd * t;
        vec3 n = calcNormal(p);
        vec3 v = -rd;

        vec3 lKey  = normalize(vec3(0.55, 0.85, 0.70));
        vec3 lFill = normalize(vec3(-0.70, -0.20, 0.50));
        vec3 lBack = normalize(vec3(0.0, 0.70, -0.80));

        float ao = calcAO(p, n);
        float diffKey  = max(dot(n, lKey), 0.0);
        float diffFill = max(dot(n, lFill), 0.0) * 0.35;
        float diffBack = max(dot(n, lBack), 0.0) * 0.20;
        float amb      = 0.28;

        float fresnel = pow(1.0 - max(dot(n, v), 0.0), 3.2);

        vec3 hKey = normalize(lKey + v);
        float specSharp = pow(max(dot(n, hKey), 0.0), 45.0);
        float specBroad = pow(max(dot(n, hKey), 0.0), 9.0) * 0.25;

        // Определяем локальные грани: передняя/задняя грань или боковые анодированные скосы
        vec3 logoPos;
        mat3 logoRot;
        float logoScale;
        getLogoTransform(uTime, uBurstTime, logoPos, logoRot, logoScale);
        vec3 localN = transpose(logoRot) * n;

        float faceFactor = smoothstep(0.40, 0.80, abs(localN.z));
        vec3 matColor = mix(COLOR_LOGO_SIDE, COLOR_LOGO_FACE, faceFactor);

        vec3 diffuse = matColor * (diffKey * 0.95 + diffFill + diffBack + amb);
        vec3 specular = vec3(1.0) * (specSharp * 0.90 + specBroad);
        vec3 rim = mix(vec3(1.0), vec3(0.85, 0.92, 1.0), 0.45) * fresnel * 0.85;
        finalColor = (diffuse + specular + rim) * ao;
    }

    // =========================================================================
    // СВЕТОВОЙ ВЗРЫВ (СВЕЧЕНИЕ ВМЕСТО СНЕЖКОВ: ЛУЧИ, СВЕТЯЩИЙСЯ БЛУМ, ВОЛНА И ИСКРЫ)
    // =========================================================================
    if (uBurstTime >= 0.40 && uBurstTime < 5.5) {
        float blastT = uBurstTime - 0.40;

        // 1. Мощная ударная волна света
        if (blastT < 2.0) {
            float waveR = blastT * 3.4;
            float waveDist = abs(distCenter - waveR);
            float shockGlow = exp(-waveDist * 22.0) * exp(-blastT * 2.2);
            vec3 shockColor = vec3(0.95, 0.97, 1.0) * shockGlow * 2.6;
            finalColor += shockColor;
        }

        // 2. Объёмное лучистое сияние (God Rays / Light Beams)
        if (blastT < 1.8) {
            float angle = atan(uv.y, uv.x);
            float rays1 = sin(angle * 12.0 + blastT * 2.4) * 0.5 + 0.5;
            float rays2 = sin(angle * 20.0 - blastT * 1.8) * 0.35;
            float rayPattern = pow(rays1 + rays2, 3.0);
            float rayFalloff = exp(-distCenter * 2.2) * exp(-blastT * 2.5);
            vec3 rayGlow = vec3(1.0, 0.96, 0.90) * rayPattern * rayFalloff * 2.2;
            finalColor += rayGlow;
        }

        // 3. Яркий радиальный блум детонации (центральное свечение)
        if (blastT < 0.9) {
            float bloom = exp(-distCenter * 2.6) * exp(-blastT * 4.5) * 3.2;
            vec3 bloomColor = vec3(1.0, 0.98, 0.94) * bloom;
            finalColor += bloomColor;
        }

        // 4. Мягкие светящиеся тлеющие частицы с нежными ореолами (вместо твёрдых снежков)
        float embers = 0.0;
        for (int k = 0; k < 64; k++) {
            float fk = float(k);
            float seed  = fract(sin(fk * 127.1 + 311.7) * 43758.5453);
            float seed2 = fract(cos(fk * 269.5 + 183.3) * 23421.631);
            float seed3 = fract(sin(fk * 419.2 + 71.9) * 31415.926);

            float sAngle = fk * (6.2831853 / 64.0) + seed * 0.65;
            float sSpeed = 1.7 + seed2 * 2.4;

            float blastTravel = sSpeed * (1.0 - exp(-blastT * 2.8)) * 0.78;
            vec2 radialPos = vec2(cos(sAngle), sin(sAngle)) * blastTravel;

            float fallGravity = blastT * blastT * (0.045 + seed3 * 0.065);
            float horizontalSway = sin(blastT * 2.4 + seed * 6.28) * (0.02 + seed2 * 0.03) * blastT;
            vec2 sPos = radialPos + vec2(horizontalSway, -fallGravity);

            float dEmber = length(uv - sPos);
            // Мягкое свечение: яркое ядро + широкий рассеянный ореол
            float eCore = exp(-dEmber * 130.0) * 1.6;
            float eHalo = exp(-dEmber * 26.0) * 0.40;

            float life = clamp(1.0 - blastT / (3.2 + seed * 2.2), 0.0, 1.0);
            float sparkle = 0.75 + 0.25 * sin(blastT * 14.0 + seed * 22.0);
            embers += (eCore + eHalo) * life * sparkle;
        }

        vec3 emberColor = vec3(1.0, 0.96, 0.90) * embers * 1.7;
        finalColor += emberColor;
    }

    fragColor = vec4(pow(finalColor, vec3(0.96)), uAlpha * VertexColor.a);
}
