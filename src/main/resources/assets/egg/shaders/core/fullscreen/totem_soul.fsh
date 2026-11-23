#version 330

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 tint;
out vec4 fragColor;

void main() {
    vec4 snapshot = texture(Sampler0, texCoord0);
    if (snapshot.a <= 0.001) discard;

    float luminance = clamp(dot(snapshot.rgb, vec3(0.2126, 0.7152, 0.0722)), 0.0, 1.0);
    vec3 shadedTint = tint.rgb * (0.19 + 0.76 * sqrt(luminance));
    vec3 sourceColor = clamp(snapshot.rgb * 1.16, 0.0, 1.0);
    vec3 blue = mix(sourceColor, shadedTint, 0.48);

    vec2 pixel = 1.0 / vec2(textureSize(Sampler0, 0));
    float neighbor = min(
        min(texture(Sampler0, texCoord0 + vec2(pixel.x, 0.0)).a,
            texture(Sampler0, texCoord0 - vec2(pixel.x, 0.0)).a),
        min(texture(Sampler0, texCoord0 + vec2(0.0, pixel.y)).a,
            texture(Sampler0, texCoord0 - vec2(0.0, pixel.y)).a)
    );
    float rim = max(0.0, snapshot.a - neighbor);
    fragColor = vec4(mix(blue, vec3(0.88, 1.0, 1.0), 0.28 * rim), snapshot.a * tint.a);
}
