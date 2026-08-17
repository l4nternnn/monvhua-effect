package com.kuilunfuzhe.monvhua.features.activity.emotion;

import net.minecraft.client.gui.DrawContext;

/** Small CPU preview of the shader effects used by the world bubble. */
final class EmotionProceduralPreviewRenderer {
    private static final int COLOR = 0xFF111111;

    private EmotionProceduralPreviewRenderer() {
    }

    static void render(DrawContext context, int contentId, int x, int y, int width, int height,
                       long millis, boolean animate) {
        long cycle = contentId == 11 ? 1600L : contentId == 12 ? 1200L
                : contentId == 14 ? 3200L : contentId == 17 ? 1800L : 1800L;
        double phase = animate ? (millis % cycle) / (double) cycle : 0.72;
        if (contentId == 11) {
            renderSleep(context, x, y, width, height, phase);
        } else if (contentId == 12) {
            renderScribble(context, x, y, width, height, phase);
        } else if (contentId == 13) {
            renderQuestions(context, x, y, width, height, phase);
        } else if (contentId == 14) {
            renderMagicDiary(context, x, y, width, height, phase);
        } else if (contentId == 17) {
            renderChest(context, x, y, width, height, phase);
        }
    }

    private static void renderChest(DrawContext c, int x, int y, int w, int h, double phase) {
        double rawOpen = Math.min(1.0, phase / 0.42);
        double open = 1.0 - Math.pow(1.0 - rawOpen, 3.0);
        int left = (int) Math.round(x + w * .18);
        int right = (int) Math.round(x + w * .82);
        int baseTop = (int) Math.round(y + h * .52);
        int baseBottom = (int) Math.round(y + h * .78);
        int lidHeight = Math.max(4, (int) Math.round(h * .17));
        int lidBottom = baseTop + 1;
        int lidTop = (int) Math.round(lidBottom - lidHeight - open * h * .22);

        c.fill(left, baseTop, right, baseBottom, 0xFF9A5F2B);
        c.fill(left + 2, baseTop + 2, right - 2, baseBottom - 2, 0xFFC98844);
        c.fill(left, baseTop, right, baseTop + 2, 0xFF5D351B);
        c.fill(left + w / 2 - 2, baseTop + 5, left + w / 2 + 3, baseTop + 12, 0xFFEDD28B);
        c.fill(left + w / 2 - 1, baseTop + 6, left + w / 2 + 2, baseTop + 11, 0xFF6C4A20);

        c.fill(left, lidTop, right, lidBottom, 0xFF75431E);
        c.fill(left + 2, lidTop + 2, right - 2, lidBottom - 2, 0xFFD4944A);
        c.fill(left, lidTop, right, lidTop + 2, 0xFF4C2B17);
        if (open > .05) {
            int insideTop = lidBottom - 1;
            int insideBottom = Math.min(baseTop + 5, insideTop + Math.max(2, (int) Math.round(open * h * .12)));
            c.fill(left + 2, insideTop, right - 2, insideBottom, 0xFF2D1A10);
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
                double t = i / 12.0;
                double[] next = scribblePoint(cx, cy, t, seed, strandPhase, w, h);
                line(c, previous[0], previous[1], next[0], next[1], 2, COLOR);
                previous = next;
            }
        }
        line(c, x + w * .22, y + h * .40, x + w * .78, y + h * .62, 1, COLOR);
        line(c, x + w * .28, y + h * .66, x + w * .80, y + h * .35, 1, COLOR);
        renderMagicWriting(c, x, y, w, h, phase);
    }

    private static void renderMagicWriting(DrawContext c, int x, int y, int w, int h, double phase) {
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 8; column++) {
                double left = x + w * (.17 + column * .085);
                double top = y + h * (.40 + row * .19);
                double glyph = (column + row * 11) * 7 + Math.floor(phase * 13.0);
                double skew = ((glyph % 5.0) - 2.0) * .006 * w;
                line(c, left, top + h * .035, left + w * .025 + skew, top, 1, COLOR);
                line(c, left + w * .025 + skew, top, left + w * .042, top + h * .060, 1, COLOR);
                if (((int) glyph & 1) == 0) {
                    line(c, left + w * .010, top + h * .030, left + w * .045, top + h * .030, 1, COLOR);
                }
            }
        }
    }

    private static void renderMagicDiary(DrawContext c, int x, int y, int w, int h, double phase) {
        for (int row = 0; row < 5; row++) {
            for (int page = 0; page < 2; page++) {
                double pageCenter = x + w * (page == 0 ? .30 : .70);
                for (int column = 0; column < 5; column++) {
                    int sequence = row * 10 + page * 5 + column;
                    if (phase * 55.0 < sequence - 1.0) {
                        continue;
                    }
                    drawRune(c, pageCenter + (column - 2) * w * .070,
                            y + h * (.36 + row * .090), w * .030, h * .040,
                            sequence * 3.0 + 17.0, COLOR);
                }
            }
        }
    }

    private static void drawRune(DrawContext c, double x, double y, double w, double h, double glyph, int color) {
        double skew = ((glyph % 5.0) - 2.0) * w * .25;
        line(c, x - w * .45, y + h * .35, x + skew, y - h * .38, 1, color);
        line(c, x + skew, y - h * .38, x + w * .45, y + h * .38, 1, color);
        if (((int) glyph & 1) == 0) {
            line(c, x - w * .22, y, x + w * .35, y, 1, color);
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
        double centerBias = t * 2.0 - 1.0;
        double px = centerBias * width * .30 + width * .15 * Math.sin(t * 5.0 + seed + phase * 1.7)
                + width * .065 * Math.sin(t * 11.0 - seed);
        double py = height * .03 + height * .25 * Math.sin(t * 3.0 + seed + phase * 1.2)
                + height * .11 * Math.cos(t * 8.0 - seed + phase * .7);
        return new double[]{
                cx + px,
                cy + py
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
