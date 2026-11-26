#version 330

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

layout(std140) uniform KillModelUniforms {
    mat4 InvViewProj;
    vec4 VictimOrigin;
    vec4 BurnState;
    vec4 ModelDepth;
};

out vec4 fragColor;

float shockHash(vec3 p) {
    p = fract(p * vec3(0.1031, 0.11369, 0.13787));
    p += dot(p, p.yzx + 19.19);
    return fract((p.x + p.y) * p.z);
}

float shockNoise(vec3 p) {
    vec3 cell = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    vec4 lo = vec4(shockHash(cell), shockHash(cell + vec3(1, 0, 0)),
            shockHash(cell + vec3(0, 1, 0)), shockHash(cell + vec3(1, 1, 0)));
    vec4 hi = vec4(shockHash(cell + vec3(0, 0, 1)), shockHash(cell + vec3(1, 0, 1)),
            shockHash(cell + vec3(0, 1, 1)), shockHash(cell + vec3(1, 1, 1)));
    vec4 values = mix(lo, hi, f.z);
    return mix(mix(values.x, values.y, f.x), mix(values.z, values.w, f.x), f.y);
}

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    vec4 model = texelFetch(Sampler1, pixel, 0);
    float depth = texelFetch(Sampler2, pixel, 0).r;
    if (model.a <= 0.01 || depth >= 1.0) discard;

    vec2 ndc = gl_FragCoord.xy / vec2(textureSize(Sampler0, 0)) * 2.0 - 1.0;
    float ndcDepth = mix(depth * 2.0 - 1.0, depth, ModelDepth.x);
    vec4 point = InvViewProj * vec4(ndc, ndcDepth, 1.0);
    vec3 p = point.xyz / point.w - VictimOrigin.xyz;
    float heightFrac = p.y / max(VictimOrigin.w, 0.4);

    vec3 grainCell = floor(p * 20.0);
    float grain = shockHash(grainCell);
    float coarse = shockNoise(p * vec3(6.5, 8.5, 6.5));
    float gone = (1.14 - heightFrac) + (grain - 0.5) * 0.16 + (coarse - 0.5) * 0.22;
    if (BurnState.y > 0.0 && gone < BurnState.y) discard;

    float dL = texelFetch(Sampler2, pixel + ivec2(-2, 0), 0).r;
    float dR = texelFetch(Sampler2, pixel + ivec2(2, 0), 0).r;
    float dU = texelFetch(Sampler2, pixel + ivec2(0, 2), 0).r;
    float dD = texelFetch(Sampler2, pixel + ivec2(0, -2), 0).r;
    float edgeMask = clamp((max(max(abs(dL - depth), abs(dR - depth)), max(abs(dU - depth), abs(dD - depth))) * 600.0), 0.0, 1.0);
    if (dL >= 0.999 || dR >= 0.999 || dU >= 0.999 || dD >= 0.999) {
        edgeMask = 1.0;
    }

    gl_FragDepth = depth;

    float shockActive = clamp(BurnState.x, 0.0, 1.0);
    float age = BurnState.z;
    float flicker = step(0.5, fract(age * 16.0));
    float zapPulse = shockActive * (0.35 + 0.65 * flicker);

    vec3 charred = mix(vec3(0.035, 0.038, 0.048), vec3(0.11, 0.11, 0.13), coarse * 0.5 + grain * 0.5);
    vec3 electricEdge = mix(vec3(0.28, 0.80, 1.0), vec3(0.92, 0.98, 1.0), flicker);
    vec3 rgb = mix(charred, electricEdge, edgeMask * zapPulse * 0.85);

    if (BurnState.y > 0.0) {
        float dustEdge = 1.0 - smoothstep(0.01, 0.08, gone - BurnState.y);
        rgb = mix(rgb, vec3(0.32, 0.34, 0.38), dustEdge * 0.65);
    }

    float baseAlpha = mix(0.92, mix(0.56, 0.72, flicker), shockActive) * BurnState.w;
    fragColor = vec4(rgb, max(baseAlpha, edgeMask * zapPulse * 0.92 * BurnState.w));
}
