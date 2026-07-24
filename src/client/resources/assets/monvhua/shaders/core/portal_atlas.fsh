#version 150

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 sampled = texture(Sampler0, texCoord0);
    if (vertexColor.a == 0.0) {
        discard;
    }
    fragColor = vec4(sampled.rgb * vertexColor.rgb, vertexColor.a);
}
