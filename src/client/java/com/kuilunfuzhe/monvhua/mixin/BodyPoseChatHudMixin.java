package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.gui.body.bodypose.BodyPoseEditorFragment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public abstract class BodyPoseChatHudMixin {
    @Unique
    private boolean monvhua$bodyPoseChatShifted;

    @Inject(method = "render", at = @At("HEAD"))
    private void monvhua$shiftBodyPoseWorldPreviewChat(DrawContext context, int tickCounter, int mouseX, int mouseY,
                                                       boolean focused, CallbackInfo ci) {
        monvhua$bodyPoseChatShifted = false;
        int offset = BodyPoseEditorFragment.getWorldPlacementChatOffset();
        if (offset <= 0) {
            return;
        }
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(offset, 0.0F);
        monvhua$bodyPoseChatShifted = true;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void monvhua$restoreBodyPoseWorldPreviewChat(DrawContext context, int tickCounter, int mouseX, int mouseY,
                                                         boolean focused, CallbackInfo ci) {
        if (monvhua$bodyPoseChatShifted) {
            context.getMatrices().popMatrix();
            monvhua$bodyPoseChatShifted = false;
        }
    }
}
