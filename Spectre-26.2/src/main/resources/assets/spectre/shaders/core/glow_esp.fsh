#version 330

uniform sampler2D SilhouetteSampler;
uniform sampler2D HaloSampler;

layout(std140) uniform GlowEspConfig {
    vec4 Shape;
    vec4 Sheen;
};

in vec2 texCoord;

out vec4 fragColor;

const vec2 RAYS[16] = vec2[](
    vec2(1.0, 0.0), vec2(0.9239, 0.3827), vec2(0.7071, 0.7071), vec2(0.3827, 0.9239),
    vec2(0.0, 1.0), vec2(-0.3827, 0.9239), vec2(-0.7071, 0.7071), vec2(-0.9239, 0.3827),
    vec2(-1.0, 0.0), vec2(-0.9239, -0.3827), vec2(-0.7071, -0.7071), vec2(-0.3827, -0.9239),
    vec2(0.0, -1.0), vec2(0.3827, -0.9239), vec2(0.7071, -0.7071), vec2(0.9239, -0.3827)
);

const mat2 TWIST = mat2(0.9808, 0.1951, -0.1951, 0.9808);

vec4 over(vec4 top, vec4 under) {
    return top + under * (1.0 - top.a);
}

vec4 widest(vec4 best, vec2 reach, mat2 turn) {
    for (int i = 0; i < 16; i++) {
        vec4 probe = texture(SilhouetteSampler, texCoord + turn * RAYS[i] * reach);
        if (probe.a > best.a) {
            best = probe;
        }
    }
    return best;
}

vec3 unpremultiply(vec4 color) {
    return color.rgb / max(color.a, 0.0001);
}

void main() {
    vec4 body = texture(SilhouetteSampler, texCoord);
    vec4 halo = texture(HaloSampler, texCoord);
    if (body.a <= 0.0 && halo.a <= 0.0) {
        discard;
    }

    vec4 edge = vec4(0.0);
    if (body.a < 1.0) {
        vec2 texel = 1.0 / vec2(textureSize(SilhouetteSampler, 0));
        edge = widest(edge, texel * Shape.x, mat2(1.0));
        if (Shape.x > 1.75) {
            edge = widest(edge, texel * Shape.x * 0.5, TWIST);
        }
    }

    float phase = fract(dot(texCoord, vec2(0.62, 0.38)) - Shape.w);
    float glint = (1.0 - smoothstep(0.0, 0.045, abs(phase - 0.5))) * Sheen.a;
    float outside = 1.0 - body.a;

    float auraAlpha = Shape.y * (1.0 - exp(-3.0 * halo.a)) * outside;
    vec4 result = vec4(unpremultiply(halo) * auraAlpha, auraAlpha * 0.65);

    float edgeAlpha = smoothstep(0.04, 0.6, edge.a) * outside;
    vec3 edgeColor = mix(unpremultiply(edge), Sheen.rgb, glint);
    result = over(vec4(edgeColor * edgeAlpha, edgeAlpha), result);

    float fillAlpha = body.a * Shape.z;
    vec3 fillColor = mix(unpremultiply(body), Sheen.rgb, glint * 0.5);
    result = over(vec4(fillColor * fillAlpha, fillAlpha), result);

    fragColor = result;
}
