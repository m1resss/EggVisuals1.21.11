#version 330

uniform sampler2D Sampler0; // размытая premultiplied-пирамида (half-res, вес в альфе)
uniform sampler2D Sampler1; // резкая копия кадра (full-res)
uniform sampler2D Sampler2; // depth мира (до рендера руки)

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

// Дистанс-блюр Ambience: смешивает резкий кадр с размытым по радиальной дистанции
// пикселя. Depth -> линейная глубина (стандартная GL-проекция) -> длина луча через
// диагональ проекции, чтобы порог был сферическим (как туман), а не плоскостью.
// Математика дистанции дублируется в ambience_masked_down.fsh — менять синхронно.
void main() {
    fragColor = vec4(0.0, 0.0, 0.0, 1.0);
    vec3 sharp = texture(Sampler1, texCoord0).rgb;
    float depth = texture(Sampler2, texCoord0).r;

    // Небо рисуется без записи глубины: у его пикселей глубина осталась от клира и означает
    // не «даль», а отсутствие геометрии (иначе они читались бы как дальняя плоскость — 2048
    // блоков — и всё выше силуэта терраина мылилось бы при любой выставленной дистанции).
    if (depth >= 0.99999) {
        fragColor = vec4(sharp, 1.0);
        return;
    }

    float ndcZ = depth * 2.0 - 1.0;
    float linearZ = 2.0 * Near * Far / (Far + Near - ndcZ * (Far - Near));
    vec2 ray = (texCoord0 * 2.0 - 1.0) / vec2(Proj00, Proj11);
    float dist = linearZ * length(vec3(ray, 1.0));
    float t = smoothstep(BlurStart, BlurEnd, dist);

    // Premultiplied-нормализация: цвет дали восстанавливается только из тапов,
    // попавших в блюр-зону, — резкий передний план не оставляет вокруг себя ореола.
    // Кламп знаменателя ограничивает усиление там, где вес почти нулевой (там t≈0).
    vec4 blurred = texture(Sampler0, texCoord0);
    vec3 blurColor = blurred.rgb / max(blurred.a, 0.05);

    fragColor = vec4(mix(sharp, blurColor, t), 1.0);
}
