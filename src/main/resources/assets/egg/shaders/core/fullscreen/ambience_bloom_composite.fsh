#version 330

uniform sampler2D Sampler0; // накопленное свечение (half-res пирамида)

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
    float Pad0;
};

in vec2 texCoord0;
flat in float blurOffset;

out vec4 fragColor;

// Композит блума: интенсивность применяется здесь (пирамида хранит нормированные
// октавы — градиенты не плющатся в RGBA8), 1-exp(-x) мягко насыщает вместо жёсткого
// клипа в белое. Screen-бленд (ONE_MINUS_DST_COLOR, ONE) из pipeline досветляет кадр,
// не пересвечивая уже яркие места. 1.5 — калибровка яркости: тонемап и screen-бленд
// съедают часть энергии свечения, компенсируем до ощутимого глоу.
void main() {
    vec3 glow = texture(Sampler0, texCoord0).rgb * (BloomIntensity * 1.5);
    glow = 1.0 - exp(-glow);
    fragColor = vec4(glow, 1.0);
}
