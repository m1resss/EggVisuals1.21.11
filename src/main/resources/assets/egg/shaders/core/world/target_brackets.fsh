#version 330

in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    if (texCoord.x > 6.5) {
        vec2 q = texCoord - vec2(8.0, 0.0);
        float aa = max(fwidth(length(q)), 0.012);
        float dRing = abs(length(q) - 0.50);
        float ringCov = (1.0 - smoothstep(0.009, 0.009 + aa, dRing)) * 0.16;
        fragColor = vec4(vertexColor.rgb * 0.92, ringCov * vertexColor.a);
        return;
    }

    if (texCoord.x > 2.5) {
        vec2 q = texCoord - vec2(4.0, 0.0);
        float aa = max(fwidth(length(q)), 0.012);
        float dWave = abs(length(q) - 0.86);
        float cov = 1.0 - smoothstep(0.016, 0.016 + aa, dWave);
        fragColor = vec4(mix(vertexColor.rgb, vec3(1.0), 0.28), cov * vertexColor.a);
        return;
    }

    vec3 rimCol = vec3(0.03, 0.04, 0.06);

    if (texCoord.x > 1.5) {
        float v = clamp(texCoord.y, 0.0, 1.0);
        float aaV = max(fwidth(v), 0.035);
        float edgeDist = min(v, 1.0 - v);
        float body = smoothstep(0.08, 0.24 + aaV, edgeDist);
        float highlight = smoothstep(0.32, 0.92, v) * 0.26;
        vec3 coreCol = mix(vertexColor.rgb, vec3(1.0), highlight);
        vec3 col = mix(rimCol, coreCol, body);
        fragColor = vec4(col, vertexColor.a);
        return;
    }

    float lat = clamp(abs(texCoord.x), 0.0, 1.0);
    float aaLat = max(fwidth(lat), 0.04);
    float body = 1.0 - smoothstep(0.68 - aaLat, 0.86, lat);
    float topMask = clamp(texCoord.y, 0.0, 1.0);
    float centerGlow = (1.0 - lat * lat) * (0.26 * topMask + 0.06);
    float shade = mix(0.76, 1.0, topMask);
    vec3 coreCol = mix(vertexColor.rgb * shade, vec3(1.0), centerGlow);
    vec3 col = mix(rimCol, coreCol, body);
    fragColor = vec4(col, vertexColor.a);
}
