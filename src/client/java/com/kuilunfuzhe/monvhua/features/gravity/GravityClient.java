package com.kuilunfuzhe.monvhua.features.gravity;

import com.kuilunfuzhe.monvhua.entity.ModEntities;
import com.kuilunfuzhe.monvhua.gui.CombinedConfigScreen;
import com.kuilunfuzhe.monvhua.item.config.GravityConfig;
import com.kuilunfuzhe.monvhua.item.gravity.GravityItems;
import com.kuilunfuzhe.monvhua.network.SafeClientNetworking;
import com.kuilunfuzhe.monvhua.network.gravity.GravityPackets;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWScrollCallbackI;

public final class GravityClient {
    private static final double MULTIPLIER_STEP = 0.1D;
    private static double selectedGravity = GravityMagic.DEFAULT_GRAVITY;
    private static double energy = 100.0D;
    private static double maxEnergy = 100.0D;
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
            GravityMagic.tickClientSyncedAreaGravity();
            GravityMagic.resetInvertedPlayerIfInactive(client.player);
            GravityExtractPoseClientState.tick();
            GravityDebugClient.tick(client);
        });
        HudRenderCallback.EVENT.register(GravityClient::renderHud);
        ClientPlayNetworking.registerGlobalReceiver(GravityPackets.AreaGravityS2C.ID, (packet, context) -> {
            context.client().execute(() -> {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.world != null) {
                    GravityMagic.addClientSyncedAreaGravity(
                            packet.id(),
                            client.world.getRegistryKey(),
                            packet.center(),
                            new GravityAreaSpec(
                                    GravityAreaSpec.Shape.byId(packet.shape()),
                                    GravityAreaSpec.Half.byId(packet.half()),
                                    packet.sizeX(),
                                    packet.sizeY(),
                                    packet.sizeZ()
                            ),
                            packet.ticks(),
                            packet.gravity()
                    );
                    scheduleAreaRerender(client, packet.center(), Math.max(packet.sizeX(), Math.max(packet.sizeY(), packet.sizeZ())));
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(GravityPackets.ClearAreaGravityS2C.ID, (packet, context) -> {
            context.client().execute(() -> {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.world != null) {
                    GravityMagic.clearClientSyncedAreaGravity(
                            packet.id(),
                            client.world.getRegistryKey(),
                            packet.all()
                    );
                    if (packet.all()) {
                        client.worldRenderer.reload();
                    } else {
                        scheduleAreaRerender(client, packet.center(), packet.extent());
                    }
                    GravityMagic.resetInvertedPlayerIfInactive(client.player);
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(GravityPackets.EntityGravityS2C.ID, (packet, context) -> {
            context.client().execute(() -> {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.world == null) {
                    return;
                }
                Entity entity = client.world.getEntityById(packet.entityId());
                if (entity == null) {
                    return;
                }
                GravityMagic.setClientDirectedEntityGravity(entity,
                        new Vec3d(packet.directionX(), packet.directionY(), packet.directionZ()),
                        packet.force(),
                        packet.ticks());
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(GravityPackets.ConfigS2C.ID, (packet, context) -> {
            context.client().execute(() -> {
                GravityConfig config = GravityConfig.fromJson(packet.json());
                GravityConfig.syncInstance(config);
                if (context.client().currentScreen instanceof CombinedConfigScreen screen) {
                    screen.receiveGravityConfig(config);
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(GravityPackets.EnergyS2C.ID, (packet, context) ->
                context.client().execute(() -> {
                    energy = packet.energy();
                    maxEnergy = Math.max(1.0D, packet.maxEnergy());
                }));
        ClientPlayNetworking.registerGlobalReceiver(GravityPackets.ExtractPoseS2C.ID, (packet, context) ->
                context.client().execute(() -> GravityExtractPoseClientState.set(packet.entityId(), packet.ticks())));
    }

    private static void scheduleAreaRerender(MinecraftClient client, net.minecraft.util.math.BlockPos center, int extent) {
        client.worldRenderer.scheduleBlockRenders(
                center.getX() - extent,
                center.getY() - extent,
                center.getZ() - extent,
                center.getX() + extent,
                center.getY() + extent,
                center.getZ() + extent
        );
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

        double baseMultiplier = GravityMagic.gravityMultiplier(currentTargetGravity(client));
        selectedGravity = GravityMagic.gravityFromMultiplier(baseMultiplier + (yOffset > 0 ? MULTIPLIER_STEP : -MULTIPLIER_STEP));
        int entityId = targetEntityId(client);
        SafeClientNetworking.send(new GravityPackets.AdjustGravityC2S(entityId, selectedGravity));
        return true;
    }

    public static boolean onMouseClicked(int button, int mods) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.currentScreen != null) {
            return false;
        }
        if (client.player.getMainHandStack().getItem() != GravityItems.GRAVITY_WAND) {
            return false;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_MIDDLE || !isCtrlDown(client, mods)) {
            return false;
        }

        HitResult hit = client.player.raycast(64.0D, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || blockHit.getType() == HitResult.Type.MISS) {
            client.player.sendMessage(Text.literal("\u00a7c[Gravity] No block selected"), true);
            return true;
        }
        SafeClientNetworking.send(new GravityPackets.SelectBlocksC2S(blockHit.getBlockPos()));
        return true;
    }

    private static boolean isCtrlDown(MinecraftClient client) {
        return isCtrlDown(client, 0);
    }

    private static boolean isCtrlDown(MinecraftClient client, int mods) {
        if ((mods & GLFW.GLFW_MOD_CONTROL) != 0) {
            return true;
        }
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

        double force = currentTargetGravity(client);
        Entity forceTarget = currentForceTarget(client);
        Vec3d direction = currentAppliedDirection(client);
        Vec3d netForce = GravityMagic.previewComposedForce(forceTarget, direction, force);
        int centerX = context.getScaledWindowWidth() / 2;
        int centerY = context.getScaledWindowHeight() / 2;
        int x = centerX + 20;
        int y = centerY + 20;
        String line1 = target;
        String line2 = "F = " + GravityMagic.formatGravityMultiplier(force);
        String line3 = "Net = " + GravityMagic.formatGravityMultiplier(netForce.length());
        int width = Math.max(client.textRenderer.getWidth(line1),
                Math.max(client.textRenderer.getWidth(line2), client.textRenderer.getWidth(line3))) + 10;
        int height = 46;

        context.fill(x, y, x + width, y + height, 0x66000000);
        context.drawText(client.textRenderer, Text.literal(line1), x + 5, y + 4, 0xFFFFFFFF, false);
        context.drawText(client.textRenderer, Text.literal(line2), x + 5, y + 14, 0xFF88CCFF, false);
        context.drawText(client.textRenderer, Text.literal(line3), x + 5, y + 24, 0xFFB6E37A, false);
        int barX = x + 5;
        int barY = y + 36;
        int barW = width - 10;
        int fillW = (int) Math.round(barW * Math.clamp(energy / maxEnergy, 0.0D, 1.0D));
        context.fill(barX, barY, barX + barW, barY + 5, 0xAA1A2028);
        context.fill(barX, barY, barX + fillW, barY + 5, 0xFF66CCFF);
    }

    private static String describeTarget(MinecraftClient client) {
        if (client.player != null && client.player.isSneaking()) {
            return "Self";
        }
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

    private static Entity currentForceTarget(MinecraftClient client) {
        if (client.player != null && client.player.isSneaking()) {
            return client.player;
        }
        HitResult hit = client.crosshairTarget;
        if (hit instanceof EntityHitResult entityHit) {
            return entityHit.getEntity();
        }
        return null;
    }

    private static Vec3d currentAppliedDirection(MinecraftClient client) {
        if (client.player == null) {
            return new Vec3d(0.0D, 0.0D, 1.0D);
        }
        return client.player.getRotationVec(1.0F);
    }

    public static boolean isEntityInInvertedField(Entity entity) {
        return GravityMagic.isInInvertedArea(entity);
    }
}
