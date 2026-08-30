#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec4 lightMapColor;
in vec2 texCoord0;

out vec4 fragColor;

vec3 searchNotGamma(vec3 color) {
    float maxComponent = max(max(color.r, color.g), color.b);
    if (maxComponent <= 0.0) {
        return color;
    }

    float maxInverted = 1.0 - maxComponent;
    float maxScaled = 1.0 - maxInverted * maxInverted
        * maxInverted * maxInverted;
    return color * (maxScaled / maxComponent);
}

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a < 0.1) {
        discard;
    }

    vec3 gammaLight = clamp(mix(lightMapColor.rgb,
        searchNotGamma(lightMapColor.rgb), 16.0), 0.0, 1.0);
    color *= vertexColor * vec4(gammaLight, lightMapColor.a) * ColorModulator;
    fragColor = color;
}
