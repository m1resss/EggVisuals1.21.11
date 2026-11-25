#version 330

in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    if (texCoord.x > 6.0) {
        float v = clamp(texCoord.y, 0.0, 1.0);
        float aaV = max(fwidth(v), 0.04);
        float edgeDist = min(v, 1.0 - v);
        float body = smoothstep(0.0, 0.20 + aaV, edgeDist);
        float highlight = smoothstep(0.30, 0.92, v) * 0.30;
        vec3 rimCol = vec3(0.03, 0.04, 0.06);
        vec3 coreCol = mix(vertexColor.rgb, vec3(1.0), highlight);
        vec3 col = mix(rimCol, coreCol, body);
        fragColor = vec4(col, vertexColor.a);
        return;
    }

    float aa = max(fwidth(length(texCoord)), 0.012);
    if (texCoord.x > 2.0) {
        vec2 q = texCoord - vec2(4.0, 0.0);
        float dWave = abs(length(q) - 0.86);
        float cov = 1.0 - smoothstep(0.016, 0.016 + aa, dWave);
        fragColor = vec4(mix(vertexColor.rgb, vec3(1.0), 0.28), cov * vertexColor.a);
        return;
    }

    vec2 p = texCoord;
    vec2 k = abs(p);
    float b = 0.64;
    float cr = 0.17;
    float arm = 0.27;
    float c = b - cr;

    float dH = length(k - vec2(clamp(k.x, arm, c), b));
    float dV = length(k - vec2(b, clamp(k.y, arm, c)));
    float dArc = (k.x > c && k.y > c) ? abs(length(k - vec2(c)) - cr) : 10.0;
    float dBracket = min(dArc, min(dH, dV));

    float rimCov = 1.0 - smoothstep(0.056, 0.056 + aa, dBracket);
    float coreCov = 1.0 - smoothstep(0.030, 0.030 + aa, dBracket);
    float hotCore = 1.0 - smoothstep(0.0, 0.020, dBracket);

    float dRing = abs(length(p) - 0.50);
    float ringCov = (1.0 - smoothstep(0.009, 0.009 + aa, dRing)) * 0.14;

    float bracketAlpha = max(coreCov, rimCov * 0.54);
    float outA = max(bracketAlpha, ringCov) * vertexColor.a;

    vec3 rimRgb = vec3(0.03, 0.04, 0.06);
    vec3 strokeRgb = mix(vertexColor.rgb, vec3(1.0), 0.36 * hotCore);
    vec3 bracketRgb = mix(rimRgb, strokeRgb, clamp(coreCov / max(bracketAlpha, 0.001), 0.0, 1.0));
    vec3 finalRgb = mix(vertexColor.rgb * 0.90, bracketRgb, clamp(rimCov * 1.4, 0.0, 1.0));

    fragColor = vec4(finalRgb, outA);
}
