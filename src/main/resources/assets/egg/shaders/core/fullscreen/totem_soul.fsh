#version 330

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 tint;
out vec4 fragColor;

void main() {
    vec4 snapshot = texture(Sampler0, texCoord0);
    if (snapshot.a <= 0.001) discard;

    float luminance = clamp(dot(snapshot.rgb, vec3(0.2126, 0.7152, 0.0722)), 0.0, 1.0);
    vec3 blue = mix(tint.rgb, vec3(0.93, 1.0, 1.0), 0.12 + 0.53 * sqrt(luminance));

    vec2 pixel = 1.0 / vec2(textureSize(Sampler0, 0));
    float neighbor = min(
        min(texture(Sampler0, texCoord0 + vec2(pixel.x, 0.0)).a,
            texture(Sampler0, texCoord0 - vec2(pixel.x, 0.0)).a),
        min(texture(Sampler0, texCoord0 + vec2(0.0, pixel.y)).a,
            texture(Sampler0, texCoord0 - vec2(0.0, pixel.y)).a)
    );
    float rim = max(0.0, snapshot.a - neighbor);
    fragColor = vec4(mix(blue, vec3(0.88, 1.0, 1.0), 0.65 * rim), snapshot.a * tint.a);
}
