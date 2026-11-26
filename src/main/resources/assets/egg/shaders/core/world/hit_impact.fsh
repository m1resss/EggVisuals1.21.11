#version 330

in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    float bucket = floor((texCoord.x + 5.0) * 0.1);
    float prog = clamp(bucket / 200.0, 0.0, 1.0);
    vec2 p = vec2(texCoord.x - bucket * 10.0, texCoord.y);

    float r = length(p);
    if (r > 1.15) discard;

    float aa = max(fwidth(r), 0.008);
    float eased = 1.0 - pow(1.0 - prog, 2.5);
    float ringR = mix(0.10, 0.94, eased);
    float fade = 1.0 - smoothstep(0.14, 1.0, prog);

    float dRing = r - ringR;
    float front = 1.0 - smoothstep(0.015, 0.042 + aa, abs(dRing));
    float wake = (dRing < 0.0 ? exp(dRing * 7.5) * 0.52 : 0.0) * smoothstep(0.03, 0.14, r);

    float ang = atan(p.y, p.x);
    float spoke = pow(abs(cos(ang * 4.0)), 22.0);
    float raySpan = (1.0 - smoothstep(ringR - 0.24, ringR + 0.07, r)) * smoothstep(0.06, 0.18, r);
    float rays = spoke * raySpan * (1.0 - smoothstep(0.22, 0.88, prog));

    float centerFlash = exp(-r * r * 36.0) * (1.0 - smoothstep(0.0, 0.34, prog));

    float outA = clamp((front * 0.95 + wake + rays * 0.72 + centerFlash) * fade * vertexColor.a, 0.0, 1.0);
    if (outA < 0.004) discard;

    vec3 rgb = mix(vertexColor.rgb, vec3(1.0), clamp(front * 0.65 + centerFlash * 0.85, 0.0, 1.0));
    fragColor = vec4(rgb, outA);
}
