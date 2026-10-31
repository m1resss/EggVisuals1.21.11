#version 330

uniform sampler2D Sampler0; // резкая копия кадра (full-res)
uniform sampler2D Sampler1; // depth мира (full-res)

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

// Попадание тапа в блюр-зону по радиальной дистанции (та же математика,
// что в ambience_depth_blur.fsh — менять синхронно).
float coverage(vec2 uv) {
    float depth = texture(Sampler1, uv).r;
    if (depth >= 0.99999) return 0.0; // небо не пишет глубину — оно фон, а не даль
    float ndcZ = depth * 2.0 - 1.0;
    float linearZ = 2.0 * Near * Far / (Far + Near - ndcZ * (Far - Near));
    vec2 ray = (uv * 2.0 - 1.0) / vec2(Proj00, Proj11);
    return smoothstep(BlurStart, BlurEnd, linearZ * length(vec3(ray, 1.0)));
}

// Premultiplied-тап: цвет умножен на вес зоны, вес уезжает в альфу.
vec4 maskedTap(vec2 uv) {
    float t = coverage(uv);
    return vec4(texture(Sampler0, uv).rgb * t, t);
}

// Маскированный Dual-Kawase DOWN для дистанс-блюра: резкий передний план (вес 0)
// не подмешивается в размытую даль — композит нормализует цвет обратно по альфе,
// и вокруг силуэтов не появляется ореол их цвета.
void main() {
    vec2 halfpixel = 0.5 / vec2(textureSize(Sampler0, 0));

    vec4 sum = maskedTap(texCoord0) * 4.0;
    sum += maskedTap(texCoord0 - halfpixel * blurOffset);
    sum += maskedTap(texCoord0 + halfpixel * blurOffset);
    sum += maskedTap(texCoord0 + vec2(halfpixel.x, -halfpixel.y) * blurOffset);
    sum += maskedTap(texCoord0 - vec2(halfpixel.x, -halfpixel.y) * blurOffset);

    fragColor = sum / 8.0;
}
