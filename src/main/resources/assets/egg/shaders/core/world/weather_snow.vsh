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
out vec2 flakeUv;
out float starShape;

// Снежинка: тот же статический буфер, что у дождя (Position — точка в единичном кубе, UV0 — угол квада,
// Color — случайные байты: R размер, G и B частоты качания, A класс скорости). Центр стоит в мире, как у капель,
// поверх падения снежинка качается и вращается; квад смотрит в камеру. Карта осадков — как в weather_rain.

const float HEIGHT_BIAS = 32768.0;
const int TYPE_SNOW = 2;
const float TAU = 6.2831853;
// Радиус снежинки не меньше этого числа пикселей: мельче она мерцает при движении камеры.
const float MIN_RADIUS_PX = 0.9;

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

float fogVisibility(float dist) {
    if (FogDensity <= 0.0) return 1.0;
    return exp(-min(max(dist - FogStart, 0.0) * FogDensity, 3.0));
}

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
    flakeUv = vec2(0.0);
    starShape = 0.0;
}

void main() {
    vec3 center = mod(Position * BoxSize - speedOrigin(int(Color.a * 2.999)), BoxSize) - 0.5 * BoxSize;
    // Частоты кратны 1/1024 Гц: время свёрнуто в 1024 с, и на стыке качание не дёргается.
    float swingA = TAU * floor(mix(96.0, 360.0, Color.b)) / 1024.0 * Time + (Position.x * 13.0 + Position.z * 7.0) * TAU;
    float swingB = TAU * floor(mix(64.0, 260.0, Color.g)) / 1024.0 * Time + (Position.y * 11.0 + Position.x * 5.0) * TAU;
    center += vec3(sin(swingA), 0.3 * sin(2.0 * swingB), cos(swingB)) * Sway * (0.45 + Color.r);

    vec4 texel = mapTexel(center.xz);
    float dist = length(center);
    float fade = shellFade(dist) * smoothstep(0.25, 0.8, dist) * fogVisibility(dist);
    if (columnType(texel) != TYPE_SNOW || center.y < columnTop(texel) || fade <= 0.001) {
        hide();
        return;
    }

    vec4 view = ModelViewMat * vec4(center, 1.0);
    if (view.z > -0.1) {
        hide();
        return;
    }
    float radius = ParticleSize * mix(0.55, 1.35, Color.r);
    float radiusPx = radius * ProjMat[1][1] * 0.5 * ScreenSize.y / -view.z;
    float grow = max(MIN_RADIUS_PX / max(radiusPx, 0.001), 1.0);
    starShape = smoothstep(4.0, 10.0, radiusPx);

    float spin = TAU * floor(mix(-160.0, 160.0, Color.b)) / 1024.0 * Time + Color.r * TAU;
    vec2 corner = UV0 * 2.0 - 1.0;
    flakeUv = mat2(cos(spin), sin(spin), -sin(spin), cos(spin)) * corner;
    view.xy += corner * radius * grow;
    gl_Position = ProjMat * view;

    tint = vec3(LightR, LightG, LightB) * (1.0 + 2.0 * Flash);
    alpha = Opacity * (0.6 + 0.4 * Color.g) * fade / (grow * grow);
}
