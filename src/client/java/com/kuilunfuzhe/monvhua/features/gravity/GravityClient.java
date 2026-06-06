package com.kuilunfuzhe.monvhua.features.gravity;

import com.kuilunfuzhe.monvhua.entity.ModEntities;
import com.kuilunfuzhe.monvhua.item.gravity.GravityItems;
import com.kuilunfuzhe.monvhua.network.SafeClientNetworking;
import com.kuilunfuzhe.monvhua.network.gravity.GravityPackets;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWScrollCallbackI;

public final class GravityClient {
    private static final double STEP = 0.01D;
    private static double selectedGravity = GravityMagic.DEFAULT_GRAVITY;
    private static GLFWScrollCallbackI previousScrollCallback;
    private static boolean scrollCallbackRegistered = false;

    private GravityClient() {
    }

    public static void initialize() {
        EntityRendererRegistry.register(ModEntities.GRAVITY_BLOCK, GravityBlockEntityRenderer::new);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!scrollCallbackRegistered && client.getWindow() != null) {
                registerScrollCallback(client);
            }
        });
        HudRenderCallback.EVENT.register(GravityClient::renderHud);
    }

    private static void registerScrollCallback(MinecraftClient client) {
        long window = client.getWindow().getHandle();
        previousScrollCallback = GLFW.glfwSetScrollCallback(window, (handle, xOffset, yOffset) -> {
            if (handleGravityScroll(yOffset)) {
                return;
            }
            if (previousScrollCallback != null) {
                previousScrollCallback.invoke(handle, xOffset, yOffset);
            }
        });
        scrollCallbackRegistered = true;
    }

    private static boolean handleGravityScroll(double yOffset) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.currentScreen != null) return false;
        if (client.player.getMainHandStack().getItem() != GravityItems.GRAVITY_WAND) return false;
        if (!isCtrlDown(client)) return false;

        double base = currentTargetGravity(client);
        selectedGravity = GravityMagic.clampGravity(base + (yOffset > 0 ? STEP : -STEP));
        int entityId = targetEntityId(client);
        SafeClientNetworking.send(new GravityPackets.AdjustGravityC2S(entityId, selectedGravity));
        return true;
    }

    private static boolean isCtrlDown(MinecraftClient client) {
        long handle = client.getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private static int targetEntityId(MinecraftClient client) {
        HitResult hit = client.crosshairTarget;
        if (hit instanceof EntityHitResult entityHit) {
            return entityHit.getEntity().getId();
        }
        return -1;
    }

    private static double currentTargetGravity(MinecraftClient client) {
        HitResult hit = client.crosshairTarget;
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof GravityBlockEntity gravityBlock) {
            return gravityBlock.getGravityAmount();
        }
        return selectedGravity;
    }

    private static void renderHud(DrawContext context, net.minecraft.client.render.RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.player.getMainHandStack().getItem() != GravityItems.GRAVITY_WAND) {
            return;
        }

        String target = describeTarget(client);
        if (target == null) return;

        double gravity = currentTargetGravity(client);
        int centerX = context.getScaledWindowWidth() / 2;
        int centerY = context.getScaledWindowHeight() / 2;
        int x = centerX + 20;
        int y = centerY + 20;
        String line1 = target;
        String line2 = "g = " + GravityMagic.format(gravity);
        int width = Math.max(client.textRenderer.getWidth(line1), client.textRenderer.getWidth(line2)) + 10;
        int height = 24;

        context.fill(x, y, x + width, y + height, 0x66000000);
        context.drawText(client.textRenderer, Text.literal(line1), x + 5, y + 4, 0xFFFFFFFF, false);
        context.drawText(client.textRenderer, Text.literal(line2), x + 5, y + 14, 0xFF88CCFF, false);
    }

    private static String describeTarget(MinecraftClient client) {
        HitResult hit = client.crosshairTarget;
        if (hit instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            return entity.getName().getString();
        }
        if (hit instanceof BlockHitResult blockHit && client.world != null) {
            BlockState state = client.world.getBlockState(blockHit.getBlockPos());
            if (!state.isAir()) {
                return state.getBlock().getName().getString();
            }
        }
        return null;
    }
}
