#version 330

uniform sampler2D Sampler0;

in vec3 skyDir;
out vec4 fragColor;

void main() {
    fragColor = texelFetch(Sampler0, ivec2(gl_FragCoord.xy), 0);
}
