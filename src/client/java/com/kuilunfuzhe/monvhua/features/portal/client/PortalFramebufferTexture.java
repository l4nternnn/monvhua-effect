package com.kuilunfuzhe.monvhua.features.portal.client;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.texture.AbstractTexture;

public final class PortalFramebufferTexture extends AbstractTexture {
    private SimpleFramebuffer framebuffer;

    public void setFramebuffer(SimpleFramebuffer framebuffer) {
        this.framebuffer = framebuffer;
        if (framebuffer != null && framebuffer.getColorAttachment() != null) {
            framebuffer.getColorAttachment().setTextureFilter(FilterMode.LINEAR, false);
        }
    }

    @Override
    public GpuTexture getGlTexture() {
        if (framebuffer != null && framebuffer.getColorAttachment() != null) {
            return framebuffer.getColorAttachment();
        }
        return super.getGlTexture();
    }

    @Override
    public GpuTextureView getGlTextureView() {
        if (framebuffer != null && framebuffer.getColorAttachmentView() != null) {
            return framebuffer.getColorAttachmentView();
        }
        return super.getGlTextureView();
    }

    @Override
    public void setUseMipmaps(boolean useMipmaps) {
        if (framebuffer == null || framebuffer.getColorAttachment() == null) {
            return;
        }
        framebuffer.getColorAttachment().setTextureFilter(FilterMode.LINEAR, false);
    }

    @Override
    public void close() {
        framebuffer = null;
    }
}
