#version 150

in vec2 pixelCoord;
in vec2 texCoord;
in vec2 rectSize;
in vec4 cornerRadii;
in vec4 fragColors[4];

out vec4 fragColor;

uniform sampler2D Sampler0;

float roundedDistance(vec2 center, vec2 halfSize, vec4 radii) {
    vec2 corners = center.x > 0.0 ? radii.yz : radii.xw;
    float radius = min(center.y > 0.0 ? corners.y : corners.x, min(halfSize.x, halfSize.y));
    vec2 q = abs(center) - halfSize + radius;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
}

float noise(vec2 pixel) {
    vec3 seed = fract(vec3(pixel.xyx) * 0.1031);
    seed += dot(seed, seed.yzx + 33.33);
    return fract((seed.x + seed.y) * seed.z);
}

void main() {
    vec2 halfSize = rectSize * 0.5;
    float distance = roundedDistance(pixelCoord - halfSize, halfSize, cornerRadii);
    float coverage = clamp(0.5 - distance / max(fwidth(distance), 0.0001), 0.0, 1.0);
    if (coverage <= 0.0) discard;

    vec3 scene = texture(Sampler0, clamp(texCoord, vec2(0.0), vec2(1.0))).rgb;
    float luminance = dot(scene, vec3(0.2126, 0.7152, 0.0722));
    vec4 material = fragColors[0];
    vec3 tint = mix(vec3(luminance), scene, 0.28);
    float grain = (noise(floor(gl_FragCoord.xy)) - 0.5) * 0.052;
    vec3 matte = (tint + vec3(0.098, 0.102, 0.112)) * material.r + grain;
    fragColor = vec4(clamp(matte, 0.0, 1.0), material.a * coverage);
}
