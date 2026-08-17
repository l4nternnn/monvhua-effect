package com.kuilunfuzhe.monvhua.features.activity.emotion;

import net.minecraft.client.gui.DrawContext;

/** Small CPU preview of the shader effects used by the world bubble. */
final class EmotionProceduralPreviewRenderer {
    private static final int COLOR = 0xFF111111;

    private EmotionProceduralPreviewRenderer() {
    }

    static void render(DrawContext context, int contentId, int x, int y, int width, int height,
                       long millis, boolean animate) {
        long cycle = contentId == 11 ? 1600L : contentId == 12 ? 1200L : 1800L;
        double phase = animate ? (millis % cycle) / (double) cycle : 0.72;
        if (contentId == 11) {
            renderSleep(context, x, y, width, height, phase);
        } else if (contentId == 12) {
            renderScribble(context, x, y, width, height, phase);
        } else if (contentId == 13) {
            renderQuestions(context, x, y, width, height, phase);
        }
    }

    private static void renderSleep(DrawContext c, int x, int y, int w, int h, double phase) {
        double travel = easeOutCubic(phase);
        double cx = x + w * (0.30 + 0.38 * travel);
        double cy = y + h * (0.63 - 0.30 * travel);
        int alpha = (int) Math.round(255 * Math.min(1.0, Math.min(phase / 0.10, (1.0 - phase) / 0.22)));
        int color = (alpha << 24) | 0x111111;
        double s = Math.min(w, h) * (0.11 + 0.13 * travel);
        double angle = -0.14 + 0.22 * travel;
        double stroke = Math.max(1.5, Math.min(w, h) * 0.018);
        curve(c, rotateAround(cx, cy, -s, -s * .62, angle),
                rotateAround(cx, cy, -s * .35, -s * .69, angle),
                rotateAround(cx, cy, s * .42, -s * .57, angle),
                rotateAround(cx, cy, s, -s * .62, angle), stroke, color);
        curve(c, rotateAround(cx, cy, s, -s * .62, angle),
                rotateAround(cx, cy, s * .62, -s * .42, angle),
                rotateAround(cx, cy, -s * .58, s * .42, angle),
                rotateAround(cx, cy, -s, s * .62, angle), stroke, color);
        curve(c, rotateAround(cx, cy, -s, s * .62, angle),
                rotateAround(cx, cy, -s * .40, s * .70, angle),
                rotateAround(cx, cy, s * .36, s * .55, angle),
                rotateAround(cx, cy, s, s * .62, angle), stroke, color);
    }

    private static void renderScribble(DrawContext c, int x, int y, int w, int h, double phase) {
        double cx = x + w * .5;
        double cy = y + h * .52;
        for (int strand = 0; strand < 3; strand++) {
            double seed = new double[]{0.7, 2.4, 4.9}[strand];
            double strandPhase = phase * (strand == 1 ? 1.13 : strand == 2 ? .87 : 1.0)
                    + (strand == 1 ? .4 : strand == 2 ? -.3 : 0.0);
            double[] previous = scribblePoint(cx, cy, 0.0, seed, strandPhase, w, h);
            for (int i = 1; i <= 12; i++) {
                double t = Math.PI * 2.0 * i / 12.0;
                double[] next = scribblePoint(cx, cy, t, seed, strandPhase, w, h);
                line(c, previous[0], previous[1], next[0], next[1], 2, COLOR);
                previous = next;
            }
        }
    }

    private static void renderQuestions(DrawContext c, int x, int y, int w, int h, double phase) {
        for (int index = 0; index < 3; index++) {
            double local = phase - index * 0.139;
            if (local < 0.0) {
                continue;
            }
            double entry = Math.min(1.0, local / .22);
            double rise = 1.0 - ease(entry);
            double angle = Math.PI / 2.0 * (1.0 - ease(entry));
            double cx = x + w * (.24 + index * .26);
            double cy = y + h * (.52 + .14 * rise);
            drawQuestion(c, cx, cy, Math.min(w, h) * .16, angle, entry);
        }
    }

    private static void drawQuestion(DrawContext c, double cx, double cy, double s, double angle, double alpha) {
        int color = (((int) Math.round(255 * Math.min(1.0, alpha * 1.5))) << 24) | 0x111111;
        curve(c, rotateAround(cx, cy, 0.0, -0.72 * s, angle),
                rotateAround(cx, cy, 0.35 * s, -0.76 * s, angle),
                rotateAround(cx, cy, 0.64 * s, -0.60 * s, angle),
                rotateAround(cx, cy, 0.60 * s, -0.22 * s, angle), 2, color);
        curve(c, rotateAround(cx, cy, 0.60 * s, -0.22 * s, angle),
                rotateAround(cx, cy, 0.55 * s, 0.08 * s, angle),
                rotateAround(cx, cy, 0.15 * s, 0.08 * s, angle),
                rotateAround(cx, cy, 0.0, 0.34 * s, angle), 2, color);
        double[] dot = rotate(0, .76 * s, angle);
        circle(c, cx + dot[0], cy + dot[1], Math.max(1.5, s * .12), color);
    }

    private static double ease(double value) {
        value = Math.max(0.0, Math.min(1.0, value));
        return value * value * (3.0 - 2.0 * value);
    }

    private static double easeOutCubic(double value) {
        double inverse = 1.0 - Math.max(0.0, Math.min(1.0, value));
        return 1.0 - inverse * inverse * inverse;
    }

    private static double[] scribblePoint(double cx, double cy, double t, double seed,
                                          double phase, int width, int height) {
        double radiusX = width * (.25 + .048 * Math.sin(t * 3.0 + seed)
                + .035 * Math.cos(t * 7.0 - seed));
        double radiusY = height * (.19 + .042 * Math.cos(t * 4.0 - seed)
                + .025 * Math.sin(t * 9.0 + seed));
        double angle = t + .30 * Math.sin(t * 2.0 + seed) + phase * .45;
        return new double[]{
                cx + Math.cos(angle) * radiusX + width * .043 * Math.sin(t * 5.0 + seed),
                cy + Math.sin(angle) * radiusY + height * .043 * Math.cos(t * 6.0 - seed)
        };
    }

    private static double[] rotate(double x, double y, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new double[]{x * cos - y * sin, x * sin + y * cos};
    }

    private static double[] rotateAround(double cx, double cy, double x, double y, double angle) {
        double[] rotated = rotate(x, y, angle);
        return new double[]{cx + rotated[0], cy + rotated[1]};
    }

    private static void curve(DrawContext c, double[] p0, double[] p1, double[] p2, double[] p3,
                              double width, int color) {
        double[] previous = p0;
        for (int i = 1; i <= 8; i++) {
            double t = i / 8.0;
            double u = 1.0 - t;
            double[] next = new double[]{
                    u * u * u * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t * t * t * p3[0],
                    u * u * u * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t * t * t * p3[1]
            };
            line(c, previous[0], previous[1], next[0], next[1], Math.max(1, (int) Math.round(width)), color);
            previous = next;
        }
    }

    private static void line(DrawContext c, double x0, double y0, double x1, double y1, int width, int color) {
        int steps = Math.max(1, (int) Math.ceil(Math.hypot(x1 - x0, y1 - y0) * 1.5));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int x = (int) Math.round(x0 + (x1 - x0) * t);
            int y = (int) Math.round(y0 + (y1 - y0) * t);
            c.fill(x - width / 2, y - width / 2, x + width / 2 + 1, y + width / 2 + 1, color);
        }
    }

    private static void circle(DrawContext c, double cx, double cy, double radius, int color) {
        int top = (int) Math.floor(cy - radius);
        int bottom = (int) Math.ceil(cy + radius);
        for (int y = top; y <= bottom; y++) {
            double dy = y - cy;
            double dx = Math.sqrt(Math.max(0.0, radius * radius - dy * dy));
            c.fill((int) Math.floor(cx - dx), y, (int) Math.ceil(cx + dx) + 1, y + 1, color);
        }
    }
}
