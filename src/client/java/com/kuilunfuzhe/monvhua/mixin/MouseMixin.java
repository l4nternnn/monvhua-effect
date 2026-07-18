package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.client.imitate.AreaSelectClientManager;
import com.kuilunfuzhe.monvhua.features.gravity.GravityClient;
import com.kuilunfuzhe.monvhua.network.hold_hands.HoldHandsInteractC2SPacket;
import com.kuilunfuzhe.monvhua.network.SafeClientNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MouseMixin {

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        if (action == GLFW.GLFW_PRESS && button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && monvhua$handleHoldHandsMiddleClick()) {
            ci.cancel();
            return;
        }
        if (action == 1 && GravityClient.onMouseClicked(button, mods)) {
            ci.cancel();
            return;
        }
        if (action == 1 && AreaSelectClientManager.onMouseClicked(button)) {
            ci.cancel();
        }
    }

    private boolean monvhua$handleHoldHandsMiddleClick() {
//        MinecraftClient client = MinecraftClient.getInstance();
//        if (client.player == null || !client.player.isSneaking()) {
//            return false;
//        }
//        if (!(client.crosshairTarget instanceof EntityHitResult hitResult) || hitResult.getType() != HitResult.Type.ENTITY) {
//            return false;
//        }
//        if (!(hitResult.getEntity() instanceof PlayerEntity) || hitResult.getEntity() == client.player) {
//            return false;
//        }
//        SafeClientNetworking.send(new HoldHandsInteractC2SPacket(hitResult.getEntity().getId()));
//        return true;
        return false;
    }
}
