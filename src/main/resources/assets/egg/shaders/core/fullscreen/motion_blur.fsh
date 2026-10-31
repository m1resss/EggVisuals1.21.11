#version 330

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

layout(std140) uniform SadnesMotionBlur {
    mat4 Proj;
    mat4 InvProj;
    mat4 DeltaView;
    mat4 InvViewRotation;
    vec4 SelfMin;
    vec4 SelfMax;
    vec4 Params;
};

in vec2 texCoord0;

out vec4 fragColor;

const int MAX_SAMPLES = 32;

vec2 toNdc(vec2 uv) {
    return vec2(uv.x, mix(uv.y, 1.0 - uv.y, Params.x)) * 2.0 - 1.0;
}

float ndcDepth(vec2 uv) {
    float depth = min(texture(Sampler1, uv).r, 0.99999);
    return mix(depth * 2.0 - 1.0, depth, Params.w);
}

vec3 viewPosition(vec2 uv) {
    vec4 view = InvProj * vec4(toNdc(uv), ndcDepth(uv), 1.0);
    return view.xyz / view.w;
}

vec2 project(vec3 view) {
    vec4 clip = Proj * vec4(view, 1.0);
    return clip.xy / clip.w;
}

bool insideSelf(vec2 uv) {
    if (Params.z < 0.5) return false;
    vec3 pos = (InvViewRotation * vec4(viewPosition(uv), 1.0)).xyz;
    return all(greaterThanEqual(pos, SelfMin.xyz)) && all(lessThanEqual(pos, SelfMax.xyz));
}

float gradientNoise(vec2 pixel) {
    return fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
}

void main() {
    vec3 center = texture(Sampler0, texCoord0).rgb;
    fragColor = vec4(center, 1.0);
    if (insideSelf(texCoord0)) return;

    vec3 view = viewPosition(texCoord0);
    vec3 previousView = (DeltaView * vec4(view, 1.0)).xyz;
    if (previousView.z > -0.05) return;

    vec2 velocity = project(view) - project(previousView);
    float speed = length(velocity);
    if (speed > Params.y) velocity *= Params.y / speed;

    vec2 trail = vec2(velocity.x, velocity.y * mix(1.0, -1.0, Params.x)) * 0.5;
    float trailPixels = length(trail * vec2(textureSize(Sampler0, 0)));
    int samples = int(min(trailPixels, float(MAX_SAMPLES)));
    if (samples < 2) return;

    float jitter = gradientNoise(gl_FragCoord.xy) - 0.5;
    vec3 sum = center;
    float weight = 1.0;
    for (int i = 0; i < samples; i++) {
        float t = (float(i) + 0.5 + jitter) / float(samples) - 0.5;
        vec2 uv = clamp(texCoord0 + trail * t, 0.0, 1.0);
        if (insideSelf(uv)) continue;
        float tapWeight = 1.0 - abs(t);
        sum += texture(Sampler0, uv).rgb * tapWeight;
        weight += tapWeight;
    }
    fragColor = vec4(sum / weight, 1.0);
}
