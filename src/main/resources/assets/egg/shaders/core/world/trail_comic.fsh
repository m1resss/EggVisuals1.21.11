#version 330

in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;

float lobe(vec2 p, vec2 center, float radius) {
    return length(p - center) - radius;
}

float merge(float a, float b, float radius) {
    float weight = max(radius - abs(a - b), 0.0) / radius;
    return min(a, b) - weight * weight * radius * 0.25;
}

float cloud(vec2 p, float style) {
    if (style == 1.0) p.x = -p.x;
    if (style == 2.0) p.y += p.x * 0.15;

    float shape = lobe(p, vec2(0.02, -0.06), 0.49);
    shape = merge(shape, lobe(p, vec2(-0.40, -0.17), 0.34), 0.105);
    shape = merge(shape, lobe(p, vec2(-0.30, 0.25), 0.30), 0.085);
    shape = merge(shape, lobe(p, vec2(0.07, 0.37), style == 2.0 ? 0.38 : 0.31), 0.11);
    shape = merge(shape, lobe(p, vec2(0.40, 0.19), 0.29), 0.095);
    shape = merge(shape, lobe(p, vec2(0.53, -0.16), style == 2.0 ? 0.27 : 0.35), 0.11);
    shape = merge(shape, lobe(p, vec2(0.22, -0.40), 0.27), 0.095);
    return shape;
}

void main() {
    float style = floor(texCoord.x / 2.0);
    vec2 p = (vec2(mod(texCoord.x, 2.0), texCoord.y) - 0.5) * 2.0;
    float facing = style == 1.0 ? -1.0 : 1.0;
    float shape = cloud(p, style);
    float small = lobe(p, vec2(-facing * 0.79, 0.38), 0.098);
    float speck = lobe(p, vec2(-facing * 0.54, 0.71), 0.051);
    float d = min(shape, min(small, speck));

    float aa = clamp(fwidth(d) * 0.77, 0.004, 0.067);
    float opacity = (1.0 - smoothstep(-aa, aa, d)) * vertexColor.a;
    if (opacity < 0.005) discard;

    vec3 ink = vec3(0.062, 0.051, 0.078);
    vec3 paper = mix(vec3(1.0, 0.956, 0.831), vertexColor.rgb, 0.115);
    vec3 shade = mix(vec3(0.60, 0.58, 0.56), vertexColor.rgb, 0.23);
    float inner = 1.0 - smoothstep(-0.055 - aa, -0.055 + aa, d);
    float shadow = smoothstep(-aa, aa, cloud(p - vec2(-0.075, 0.21), style) + 0.027);
    shadow *= 1.0 - smoothstep(-aa, aa, shape);

    vec2 dots = vec2(p.x * 7.7 + 0.25, p.y * 9.5);
    dots.x += mod(floor(dots.y), 2.0) * 0.5;
    float dotDist = length(fract(dots) - 0.5);
    float smoothDot = clamp(fwidth(dotDist) * 0.79, 0.035, 0.25);
    float printDots = (1.0 - smoothstep(0.255 - smoothDot, 0.255 + smoothDot, dotDist)) * shadow;
    vec3 fill = mix(paper, shade, shadow);
    fill = mix(fill, ink, printDots * 0.87);
    fragColor = vec4(mix(ink, fill, inner), opacity);
}
