#version 330

uint mixBits(uint x) {
    x ^= x >> 16;
    x *= 0x7feb352du;
    x ^= x >> 15;
    x *= 0x846ca68bu;
    x ^= x >> 16;
    return x;
}

float hash31(vec3 p) {
    uvec3 b = floatBitsToUint(p);
    return float(mixBits(b.x ^ mixBits(b.y ^ mixBits(b.z))) >> 8) * (1.0 / 16777216.0);
}

float hash21(vec2 p) {
    uvec2 b = floatBitsToUint(p);
    return float(mixBits(b.x ^ mixBits(b.y + 0x9e3779b9u)) >> 8) * (1.0 / 16777216.0);
}

#moj_import <egg:sky.glsl>
