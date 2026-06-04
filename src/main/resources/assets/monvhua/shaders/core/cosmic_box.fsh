#version 150

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:matrix.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec4 texProj0;
in vec3 localPos;
in float sphericalVertexDistance;
in float cylindricalVertexDistance;

out vec4 fragColor;

const float TAU = 6.28318530718;

float hash21(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float starLayer(vec2 uv, float scale, float time, float threshold) {
    vec2 grid = uv * scale;
    vec2 cell = floor(grid);
    vec2 local = fract(grid) - 0.5;
    float seed = hash21(cell);
    float d = length(local);
    float core = smoothstep(0.056, 0.0, d);
    float glow = smoothstep(0.19, 0.0, d) * 0.16;
    float twinkle = 0.45 + 0.55 * sin(time * (8.0 + seed * 18.0) + seed * TAU);
    return step(threshold, seed) * (core + glow) * (0.35 + 0.65 * twinkle);
}

vec2 rotate2(vec2 p, float angle) {
    float s = sin(angle);
    float c = cos(angle);
    return vec2(c * p.x - s * p.y, s * p.x + c * p.y);
}

float pointedStar(vec2 uv, float scale, float time, float threshold, float points, float orbitSpeed, float spinSpeed) {
    vec2 centeredUv = uv - 0.5;
    vec2 orbitUv = rotate2(centeredUv, time * orbitSpeed) + 0.5;
    vec2 grid = orbitUv * scale;
    vec2 cell = floor(grid);
    vec2 local = fract(grid) - 0.5;
    float seed = hash21(cell);
    if (seed < threshold) {
        return 0.0;
    }

    local = rotate2(local, time * (spinSpeed + seed * 1.6) + seed * TAU);
    float angle = atan(local.y, local.x);
    float radius = length(local);
    float spikes = pow(abs(cos(angle * points)), 18.0);
    float core = smoothstep(0.155, 0.0, radius);
    float rays = spikes * smoothstep(0.34, 0.0, radius) * 0.82;
    float halo = smoothstep(0.18, 0.0, radius) * 0.16;
    float twinkle = 0.55 + 0.45 * sin(time * (7.0 + seed * 15.0) + seed * TAU);
    return (core + rays + halo) * twinkle;
}

vec3 galaxy(vec2 uv, float time) {
    vec2 p = uv - 0.5;
    p.x *= ScreenSize.x / max(ScreenSize.y, 1.0);

    float r = length(p);
    float a = atan(p.y, p.x);
    float swirl = a * 2.0 - r * 18.0 + time * 0.8;
    float arms = pow(0.5 + 0.5 * cos(swirl), 4.0) * smoothstep(0.78, 0.05, r);
    float core = exp(-r * 7.0);
    float dust = pow(max(0.0, 1.0 - abs(sin(a * 2.0 + r * 9.0 - time * 0.35))), 3.0) * smoothstep(0.72, 0.08, r);

    vec3 violet = vec3(0.56, 0.12, 0.92);
    vec3 magenta = vec3(1.00, 0.16, 0.66);
    vec3 cyan = vec3(0.10, 0.88, 1.00);
    vec3 emerald = vec3(0.18, 1.00, 0.58);
    vec3 gold = vec3(1.00, 0.74, 0.26);
    vec3 rose = vec3(1.00, 0.28, 0.32);
    vec3 rainbowA = mix(violet, cyan, 0.5 + 0.5 * sin(a * 2.0 + r * 13.0 + time * 0.28));
    vec3 rainbowB = mix(gold, magenta, 0.5 + 0.5 * sin(a * 3.0 - r * 9.0 - time * 0.22));
    vec3 rainbowC = mix(emerald, rose, 0.5 + 0.5 * cos(a * 4.0 + r * 17.0));

    return rainbowA * arms * 0.54
        + rainbowB * arms * 0.36
        + rainbowC * dust * 0.30
        + gold * core * 0.70;
}

vec3 projectedNebula(vec2 fixedUv, float time) {
    vec3 customNebula = texture(Sampler1, fixedUv).rgb;
    vec3 color = texture(Sampler0, fixedUv).rgb * vec3(0.0035, 0.0035, 0.0040);
    color += customNebula * vec3(0.72);

    for (int i = 0; i < COSMIC_LAYERS; i++) {
        float layer = float(i + 1);
        vec2 centered = fixedUv - 0.5;
        float scale = 1.08 + layer * 0.15;
        float angle = time * (0.09 + layer * 0.013) + layer * 1.83;
        vec2 projected = mat2_rotate_z(angle) * centered * scale + 0.5;

        vec3 sampleColor = texture(Sampler1, projected).rgb;
        vec3 tintA = vec3(1.00, 0.46, 0.18);
        vec3 tintB = vec3(0.38, 1.00, 0.42);
        vec3 tintC = vec3(1.00, 0.22, 0.70);
        vec3 tintD = vec3(0.25, 0.72, 1.00);
        vec3 warmCool = mix(tintA, tintB, 0.5 + 0.5 * sin(layer * 1.71));
        vec3 vivid = mix(tintC, tintD, 0.5 + 0.5 * cos(layer * 2.13));
        vec3 tint = mix(warmCool, vivid, 0.38);
        color += sampleColor * tint / (layer * 2.75);
    }

    return color;
}

void main() {
    float time = GameTime * 90.0;
    vec2 screenUv = texProj0.xy / max(texProj0.w, 0.0001);
    vec3 roomDir = normalize(localPos - vec3(0.5));
    vec2 roomUv = vec2(atan(roomDir.z, roomDir.x) / TAU + 0.5, asin(clamp(roomDir.y, -1.0, 1.0)) / 3.14159265359 + 0.5);
    vec2 rotateUv = mat2_rotate_z(time * 0.018) * (roomUv - 0.5) + 0.5;

    vec3 color = projectedNebula(roomUv, time);
    color += galaxy(rotateUv, time) * 1.28;
    color += vec3(1.00, 0.94, 0.82) * starLayer(screenUv + time * 0.002, 55.0, time, 0.982) * 1.75;
    color += vec3(1.00, 0.78, 0.98) * starLayer(screenUv - time * 0.0013, 110.0, time * 1.4, 0.988) * 1.45;
    color += vec3(0.92, 1.00, 0.72) * pointedStar(roomUv, 34.0, time, 0.976, 4.0, 0.020, 1.75) * 1.45;
    color += vec3(1.00, 0.86, 0.44) * pointedStar(roomUv, 26.0, time, 0.982, 5.0, -0.014, 1.15) * 1.60;

    float edge = 1.0 - min(min(localPos.x, 1.0 - localPos.x), min(localPos.y, 1.0 - localPos.y)) * 2.0;
    edge = max(edge, 1.0 - min(localPos.z, 1.0 - localPos.z) * 2.0);
    color += vec3(1.00, 0.48, 0.18) * pow(clamp(edge, 0.0, 1.0), 5.0) * 0.10;

    color = color / (color + vec3(1.0));
    color = pow(color, vec3(0.88));
    color *= 0.92;
    fragColor = apply_fog(vec4(color, 1.0), sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
