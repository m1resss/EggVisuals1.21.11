#version 330

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;

flat in float strength;
out vec4 fragColor;

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    vec4 scene = texelFetch(Sampler0, pixel, 0);
    float sceneDepth = texelFetch(Sampler1, pixel, 0).r;
    float targetMask = texelFetch(Sampler2, pixel, 0).a;
    float targetDepth = texelFetch(Sampler3, pixel, 0).r;
    float tolerance = max(0.000008, min(0.00028, (1.0 - targetDepth) * 0.014));
    float depthMatch = 1.0 - smoothstep(tolerance * 0.4, tolerance, abs(sceneDepth - targetDepth));
    float selected = targetDepth < 1.0 ? targetMask * depthMatch : 0.0;

    float luma = dot(scene.rgb, vec3(0.2126, 0.7152, 0.0722));
    vec3 muted = mix(vec3(luma), scene.rgb, 0.15) * 0.65;
    vec3 vivid = clamp((vec3(luma) + (scene.rgb - luma) * 1.58) * 1.06, 0.0, 1.0);
    fragColor = vec4(mix(scene.rgb, mix(muted, vivid, selected), strength), scene.a);
}
