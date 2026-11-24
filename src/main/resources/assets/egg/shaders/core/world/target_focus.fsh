#version 330

uniform sampler2D Sampler0;

in vec2 local;
in float strength;
out vec4 fragColor;

void main() {
    vec4 scene = texelFetch(Sampler0, ivec2(gl_FragCoord.xy), 0);
    float edge = 1.0 - smoothstep(0.26, 1.0, length(local * 2.0 - 1.0));
    vec3 rgb = scene.rgb;
    float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
    float chroma = max(rgb.r, max(rgb.g, rgb.b)) - min(rgb.r, min(rgb.g, rgb.b));
    float detail = clamp(fwidth(luma) * 2.7 + fwidth(chroma) * 1.4, 0.0, 1.0);
    float activity = 0.21 + 0.45 * smoothstep(0.03, 0.50, chroma) + 0.34 * detail;
    if (edge <= 0.0) discard;
    fragColor = vec4(mix(rgb, vec3(luma), strength * activity * edge), scene.a);
}
