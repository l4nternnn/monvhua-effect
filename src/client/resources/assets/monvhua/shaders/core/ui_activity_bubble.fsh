#version 150

in vec2 localUv;
in vec4 bubbleParameters;

out vec4 fragColor;

uniform sampler2D Sampler0;

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

float lineMask(vec2 p, vec2 a, vec2 b, float width) {
    vec2 delta = b - a;
    float along = clamp(dot(p - a, delta) / max(dot(delta, delta), 0.00001), 0.0, 1.0);
    float distanceToLine = length(p - mix(a, b, along)) - width;
    float aa = max(fwidth(distanceToLine) * 1.2, 0.001);
    return 1.0 - smoothstep(-aa, aa, distanceToLine);
}

vec2 rotatePoint(vec2 p, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return vec2(c * p.x - s * p.y, s * p.x + c * p.y);
}

float easeOutCubic(float value) {
    float inverse = 1.0 - clamp(value, 0.0, 1.0);
    return 1.0 - inverse * inverse * inverse;
}

float roundLineMask(vec2 p, vec2 a, vec2 b, float width) {
    return max(lineMask(p, a, b, width),
        max(circleMask(p, a, width), circleMask(p, b, width)));
}

vec2 cubicPoint(vec2 p0, vec2 p1, vec2 p2, vec2 p3, float t) {
    float u = 1.0 - t;
    return u * u * u * p0 + 3.0 * u * u * t * p1
        + 3.0 * u * t * t * p2 + t * t * t * p3;
}

float cubicStroke(vec2 p, vec2 p0, vec2 p1, vec2 p2, vec2 p3, float width) {
    float mask = 0.0;
    vec2 previous = p0;
    for (int index = 1; index <= 8; index++) {
        float t = float(index) / 8.0;
        vec2 next = cubicPoint(p0, p1, p2, p3, t);
        mask = max(mask, roundLineMask(p, previous, next, width));
        previous = next;
    }
    return mask;
}

float sleepZMask(vec2 p, float phase) {
    float travel = easeOutCubic(phase);
    vec2 center = cubicPoint(vec2(-0.22, -0.11), vec2(-0.18, -0.16),
        vec2(0.15, 0.05), vec2(0.22, 0.16), travel);
    float scale = mix(0.060, 0.135, easeOutCubic(phase));
    float angle = mix(-0.14, 0.08, easeOutCubic(phase));
    float width = mix(0.010, 0.015, scale / 0.135);
    vec2 topA = rotatePoint(vec2(-scale, -scale * 0.62), angle) + center;
    vec2 topB = rotatePoint(vec2(scale, -scale * 0.62), angle) + center;
    vec2 mid = rotatePoint(vec2(-scale * 0.03, scale * 0.05), angle) + center;
    vec2 bottomA = rotatePoint(vec2(-scale, scale * 0.62), angle) + center;
    vec2 bottomB = rotatePoint(vec2(scale, scale * 0.62), angle) + center;
    float mask = cubicStroke(p, topA,
        rotatePoint(vec2(-scale * 0.35, -scale * 0.69), angle) + center,
        rotatePoint(vec2(scale * 0.42, -scale * 0.57), angle) + center, topB, width);
    mask = max(mask, cubicStroke(p, topB,
        rotatePoint(vec2(scale * 0.62, -scale * 0.42), angle) + center,
        rotatePoint(vec2(-scale * 0.58, scale * 0.42), angle) + center, bottomA, width));
    mask = max(mask, cubicStroke(p, bottomA,
        rotatePoint(vec2(-scale * 0.40, scale * 0.70), angle) + center,
        rotatePoint(vec2(scale * 0.36, scale * 0.55), angle) + center, bottomB, width));
    float fadeIn = smoothstep(0.0, 0.10, phase);
    float fadeOut = 1.0 - smoothstep(0.78, 1.0, phase);
    return mask * fadeIn * fadeOut;
}

vec2 scribblePoint(float t, float seed, float phase) {
    float radiusX = 0.13 + 0.025 * sin(t * 3.0 + seed) + 0.018 * cos(t * 7.0 - seed);
    float radiusY = 0.090 + 0.020 * cos(t * 4.0 - seed) + 0.012 * sin(t * 9.0 + seed);
    float angle = t + 0.30 * sin(t * 2.0 + seed) + phase * 0.45;
    vec2 point = vec2(cos(angle) * radiusX, sin(angle) * radiusY);
    point += vec2(0.022 * sin(t * 5.0 + seed), 0.018 * cos(t * 6.0 - seed));
    return point + vec2(0.0, 0.055);
}

float scribbleStrand(vec2 p, float seed, float phase) {
    float mask = 0.0;
    vec2 previous = scribblePoint(0.0, seed, phase);
    for (int index = 1; index <= 12; index++) {
        float t = 6.2831853 * float(index) / 12.0;
        vec2 next = scribblePoint(t, seed, phase);
        mask = max(mask, roundLineMask(p, previous, next, 0.011));
        previous = next;
    }
    return mask;
}

float scribbleMask(vec2 p, float phase) {
    float mask = scribbleStrand(p, 0.7, phase);
    mask = max(mask, scribbleStrand(p, 2.4, phase * 1.13 + 0.4));
    mask = max(mask, scribbleStrand(p, 4.9, phase * 0.87 - 0.3));
    return mask;
}

float questionGlyph(vec2 p, vec2 center, float angle) {
    vec2 q = rotatePoint(p - center, -angle);
    float mask = cubicStroke(q, vec2(0.0, -0.085), vec2(0.035, -0.090),
        vec2(0.070, -0.070), vec2(0.065, -0.025), 0.013);
    mask = max(mask, cubicStroke(q, vec2(0.065, -0.025), vec2(0.060, 0.010),
        vec2(0.015, 0.012), vec2(0.0, 0.040), 0.013));
    mask = max(mask, circleMask(q, vec2(0.0, 0.092), 0.017));
    return mask;
}

float questionsMask(vec2 p, float phase) {
    float mask = 0.0;
    for (int index = 0; index < 3; index++) {
        float start = float(index) * 0.139;
        float local = phase - start;
        float enabled = step(0.0, local);
        float entry = clamp(local / 0.22, 0.0, 1.0);
        float rise = 1.0 - smoothstep(0.0, 1.0, entry);
        float angle = PI * 0.5 * (1.0 - smoothstep(0.0, 1.0, entry));
        vec2 center = vec2(-0.16 + float(index) * 0.16, 0.055 + rise * 0.12);
        float alpha = enabled * smoothstep(0.0, 0.12, local);
        mask = max(mask, questionGlyph(p, center, angle) * alpha);
    }
    return mask;
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

    int contentId = int(floor(bubbleParameters.b * 255.0 + 0.5));
    float hasContent = step(0.5 / 255.0, bubbleParameters.b);
    float procedural = step(10.5, float(contentId)) * step(float(contentId), 13.5);
    float dots = 0.0;
    dots = max(dots, circleMask(p, vec2(-0.14, 0.055 + firstJump), 0.034));
    dots = max(dots, circleMask(p, vec2(0.0, 0.055 + secondJump), 0.034));
    dots = max(dots, circleMask(p, vec2(0.14, 0.055 + thirdJump), 0.034));
    color = mix(color, OUTLINE_COLOR, dots * fillMask * (1.0 - hasContent));

    vec2 imageUv = vec2(
        (p.x + 0.345) / 0.69,
        1.0 - ((p.y + 0.085) / 0.29)
    );
    float imageRegion = step(0.0, imageUv.x) * step(imageUv.x, 1.0)
        * step(0.0, imageUv.y) * step(imageUv.y, 1.0);
    vec4 imageColor = texture(Sampler0, clamp(imageUv, 0.0, 1.0));
    float imageAmount = hasContent * (1.0 - procedural) * imageRegion * fillMask * imageColor.a;
    color = mix(color, imageColor.rgb, imageAmount);

    float proceduralMask = 0.0;
    if (contentId == 11) {
        proceduralMask = sleepZMask(p, bubbleParameters.g);
    } else if (contentId == 12) {
        proceduralMask = scribbleMask(p, bubbleParameters.g);
    } else if (contentId == 13) {
        proceduralMask = questionsMask(p, bubbleParameters.g);
    }
    color = mix(color, OUTLINE_COLOR, proceduralMask * fillMask);

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
