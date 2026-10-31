#version 330

uniform sampler2D Sampler0; // premultiplied-отражения (full-res: rgb*вес, вес в альфе)

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
    float Pad0;
};

in vec2 texCoord0;
flat in float blurOffset;

out vec4 fragColor;

// Композит отражений поверх кадра: premultiplied-слой ложится блендом
// (ONE, ONE_MINUS_SRC_ALPHA) из pipeline; сила из настройки масштабирует
// и цвет, и покрытие разом.
void main() {
    fragColor = texture(Sampler0, texCoord0) * ReflStrength;
}
