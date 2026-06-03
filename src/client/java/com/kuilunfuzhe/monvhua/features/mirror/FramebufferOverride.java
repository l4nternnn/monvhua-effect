package com.kuilunfuzhe.monvhua.features.mirror;

import net.minecraft.client.gl.Framebuffer;

/**
 * ThreadLocal帧缓冲覆盖工具。
 * 通过线程局部变量在当前线程临时替换Minecraft的渲染目标帧缓冲，
 * 使世界渲染输出到镜像FBO而非主屏幕缓冲，配合Mixins中的截获点使用。
 */
public class FramebufferOverride {
    private static final ThreadLocal<Framebuffer> OVERRIDE_FRAMEBUFFER = new ThreadLocal<>();

    /**
     * 设置当前线程的帧缓冲覆盖
     * @param framebuffer 要覆盖的帧缓冲（通常为镜像FBO）
     */
    public static void setOverride(Framebuffer framebuffer) {
        OVERRIDE_FRAMEBUFFER.set(framebuffer);
    }

    /** 清除当前线程的帧缓冲覆盖 */
    public static void clearOverride() {
        OVERRIDE_FRAMEBUFFER.remove();
    }

    /**
     * 获取当前线程的帧缓冲覆盖
     * @return 覆盖的帧缓冲，null表示无覆盖
     */
    public static Framebuffer getOverride() {
        return OVERRIDE_FRAMEBUFFER.get();
    }
}
