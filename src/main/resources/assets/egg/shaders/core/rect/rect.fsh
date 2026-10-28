#version 150

in vec2 FragCoord;
in vec4 FragColor;

uniform vec2 uSize;
uniform vec4 uRadius;
uniform float uSmoothness;
uniform float uOutlineWidth;

out vec4 fragColor;

float roundedBoxSDF(vec2 p, vec2 b, vec4 r) {
    r.xy = (p.x > 0.0) ? r.xy : r.wz;
    float radius = (p.y > 0.0) ? r.x : r.y;
    radius = clamp(radius, 0.0, min(b.x, b.y));
    vec2 q = abs(p) - b + radius;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
}

void main() {
    vec2 halfSize = uSize * 0.5;
    vec2 p = (FragCoord - 0.5) * uSize;

    float dist = roundedBoxSDF(p, halfSize, uRadius);
    float smoothness = max(uSmoothness, 0.8);

    float alpha;
    if (uOutlineWidth > 0.001) {
        float innerDist = dist + uOutlineWidth;
        float outerAlpha = 1.0 - smoothstep(-smoothness, smoothness, dist);
        float innerAlpha = 1.0 - smoothstep(-smoothness, smoothness, innerDist);
        alpha = clamp(outerAlpha - innerAlpha, 0.0, 1.0);
    } else {
        alpha = 1.0 - smoothstep(-smoothness, smoothness, dist);
    }

    if (alpha <= 0.002) {
        discard;
    }

    fragColor = vec4(FragColor.rgb, FragColor.a * alpha);
}
