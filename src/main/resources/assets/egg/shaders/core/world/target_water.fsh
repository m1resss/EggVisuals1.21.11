#version 330

in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 cell = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(cell), hash(cell + vec2(1, 0)), f.x),
               mix(hash(cell + vec2(0, 1)), hash(cell + vec2(1, 1)), f.x), f.y);
}

void main() {
    float angle = texCoord.x * 6.2831853;
    float v = texCoord.y;
    vec2 flow = vec2(cos(angle), sin(angle));
    float whirl = noise(flow * 8.1 + vec2(v * 8.0, v * 3.2));
    float grain = noise(flow * 31.0 + vec2(v * 18.0, -v * 24.0));
    float wave = sin(angle * 9.0 + v * 12.0 + whirl * 3.9);
    float ripple = sin(angle * 23.0 - v * 20.0 + whirl * 5.4);
    float reflection = pow(max(0.0, wave * 0.68 + ripple * 0.32), 6.0);
    float crest = 0.76 + (whirl - 0.5) * 0.22 + wave * 0.04;
    float rim = 1.0 - smoothstep(0.018, 0.105, abs(v - crest));
    float flecks = smoothstep(0.48, 0.81, grain);
    float foam = clamp(rim * (0.18 + 0.95 * flecks) + reflection * smoothstep(0.33, 0.8, v) * flecks * 0.45, 0.0, 1.0);
    vec3 water = mix(vec3(0.018, 0.31, 0.49), vec3(0.18, 0.68, 0.82), 0.38 + 0.28 * wave + 0.20 * reflection);
    vec3 color = mix(water, vec3(0.91, 0.98, 1.0), foam);
    float edges = smoothstep(0.0, 0.19, v) * (1.0 - smoothstep(0.91, 1.0, v));
    float alpha = edges * vertexColor.a * (0.56 + 0.23 * reflection + 0.68 * foam);
    fragColor = vec4(color, clamp(alpha, 0.0, 1.0));
}
