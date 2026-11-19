#version 330

layout(std140) uniform CausticUniforms {
    vec4 uParams;
    vec4 uAccentColor;
    vec4 uFade;
};

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return a + u.x * (b - a) + u.y * (c - a) + u.x * u.y * (a - b - c + d);
}

float evalCaustic(float angle, float r, float rShift, float aShift, float baseR, float time, float twist) {
    float curR = r + rShift;
    float curA = angle + aShift + (curR - baseR) * twist;

    vec2 dir = vec2(cos(curA), sin(curA));

    float edgeNoise = vnoise(dir * 3.5 + vec2(time * 0.16, -time * 0.13)) * 0.65
                    + vnoise(dir * 8.5 - vec2(time * 0.11,  time * 0.15)) * 0.35;
    float innerEdge = baseR + (edgeNoise - 0.5) * 0.052;

    float dist = max(0.0, curR - innerEdge);

    float macro = vnoise(dir * 4.5 + vec2(time * 0.18 + 1.2, -time * 0.15 + 0.8)) * 0.65
                + vnoise(dir * 10.5 - vec2(time * 0.12 + 0.5,  time * 0.14 + 1.3)) * 0.35;
    macro = pow(clamp(macro * 1.35 - 0.15, 0.0, 1.0), 2.5);

    float f1 = vnoise(dir * 28.0 + vec2(time * 0.10, -time * 0.08));
    float f2 = vnoise(dir * 58.0 - vec2(time * 0.07,  time * 0.09));
    float needles = pow(clamp(f1 * 0.6 + f2 * 0.4, 0.0, 1.0), 2.5);

    float rimHighlight = exp(-dist * 42.0) * 2.0;
    float falloffMod = 5.5 + (1.0 - macro) * 12.0;
    float rayFalloff = exp(-dist * falloffMod);
    float flareBody = macro * (0.45 + 0.95 * needles) * rayFalloff * 2.8;

    float total = rimHighlight * macro + flareBody;

    float innerMask = smoothstep(innerEdge, innerEdge + 0.015, curR);
    float outerMask = smoothstep(0.98, 0.80, curR);

    return total * innerMask * outerMask;
}

void main() {
    float r = length(texCoord);
    if (r > 1.0) {
        discard;
    }

    float angle = atan(texCoord.y, texCoord.x);

    float prog = clamp(uParams.x, 0.0, 1.0);
    float time = uParams.y;
    float disp = uParams.z;
    float brightness = uParams.w;
    float baseAlpha = uFade.x;
    float twist = uFade.y;

    float easedProg = 1.0 - pow(1.0 - prog, 3.0);
    float baseRadius = mix(0.15, 0.75, easedProg);

    float rShift = 0.012 * disp;
    float aShift = 0.032 * disp;

    float rVal = evalCaustic(angle, r, -rShift, -aShift, baseRadius, time, twist);
    float gVal = evalCaustic(angle, r,     0.0,     0.0, baseRadius, time, twist);
    float bVal = evalCaustic(angle, r,  rShift,  aShift, baseRadius, time, twist);

    float red = rVal * 1.15 + max(0.0, rVal - gVal) * 0.55;
    float green = gVal * 1.02;
    float blue = bVal * 1.20 + max(0.0, bVal - gVal) * 0.65;

    vec3 rainbowCol = vec3(red, green, blue);

    float luma = dot(rainbowCol, vec3(0.299, 0.587, 0.114));
    vec3 accentCol = uAccentColor.rgb * luma * 1.6;
    vec3 finalRgb = mix(rainbowCol, accentCol, clamp(uAccentColor.a, 0.0, 1.0));

    finalRgb *= brightness;

    finalRgb = vec3(1.0) - exp(-finalRgb * 1.3);

    float totalAlpha = max(finalRgb.r, max(finalRgb.g, finalRgb.b)) * baseAlpha * vertexColor.a;

    if (totalAlpha <= 0.002) {
        discard;
    }

    fragColor = vec4(finalRgb, totalAlpha);
}
