package com.kuilunfuzhe.monvhua.features.textarea;

import java.util.function.Consumer;

public final class GenericDragger {
    private static final int HANDLE = 10;
    private boolean enabled;
    private Consumer<Transform> callback = transform -> {
    };
    private Target target;
    private Mode mode = Mode.NONE;
    private double startMouseX;
    private double startMouseY;
    private Transform start = new Transform(0, 0, 1, 0, 100, 40);

    public void enableDragging(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            mode = Mode.NONE;
        }
    }

    public void attachTo(Target target) {
        this.target = target;
    }

    public void setTransformCallback(Consumer<Transform> callback) {
        this.callback = callback == null ? transform -> {
        } : callback;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!enabled || target == null) {
            return false;
        }
        Transform transform = target.transform();
        Mode nextMode = hitMode(transform, mouseX, mouseY, button);
        if (nextMode == Mode.NONE) {
            return false;
        }
        mode = nextMode;
        startMouseX = mouseX;
        startMouseY = mouseY;
        start = transform;
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (!enabled || target == null || mode == Mode.NONE) {
            return false;
        }
        float dx = (float) (mouseX - startMouseX);
        float dy = (float) (mouseY - startMouseY);
        Transform next = start;
        if (mode == Mode.MOVE) {
            next = start.withPosition(start.x + dx, start.y + dy);
        } else if (mode == Mode.SCALE) {
            float base = Math.max(32.0F, Math.max(start.width, start.height));
            float scale = Math.clamp(start.scale + (dx + dy) / base, 0.2F, 8.0F);
            next = start.withScale(scale);
        } else if (mode == Mode.ROTATE) {
            double centerX = start.x + start.width * start.scale * 0.5D;
            double centerY = start.y + start.height * start.scale * 0.5D;
            double a0 = Math.atan2(startMouseY - centerY, startMouseX - centerX);
            double a1 = Math.atan2(mouseY - centerY, mouseX - centerX);
            next = start.withRotation(normalize(start.rotation + (float) Math.toDegrees(a1 - a0)));
        }
        callback.accept(next);
        return true;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (mode == Mode.NONE) {
            return false;
        }
        mode = Mode.NONE;
        return true;
    }

    private static Mode hitMode(Transform t, double mouseX, double mouseY, int button) {
        if (button == 1 && containsScaled(t, mouseX, mouseY)) {
            return Mode.ROTATE;
        }
        if (button != 0) {
            return Mode.NONE;
        }
        float scaledWidth = t.width * t.scale;
        float scaledHeight = t.height * t.scale;
        if (mouseX >= t.x + scaledWidth - HANDLE && mouseX <= t.x + scaledWidth + HANDLE
                && mouseY >= t.y + scaledHeight - HANDLE && mouseY <= t.y + scaledHeight + HANDLE) {
            return Mode.SCALE;
        }
        return containsScaled(t, mouseX, mouseY) ? Mode.MOVE : Mode.NONE;
    }

    private static boolean containsScaled(Transform t, double mouseX, double mouseY) {
        return mouseX >= t.x && mouseX <= t.x + t.width * t.scale
                && mouseY >= t.y && mouseY <= t.y + t.height * t.scale;
    }

    private static float normalize(float degrees) {
        while (degrees <= -180.0F) degrees += 360.0F;
        while (degrees > 180.0F) degrees -= 360.0F;
        return degrees;
    }

    private enum Mode {
        NONE,
        MOVE,
        SCALE,
        ROTATE
    }

    public interface Target {
        Transform transform();
    }

    public record Transform(float x, float y, float scale, float rotation, int width, int height) {
        public Transform withPosition(float x, float y) {
            return new Transform(x, y, scale, rotation, width, height);
        }

        public Transform withScale(float scale) {
            return new Transform(x, y, scale, rotation, width, height);
        }

        public Transform withRotation(float rotation) {
            return new Transform(x, y, scale, rotation, width, height);
        }
    }
}
