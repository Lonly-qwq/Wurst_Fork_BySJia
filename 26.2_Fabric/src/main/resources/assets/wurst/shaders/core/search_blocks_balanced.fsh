#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec4 lightMapColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a < 0.1) {
        discard;
    }

    vec3 gammaLight = pow(clamp(lightMapColor.rgb, 0.0, 1.0), vec3(0.65));
    color *= vertexColor * vec4(gammaLight, lightMapColor.a) * ColorModulator;
    fragColor = color;
}
