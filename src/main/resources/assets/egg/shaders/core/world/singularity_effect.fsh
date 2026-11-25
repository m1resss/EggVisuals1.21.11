#version 330

in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    vec2 uv = vec2(mod(texCoord.x, 2.0), texCoord.y);
    float mode = floor(texCoord.x * 0.5);

    if (mode < 0.5) {
        vec2 p = (uv - 0.5) * 2.0;
        float r = length(p);
        if (r > 1.0) discard;

        float horizon = 1.0 - smoothstep(0.24, 0.34, r);
        float ring = exp(-pow((r - 0.36) * 8.5, 2.0));
        float darkHalo = (1.0 - smoothstep(0.32, 0.96, r)) * 0.82;
        float shimmer = 0.82 + 0.18 * sin(atan(p.y, p.x) * 5.0 + r * 9.0);

        vec3 coreDark = vec3(0.015, 0.010, 0.035);
        vec3 rimGlow = mix(vertexColor.rgb, vec3(0.94, 0.97, 1.0), smoothstep(0.55, 1.0, ring));
        vec3 col = mix(coreDark * darkHalo, rimGlow, clamp(ring * shimmer, 0.0, 1.0));
        col = mix(col, coreDark, horizon * 0.96);

        float alpha = clamp(max(max(horizon, darkHalo * 0.78), ring * 1.15), 0.0, 1.0) * vertexColor.a;
        fragColor = vec4(col, alpha);
    } else if (mode < 1.5) {
        float across = abs(uv.y - 0.5) * 2.0;
        float band = exp(-across * across * 6.5) * (1.0 - smoothstep(0.62, 1.0, across));
        float streak = 0.72 + 0.28 * sin(uv.x * 18.84955);
        vec3 col = mix(vertexColor.rgb, vec3(0.96, 0.98, 1.0), band * band * 0.65);
        fragColor = vec4(col, band * streak * vertexColor.a);
    } else {
        vec2 p = (uv - 0.5) * 2.0;
        float r = length(p);
        if (r > 1.0) discard;
        float orb = exp(-5.4 * r * r) * (1.0 - smoothstep(0.52, 1.0, r));
        float crossH = exp(-abs(p.y) * 14.0) * exp(-abs(p.x) * 3.0);
        float crossV = exp(-abs(p.x) * 14.0) * exp(-abs(p.y) * 3.0);
        float star = (crossH + crossV) * 0.65 * (1.0 - smoothstep(0.6, 1.0, r));
        float energy = clamp(orb + star, 0.0, 1.0);
        vec3 col = mix(vertexColor.rgb, vec3(1.0), clamp(orb * orb + star * 0.6, 0.0, 1.0));
        fragColor = vec4(col, energy * vertexColor.a);
    }

    if (fragColor.a < 0.004) discard;
}
