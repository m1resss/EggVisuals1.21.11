#version 330

in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    bool dashed = texCoord.x > 2.0;
    vec2 p = dashed ? (texCoord - vec2(4.0, 0.0)) : texCoord;

    float r = length(p);
    float aa = max(fwidth(r), 0.008);

    float dash = 1.0;
    if (dashed) {
        float ang = atan(p.y, p.x);
        float wave = sin(ang * 24.0);
        dash = smoothstep(-0.22, 0.22, wave);
    }

    float d = abs(r - 0.90);
    float rim = (1.0 - smoothstep(0.021, 0.021 + aa, d)) * dash;
    float core = (1.0 - smoothstep(0.011, 0.011 + aa, d)) * dash;
    float fill = smoothstep(0.55, 0.90, r) * (1.0 - smoothstep(0.895, 0.90 + aa, r)) * 0.11;

    float ringAlpha = max(core, rim * 0.54);
    float outA = max(ringAlpha, fill) * vertexColor.a;
    if (outA < 0.004) discard;

    vec3 darkRim = vec3(0.03, 0.04, 0.06);
    vec3 brightCore = mix(vertexColor.rgb, vec3(1.0), 0.20 * core);
    vec3 ringCol = mix(darkRim, brightCore, clamp(core / max(ringAlpha, 0.001), 0.0, 1.0));
    vec3 finalRgb = mix(vertexColor.rgb * 0.92, ringCol, clamp(rim * 1.4, 0.0, 1.0));

    fragColor = vec4(finalRgb, outA);
}
