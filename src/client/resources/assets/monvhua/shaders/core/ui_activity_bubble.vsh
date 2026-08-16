#version 150

#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 localUv;
out vec4 bubbleParameters;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    localUv = UV0;
    bubbleParameters = Color;
}
