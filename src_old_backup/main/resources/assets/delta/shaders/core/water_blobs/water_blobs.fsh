#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

uniform mat4 InvViewProj;
uniform vec4 Blob0;
uniform vec4 Blob1;
uniform vec4 Blob2;
uniform vec4 Blob3;
uniform vec4 Blob4;
uniform vec4 Blob5;
uniform vec4 Blob6;
uniform vec4 Blob7;

uniform float Count;
uniform float Smooth;
uniform float Refract;
uniform float Density;
uniform float Steps;
uniform vec3 Tint;

in vec2 TexCoord;
out vec4 fragColor;

float smoothUnion(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / max(k, 0.001), 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

vec4 getBlob(int i) {
    if (i == 0) return Blob0;
    if (i == 1) return Blob1;
    if (i == 2) return Blob2;
    if (i == 3) return Blob3;
    if (i == 4) return Blob4;
    if (i == 5) return Blob5;
    if (i == 6) return Blob6;
    return Blob7;
}

float map(vec3 p) {
    float d = 10000.0;
    bool hasAny = false;
    int c = int(clamp(Count, 1.0, 8.0));
    for (int i = 0; i < 8; i++) {
        if (i >= c) break;
        vec4 b = getBlob(i);
        if (b.w > 0.005) {
            float dist = length(p - b.xyz) - b.w;
            if (!hasAny) {
                d = dist;
                hasAny = true;
            } else {
                d = smoothUnion(d, dist, max(Smooth, 0.01));
            }
        }
    }
    return hasAny ? d : 10000.0;
}

vec3 surfaceNormal(vec3 p) {
    vec2 e = vec2(0.015, 0.0);
    return normalize(vec3(
        map(p + e.xyy) - map(p - e.xyy),
        map(p + e.yxy) - map(p - e.yxy),
        map(p + e.yyx) - map(p - e.yyx)
    ));
}

void main() {
    vec2 uv = TexCoord;
    vec3 background = texture(Sampler0, uv).rgb;
    vec2 ndc = uv * 2.0 - 1.0;

    vec4 farPoint = InvViewProj * vec4(ndc, 1.0, 1.0);
    vec3 dir = normalize(farPoint.xyz / farPoint.w);

    float sceneDepth = texture(Sampler1, uv).r;
    float sceneDist = 1000.0;
    if (sceneDepth < 0.9999) {
        vec4 scenePoint = InvViewProj * vec4(ndc, sceneDepth * 2.0 - 1.0, 1.0);
        sceneDist = length(scenePoint.xyz / scenePoint.w);
    }

    float travelled = 0.0;
    float hit = -1.0;
    int maxSteps = int(clamp(Steps, 16.0, 96.0));
    for (int i = 0; i < 96; i++) {
        if (i >= maxSteps) break;

        float dist = map(dir * travelled);
        if (dist < 0.012) {
            hit = travelled;
            break;
        }

        travelled += max(dist, 0.02);
        if (travelled > 48.0 || travelled > sceneDist) break;
    }

    if (hit < 0.0 || hit > sceneDist) {
        fragColor = vec4(background, 1.0);
        return;
    }

    vec3 point = dir * hit;
    vec3 normal = surfaceNormal(point);

    float back = hit;
    for (int i = 0; i < 24; i++) {
        back += 0.12;
        if (map(dir * back) > 0.0) break;
    }
    float thickness = back - hit;

    vec2 offset = normal.xy * Refract * 0.05 * clamp(thickness, 0.1, 2.0);
    vec3 refracted = texture(Sampler0, clamp(uv + offset, vec2(0.002), vec2(0.998))).rgb;

    vec3 water = mix(refracted, Tint, clamp(thickness * Density * 0.35, 0.0, 0.8));

    float fresnel = pow(1.0 - max(dot(normal, -dir), 0.0), 3.0);
    water += fresnel * 0.3;

    vec3 lightDir = normalize(vec3(0.35, 1.0, 0.2));
    water += pow(max(dot(reflect(dir, normal), lightDir), 0.0), 48.0) * 0.5;

    fragColor = vec4(water, 1.0);
}
