#version 150

uniform sampler2D InSampler;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    fragColor = texture(InSampler, texCoord) * vertexColor;
}
