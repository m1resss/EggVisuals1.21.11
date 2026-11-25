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

float hash3(vec3 p) {
    p = fract(p * vec3(0.1031, 0.11369, 0.13787));
    p += dot(p, p.yzx + 19.19);
    return fract((p.x + p.y) * p.z);
}

float noise3(vec3 p) {
    vec3 cell = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    vec4 lo = vec4(hash3(cell), hash3(cell + vec3(1, 0, 0)),
            hash3(cell + vec3(0, 1, 0)), hash3(cell + vec3(1, 1, 0)));
    vec4 hi = vec4(hash3(cell + vec3(0, 0, 1)), hash3(cell + vec3(1, 0, 1)),
            hash3(cell + vec3(0, 1, 1)), hash3(cell + vec3(1, 1, 1)));
    vec4 v = mix(lo, hi, f.z);
    return mix(mix(v.x, v.y, f.x), mix(v.z, v.w, f.x), f.y);
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
    vec3 center = vec3(0.0, max(VictimOrigin.w, 0.5) * 0.52, 0.0);
    vec3 rel = p - center;
    float radial = length(rel);

    gl_FragDepth = depth;

    float collapse = clamp(BurnState.x, 0.0, 1.0);
    float flash = clamp(BurnState.y, 0.0, 1.0);
    float time = BurnState.z;

    float swirlAngle = time * 6.0 + radial * 5.0;
    vec3 swirlCoord = vec3(
        rel.x * cos(swirlAngle) - rel.z * sin(swirlAngle),
        rel.y * 2.2,
        rel.x * sin(swirlAngle) + rel.z * cos(swirlAngle)
    ) * 4.5;
    float vein = 1.0 - smoothstep(0.02, 0.14, abs(noise3(swirlCoord) - 0.5));

    float luma = dot(model.rgb, vec3(0.299, 0.587, 0.114));
    vec3 obsidian = vec3(0.024, 0.016, 0.048) + vec3(0.06, 0.03, 0.12) * luma;
    vec3 violetRim = mix(vec3(0.45, 0.24, 0.96), vec3(0.46, 0.85, 1.0), vein);
    vec3 voidColor = mix(model.rgb, obsidian, smoothstep(0.08, 0.68, collapse));
    voidColor += violetRim * vein * (0.45 + 0.85 * collapse);

    float coreGlow = smoothstep(0.45, 0.03, radial) * smoothstep(0.35, 1.0, collapse);
    vec3 hotCore = mix(vec3(0.62, 0.38, 1.0), vec3(0.95, 0.98, 1.0), flash);
    vec3 finalRgb = mix(voidColor, hotCore, clamp(coreGlow * 0.88 + flash * 0.72, 0.0, 1.0));

    fragColor = vec4(finalRgb, model.a * BurnState.w);
}
