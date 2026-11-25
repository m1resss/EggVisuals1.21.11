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

float ashHash(vec3 p) {
    p = fract(p * vec3(0.1031, 0.11369, 0.13787));
    p += dot(p, p.yzx + 19.19);
    return fract((p.x + p.y) * p.z);
}

float ashNoise(vec3 p) {
    vec3 cell = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    vec4 lo = vec4(ashHash(cell), ashHash(cell + vec3(1, 0, 0)),
            ashHash(cell + vec3(0, 1, 0)), ashHash(cell + vec3(1, 1, 0)));
    vec4 hi = vec4(ashHash(cell + vec3(0, 0, 1)), ashHash(cell + vec3(1, 0, 1)),
            ashHash(cell + vec3(0, 1, 1)), ashHash(cell + vec3(1, 1, 1)));
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
    float height = p.y / max(VictimOrigin.w, 0.4);
    vec3 block = floor(p * 18.0);
    float grain = ashHash(block);
    float coarse = ashNoise(p * vec3(6.0, 8.0, 6.0));
    float burn = BurnState.x - height + (coarse - 0.5) * 0.15;
    float charred = smoothstep(-0.04, 0.105, burn);
    float hotFront = 1.0 - smoothstep(0.012, 0.090, abs(burn));
    float gone = (1.13 - height) + (grain - 0.5) * 0.15 + (coarse - 0.5) * 0.22;
    if (BurnState.y > 0.0 && gone < BurnState.y) discard;
    gl_FragDepth = depth;

    float luma = dot(model.rgb, vec3(0.299, 0.587, 0.114));
    vec3 coal = mix(vec3(0.030, 0.027, 0.023), vec3(0.135, 0.106, 0.083),
            coarse * 0.48 + grain * 0.26 + luma * 0.26);
    vec3 warped = p * 7.4;
    warped += vec3(ashNoise(p * 12.5), ashNoise(p * 12.5 + 5.7), ashNoise(p * 12.5 + 18.4)) * 1.45;
    float network = abs(ashNoise(warped) - 0.52);
    float rim = 1.0 - smoothstep(0.007, 0.044, network);
    float ember = 1.0 - smoothstep(0.004, 0.015, network);
    float heat = 0.40 + ashNoise(p * 4.1 - vec3(0.0, BurnState.z * 1.7, 0.0)) * 0.60;
    coal += vec3(0.58, 0.18, 0.022) * rim * heat;
    coal += vec3(0.70, 0.38, 0.07) * ember * heat;
    float glowingEdge = BurnState.y > 0.0 ? 1.0 - smoothstep(0.008, 0.085, gone - BurnState.y) : 0.0;
    vec3 rgb = mix(model.rgb, coal, charred);
    rgb = mix(rgb, vec3(1.0, 0.48, 0.13), hotFront * 0.46 + glowingEdge * 0.55);
    fragColor = vec4(rgb, BurnState.w);
}
