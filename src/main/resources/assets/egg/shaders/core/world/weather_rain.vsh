#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

layout(std140) uniform Projection {
    mat4 ProjMat;
};

layout(std140) uniform Globals {
    ivec3 CameraBlockPos;
    vec3 CameraOffset;
    vec2 ScreenSize;
    float GlintAlpha;
    float GameTime;
    int MenuBlurRadius;
    int UseRgss;
};

layout(std140) uniform SadnesWeather {
    float BoxSize;
    float InnerFade;
    float OriginAX;
    float OriginAY;
    float OriginAZ;
    float OriginBX;
    float OriginBY;
    float OriginBZ;
    float OriginCX;
    float OriginCY;
    float OriginCZ;
    float FallX;
    float FallY;
    float FallZ;
    float StreakLength;
    float ParticleSize;
    float Opacity;
    float Time;
    float MapOriginX;
    float MapOriginZ;
    float MapCamY;
    float MapSize;
    float LightR;
    float LightG;
    float LightB;
    float Flash;
    float FogStart;
    float FogDensity;
    float Ripples;
    float SnowCover;
    float Sway;
    int DefaultType;
    int Quality;
    float Pad0;
    float Pad1;
    float Pad2;
};

uniform sampler2D Sampler0;

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec3 tint;
out float alpha;
out float edgePx;
out float halfPx;
out float along;
out float heightAboveTop;

// Капля дождя. Статический буфер (AmbienceWeatherFx) хранит на каждую каплю: Position — точку в
// единичном кубе, UV0 — угол квада, Color — случайные байты (R длина, G толщина и яркость, A класс скорости).
// Куб растягивается в оболочку вокруг камеры и сдвигается на пройденный дождём путь, свёрнутый в период
// куба, поэтому капли стоят в мире, а не едут за камерой. Полоса расширяется в пикселях, как AA-линия.
// Карту осадков пишет WeatherMap: RG — верх столба (16 бит со сдвигом), B — тип осадков плюс 4 у воды.

const float HEIGHT_BIAS = 32768.0;
const int TYPE_RAIN = 1;
// Запас квада за краем капли под сглаживание, пикселей.
const float FEATHER = 1.0;

vec4 mapTexel(vec2 relativeXZ) {
    ivec2 texel = ivec2(floor(relativeXZ - vec2(MapOriginX, MapOriginZ)));
    int size = int(MapSize);
    if (texel.x < 0 || texel.y < 0 || texel.x >= size || texel.y >= size) return vec4(-1.0);
    return texelFetch(Sampler0, texel, 0);
}

float columnTop(vec4 texel) {
    if (texel.a < 0.0) return -1.0e6;
    return floor(dot(texel.rg, vec2(65280.0, 255.0)) + 0.5) - HEIGHT_BIAS - MapCamY;
}

int columnType(vec4 texel) {
    if (texel.a < 0.0) return DefaultType;
    return int(texel.b * 255.0 + 0.5) % 4;
}

vec3 speedOrigin(int speedClass) {
    if (speedClass == 0) return vec3(OriginAX, OriginAY, OriginAZ);
    if (speedClass == 1) return vec3(OriginBX, OriginBY, OriginBZ);
    return vec3(OriginCX, OriginCY, OriginCZ);
}

// Туман Ambience на дистанции капли — та же экспонента, что в ambience_fog: вдали дождь тонет вместе с миром.
float fogVisibility(float dist) {
    if (FogDensity <= 0.0) return 1.0;
    return exp(-min(max(dist - FogStart, 0.0) * FogDensity, 3.0));
}

// Слой гаснет у края куба (там капли перескакивают на другую сторону) и ближе InnerFade, где рисует слой поменьше.
float shellFade(float dist) {
    float halfBox = 0.5 * BoxSize;
    float outer = 1.0 - smoothstep(0.6 * halfBox, 0.95 * halfBox, dist);
    float inner = InnerFade > 0.0 ? smoothstep(0.6 * InnerFade, InnerFade, dist) : 1.0;
    return outer * inner;
}

void hide() {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    tint = vec3(0.0);
    alpha = 0.0;
    edgePx = 0.0;
    halfPx = 0.0;
    along = 0.0;
    heightAboveTop = -1.0;
}

void main() {
    vec3 head = mod(Position * BoxSize - speedOrigin(int(Color.a * 2.999)), BoxSize) - 0.5 * BoxSize;
    vec3 tail = head - vec3(FallX, FallY, FallZ) * StreakLength * (0.7 + 0.6 * Color.r);
    vec4 texel = mapTexel(head.xz);
    float top = columnTop(texel);
    float dist = length(head);
    float fade = shellFade(dist) * smoothstep(0.3, 1.0, dist) * fogVisibility(dist);
    // Весь след под верхом столба — капля под крышей или в земле.
    if (columnType(texel) != TYPE_RAIN || tail.y < top || fade <= 0.001) {
        hide();
        return;
    }

    vec4 headView = ModelViewMat * vec4(head, 1.0);
    vec4 tailView = ModelViewMat * vec4(tail, 1.0);
    if (max(headView.z, tailView.z) > -0.1) {
        hide();
        return;
    }
    vec4 headClip = ProjMat * headView;
    vec4 tailClip = ProjMat * tailView;
    vec2 headNdc = headClip.xy / headClip.w;
    vec2 tailNdc = tailClip.xy / tailClip.w;

    // Капля, летящая прямо в камеру, схлопывается в точку — тогда квад просто квадратный.
    vec2 axisPx = (tailNdc - headNdc) * 0.5 * ScreenSize;
    float lengthPx = length(axisPx);
    vec2 dir = lengthPx > 0.001 ? axisPx / lengthPx : vec2(0.0, 1.0);
    vec2 perp = vec2(-dir.y, dir.x);

    // Тоньше пикселя капля рисуется пиксельной полосой, но прозрачнее — вдали дождь не рябит. Корень мягче
    // честной доли покрытия: дальние капли складываются в видимую завесу, а не пропадают совсем.
    float widthPx = ParticleSize * (0.75 + 0.5 * Color.g) * ProjMat[1][1] * 0.5 * ScreenSize.y / -headView.z;
    halfPx = max(0.5 * widthPx, 0.55);
    float coverage = sqrt(min(0.5 * widthPx / halfPx, 1.0));
    float extent = halfPx + FEATHER;

    bool atTail = UV0.y > 0.5;
    float side = UV0.x * 2.0 - 1.0;
    vec4 clip = atTail ? tailClip : headClip;
    vec2 ndc = atTail ? tailNdc : headNdc;
    vec2 offsetPx = perp * side * extent + dir * (atTail ? extent : -extent);
    gl_Position = vec4((ndc + offsetPx * 2.0 / ScreenSize) * clip.w, clip.z, clip.w);

    edgePx = side * extent;
    along = UV0.y;
    heightAboveTop = (atTail ? tail.y : head.y) - top;
    tint = vec3(LightR, LightG, LightB) * (1.0 + 2.0 * Flash);
    alpha = Opacity * (0.55 + 0.45 * Color.g) * coverage * fade;
}
