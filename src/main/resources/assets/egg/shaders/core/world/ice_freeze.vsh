#version 330

in vec3 Position;
in vec2 UV0;
in vec4 Color;

void main() {
    gl_Position = vec4(Position, 1.0);
}
