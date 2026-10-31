#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;
flat in vec2 atlasSize;
flat in float distanceRange;

out vec4 fragColor;

float median(float red, float green, float blue) {
    return max(min(red, green), min(max(red, green), blue));
}

void main() {
    vec4 texColor = texture(Sampler0, texCoord0);

    // Экранных пикселей на единицу дистанс-поля (Chlumsky): среднее по осям через fwidth.
    vec2 unitRange = vec2(distanceRange) / atlasSize;
    vec2 screenTexSize = vec2(1.0) / max(fwidth(texCoord0), vec2(1.0e-6));
    float screenPxRange = clamp(0.5 * dot(unitRange, screenTexSize), 1.0, 1.0e5);

    // Минификация (texel атласа на экранный пиксель): median(RGB) при уменьшении смешивает
    // каналы (грязь в углах глифов) — плавно переходим на true-SDF из альфы mtsdf-атласа.
    float texelsPerPixel = distanceRange / screenPxRange;
    float sdfMix = clamp((texelsPerPixel - 1.0) * 0.5, 0.0, 1.0);

    float msdfDist = median(texColor.r, texColor.g, texColor.b);
    float signedDist = mix(msdfDist, texColor.a, sdfMix) - 0.5;

    // Линейный фильтр шириной ровно в 1 экранный пиксель — эталонное MSDF-сглаживание.
    float alpha = clamp(signedDist * screenPxRange + 0.5, 0.0, 1.0);
    if (alpha <= 0.0005) discard;

    vec4 color = vertexColor * ColorModulator;
    fragColor = vec4(color.rgb, color.a * alpha);
}
