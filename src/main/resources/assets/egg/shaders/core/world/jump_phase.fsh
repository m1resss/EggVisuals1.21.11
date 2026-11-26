#version 330

layout(std140) uniform CausticUniforms {
    vec4 uParams;
    vec4 uAccentColor;
    vec4 uFade;
};

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    float r = length(texCoord);
    if (r > 1.0) discard;

    float prog = clamp(uParams.x, 0.0, 1.0);
    float time = uParams.y;
    float brightness = uParams.w;
    float baseAlpha = uFade.x;
    float twist = uFade.y;

    float eased = 1.0 - pow(1.0 - prog, 3.0);
    float mainR = mix(0.14, 0.88, eased);
    float echoP = clamp((prog - 0.10) / 0.90, 0.0, 1.0);
    float echoEased = 1.0 - pow(1.0 - echoP, 2.6);
    float echoR = mix(0.10, 0.68, echoEased);

    float aa = max(fwidth(r), 0.006);

    float angle = atan(texCoord.y, texCoord.x);
    float dirSign = twist == 0.0 ? 1.0 : sign(twist);
    float rot = angle - time * 1.15 * dirSign - prog * twist * 1.5;
    float doubleWave = cos(rot * 2.0);
    float arcMask = smoothstep(-0.32, 0.32, doubleWave);
    float taper = smoothstep(-0.40, 0.78, doubleWave);

    float dMain = abs(r - mainR);
    float guideCov = 1.0 - smoothstep(0.0055, 0.0055 + aa, dMain);
    float arcHalfW = mix(0.006, 0.0155, taper);
    float arcCov = (1.0 - smoothstep(arcHalfW, arcHalfW + aa, dMain)) * arcMask;
    float rimCov = (1.0 - smoothstep(arcHalfW + 0.011, arcHalfW + 0.011 + aa, dMain)) * arcMask;

    float dEcho = abs(r - echoR);
    float echoAlpha = (1.0 - echoP) * 0.42;
    float echoCov = (1.0 - smoothstep(0.005, 0.005 + aa, dEcho)) * echoAlpha * smoothstep(0.0, 0.12, echoP);

    float tickZone = 1.0 - smoothstep(0.0, 0.38, prog);
    float axis = min(abs(cos(rot)), abs(sin(rot)));
    float tickAng = 1.0 - smoothstep(0.018, 0.018 + aa * 2.5, axis);
    float tickRad = smoothstep(mainR - 0.06, mainR - 0.03, r) * (1.0 - smoothstep(mainR - 0.01, mainR + 0.01, r));
    float ticksCov = tickAng * tickRad * tickZone * 0.68;

    vec3 rainbow = 0.58 + 0.42 * cos(6.28318 * (vec3(0.0, 0.33, 0.67) + time * 0.10 + angle / 6.28318));
    vec3 col = mix(rainbow, uAccentColor.rgb, clamp(uAccentColor.a, 0.0, 1.0));
    col *= clamp(0.75 + 0.25 * brightness, 0.65, 1.35);

    float strokeCov = max(arcCov, max(guideCov * 0.35, max(echoCov, ticksCov)));
    float totalA = max(strokeCov, rimCov * 0.46) * baseAlpha;
    if (totalA < 0.004) discard;

    vec3 darkRim = vec3(0.03, 0.04, 0.06);
    vec3 brightLine = mix(col, vec3(1.0), 0.30 * arcCov);
    vec3 rgb = mix(darkRim, brightLine, clamp(strokeCov / max(max(strokeCov, rimCov * 0.46), 0.001), 0.0, 1.0));
    fragColor = vec4(rgb, totalA);
}
