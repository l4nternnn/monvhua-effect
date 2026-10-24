package com.kuilunfuzhe.monvhua.gui.body.bodypose.viewport;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** One camera contract for the offscreen scene and screen-space overlays. Scene Y points down. */
public final class PoseViewportFrame {
    private final Matrix4f projection;
    private final Matrix4f view;
    private final Matrix4f viewProjection;
    private final int left, top, width, height;
    private final float distance;
    private final boolean orthographic;

    public PoseViewportFrame(int left, int top, int width, int height, float pixelsPerUnit,
                             float panX, float panY, float pitch, float yaw, float roll,
                             float distance, boolean orthographic) {
        this.left = left;
        this.top = top;
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.distance = distance;
        this.orthographic = orthographic;
        float scale = Math.max(0.001f, pixelsPerUnit);
        if (orthographic) {
            projection = new Matrix4f().setOrtho(-this.width / (2 * scale), this.width / (2 * scale),
                    -this.height / (2 * scale), this.height / (2 * scale), 0.1f, 512f);
            projection.m30(2 * panX / this.width).m31(-2 * panY / this.height);
        } else {
            float fov = 2 * (float) Math.atan(this.height / (2 * scale * distance));
            projection = new Matrix4f().setPerspective(fov, (float) this.width / this.height, 0.1f, 512f);
            projection.m20(-2 * panX / this.width).m21(2 * panY / this.height);
        }
        view = new Matrix4f().translation(0, 0, -distance).scale(1, -1, 1)
                .rotateZ(rad(roll)).rotateX(rad(pitch)).rotateY(rad(-yaw));
        viewProjection = new Matrix4f(projection).mul(view);
    }

    public Matrix4f projection() { return new Matrix4f(projection); }
    public Matrix4f view() { return new Matrix4f(view); }
    public boolean orthographic() { return orthographic; }

    public Projected project(float x, float y, float z) {
        Vector4f clip = viewProjection.transform(new Vector4f(x, y, z, 1));
        if (!Float.isFinite(clip.w) || clip.w <= 0 || clip.z < -clip.w || clip.z > clip.w) {
            return new Projected(Float.NaN, Float.NaN, 1, Float.POSITIVE_INFINITY);
        }
        return new Projected(left + (clip.x / clip.w + 1) * width / 2,
                top + (1 - clip.y / clip.w) * height / 2,
                orthographic ? 1 : distance / clip.w, -view.transformPosition(new Vector3f(x, y, z)).z);
    }

    private static float rad(float degrees) { return (float) Math.toRadians(degrees); }
    public record Projected(float x, float y, float scale, float depth) {}
}
