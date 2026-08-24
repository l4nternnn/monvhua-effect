#version 150

uniform sampler2D InSampler;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    bool pixelAvatar = vertexColor.a < 0.998;
    vec2 avatarUv = vec2(texCoord.x, 1.0 - texCoord.y);
    vec4 sample = pixelAvatar
            ? texture(InSampler, (floor(avatarUv * vec2(32.0, 33.0)) + 0.5) / vec2(32.0, 33.0))
            : texture(InSampler, avatarUv);
    fragColor = vec4(sample.rgb, sample.a * vertexColor.r);
}
