#version 330

layout(std140) uniform MenuBgData {
    vec4 Params;
};

in vec2 screenUv;
in vec4 tintColor;

out vec4 fragColor;

void main() {
    vec2 fbSize = max(Params.xy, vec2(1.0));
    float aspect = fbSize.x / fbSize.y;
    vec2 uv = gl_FragCoord.xy / fbSize;

    vec3 topLeft = vec3(0.028, 0.031, 0.043);
    vec3 botRight = vec3(0.043, 0.047, 0.064);
    vec3 base = mix(botRight, topLeft, clamp(uv.y * 0.65 + (1.0 - uv.x) * 0.35, 0.0, 1.0));

    vec2 eggCenter = vec2(Params.z, 0.52);
    vec2 dEgg = (uv - eggCenter) * vec2(aspect, 1.0);
    float glowDist = length(dEgg);
    float eggAura = exp(-glowDist * glowDist * 4.4) * 0.068;
    base += vec3(0.62, 0.72, 0.88) * eggAura;

    float stepPx = max(14.0, fbSize.y / 60.0);
    vec2 cell = mod(gl_FragCoord.xy, stepPx) - stepPx * 0.5;
    float dotMask = 1.0 - smoothstep(0.65, 1.45, length(cell));
    float dotFade = exp(-glowDist * glowDist * 2.2) * 0.72 + 0.28;
    base += vec3(0.76, 0.82, 0.94) * (dotMask * 0.055 * dotFade);

    float edgeDist = length((uv - 0.5) * vec2(1.15, 1.0));
    float vignette = smoothstep(0.35, 1.05, edgeDist);
    base *= 1.0 - vignette * 0.30;

    fragColor = vec4(base, Params.w * tintColor.a);
}
