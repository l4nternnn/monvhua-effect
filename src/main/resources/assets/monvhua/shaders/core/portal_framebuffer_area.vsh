#version 150

in vec3 Position;
in float ClipW;
in vec2 UV0;
in float TextureW;

out vec3 projectedTexCoord;

void main() {
    gl_Position = vec4(Position, ClipW);
    projectedTexCoord = vec3(UV0, TextureW);
}
