package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.forensics.EvidencePanelTexture;
import com.kuilunfuzhe.monvhua.features.forensics.cards.ClueSnapshotTexture;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Override dimensions only for the dedicated panel renderer, never the actual window. */
@Mixin(GuiRenderer.class)
public abstract class EvidencePanelGuiSizeMixin {
    @Redirect(method="renderPreparedDraws",at=@At(value="INVOKE",target="Lnet/minecraft/client/util/Window;getFramebufferWidth()I"))
    private int evidence$width(Window window){return ClueSnapshotTexture.isRendering(this)?ClueSnapshotTexture.WIDTH:EvidencePanelTexture.isRendering(this)?EvidencePanelTexture.LOGICAL_WIDTH*EvidencePanelTexture.renderScale():window.getFramebufferWidth();}
    @Redirect(method={"renderPreparedDraws","enableScissor"},at=@At(value="INVOKE",target="Lnet/minecraft/client/util/Window;getFramebufferHeight()I"))
    private int evidence$height(Window window){return ClueSnapshotTexture.isRendering(this)?ClueSnapshotTexture.HEIGHT:EvidencePanelTexture.isRendering(this)?EvidencePanelTexture.LOGICAL_HEIGHT*EvidencePanelTexture.renderScale():window.getFramebufferHeight();}
    @Redirect(method={"renderPreparedDraws","enableScissor","prepareSpecialElements","getWindowScaleFactor"},at=@At(value="INVOKE",target="Lnet/minecraft/client/util/Window;getScaleFactor()I"))
    private int evidence$scale(Window window){return ClueSnapshotTexture.isRendering(this)?ClueSnapshotTexture.SCALE:EvidencePanelTexture.isRendering(this)?EvidencePanelTexture.renderScale():window.getScaleFactor();}
}
