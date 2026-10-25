#version 330

uniform sampler2D InSampler;

layout(std140) uniform AuraSpread {
    vec2 Step;
};

in vec2 texCoord;

out vec4 fragColor;

const float WEIGHTS[7] = float[](0.1553, 0.1443, 0.1155, 0.0798, 0.0475, 0.0244, 0.0109);

void main() {
    vec4 sum = texture(InSampler, texCoord) * WEIGHTS[0];
    for (int i = 1; i < 7; i++) {
        vec2 offset = Step * float(i);
        sum += (texture(InSampler, texCoord + offset) + texture(InSampler, texCoord - offset)) * WEIGHTS[i];
    }
    fragColor = sum;
}
