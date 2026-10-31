#version 330

uniform sampler2D Sampler0;

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
    float GradeSaturation;
    float GradeContrast;
    float GradeBrightness;
    float GradeTemperature;
};

in vec2 texCoord0;
flat in float blurOffset;

out vec4 fragColor;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

void main() {
    vec3 color = texelFetch(Sampler0, ivec2(gl_FragCoord.xy), 0).rgb * GradeBrightness;

    vec3 whiteBalance = vec3(1.0 + 0.2 * GradeTemperature, 1.0, 1.0 - 0.2 * GradeTemperature);
    color *= whiteBalance / dot(whiteBalance, LUMA);

    color = (color - 0.5) * GradeContrast + 0.5;
    color = mix(vec3(dot(color, LUMA)), color, GradeSaturation);

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
