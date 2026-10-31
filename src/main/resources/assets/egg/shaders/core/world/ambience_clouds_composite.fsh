#version 330

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

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
};

out vec4 fragColor;

// Композит облаков: половинный слой (Sampler0) растягивается на кадр билинейно, а пиксели рельефа,
// стоящего ближе входа луча в облачный слой, отсекаются по полному depth (Sampler1), чтобы облако
// не наползало ореолом на силуэты гор и деревьев.

float linearZ(float depth) {
    float ndcZ = depth * 2.0 - 1.0;
    return 2.0 * Near * Far / (Far + Near - ndcZ * (Far - Near));
}

vec3 pixelViewDirection(vec2 nativeUv) {
    float ndcY = mix(nativeUv.y * 2.0 - 1.0, 1.0 - nativeUv.y * 2.0, ScreenOriginTop);
    vec2 ndc = vec2(nativeUv.x * 2.0 - 1.0, ndcY);
    return normalize(vec3(ndc / vec2(Proj00, Proj11), -1.0));
}

// Дистанция входа луча в облачный слой с разбросом высоты облаков: камера внутри слоя - 0, луч мимо слоя - бесконечность.
float layerEntry(float directionY) {
    float bottom = CloudBottom - CloudLift;
    float top = CloudBottom + CloudThickness + CloudLift;
    if (bottom <= 0.0 && top >= 0.0) return 0.0;
    float entry = (bottom > 0.0 ? bottom : top) / directionY;
    return entry > 0.0 ? entry : 1.0e9;
}

void main() {
    fragColor = vec4(0.0);

    ivec2 frameSize = textureSize(Sampler1, 0);
    vec2 uv = gl_FragCoord.xy / vec2(frameSize);
    vec4 clouds = texture(Sampler0, uv);
    if (clouds.a <= 0.002) return;

    float depth = texelFetch(Sampler1, ivec2(gl_FragCoord.xy), 0).r;
    if (depth < 0.99999) {
        vec3 viewDirection = pixelViewDirection(uv);
        float sceneDistance = linearZ(depth) / max(-viewDirection.z, 0.0001);
        float directionY = dot(viewDirection, vec3(UpViewX, UpViewY, UpViewZ));
        if (sceneDistance < layerEntry(directionY)) return;
    }
    fragColor = clouds;
}
