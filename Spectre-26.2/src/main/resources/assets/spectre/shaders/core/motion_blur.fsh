#version 330

uniform sampler2D InSampler;

layout(std140) uniform MotionBlurConfig {
    float Retention;
    float Seed;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    float noise = fract(52.9829189 * fract(dot(gl_FragCoord.xy + Seed, vec2(0.06711056, 0.00583715))));
    vec3 history = texture(InSampler, texCoord).rgb + (noise - 0.5) / (255.0 * Retention);
    fragColor = vec4(history, Retention);
}
