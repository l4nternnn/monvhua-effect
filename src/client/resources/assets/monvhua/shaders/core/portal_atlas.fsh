#version 150

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec2 screenUv = gl_FragCoord.xy / vec2(textureSize(Sampler0, 0));
    vec4 sampled = texture(Sampler0, screenUv);
    if (vertexColor.a == 0.0) {
        discard;
    }
    fragColor = vec4(sampled.rgb * vertexColor.rgb, vertexColor.a);
}
