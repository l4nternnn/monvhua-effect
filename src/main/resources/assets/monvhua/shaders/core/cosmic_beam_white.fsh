#version 150

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>

in vec3 localPos;
in float sphericalVertexDistance;
in float cylindricalVertexDistance;

out vec4 fragColor;

void main() {
    float time = GameTime * 90.0;
    float pulse = 0.92 + 0.08 * sin(localPos.y * 0.18 - time * 1.4);
    float stripe = 0.92 + 0.08 * smoothstep(0.08, 0.92, fract(localPos.y * 0.16 - time * 0.24));
    float shimmer = 0.90 + 0.10 * sin((localPos.x + localPos.z) * 8.0 + localPos.y * 0.22 - time * 1.8);
    vec3 color = vec3(2.25, 2.30, 2.38) * pulse * stripe * shimmer + vec3(0.35);

    fragColor = apply_fog(vec4(color, 1.0), sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
