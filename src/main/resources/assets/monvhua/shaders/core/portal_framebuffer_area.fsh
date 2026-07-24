#version 150

uniform sampler2D InSampler;

in vec3 projectedTexCoord;

out vec4 fragColor;

void main() {
    vec2 texCoord = projectedTexCoord.xy / max(projectedTexCoord.z, 0.000001);
    fragColor = texture(InSampler, texCoord);
}
