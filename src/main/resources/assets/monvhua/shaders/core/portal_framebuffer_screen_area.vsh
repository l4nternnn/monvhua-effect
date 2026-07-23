#version 150

in vec3 Position;
in float ClipW;
in vec2 UV0;

noperspective out vec2 texCoord;

void main() {
    gl_Position = vec4(Position, ClipW);
    texCoord = UV0;
}
