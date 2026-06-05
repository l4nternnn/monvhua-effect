#version 150

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:matrix.glsl>

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec3 localPos;
in float sphericalVertexDistance;
in float cylindricalVertexDistance;

out vec4 fragColor;

const float PI = 3.14159265359;
const float TAU = 6.28318530718;

float hash21(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

vec2 rotate2(vec2 p, float angle) {
    float s = sin(angle);
    float c = cos(angle);
    return vec2(c * p.x - s * p.y, s * p.x + c * p.y);
}

float starField(vec2 uv, float scale, float time, float threshold) {
    vec2 grid = uv * scale;
    vec2 cell = floor(grid);
    vec2 local = fract(grid) - 0.5;
    float seed = hash21(cell);
    float d = length(local);
    float core = smoothstep(0.055, 0.0, d);
    float glow = smoothstep(0.19, 0.0, d) * 0.18;
    float twinkle = 0.45 + 0.55 * sin(time * (5.0 + seed * 17.0) + seed * TAU);
    return step(threshold, seed) * (core + glow) * twinkle;
}

vec3 lensedBackground(vec2 p, float r, float time) {
    float gravity = smoothstep(0.52, 0.055, r);
    float bend = gravity * 0.18 / max(r * 4.8, 0.18);
    float twist = gravity * (0.18 + 0.85 / max(r * 8.0, 0.3)) + time * 0.055;
    vec2 source = rotate2(p, twist) * (1.0 + bend);
    vec2 uv = fract(source * 0.86 + vec2(0.5, 0.5 + time * 0.004));

    vec3 textureNebula = texture(Sampler0, uv).rgb * vec3(0.92, 0.96, 1.12);
    vec3 deep = vec3(0.005, 0.007, 0.014);
    vec3 color = deep + textureNebula * 0.52;
    color += vec3(0.65, 0.86, 1.0) * starField(uv + time * 0.002, 42.0, time, 0.978) * 1.3;
    color += vec3(1.0, 0.76, 0.55) * starField(uv - time * 0.001, 91.0, time * 1.4, 0.988);
    return color;
}

vec3 accretionDisk(vec2 p, float time, float horizon) {
    vec2 disk = rotate2(p, -0.24 + time * 0.045);
    float diskRadius = length(vec2(disk.x, disk.y * 3.2));
    float band = smoothstep(0.34, 0.22, diskRadius) * smoothstep(horizon * 1.25, horizon * 1.75, diskRadius);
    float split = smoothstep(0.022, 0.0, abs(disk.y));
    float turbulence = 0.68 + 0.32 * sin(atan(disk.y * 3.2, disk.x) * 9.0 - time * 4.2 + diskRadius * 27.0);
    vec3 hot = mix(vec3(1.0, 0.38, 0.08), vec3(0.38, 0.86, 1.0), smoothstep(-0.22, 0.22, disk.x));
    return hot * band * split * turbulence * 2.5;
}

void main() {
    float time = GameTime * 90.0;
    vec2 uv = texCoord0;
    vec2 p = uv - 0.5;
    float r = length(p);

    float outerFade = smoothstep(0.56, 0.46, r);
    if (outerFade <= 0.001) {
        discard;
    }

    float horizon = 0.13 + 0.006 * sin(time * 1.8);
    vec3 color = lensedBackground(p, r, time);

    float gravity = smoothstep(0.50, 0.06, r);
    float lensRing = exp(-abs(r - 0.255) * 23.0) * gravity;
    float photonRing = exp(-abs(r - horizon * 1.38) * 86.0);
    color += vec3(0.35, 0.72, 1.0) * lensRing * 0.38;
    color += vec3(1.0, 0.82, 0.38) * photonRing * 1.65;
    color += accretionDisk(p, time, horizon);

    float shadow = smoothstep(horizon * 1.24, horizon * 0.86, r);
    color = mix(color, vec3(0.0), shadow);

    float corona = exp(-abs(r - horizon * 1.6) * 36.0) * (1.0 - shadow);
    color += vec3(0.65, 0.78, 1.0) * corona * 0.35;

    color = color / (color + vec3(1.0));
    color = pow(color, vec3(0.82));

    float alpha = max(outerFade * (0.24 + gravity * 0.68), photonRing);
    alpha = max(alpha, smoothstep(0.34, 0.22, length(vec2(p.x, p.y * 3.2))) * outerFade * 0.72);
    alpha = clamp(alpha, 0.0, 1.0);
    fragColor = apply_fog(vec4(color, alpha), sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
