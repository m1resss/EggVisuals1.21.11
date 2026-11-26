#version 330

layout(std140) uniform CausticUniforms {
    vec4 uParams;
    vec4 uAccentColor;
    vec4 uFade;
};

in vec2 screenUv;
in vec4 tintColor;

out vec4 fragColor;

void main() {
    float time = uParams.x;
    float split = clamp(uParams.y, 0.0, 1.0);
    float intro = clamp(uParams.z, 0.0, 1.0);
    float aspect = max(uParams.w, 0.5);

    vec2 p = screenUv * 2.0 - 1.0;

    float open = split * split * (3.0 - 2.0 * split);
    float curveLead = 0.08 * sin((1.0 - p.y) * 1.5707963) * sin(open * 3.1415926);
    float gapHalf = open * 1.24 + curveLead;

    float absX = abs(p.x);
    float aa = max(fwidth(absX), 0.003);
    float curtainMask = smoothstep(gapHalf - aa, gapHalf + aa, absX);
    if (curtainMask <= 0.002) discard;

    float side = p.x >= 0.0 ? 1.0 : -1.0;
    float localX = max(0.0, absX - gapHalf);

    float aspectFix = clamp(1.38 / aspect, 0.56, 1.18);
    float yNorm = clamp((1.0 - p.y) * 0.5, 0.0, 1.0);
    float halfW = (0.14 + 0.54 * pow(yNorm, 1.85)) * aspectFix * mix(1.0, 0.74, open);

    float ribFreq = 12.5 * clamp(aspect / 1.45, 0.80, 1.35);
    float ribCoord = side * localX * ribFreq;
    float ribId = floor(ribCoord);
    float ribFract = fract(ribCoord);
    float fluteGloss = pow(sin(ribFract * 3.14159265), 1.30);
    float ribStep = (ribFract - 0.5) * 0.16;

    float refractX = ((ribId + 0.5) / ribFreq) * sign(ribId + 0.5) + (ribFract - 0.5) * 0.028;
    float u = clamp(abs(refractX) / max(halfW, 0.01), 0.0, 1.35);

    float env = 1.0 - smoothstep(0.56, 1.04, u);
    float halo = exp(-2.7 * max(0.0, u - 0.42) * max(0.0, u - 0.42)) * (1.0 - smoothstep(0.88, 1.32, u));
    float intensity = max(env, halo * 0.56);

    float t = time * 0.92;
    float waveA = sin(t * 1.15 - p.y * 1.30 + u * 2.35);
    float waveB = sin(t * 1.60 - p.y * 1.90 - u * 1.85 + 1.15);
    float coreMix = smoothstep(-0.78, 0.78, waveA);
    float edgeMix = smoothstep(-0.82, 0.82, waveB);

    vec3 royalBlue = vec3(0.11, 0.34, 1.00);
    vec3 iceWhite  = vec3(0.87, 0.92, 1.00);
    vec3 vermilion = vec3(1.00, 0.30, 0.06);

    vec3 colInner = mix(royalBlue, vermilion, coreMix);
    vec3 colOuter = mix(vermilion, royalBlue, edgeMix);
    vec3 baseGrad = mix(colInner, colOuter, smoothstep(0.18, 0.86, u));

    float whiteCore = (1.0 - smoothstep(0.0, 0.52, u)) * (0.55 + 0.45 * sin(t * 1.35 - p.y * 1.15));
    vec3 lightCol = mix(baseGrad, iceWhite, clamp(whiteCore * 0.84, 0.0, 0.88));

    float glassMod = 0.84 + 0.24 * fluteGloss + ribStep;
    vec3 col = lightCol * glassMod * intensity * intro;

    float seamFlash = exp(-localX * 34.0) * smoothstep(0.0, 0.14, split) * (1.0 - smoothstep(0.75, 1.0, split));
    col += mix(iceWhite, vermilion, 0.30) * seamFlash * 0.46;

    float endFade = 1.0 - smoothstep(0.88, 1.0, split);
    fragColor = vec4(col, curtainMask * endFade);
}
