#version 150

layout(std140) uniform ImmediateData {
    mat4 ProjView;
};

in vec3 Position;
in vec4 Color;

out vec4 vertexColor;

void main() {
    gl_Position = ProjView * vec4(Position, 1.0);
    vertexColor = Color;
}
