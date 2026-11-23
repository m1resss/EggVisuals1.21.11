#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
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

in vec3 Position;
in vec4 Color;

out vec3 WorldPos;
out vec3 ViewPos;
out vec4 FragColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vec3 relative = (InverseView * vec4(Position, 1.0)).xyz;
    WorldPos = relative + Origin;
    ViewPos = relative;
    FragColor = Color;
}
