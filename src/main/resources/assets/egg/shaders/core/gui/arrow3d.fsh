#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 quadCoord;
in vec2 arrowAngles;
in vec2 arrowShine;

out vec4 fragColor;

// Квад нормируем в -ZOOM..ZOOM, поэтому вся геометрия задана в долях от него и вид стрелки
// не зависит от того, какого размера метку попросили нарисовать.
const float ZOOM = 1.1;
const float HALF_THICKNESS = 0.18;
// Фаска намеренно заметная: на ней живёт блик, а метка занимает всего пару десятков пикселей —
// слишком узкая фаска не набирает и пикселя, и подсветка кромки распадается в пунктир.
// У шеврона плечи тонкие, поэтому фаска меньше — иначе она их пережимает.
const float EDGE_ROUND = 0.08;
const float SURFACE_EPS = 0.0015;
// Стекло: доля непрозрачности там, где смотрим на грань в лоб. К кромке она добирается
// френелем, поэтому сквозь тело видно мир, а силуэт всё равно очерчен.
const float GLASS_CORE = 0.42;

// Шеврон в плане: остриё в -Z, вырез сзади уходит почти до острия — тело лёгкое, и метка
// читается как указатель направления, а не как объект.
const vec2 CHEVRON[4] = vec2[4](
    vec2(0.0, -0.95),
    vec2(0.58, 0.58),
    vec2(0.0, -0.02),
    vec2(-0.58, 0.58)
);

vec3 linearToSrgb(vec3 c) {
    c = clamp(c, 0.0, 1.0);
    return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(0.0031308, c));
}

// SDF вогнутого четырёхугольника: знак берём по числу пересечений луча с рёбрами.
float sdChevron(vec2 p) {
    float d = dot(p - CHEVRON[0], p - CHEVRON[0]);
    float s = 1.0;
    for (int i = 0, j = 3; i < 4; j = i, i++) {
        vec2 e = CHEVRON[j] - CHEVRON[i];
        vec2 w = p - CHEVRON[i];
        vec2 b = w - e * clamp(dot(w, e) / dot(e, e), 0.0, 1.0);
        d = min(d, dot(b, b));
        bvec3 c = bvec3(p.y >= CHEVRON[i].y, p.y < CHEVRON[j].y, e.x * w.y > e.y * w.x);
        if (all(c) || all(not(c))) s = -s;
    }
    return s * sqrt(d);
}

// Плита шеврона: контур ужимаем на радиус фаски, выдавливаем по Y и радиус возвращаем —
// грани получают скругление, на котором и ловится свет.
float map(vec3 p) {
    float ca = cos(arrowAngles.x);
    float sa = sin(arrowAngles.x);
    vec2 plan = vec2(ca * p.x - sa * p.z, sa * p.x + ca * p.z);
    vec2 w = vec2(sdChevron(plan) + EDGE_ROUND, abs(p.y) - (HALF_THICKNESS - EDGE_ROUND));
    return min(max(w.x, w.y), 0.0) + length(max(w, vec2(0.0))) - EDGE_ROUND;
}

vec3 surfaceNormal(vec3 p) {
    vec2 e = vec2(SURFACE_EPS, 0.0);
    return normalize(vec3(
        map(p + e.xyy) - map(p - e.xyy),
        map(p + e.yxy) - map(p - e.yxy),
        map(p + e.yyx) - map(p - e.yyx)));
}

void main() {
    fragColor = vec4(0.0);

    // Производную снимаем до марша: после цикла с break управление уже неоднородное и fwidth там неопределён.
    float pixel = max(fwidth(quadCoord).x, 1e-6) * (2.0 * ZOOM);

    vec2 ndc = (quadCoord - 0.5) * (2.0 * ZOOM);
    vec3 ro = vec3(ndc.x, -ndc.y, 2.6);
    vec3 rd = vec3(0.0, 0.0, -1.0);

    // Ортокамера поднята над плоскостью стрелки: тело видно сверху-сбоку, поэтому при
    // развороте оно честно уходит в перспективу, а не крутится как плоский значок.
    // Синус берём со знаком минус — иначе луч уходит вверх и мы смотрим на стрелку снизу.
    float ct = cos(arrowAngles.y);
    float st = -sin(arrowAngles.y);
    ro.yz = vec2(ct * ro.y - st * ro.z, st * ro.y + ct * ro.z);
    rd.yz = vec2(ct * rd.y - st * rd.z, st * rd.y + ct * rd.z);

    float t = 0.0;
    float nearest = 1e9;
    float nearestT = 0.0;
    bool hit = false;
    for (int i = 0; i < 64; i++) {
        float d = map(ro + rd * t);
        if (d < nearest) {
            nearest = d;
            nearestT = t;
        }
        if (d < SURFACE_EPS) {
            hit = true;
            break;
        }
        t += d;
        if (t > 6.0) break;
    }

    // Сглаживание силуэта: за кромкой луч не попал, но знает, насколько близко прошёл —
    // сравниваем этот промах с размером пикселя в объектных единицах.
    float coverage = hit ? 1.0 : 1.0 - smoothstep(0.0, pixel * 1.3, nearest);
    if (coverage <= 0.0) discard;

    vec3 hitPoint = ro + rd * (hit ? t : nearestT);
    vec3 normal = surfaceNormal(hitPoint);
    vec3 lightDir = normalize(vec3(-0.38, 0.88, 0.42));

    float diffuse = max(dot(normal, lightDir), 0.0);
    float sky = normal.y * 0.5 + 0.5;
    float rim = pow(1.0 - clamp(dot(normal, -rd), 0.0, 1.0), 2.2);
    // Блик держим широким: на gui-scale 1 метка занимает ~30 px, и узкая полоса рассыпалась бы
    // в пунктир между центрами пикселей.
    float specular = pow(max(dot(reflect(-lightDir, normal), -rd), 0.0), 14.0);

    vec3 base = linearToSrgb(vertexColor.rgb);
    // Светлый акцент темы сам почти белый — подсветка кромки и блик на нём съедают объём,
    // поэтому их силу привязываем к запасу яркости, который у цвета ещё остался до белого.
    float headroom = 1.0 - dot(base, vec3(0.2126, 0.7152, 0.0722));
    float highlight = mix(0.4, 1.0, headroom);

    vec3 color = mix(base * 0.24, base, diffuse);
    color += base * sky * 0.22;
    color = mix(color, vec3(1.0), rim * 0.32 * highlight);
    color += vec3(specular * 0.38 * highlight);

    // Блик едет от хвоста к острию — метка живая, даже когда игрок стоит на месте.
    float strength = clamp(arrowShine.y, 0.0, 1.0);
    if (strength > 0.0) {
        float ca = cos(arrowAngles.x);
        float sa = sin(arrowAngles.x);
        float along = clamp(0.5 - (sa * hitPoint.x + ca * hitPoint.z) * 0.55, 0.0, 1.0);
        float wave = abs(fract(along - arrowShine.x + 0.5) - 0.5) * 2.0;
        color = mix(color, vec3(1.0), pow(1.0 - wave, 10.0) * strength * 0.35);
    }

    // Грань, повёрнутая к камере, просвечивает; чем острее ракурс, тем плотнее — тот же френель,
    // что уже подсвечивает кромку, задаёт и её непрозрачность.
    float glass = GLASS_CORE + (1.0 - GLASS_CORE) * rim;

    fragColor = vec4(color, vertexColor.a * coverage * clamp(glass, 0.0, 1.0)) * ColorModulator;
    if (fragColor.a <= 0.0005) discard;
}
