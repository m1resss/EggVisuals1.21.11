#version 150

uniform sampler2D Sampler0;
uniform vec2 uVelocity;

in vec2 TexCoord;
out vec4 fragColor;

const int SAMPLES = 28;

float dither(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec2 vel = uVelocity;
    float jitter = (dither(gl_FragCoord.xy) - 0.5) / float(SAMPLES - 1);

    vec4 sum = vec4(0.0);
    for (int i = 0; i < SAMPLES; i++) {
        float t = float(i) / float(SAMPLES - 1) - 0.5 + jitter;
        vec2 uv = clamp(TexCoord + vel * t, 0.0, 1.0);
        sum += texture(Sampler0, uv);
    }

    fragColor = vec4(sum.rgb / float(SAMPLES), 1.0);
}
