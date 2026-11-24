#version 330

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

layout(std140) uniform IceFreezeUniforms {
    mat4 InvViewProj;
    vec4 VictimOrigin;
    vec4 FreezeState;
    vec4 FreezeDepth;
};

out vec4 fragColor;

float hash3(vec3 p) {
    p = fract(p * vec3(443.897, 441.423, 437.195));
    p += dot(p, p.yzx + 19.19);
    return fract((p.x + p.y) * p.z);
}

float noise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = hash3(i);
    float n100 = hash3(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash3(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash3(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash3(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash3(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash3(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash3(i + vec3(1.0, 1.0, 1.0));
    float nx00 = mix(n000, n100, f.x);
    float nx10 = mix(n010, n110, f.x);
    float nx01 = mix(n001, n101, f.x);
    float nx11 = mix(n011, n111, f.x);
    return mix(mix(nx00, nx10, f.y), mix(nx01, nx11, f.y), f.z);
}

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    vec4 ent = texelFetch(Sampler1, pixel, 0);
    float entDepth = texelFetch(Sampler2, pixel, 0).r;
    if (ent.a <= 0.01 || entDepth >= 1.0) {
        discard;
    }

    gl_FragDepth = entDepth;

    vec2 texSize = vec2(textureSize(Sampler0, 0));
    vec2 ndc = (gl_FragCoord.xy / texSize) * 2.0 - 1.0;
    float ndcZ = mix(entDepth * 2.0 - 1.0, entDepth, FreezeDepth.x);
    vec4 rel = InvViewProj * vec4(ndc, ndcZ, 1.0);
    vec3 relPos = rel.xyz / rel.w;
    vec3 localPos = relPos - VictimOrigin.xyz;

    float hNorm = clamp(localPos.y / max(VictimOrigin.w, 0.4), -0.1, 1.3);
    float edgeWave = (noise3(localPos * 6.5 + vec3(0.0, FreezeState.z * 0.35, 0.0)) - 0.5) * 0.075;
    float front = FreezeState.x;
    float frozenMask = 1.0 - smoothstep(front - 0.11, front + 0.02, hNorm + edgeWave);
    float frostRim = smoothstep(front - 0.17, front - 0.02, hNorm + edgeWave)
            * (1.0 - smoothstep(front - 0.01, front + 0.06, hNorm + edgeWave));

    vec4 scene = texelFetch(Sampler0, pixel, 0);
    float lum = dot(ent.rgb, vec3(0.299, 0.587, 0.114));
    float frostCloud = noise3(localPos * 4.4);
    vec3 iceBase = mix(vec3(0.46, 0.83, 0.97), vec3(0.84, 0.97, 1.0), clamp(lum * 0.82 + 0.30, 0.0, 1.0));
    iceBase = mix(iceBase, vec3(0.68, 0.93, 1.0), frostCloud * 0.45);

    float ridgeA = 1.0 - smoothstep(0.0, 0.032, abs(noise3(localPos * 5.2 + 1.7) - 0.5));
    float ridgeB = 1.0 - smoothstep(0.0, 0.024, abs(noise3(localPos * 9.8 + 5.1) - 0.5));
    float cracks = clamp(ridgeA * 0.65 + ridgeB * 0.45, 0.0, 1.0);
    vec3 crackedIce = mix(iceBase, vec3(0.16, 0.40, 0.58), cracks * 0.44);
    crackedIce = mix(crackedIce, vec3(0.95, 0.995, 1.0), cracks * FreezeState.y * 0.85);

    vec3 spCoord = localPos * 18.0 + vec3(0.01);
    vec3 spCell = floor(spCoord);
    vec3 spFrac = fract(spCoord) - 0.5;
    float speckle = step(0.84, hash3(spCell)) * (1.0 - smoothstep(0.16, 0.34, length(spFrac)));
    vec3 iceSurface = mix(crackedIce, vec3(0.98, 1.0, 1.0), speckle * 0.88);

    vec3 translucentIce = mix(scene.rgb, iceSurface, 0.78);
    vec3 finalRgb = mix(ent.rgb, translucentIce, frozenMask);
    finalRgb = mix(finalRgb, vec3(0.94, 0.995, 1.0), frostRim * 0.75);

    fragColor = vec4(finalRgb, clamp(FreezeState.w, 0.0, 1.0));
}
