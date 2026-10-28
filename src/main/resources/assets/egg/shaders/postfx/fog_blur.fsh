#version 330 core

in vec2 vUv;
layout(location = 0) out vec4 fragColor;

uniform sampler2D u_SceneTexture;
uniform sampler2D u_DepthTexture;
uniform vec2 u_Resolution;
uniform mat4 u_InverseProjectionMatrix;
uniform float u_BlurStartDistance;
uniform float u_BlurEndDistance;
uniform float u_BlurStrength;

vec3 reconstructViewPosition(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 view = u_InverseProjectionMatrix * clip;
    float invW = abs(view.w) > 0.000001 ? 1.0 / view.w : 1.0;
    return view.xyz * invW;
}

void main() {
    vec4 baseColor = texture(u_SceneTexture, vUv);
    float depth = texture(u_DepthTexture, vUv).r;

    float blurFactor = 0.0;
    if (depth >= 0.999995) {
        blurFactor = 1.0;
    } else {
        vec3 viewPosition = reconstructViewPosition(vUv, depth);
        float distanceToCamera = length(viewPosition);
        if (distanceToCamera > u_BlurStartDistance) {
            float range = max(0.5, u_BlurEndDistance - u_BlurStartDistance);
            blurFactor = clamp((distanceToCamera - u_BlurStartDistance) / range, 0.0, 1.0);
            blurFactor = smoothstep(0.0, 1.0, blurFactor);
        }
    }

    blurFactor = clamp(blurFactor * u_BlurStrength, 0.0, 1.0);
    if (blurFactor <= 0.002) {
        fragColor = vec4(baseColor.rgb, 1.0);
        return;
    }

    vec2 texel = (1.0 / max(u_Resolution, vec2(1.0))) * (blurFactor * 4.0);
    vec4 accum = baseColor * 2.0;
    float totalWeight = 2.0;

    const vec2 offsets[12] = vec2[](
        vec2(-1.4, -1.4), vec2( 1.4, -1.4),
        vec2(-1.4,  1.4), vec2( 1.4,  1.4),
        vec2(-2.5,  0.0), vec2( 2.5,  0.0),
        vec2( 0.0, -2.5), vec2( 0.0,  2.5),
        vec2(-3.4, -3.4), vec2( 3.4, -3.4),
        vec2(-3.4,  3.4), vec2( 3.4,  3.4)
    );

    for (int i = 0; i < 8; i++) {
        accum += texture(u_SceneTexture, vUv + offsets[i] * texel);
        totalWeight += 1.0;
    }
    for (int i = 8; i < 12; i++) {
        accum += texture(u_SceneTexture, vUv + offsets[i] * texel) * 0.75;
        totalWeight += 0.75;
    }

    vec4 blurred = accum / totalWeight;
    fragColor = vec4(mix(baseColor.rgb, blurred.rgb, blurFactor), 1.0);
}
