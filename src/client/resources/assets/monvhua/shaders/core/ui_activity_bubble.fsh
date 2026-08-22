#version 150

in vec2 localUv;
in vec4 bubbleParameters;

out vec4 fragColor;

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

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

int pixelPointKind(vec2 p, vec2 center) {
    const float cellSize = 0.017;
    vec2 local = p - center;
    float column = floor((local.x + cellSize * 2.0) / cellSize);
    float row = floor((-local.y + cellSize * 2.0) / cellSize);
    if (column < 0.0 || column > 3.0 || row < 0.0 || row > 3.0) {
        return 0;
    }

    // 4x4 pixel glyph matching point.png. 1 is the black theme pixel,
    // 2 is the gray shadow pixel.
    if (row < 2.0) {
        return column <= 2.0 ? 1 : 0;
    }
    if (row < 3.0) {
        return column <= 2.0 ? 1 : 2;
    }
    return column >= 2.0 ? 2 : 0;
}

int pixelFrameKind() {
    int x = int(clamp(floor(localUv.x * 28.0), 0.0, 27.0));
    int y = int(clamp(floor((1.0 - localUv.y) * 18.0), 0.0, 17.0));

    if (y == 0) {
        return x >= 8 && x <= 22 ? 2 : 0;
    } else if (y == 1) {
        if (x == 6 || x == 24) return 2;
        if (x == 7 || x == 23) return 3;
        if (x == 8 || x == 22) return 4;
        return x >= 9 && x <= 21 ? 1 : 0;
    } else if (y == 2) {
        if (x == 5 || x == 25) return 2;
        if (x == 6 || x == 24) return 4;
        return x >= 7 && x <= 23 ? 1 : 0;
    } else if (y == 3) {
        if (x == 4 || x == 26) return 2;
        if (x == 5 || x == 25) return 4;
        return x >= 6 && x <= 24 ? 1 : 0;
    } else if (y == 4) {
        if (x == 4 || x == 26) return 3;
        return x >= 5 && x <= 25 ? 1 : 0;
    } else if (y == 5) {
        if (x == 3 || x == 27) return 2;
        if (x == 4 || x == 26) return 4;
        return x >= 5 && x <= 25 ? 1 : 0;
    } else if (y == 6 || y == 7) {
        if (x == 3) return 3;
        if (x == 27) return 2;
        return x >= 4 && x <= 26 ? 1 : 0;
    } else if (y == 8) {
        if (x == 3) return 3;
        if (x == 26) return 4;
        if (x == 27) return 2;
        return x >= 4 && x <= 25 ? 1 : 0;
    } else if (y == 9) {
        if (x == 3) return 2;
        if (x == 26) return 3;
        return x >= 4 && x <= 25 ? 1 : 0;
    } else if (y == 10) {
        if (x == 3 || x == 26) return 2;
        if (x == 4 || x == 25) return 4;
        return x >= 5 && x <= 24 ? 1 : 0;
    } else if (y == 11) {
        if (x == 3 || x == 25) return 2;
        if (x == 24) return 4;
        return x >= 4 && x <= 23 ? 1 : 0;
    } else if (y == 12) {
        if (x == 2 || x == 6 || x == 24) return 2;
        if (x == 7 || x == 8 || x == 23) return 3;
        if (x == 9 || x == 22) return 4;
        return (x >= 3 && x <= 5) || (x >= 10 && x <= 21) ? 1 : 0;
    } else if (y == 13) {
        if (x == 2) return 3;
        if (x == 5 || x == 6 || (x >= 8 && x <= 22)) return 2;
        return x == 3 || x == 4 ? 1 : 0;
    } else if (y == 14) {
        if (x == 1 || x == 4) return 2;
        if (x == 2) return 4;
        return x == 3 ? 1 : 0;
    } else if (y == 15) {
        if (x == 1) return 3;
        if (x == 3) return 2;
        return x == 2 ? 1 : 0;
    } else if (y == 16) {
        if (x == 0 || x == 2) return 2;
        if (x == 1) return 4;
    } else if (y == 17) {
        return x <= 1 ? 2 : 0;
    }
    return 0;
}

vec3 pixelFrameColor(int kind) {
    if (kind == 2) {
        return OUTLINE_COLOR;
    } else if (kind == 3) {
        return vec3(0.412, 0.392, 0.392);
    } else if (kind == 4) {
        return vec3(0.667, 0.659, 0.659);
    }
    return FILL_COLOR;
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

// Each bite is a large circular cut with tangent small circles on its upper edge.
// For a small radius r inside a large radius R, d=sqrt(R^2-r^2) puts both
// endpoints of the small circle's tangent diameter on the large circle.
float foodBiteShape(vec2 p, vec2 center, float fullRadius, float progress) {
    float bigRadius = mix(0.001, fullRadius, progress);
    float smallRadius = bigRadius * 0.34;
    float diameterOffset = sqrt(max(bigRadius * bigRadius
        - smallRadius * smallRadius, 0.0));
    float mask = circleMask(p, center, bigRadius);
    for (int index = 0; index < 4; index++) {
        float angle = mix(0.35, 2.79, float(index) / 3.0);
        vec2 radial = vec2(cos(angle), sin(angle));
        mask = max(mask, circleMask(
            p, center + radial * diameterOffset, smallRadius));
    }
    return mask;
}

vec2 foodBiteCenter(int index, float breadFood) {
    if (index == 0) {
        return mix(vec2(0.110, 0.137), vec2(0.115, 0.132), breadFood);
    }
    if (index == 1) {
        return mix(vec2(0.110, 0.047), vec2(0.115, 0.032), breadFood);
    }
    if (index == 2) {
        return mix(vec2(0.014, 0.101), vec2(0.010, 0.090), breadFood);
    }
    return mix(vec2(-0.093, -0.030), vec2(-0.103, -0.043), breadFood);
}

float foodBiteRadius(int index, float breadFood) {
    if (index == 0) {
        return mix(0.077, 0.087, breadFood);
    }
    if (index == 1) {
        return mix(0.086, 0.100, breadFood);
    }
    if (index == 2) {
        return mix(0.086, 0.094, breadFood);
    }
    return mix(0.099, 0.107, breadFood);
}

float foodBiteMask(vec2 p, float phase, float breadFood) {
    // A bite reaches its cleanup radius before the next bite may begin.
    float mask = 0.0;
    for (int index = 0; index < 4; index++) {
        float start = 0.12 + float(index) * 0.22;
        float progress = smoothstep(start, start + 0.16, phase);
        mask = max(mask, foodBiteShape(
            p,
            foodBiteCenter(index, breadFood),
            foodBiteRadius(index, breadFood),
            progress
        ));
    }
    return mask;
}

float foodCrumbMask(vec2 p, float phase, float breadFood) {
    float mask = 0.0;
    for (int index = 0; index < 4; index++) {
        float start = 0.12 + float(index) * 0.22;
        float local = clamp((phase - start) / 0.16, 0.0, 1.0);
        float appear = smoothstep(0.05, 0.32, local);
        float fade = 1.0 - smoothstep(0.66, 1.0, local);
        vec2 direction = mix(vec2(0.025, 0.050), vec2(0.031, 0.044), breadFood);
        vec2 center = foodBiteCenter(index, breadFood) + direction * easeOutCubic(local);
        mask = max(mask, circleMask(p, center, mix(0.009, 0.004, local))
            * appear * fade);
    }
    return mask;
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

// Sleep is deliberately rendered as three independent cloud sprites.  This
// branch is entered before the normal speech-bubble SDF is evaluated, so the
// clouds cannot inherit the bubble body or its tail.
float cloudSdf(vec2 p) {
    // A conventional cloud silhouette: a short rounded base with three lobes.
    float shape = sdRoundedBox(p - vec2(0.0, 0.18), vec2(0.52, 0.19), 0.19);
    shape = smoothUnion(shape, length(p - vec2(-0.33, 0.04)) - 0.27, 0.075);
    shape = smoothUnion(shape, length(p - vec2(0.00, -0.06)) - 0.37, 0.075);
    shape = smoothUnion(shape, length(p - vec2(0.33, 0.05)) - 0.26, 0.075);
    return shape;
}

float sleepCloudFace(vec2 p, float variant) {
    float mask = 0.0;
    float eyeY = -0.045 + variant * 0.010;
    // q.y grows toward the lower part of the cloud. U-shaped eyes therefore
    // use control points slightly below their endpoints.
    mask = max(mask, cubicStroke(p, vec2(-0.175, eyeY), vec2(-0.147, eyeY + 0.027),
        vec2(-0.112, eyeY + 0.027), vec2(-0.084, eyeY), 0.014));
    mask = max(mask, cubicStroke(p, vec2(0.084, eyeY), vec2(0.112, eyeY + 0.027),
        vec2(0.147, eyeY + 0.027), vec2(0.175, eyeY), 0.014));
    // A compact W: two low points separated by a raised center.
    mask = max(mask, cubicStroke(p, vec2(-0.098, 0.105), vec2(-0.074, 0.133),
        vec2(-0.049, 0.133), vec2(-0.025, 0.105), 0.013));
    mask = max(mask, cubicStroke(p, vec2(-0.025, 0.105), vec2(-0.008, 0.077),
        vec2(0.008, 0.077), vec2(0.025, 0.105), 0.013));
    mask = max(mask, cubicStroke(p, vec2(0.025, 0.105), vec2(0.049, 0.133),
        vec2(0.074, 0.133), vec2(0.098, 0.105), 0.013));
    return mask;
}

vec4 sleepCloudPixel(vec2 p, vec2 center, float size, float appear, float variant) {
    vec2 q = (p - center) / max(size, 0.001);
    float distanceToCloud = cloudSdf(q);
    float edgeAA = max(fwidth(distanceToCloud) * 1.25, 0.002);
    float shapeAlpha = 1.0 - smoothstep(-edgeAA, edgeAA, distanceToCloud);
    if (shapeAlpha <= 0.001 || appear <= 0.001) {
        return vec4(0.0);
    }

    float strokeWidth = 0.075;
    float fillMask = 1.0 - smoothstep(-strokeWidth - edgeAA,
        -strokeWidth + edgeAA, distanceToCloud);
    vec3 color = mix(OUTLINE_COLOR, FILL_COLOR, fillMask);
    // q is the single sleep-cloud coordinate space. The cloud silhouette and
    // all facial marks must use it directly; a second Y flip puts the face
    // below the cheeks after the off-screen texture correction in main().
    float face = sleepCloudFace(q, variant);
    color = mix(color, OUTLINE_COLOR, face * fillMask);
    // Cheeks sit between the eyes and the mouth, with no overlap into either.
    float cheekLeft = circleMask(q, vec2(-0.23, 0.085), 0.042);
    float cheekRight = circleMask(q, vec2(0.23, 0.085), 0.042);
    color = mix(color, vec3(0.88, 0.63, 0.67),
        max(cheekLeft, cheekRight) * fillMask * 0.52);
    return vec4(color, shapeAlpha * appear);
}

vec4 sleepOverlay(vec4 base, vec4 layer) {
    float alpha = layer.a + base.a * (1.0 - layer.a);
    if (alpha <= 0.001) {
        return vec4(0.0);
    }
    vec3 color = (layer.rgb * layer.a + base.rgb * base.a * (1.0 - layer.a)) / alpha;
    return vec4(color, alpha);
}

vec4 renderSleepClouds(vec2 p, float phase, float reveal) {
    float time = phase * 5.20;
    float cycleFade = 1.0 - smoothstep(4.36, 5.20, time);
    vec4 result = vec4(0.0);

    // The small cloud starts at the head, then the two higher clouds follow it.
    float first = smoothstep(0.00, 0.56, time);
    float second = smoothstep(0.64, 1.24, time);
    float third = smoothstep(1.32, 1.96, time);
    float firstScale = 0.78 + first * (1.0 + 0.14 * sin(PI * first));
    float secondScale = 0.78 + second * (1.0 + 0.14 * sin(PI * second));
    float thirdScale = 0.78 + third * (1.0 + 0.14 * sin(PI * third));
    result = sleepOverlay(result, sleepCloudPixel(
        // The first/small cloud belongs closest to the head (visual bottom).
        p, vec2(-0.205, 0.225 + first * 0.018), 0.063 * firstScale,
        first * cycleFade * reveal, 0.0));
    result = sleepOverlay(result, sleepCloudPixel(
        p, vec2(0.015, 0.105 + second * 0.020), 0.0966667 * secondScale,
        second * cycleFade * reveal, 0.35));
    result = sleepOverlay(result, sleepCloudPixel(
        // The last/large cloud floats toward the visual top.
        p, vec2(0.283333, -0.063333 + third * 0.022), 0.185 * thirdScale,
        third * cycleFade * reveal, 0.70));

    return result;
}

vec2 scribblePoint(float t, float seed, float phase) {
    float centerBias = t * 2.0 - 1.0;
    float x = centerBias * 0.16 + 0.080 * sin(t * 5.0 + seed + phase * 1.7)
        + 0.035 * sin(t * 11.0 - seed);
    float y = 0.055 + 0.090 * sin(t * 3.0 + seed + phase * 1.2)
        + 0.040 * cos(t * 8.0 - seed + phase * 0.7);
    return vec2(x, y);
}

float scribbleStrand(vec2 p, float seed, float phase) {
    float mask = 0.0;
    vec2 previous = scribblePoint(0.0, seed, phase);
    for (int index = 1; index <= 12; index++) {
        float t = float(index) / 12.0;
        vec2 next = scribblePoint(t, seed, phase);
        mask = max(mask, roundLineMask(p, previous, next, 0.012));
        previous = next;
    }
    return mask;
}

float scribbleMask(vec2 p, float phase) {
    float mask = scribbleStrand(p, 0.7, phase);
    mask = max(mask, scribbleStrand(p, 2.4, phase * 1.13 + 0.4));
    mask = max(mask, scribbleStrand(p, 4.9, phase * 0.87 - 0.3));
    mask = max(mask, scribbleStrand(p, 7.1, phase * 1.31 + 1.1));
    mask = max(mask, roundLineMask(p, vec2(-0.13, 0.02), vec2(0.12, 0.10), 0.009));
    mask = max(mask, roundLineMask(p, vec2(-0.10, 0.11), vec2(0.13, -0.01), 0.009));
    return mask;
}

float magicRuneMask(vec2 p, vec2 center, vec2 size, float glyph) {
    vec2 cell = (p - center) / size;
    float inside = step(abs(cell.x), 0.5) * step(abs(cell.y), 0.5);
    float glyphIndex = mod(glyph, 26.0);
    float atlasColumn = glyphIndex < 15.0 ? glyphIndex + 1.0 : glyphIndex - 15.0;
    float atlasRow = glyphIndex < 15.0 ? 4.0 : 5.0;
    vec2 atlasUv = vec2((atlasColumn + cell.x + 0.5) / 16.0,
        (atlasRow + cell.y + 0.5) / 16.0);
    return inside * texture(Sampler1, atlasUv).a;
}

float diaryWritingMask(vec2 p, float phase) {
    float mask = 0.0;
    for (int row = 0; row < 5; row++) {
        for (int page = 0; page < 2; page++) {
            for (int column = 0; column < 5; column++) {
                float sequence = float(row * 10 + page * 5 + column);
                float pageOffset = page == 0 ? -0.16 : 0.16;
                float glyph = sequence * 3.0 + 17.0;
                // World UV Y is opposite to DrawContext Y: row zero must be at the visual top.
                vec2 center = vec2(pageOffset + (float(column) - 2.0) * 0.041,
                    0.130 - float(row) * 0.045);
                float reveal = smoothstep(sequence - 1.0, sequence + 0.25, phase * 55.0);
                mask = max(mask, magicRuneMask(p, center, vec2(0.043, 0.057), glyph) * reveal);
            }
        }
    }
    return mask;
}

float questionGlyph(vec2 p, vec2 center, float angle) {
    vec2 q = rotatePoint(vec2(p.x - center.x, -(p.y - center.y)), -angle);
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

    int contentId = int(floor(bubbleParameters.b * 255.0 + 0.5));
    if (contentId == 11) {
        // The off-screen framebuffer and the final composite quad use opposite
        // vertical origins. Keep this correction local to sleep clouds.
        vec2 sleepP = vec2((localUv.x - 0.5) * 0.92,
            ((1.0 - localUv.y) - 0.5) * 0.62);
        vec4 sleep = renderSleepClouds(sleepP, bubbleParameters.g, bubbleParameters.r);
        if (sleep.a <= 0.001) {
            discard;
        }
        fragColor = vec4(sleep.rgb, sleep.a * bubbleParameters.a);
        return;
    }

    bool pixelStyle = bubbleParameters.a < 0.998;
    float body;
    float edgeAA;
    float shapeAlpha;
    float fillMask;
    vec3 color;
    if (pixelStyle) {
        int frameKind = pixelFrameKind();
        body = 0.0;
        edgeAA = 0.0;
        shapeAlpha = frameKind == 0 ? 0.0 : 1.0;
        fillMask = frameKind == 1 ? 1.0 : 0.0;
        color = pixelFrameColor(frameKind);
    } else {
        vec2 shapeP = p;
        body = sdRoundedBox(shapeP - vec2(0.0, 0.065), vec2(0.39, 0.19), 0.095);
        float tail = sdTriangle(
            shapeP,
            vec2(-0.235, -0.105),
            vec2(-0.325, -0.275),
            vec2(-0.055, -0.115)
        ) - 0.008;
        float bubble = smoothUnion(body, tail, 0.022);
        edgeAA = max(fwidth(bubble) * 1.2, 0.0009);
        shapeAlpha = 1.0 - smoothstep(-edgeAA, edgeAA, bubble);
        fillMask = 1.0 - smoothstep(-0.018 - edgeAA, -0.018 + edgeAA, bubble);
        color = mix(OUTLINE_COLOR, FILL_COLOR, fillMask);
    }
    if (shapeAlpha <= 0.001) {
        discard;
    }

    float strokeWidth = pixelStyle ? 0.0 : 0.018;

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

    float hasContent = step(0.5 / 255.0, bubbleParameters.b);
    float procedural = step(10.5, float(contentId)) * step(float(contentId), 14.5);
    float blockDisplay = step(16.5, float(contentId)) * step(float(contentId), 17.5);
    float appleFood = step(17.5, float(contentId)) * step(float(contentId), 18.5);
    float breadFood = step(18.5, float(contentId)) * step(float(contentId), 19.5);
    float eatFood = max(appleFood, breadFood);
    float themeDots = 0.0;
    float shadowDots = 0.0;
    if (pixelStyle) {
        int firstPoint = pixelPointKind(p, vec2(-0.14, 0.055 + firstJump));
        int secondPoint = pixelPointKind(p, vec2(0.0, 0.055 + secondJump));
        int thirdPoint = pixelPointKind(p, vec2(0.14, 0.055 + thirdJump));
        themeDots = max(themeDots, firstPoint == 1 ? 1.0 : 0.0);
        themeDots = max(themeDots, secondPoint == 1 ? 1.0 : 0.0);
        themeDots = max(themeDots, thirdPoint == 1 ? 1.0 : 0.0);
        shadowDots = (firstPoint == 2 ? 1.0 : 0.0)
            * (1.0 - smoothstep(0.0, 0.055, firstJump));
        shadowDots = max(shadowDots,
            (secondPoint == 2 ? 1.0 : 0.0)
                * (1.0 - smoothstep(0.0, 0.055, secondJump)));
        shadowDots = max(shadowDots,
            (thirdPoint == 2 ? 1.0 : 0.0)
                * (1.0 - smoothstep(0.0, 0.055, thirdJump)));
        color = mix(color, vec3(0.667, 0.659, 0.659),
            shadowDots * (1.0 - hasContent));
    } else {
        themeDots = max(themeDots, circleMask(p, vec2(-0.14, 0.055 + firstJump), 0.034));
        themeDots = max(themeDots, circleMask(p, vec2(0.0, 0.055 + secondJump), 0.034));
        themeDots = max(themeDots, circleMask(p, vec2(0.14, 0.055 + thirdJump), 0.034));
    }
    color = mix(color, OUTLINE_COLOR, themeDots * fillMask * (1.0 - hasContent));

    vec2 imageUv = vec2(
        (p.x + 0.345) / 0.69,
        1.0 - ((p.y + 0.085) / 0.29)
    );
    float imageRegion = step(0.0, imageUv.x) * step(imageUv.x, 1.0)
        * step(0.0, imageUv.y) * step(imageUv.y, 1.0);
    const float foodWidth = 0.29 / 0.69;
    float foodMinX = (1.0 - foodWidth) * 0.5;
    float foodMaxX = foodMinX + foodWidth;
    float foodRegion = step(foodMinX, imageUv.x) * step(imageUv.x, foodMaxX)
        * step(0.0, imageUv.y) * step(imageUv.y, 1.0);
    vec2 sampledImageUv = imageUv;
    if (blockDisplay > 0.5) {
        sampledImageUv = vec2(imageUv.x, 1.0 - imageUv.y);
    } else if (eatFood > 0.5) {
        // Map the complete square vanilla item texture into a square slot.
        sampledImageUv = vec2((imageUv.x - foodMinX) / foodWidth, imageUv.y);
    }
    vec4 imageColor = texture(Sampler0, clamp(sampledImageUv, 0.0, 1.0));
    float biteMask = eatFood > 0.5
        ? foodBiteMask(p, bubbleParameters.g, breadFood) : 0.0;
    float contentRegion = eatFood > 0.5 ? foodRegion : imageRegion;
    float imageAmount = hasContent * (1.0 - procedural)
        * contentRegion * fillMask * imageColor.a * (1.0 - biteMask);
    color = mix(color, imageColor.rgb, imageAmount);

    if (eatFood > 0.5) {
        float crumbs = foodCrumbMask(p, bubbleParameters.g, breadFood) * fillMask;
        color = mix(color, vec3(0.70, 0.38, 0.12), crumbs);
    }

    if (contentId == 14) {
        // 11.png contains generous margins; crop them while keeping the page artwork in the body.
        // Map the visible body (roughly v=.30.. .90) to the complete page crop.
        vec2 diaryUv = vec2(localUv.x, 0.10 + (0.90 - localUv.y) * 1.3333333);
        vec4 diaryImage = texture(Sampler0, clamp(diaryUv, 0.0, 1.0));
        float bodyFill = pixelStyle ? fillMask : 1.0 - smoothstep(
            -strokeWidth - edgeAA,
            -strokeWidth + edgeAA,
            body
        );
        float diaryRegion = step(-0.36, p.x) * step(p.x, 0.36) * bodyFill;
        color = mix(color, diaryImage.rgb, diaryRegion * diaryImage.a);
    }

    float proceduralMask = 0.0;
    if (contentId == 11) {
        proceduralMask = sleepZMask(p, bubbleParameters.g);
    } else if (contentId == 12) {
        proceduralMask = scribbleMask(p, bubbleParameters.g);
    } else if (contentId == 13) {
        proceduralMask = questionsMask(p, bubbleParameters.g);
    } else if (contentId == 14) {
        proceduralMask = diaryWritingMask(p, bubbleParameters.g);
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
