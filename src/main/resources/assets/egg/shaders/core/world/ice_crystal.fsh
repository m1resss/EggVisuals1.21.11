#version 330

in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;

float hash2(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash2(i), hash2(i + vec2(1.0, 0.0)), f.x),
               mix(hash2(i + vec2(0.0, 1.0)), hash2(i + vec2(1.0, 1.0)), f.x), f.y);
}

void main() {
    float edgeDist = min(min(texCoord.x, 1.0 - texCoord.x), min(texCoord.y, 1.0 - texCoord.y));
    float rim = 1.0 - smoothstep(0.0, 0.085, edgeDist);
    float vGrad = smoothstep(0.0, 1.0, texCoord.y);
    float cloud = noise2(texCoord * vec2(6.0, 11.0));

    vec2 spCoord = texCoord * vec2(9.0, 18.0);
    vec2 spCell = floor(spCoord);
    vec2 spFrac = fract(spCoord) - 0.5;
    float speckle = step(0.83, hash2(spCell)) * (1.0 - smoothstep(0.14, 0.34, length(spFrac)));

    vec3 baseIce = mix(vertexColor.rgb, vec3(0.86, 0.97, 1.0), vGrad * 0.38 + cloud * 0.16);
    vec3 litIce = mix(baseIce, vec3(0.96, 1.0, 1.0), rim * 0.55 + speckle * 0.72);
    float alpha = clamp(vertexColor.a * (0.76 + 0.18 * rim + 0.12 * speckle), 0.0, 1.0);
    fragColor = vec4(litIce, alpha);
}
