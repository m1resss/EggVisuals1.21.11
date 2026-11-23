#version 330

layout(std140) uniform EggSnowData {
    mat4 InverseView;
    vec4 Origin4;
    vec4 SunDir4;
    vec4 SunColor4;
    vec4 SkyColor4;
    float GrainScale;
    float WrapPeriod;
    float GrainStrength;
    float CrustAmount;
    float SparkleAmount;
    float Wetness;
    float Sheen;
};

#define Origin Origin4.xyz
#define SunDir SunDir4.xyz
#define SunColor SunColor4.xyz
#define SkyColor SkyColor4.xyz

in vec3 WorldPos;
in vec3 ViewPos;
in vec4 FragColor;

out vec4 fragColor;

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float valueNoise(vec3 p, float period) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);

    vec3 a = mod(i, period);
    vec3 b = mod(i + 1.0, period);

    float n000 = hash13(vec3(a.x, a.y, a.z));
    float n100 = hash13(vec3(b.x, a.y, a.z));
    float n010 = hash13(vec3(a.x, b.y, a.z));
    float n110 = hash13(vec3(b.x, b.y, a.z));
    float n001 = hash13(vec3(a.x, a.y, b.z));
    float n101 = hash13(vec3(b.x, a.y, b.z));
    float n011 = hash13(vec3(a.x, b.y, b.z));
    float n111 = hash13(vec3(b.x, b.y, b.z));

    float x00 = mix(n000, n100, f.x);
    float x10 = mix(n010, n110, f.x);
    float x01 = mix(n001, n101, f.x);
    float x11 = mix(n011, n111, f.x);
    return mix(mix(x00, x10, f.y), mix(x01, x11, f.y), f.z);
}

float fbm(vec3 p, float period) {
    float h = valueNoise(p, period) * 0.55;
    h += valueNoise(p * 2.0 + 17.0, period * 2.0) * 0.28;
    h += valueNoise(p * 4.0 + 37.0, period * 4.0) * 0.17;
    return h;
}

float valueNoisePlane(vec2 p, float c, float period) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);

    vec2 a = mod(i, period);
    vec2 b = mod(i + 1.0, period);
    float az = mod(floor(c), period);

    float n000 = hash13(vec3(a.x, a.y, az));
    float n100 = hash13(vec3(b.x, a.y, az));
    float n010 = hash13(vec3(a.x, b.y, az));
    float n110 = hash13(vec3(b.x, b.y, az));

    float x00 = mix(n000, n100, f.x);
    float x10 = mix(n010, n110, f.x);
    return mix(x00, x10, f.y);
}

float fbmPlane(vec2 p, float c, float period) {
    float h = valueNoisePlane(p, c, period) * 0.55;
    h += valueNoisePlane(p * 2.0 + 17.0, c * 2.0 + 17.0, period * 2.0) * 0.28;
    h += valueNoisePlane(p * 4.0 + 37.0, c * 4.0 + 37.0, period * 4.0) * 0.17;
    return h;
}

const float SLOPE_CUTOFF = 0.004;

vec3 slopeNormal(vec3 pos, vec3 base, float scale, float period, float strength) {
    if (strength < 0.002) {
        return base;
    }

    vec3 blend = abs(base);
    blend = pow(blend, vec3(4.0));
    blend /= max(blend.x + blend.y + blend.z, 1e-4);

    vec3 p = pos * scale;
    float d = 1.0;
    vec3 detail = vec3(0.0);

    if (blend.x > SLOPE_CUTOFF) {
        float h = fbmPlane(vec2(p.y, p.z), 3.0, period);
        float hu = fbmPlane(vec2(p.y + d, p.z), 3.0, period);
        float hv = fbmPlane(vec2(p.y, p.z + d), 3.0, period);
        detail += vec3(0.0, h - hu, h - hv) * blend.x;
    }

    if (blend.y > SLOPE_CUTOFF) {
        float h = fbmPlane(vec2(p.x, p.z), 11.0, period);
        float hu = fbmPlane(vec2(p.x + d, p.z), 11.0, period);
        float hv = fbmPlane(vec2(p.x, p.z + d), 11.0, period);
        detail += vec3(h - hu, 0.0, h - hv) * blend.y;
    }

    if (blend.z > SLOPE_CUTOFF) {
        float h = fbmPlane(vec2(p.x, p.y), 23.0, period);
        float hu = fbmPlane(vec2(p.x + d, p.y), 23.0, period);
        float hv = fbmPlane(vec2(p.x, p.y + d), 23.0, period);
        detail += vec3(h - hu, h - hv, 0.0) * blend.z;
    }

    return normalize(base + detail * strength);
}

float ggxDistribution(float ndh, float rough) {
    float a = rough * rough;
    float a2 = a * a;
    float d = ndh * ndh * (a2 - 1.0) + 1.0;
    return a2 / max(3.14159265 * d * d, 1.0e-6);
}

float smithVisibility(float ndv, float ndl, float rough) {
    float k = rough * rough * 0.5;
    float gv = ndv / (ndv * (1.0 - k) + k);
    float gl = ndl / (ndl * (1.0 - k) + k);
    return gv * gl;
}

float schlick(float u, float f0) {
    return f0 + (1.0 - f0) * pow(1.0 - u, 5.0);
}

void main() {
    vec3 view = normalize(-ViewPos);

    vec3 geometric = normalize(cross(dFdx(WorldPos), dFdy(WorldPos)));
    if (dot(geometric, view) < 0.0) {
        geometric = -geometric;
    }

    float footprint = max(length(fwidth(WorldPos)), 1.0e-5);

    float coarseCell = 16.0 / GrainScale;
    float coarseFade = 1.0 - smoothstep(coarseCell * 0.5, coarseCell * 2.0, footprint);
    float fineCell = 1.0 / GrainScale;
    float fineFade = 1.0 - smoothstep(fineCell * 0.5, fineCell * 2.0, footprint);

    float sparkleScale = GrainScale * 1.5;
    float sparkleCell = 1.0 / sparkleScale;

    float sparklePerPixel = sparkleCell / footprint;
    float sparkleFade = 1.0;

    vec3 normal = slopeNormal(WorldPos, geometric, GrainScale / 16.0,
            WrapPeriod / 16.0, GrainStrength * 0.55 * coarseFade);
    normal = slopeNormal(WorldPos, normal, GrainScale, WrapPeriod,
            GrainStrength * 0.30 * fineFade);

    vec3 sun = normalize(SunDir);

    float wrap = 0.38;
    float lambert = max(0.0, (dot(normal, sun) + wrap) / (1.0 + wrap));

    float sky = 0.90 + 0.10 * max(0.0, normal.y);

    vec3 cool = mix(vec3(0.90, 0.94, 1.00), vec3(1.0), clamp(lambert, 0.0, 1.0));
    vec3 light = (SkyColor * sky + SunColor * lambert * 0.52) * cool;

    float crustAmount = CrustAmount * fineFade;
    float density = 1.0;
    if (crustAmount > 0.002) {
        float crust = fbm(WorldPos * GrainScale * 0.5, WrapPeriod * 0.5);
        density = mix(1.0, 0.88 + 0.20 * crust, crustAmount);
    }

    vec3 halfway = normalize(sun + view);
    float wet = clamp(Wetness, 0.0, 1.0);

    vec3 albedo = FragColor.rgb * mix(1.0, 0.90, wet);
    vec3 color = albedo * light * density;

    float ndl = max(dot(normal, sun), 0.0);
    float ndv = max(dot(normal, view), 1.0e-3);
    float ndh = max(dot(normal, halfway), 0.0);
    float vdh = max(dot(view, halfway), 0.0);

    float rough = clamp(mix(0.78, 0.42, wet) + (1.0 - fineFade) * 0.30, 0.10, 1.0);
    float f0 = mix(0.020, 0.035, wet);

    float specular = ggxDistribution(ndh, rough) * smithVisibility(ndv, ndl, rough)
            * schlick(vdh, f0) / max(4.0 * ndv * ndl, 1.0e-4);

    color += SunColor * specular * ndl * max(0.0, sun.y) * (0.95 + 1.20 * wet) * Sheen;

    float retro = pow(max(dot(view, sun), 0.0), 2.0);
    float daylight = smoothstep(-0.18, 0.22, sun.y);
    color += albedo * SunColor * retro * daylight * 0.22;
    color += albedo * SkyColor * retro * (1.0 - daylight) * 0.14;

    float grazing = pow(1.0 - ndv, 3.0);
    color += SkyColor * grazing * (0.26 + 0.24 * wet) * (0.55 + 0.45 * max(0.0, normal.y)) * Sheen;

    vec3 moon = -sun;
    float moonUp = max(0.0, moon.y);
    if (moonUp > 0.0 && daylight < 0.999) {
        vec3 moonHalf = normalize(moon + view);
        float mdl = max(dot(normal, moon), 0.0);
        float mdh = max(dot(normal, moonHalf), 0.0);
        float mdv = max(dot(view, moonHalf), 0.0);

        float moonRough = clamp(rough * 0.55, 0.10, 1.0);
        float moonSpec = ggxDistribution(mdh, moonRough) * smithVisibility(ndv, mdl, moonRough)
                * schlick(mdv, f0) / max(4.0 * ndv * mdl, 1.0e-4);
        vec3 moonLight = mix(vec3(0.66, 0.74, 0.96), SkyColor * 1.6, 0.45);
        color += moonLight * moonSpec * mdl * moonUp * (1.0 - daylight) * (1.70 + 1.30 * wet) * Sheen;

        color += albedo * moonLight * mdl * moonUp * (1.0 - daylight) * 0.10;
    }

    vec3 mirror = reflect(-view, normal);
    vec3 skyHit = SkyColor * (0.80 + 0.60 * max(0.0, mirror.y));
    float fresnel = schlick(ndv, f0) * mix(0.34, 0.78, wet) * mix(0.35, 1.0, fineFade);
    color = mix(color, skyHit, clamp(fresnel * Sheen, 0.0, 0.55));

    float sparkleWanted = 0.022 * sparklePerPixel * sparklePerPixel;
    float sparkleAmount = min(SparkleAmount, sparkleWanted);
    if (sparkleAmount > 0.0004) {
        vec3 cellIndex = floor(WorldPos * sparkleScale);
        float glint = hash13(cellIndex);

        float pick = smoothstep(1.0 - sparkleAmount, 1.0 - sparkleAmount * 0.4, glint);

        vec3 inside = (fract(WorldPos * sparkleScale) - 0.5) * sparkleCell;

        float sparkleRadius = max(sparkleCell * 0.30, footprint * 1.2);
        float shape = 1.0 - smoothstep(sparkleRadius * 0.45, sparkleRadius, length(inside));

        vec3 tilt = vec3(hash13(cellIndex + 13.0), hash13(cellIndex + 57.0), hash13(cellIndex + 91.0));
        vec3 facetNormal = normalize(geometric + (tilt - 0.5) * 1.6);

        float align = max(dot(facetNormal, sun), 0.0);

        float sparkle = pick * shape * pow(align, 24.0) * sparkleFade * 2.3 * Sheen;
        vec3 sparkLight = mix(SkyColor * 1.5, SunColor, daylight);
        color += sparkLight * sparkle;

        vec3 skyTilt = vec3(hash13(cellIndex + 211.0), hash13(cellIndex + 307.0), hash13(cellIndex + 419.0));
        vec3 skyFacet = normalize(geometric + (skyTilt - 0.5) * 1.9);
        float skyPick = smoothstep(1.0 - sparkleAmount * 1.6, 1.0 - sparkleAmount * 0.5, hash13(cellIndex + 53.0));

        float skyCatch = pow(max(skyFacet.y, 0.0), 4.0);
        float skySpark = skyPick * shape * skyCatch * sparkleFade * 0.9 * Sheen;
        color += SkyColor * skySpark;
    }

    fragColor = vec4(clamp(color, 0.0, 1.0), FragColor.a);
}
