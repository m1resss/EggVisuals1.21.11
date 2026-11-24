#version 330

uniform sampler2D Sampler0;

layout(std140) uniform LyricPaint {
    vec4 Paint;
};

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
    vec4 mask = texture(Sampler0, texCoord);
    float along = Paint.y > 0.0 ? texCoord.x : 1.0 - texCoord.x;
    float grain = noise(vec2(along * 40.0 + Paint.z, texCoord.y * 18.0));
    float bristles = noise(vec2(along * 8.0 + Paint.z, texCoord.y * 37.0));
    float front = mix(-0.13, 1.20, Paint.x);
    float edge = front - along - (texCoord.y - 0.5) * 0.17 - (bristles - 0.5) * 0.19;
    float coverage = smoothstep(-0.064, 0.028, edge + (grain - 0.5) * 0.09);
    float wetEdge = 1.0 - smoothstep(0.014, 0.15, abs(edge));
    float ink = mask.a * coverage * (1.0 - wetEdge * (0.10 + 0.26 * grain));
    float halo = (mask.r * 0.68 + mask.g * 0.74) * coverage * Paint.w * (1.0 + wetEdge * 0.5);
    vec3 pigment = vertexColor.rgb;
    vec3 paperLight = mix(pigment, vec3(0.99, 0.98, 1.0), 0.71);
    vec3 wetLight = mix(paperLight, pigment, wetEdge * 0.44 + (1.0 - texCoord.y) * 0.12);
    float alpha = ink + (1.0 - ink) * halo;
    vec3 color = mix(pigment, wetLight, ink / max(alpha, 0.001));
    fragColor = vec4(color, clamp(alpha * vertexColor.a, 0.0, 1.0));
}
