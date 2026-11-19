#version 330

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

layout(std140) uniform EggAccumBlur {
    vec4 Params;
};

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec3 current = texture(Sampler0, texCoord0).rgb;
    vec3 prev = texture(Sampler1, texCoord0).rgb;
    fragColor = vec4(mix(current, prev, Params.x), 1.0);
}
