#version 150

in vec3 Position;
in vec4 Color;

out vec4 vertexColor;

void main() {
    vec2 screenPos = Position.xy * 2.0 - 1.0;
    gl_Position = vec4(screenPos.x, screenPos.y, 1.0, 1.0);
    vertexColor = Color;
}
