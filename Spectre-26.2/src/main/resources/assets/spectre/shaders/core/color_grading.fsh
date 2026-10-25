#version 330

uniform sampler2D InSampler;

layout(std140) uniform ColorGradingConfig {
    float Vibrance;
    float Contrast;
    float Exposure;
    float Warmth;
    float Vignette;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 color = texture(InSampler, texCoord).rgb * Exposure;
    color *= vec3(1.0 + 0.08 * Warmth, 1.0 + 0.02 * Warmth, 1.0 - 0.08 * Warmth);

    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    float saturation = max(color.r, max(color.g, color.b)) - min(color.r, min(color.g, color.b));
    color = mix(vec3(luma), color, 1.0 + Vibrance * (1.0 - saturation));

    color = (color - 0.5) * Contrast + 0.5;

    vec2 fromCentre = (texCoord - 0.5) * vec2(1.25, 1.0);
    color *= 1.0 - Vignette * smoothstep(0.35, 0.95, length(fromCentre) * 1.35);

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
