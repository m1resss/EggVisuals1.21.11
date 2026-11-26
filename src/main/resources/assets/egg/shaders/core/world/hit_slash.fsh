#version 330

in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    float bucket = floor((texCoord.x + 5.0) * 0.1);
    float prog = clamp(bucket / 200.0, 0.0, 1.0);
    vec2 p = vec2(texCoord.x - bucket * 10.0, texCoord.y);

    vec2 q = p - vec2(0.0, -0.58);
    float r = length(q);
    float R = 0.86;
    float arcHalf = 1.06;

    float ang = atan(q.x, q.y);
    float angNorm = ang / arcHalf;
    if (abs(angNorm) > 1.15 || abs(r - R) > 0.38) discard;

    float head = mix(-arcHalf * 0.82, arcHalf * 1.16, smoothstep(0.0, 0.28, prog));
    float tail = mix(-arcHalf * 1.16, arcHalf * 0.84, smoothstep(0.30, 1.0, prog));

    float sweepIn = 1.0 - smoothstep(head - 0.14, head + 0.04, ang);
    float sweepOut = smoothstep(tail - 0.06, tail + 0.20, ang);
    float tipTaper = 1.0 - smoothstep(0.88, 1.04, abs(angNorm));
    float spanMask = sweepIn * sweepOut * tipTaper;
    if (spanMask < 0.002) discard;

    float bladeShape = pow(max(0.0, 1.0 - angNorm * angNorm), 1.35);
    float fade = 1.0 - smoothstep(0.24, 1.0, prog);

    float dr = r - R;
    float asymDist = dr < 0.0 ? abs(dr) * 0.50 : abs(dr) * 1.35;
    float aa = max(fwidth(r), 0.008);
    float halfW = (0.014 + 0.072 * bladeShape) * (0.28 + 0.72 * fade);

    float core = (1.0 - smoothstep(halfW * 0.38, halfW * 0.38 + aa, abs(dr))) * spanMask;
    float glow = (1.0 - smoothstep(halfW * 0.85, halfW * 2.45 + aa, asymDist)) * spanMask;

    float tipDist = length(vec2(ang - head, (r - R) * 1.45));
    float tipGlow = exp(-tipDist * 9.5) * (1.0 - smoothstep(0.16, 0.50, prog)) * 0.85;

    float outA = clamp((glow * 0.82 + core * 0.98 + tipGlow) * fade * vertexColor.a, 0.0, 1.0);
    if (outA < 0.004) discard;

    vec3 rgb = mix(vertexColor.rgb, vec3(1.0), clamp(core * 0.78 + tipGlow * 0.65, 0.0, 1.0));
    fragColor = vec4(rgb, outA);
}
