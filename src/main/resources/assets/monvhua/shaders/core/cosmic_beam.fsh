#version 150

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>

in vec3 localPos;
in float sphericalVertexDistance;
in float cylindricalVertexDistance;

out vec4 fragColor;

const float TAU = 6.28318530718;

vec3 hsv2rgb(vec3 c) {
    vec3 p = abs(fract(c.xxx + vec3(0.0, 0.3333333, 0.6666667)) * 6.0 - 3.0);
    return c.z * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), c.y);
}

void main() {
    float time = GameTime * 90.0;
    float heightFlow = localPos.y * 0.055 - time * 0.035;
    float sideFlow = atan(localPos.z - 0.5, localPos.x - 0.5) / TAU;
    float hue = fract(heightFlow + sideFlow * 0.35);

    vec3 rainbow = hsv2rgb(vec3(hue, 0.92, 1.0));
    float pulse = 0.78 + 0.22 * sin(localPos.y * 0.13 - time * 2.2);
    float stripe = 0.78 + 0.22 * smoothstep(0.15, 0.95, fract(localPos.y * 0.18 - time * 0.45));
    vec3 color = rainbow * (1.15 * pulse * stripe) + vec3(1.0) * 0.12;

    fragColor = apply_fog(vec4(color, 1.0), sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
