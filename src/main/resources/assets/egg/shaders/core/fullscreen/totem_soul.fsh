#version 330

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 tint;
out vec4 fragColor;

void main() {
    vec4 snapshot = texture(Sampler0, texCoord0);
    if (snapshot.a <= 0.001) discard;
    float luminance = dot(snapshot.rgb, vec3(0.2126, 0.7152, 0.0722));
    vec3 blue = tint.rgb * (0.48 + 0.52 * sqrt(luminance));
    vec3 highlights = mix(blue, vec3(0.89, 0.99, 1.0), smoothstep(0.45, 0.95, luminance) * 0.6);
    fragColor = vec4(highlights, snapshot.a * tint.a);
}
