package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.gui.body.bodypose.BodyPoseEditorFragment;
import net.minecraft.client.Keyboard;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class BodyPoseKeyboardMixin {
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void monvhua$blockBodyPoseWorldPreviewChatKey(long window, int key, int scancode, int action, int modifiers,
                                                          CallbackInfo ci) {
        if (!BodyPoseEditorFragment.isWorldPlacementOverlayActive()) {
            return;
        }
        if ((action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT)
                && BodyPoseEditorFragment.isChatRelatedKey(key)) {
            ci.cancel();
        }
    }
}
