#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;
in float LineWidth;

// Входы лого из света (упаковка — LogoGlowGuiRenderer). UV2 и Normal не используются, Java шлёт нули.
// vertexColor — акцент (a = альфа лого); texCoord — UV SDF-квадрата лого (окно вокруг глифа);
// energy — возбуждение от скорости мыши; flash — вспышка от удара лого о стенку;
// phase — накопленное время анимации в секундах.
out vec4 vertexColor;
out vec2 texCoord;
out float energy;
out float flash;
out float phase;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    texCoord = UV0;
    // Энергия и вспышка — в SHORT-канале overlay с шагом 1/10000.
    energy = float(UV1.x) / 10000.0;
    flash = float(UV1.y) / 10000.0;
    // Java интегрирует фазу сама (быстрее при движении мыши), поэтому ускорение не рвёт анимацию.
    phase = LineWidth;
}
