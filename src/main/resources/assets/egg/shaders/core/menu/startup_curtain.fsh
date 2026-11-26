#version 330

uniform sampler2D Sampler0;

layout(std140) uniform CausticUniforms {
    vec4 uParams;
    vec4 uAccentColor;
    vec4 uFade;
};

in vec2 screenUv;
in vec4 tintColor;

out vec4 fragColor;

void main() {
    float split = clamp(uParams.y, 0.0, 1.0);
    float intro = clamp(uParams.z, 0.0, 1.0);
    float aspect = max(uParams.w, 0.5);

    vec2 p = screenUv * 2.0 - 1.0;

    float open = split * split * (3.0 - 2.0 * split);
    float curveLead = 0.075 * sin((1.0 - p.y) * 1.5707963) * sin(open * 3.1415926);
    float gapHalf = open * 1.18 + curveLead;

    float absX = abs(p.x);
    float aa = max(fwidth(absX), 0.003);
    float curtainMask = smoothstep(gapHalf - aa, gapHalf + aa, absX);
    if (curtainMask <= 0.002) discard;

    float side = p.x >= 0.0 ? 1.0 : -1.0;
    float localX = max(0.0, absX - gapHalf);
    float shiftedX = side * localX;

    float vidU = clamp(0.5 + shiftedX * 0.5, 0.0, 1.0);
    float vidV = clamp(1.0 - screenUv.y, 0.0, 1.0);

    vec3 col = texture(Sampler0, vec2(vidU, vidV)).rgb * intro;

    float seamFlash = exp(-localX * 36.0) * smoothstep(0.0, 0.14, split) * (1.0 - smoothstep(0.76, 1.0, split));
    col += vec3(0.95, 0.62, 0.36) * seamFlash * 0.24;

    float endFade = 1.0 - smoothstep(0.89, 1.0, split);
    fragColor = vec4(col, curtainMask * endFade);
}
