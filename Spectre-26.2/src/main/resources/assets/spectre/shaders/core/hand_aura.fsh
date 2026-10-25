#version 330

uniform sampler2D CaptureSampler;
uniform sampler2D HaloSampler;

layout(std140) uniform HandAuraConfig {
    vec4 Tint;
    vec4 Sheen;
    vec4 Shape;
};

in vec2 texCoord;

out vec4 fragColor;

const vec2 RAYS[8] = vec2[](
    vec2(1.0, 0.0), vec2(0.7071, 0.7071), vec2(0.0, 1.0), vec2(-0.7071, 0.7071),
    vec2(-1.0, 0.0), vec2(-0.7071, -0.7071), vec2(0.0, -1.0), vec2(0.7071, -0.7071)
);

void main() {
    vec4 hand = texture(CaptureSampler, texCoord);
    vec4 halo = texture(HaloSampler, texCoord);
    if (hand.a <= 0.0 && halo.a <= 0.0) {
        discard;
    }

    float phase = fract(dot(texCoord, vec2(0.45, -0.55)) - Shape.z);
    float glint = (1.0 - smoothstep(0.0, 0.06, abs(phase - 0.5))) * Sheen.a;
    vec3 tint = mix(Tint.rgb, Sheen.rgb, glint);

    float auraAlpha = Tint.a * (1.0 - exp(-3.4 * halo.a));
    vec4 aura = vec4(tint * auraAlpha, auraAlpha * 0.6);

    vec3 body = hand.rgb;
    if (hand.a > 0.0 && Shape.x > 0.0) {
        vec2 reach = Shape.y / vec2(textureSize(CaptureSampler, 0));
        float open = 0.0;
        for (int i = 0; i < 8; i++) {
            open += 1.0 - texture(CaptureSampler, texCoord + RAYS[i] * reach).a;
        }
        float rim = smoothstep(0.0, 0.45, open * 0.125) * Shape.x;
        body = mix(body, tint * hand.a, rim * 0.7) + tint * hand.a * rim * 0.25;
    }

    fragColor = vec4(body, hand.a) + aura * (1.0 - hand.a);
}
