#version 150

in vec2 localUv;
in vec4 bubbleParameters;

out vec4 fragColor;

const float PI = 3.14159265359;
const vec3 OUTLINE_COLOR = vec3(0.035, 0.035, 0.04);
const vec3 FILL_COLOR = vec3(0.98, 0.985, 0.97);

float sdRoundedBox(vec2 p, vec2 halfSize, float radius) {
    vec2 q = abs(p) - halfSize + radius;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
}

float sdTriangle(vec2 p, vec2 p0, vec2 p1, vec2 p2) {
    vec2 e0 = p1 - p0;
    vec2 e1 = p2 - p1;
    vec2 e2 = p0 - p2;
    vec2 v0 = p - p0;
    vec2 v1 = p - p1;
    vec2 v2 = p - p2;
    vec2 pq0 = v0 - e0 * clamp(dot(v0, e0) / dot(e0, e0), 0.0, 1.0);
    vec2 pq1 = v1 - e1 * clamp(dot(v1, e1) / dot(e1, e1), 0.0, 1.0);
    vec2 pq2 = v2 - e2 * clamp(dot(v2, e2) / dot(e2, e2), 0.0, 1.0);
    float orientation = sign(e0.x * e2.y - e0.y * e2.x);
    vec2 d = min(
        min(vec2(dot(pq0, pq0), orientation * (v0.x * e0.y - v0.y * e0.x)),
            vec2(dot(pq1, pq1), orientation * (v1.x * e1.y - v1.y * e1.x))),
        vec2(dot(pq2, pq2), orientation * (v2.x * e2.y - v2.y * e2.x))
    );
    return -sqrt(d.x) * sign(d.y);
}

float smoothUnion(float a, float b, float radius) {
    float h = clamp(0.5 + 0.5 * (b - a) / radius, 0.0, 1.0);
    return mix(b, a, h) - radius * h * (1.0 - h);
}

float circleMask(vec2 p, vec2 center, float radius) {
    float distanceToCircle = length(p - center) - radius;
    float aa = max(fwidth(distanceToCircle) * 1.15, 0.0008);
    return 1.0 - smoothstep(-aa, aa, distanceToCircle);
}

void main() {
    vec2 p = vec2((localUv.x - 0.5) * 0.92, (localUv.y - 0.5) * 0.62);

    float body = sdRoundedBox(p - vec2(0.0, 0.065), vec2(0.39, 0.19), 0.095);
    float tail = sdTriangle(
        p,
        vec2(-0.235, -0.105),
        vec2(-0.325, -0.275),
        vec2(-0.055, -0.115)
    ) - 0.008;
    float bubble = smoothUnion(body, tail, 0.022);

    float edgeAA = max(fwidth(bubble) * 1.2, 0.0009);
    float shapeAlpha = 1.0 - smoothstep(-edgeAA, edgeAA, bubble);
    if (shapeAlpha <= 0.001) {
        discard;
    }

    float strokeWidth = 0.018;
    float fillMask = 1.0 - smoothstep(
        -strokeWidth - edgeAA,
        -strokeWidth + edgeAA,
        bubble
    );
    vec3 color = mix(OUTLINE_COLOR, FILL_COLOR, fillMask);

    float phase = bubbleParameters.g * 3.0;
    float firstSlot = phase;
    float secondSlot = phase - 1.0;
    float thirdSlot = phase - 2.0;
    float firstJump = step(0.0, firstSlot) * step(firstSlot, 1.0)
        * sin(PI * clamp(firstSlot, 0.0, 1.0)) * 0.055;
    float secondJump = step(0.0, secondSlot) * step(secondSlot, 1.0)
        * sin(PI * clamp(secondSlot, 0.0, 1.0)) * 0.055;
    float thirdJump = step(0.0, thirdSlot) * step(thirdSlot, 1.0)
        * sin(PI * clamp(thirdSlot, 0.0, 1.0)) * 0.055;

    float dots = 0.0;
    dots = max(dots, circleMask(p, vec2(-0.14, 0.055 + firstJump), 0.034));
    dots = max(dots, circleMask(p, vec2(0.0, 0.055 + secondJump), 0.034));
    dots = max(dots, circleMask(p, vec2(0.14, 0.055 + thirdJump), 0.034));
    color = mix(color, OUTLINE_COLOR, dots * fillMask);

    vec2 tailUv = vec2(0.147, 0.056);
    vec2 oppositeUv = vec2(0.94, 0.94);
    vec2 revealDirection = normalize(oppositeUv - tailUv);
    float revealCoordinate = dot(localUv - tailUv, revealDirection);
    float revealLength = dot(oppositeUv - tailUv, revealDirection);
    float revealFront = mix(-0.015, revealLength + 0.025, bubbleParameters.r);
    float revealAA = max(fwidth(revealCoordinate) * 1.5, 0.002);
    float revealMask = 1.0 - smoothstep(revealFront, revealFront + revealAA, revealCoordinate);

    float alpha = shapeAlpha * revealMask * bubbleParameters.a;
    if (alpha <= 0.001) {
        discard;
    }
    fragColor = vec4(color, alpha);
}
