package com.kuilunfuzhe.monvhua.features.possession;

import com.kuilunfuzhe.monvhua.util.RaycastHelper;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class PossessionManager {
    private static final long INPUT_TIMEOUT_TICKS = 100L;
    private static final Map<UUID, Session> BY_CONTROLLER = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> CONTROLLER_BY_TARGET = new ConcurrentHashMap<>();
    private static final Map<UUID, BreakingState> BREAKING_BY_CONTROLLER = new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> REPLAYING_NETWORK_PACKET = ThreadLocal.withInitial(() -> false);

    private PossessionManager() {
    }

    public static boolean isController(ServerPlayerEntity player) {
        return player != null && BY_CONTROLLER.containsKey(player.getUuid());
    }

    public static boolean isTarget(ServerPlayerEntity player) {
        return player != null && CONTROLLER_BY_TARGET.containsKey(player.getUuid());
    }

    public static ServerPlayerEntity getTarget(ServerPlayerEntity controller) {
        if (controller == null || controller.getServer() == null) {
            return null;
        }
        Session session = BY_CONTROLLER.get(controller.getUuid());
        return session == null ? null : controller.getServer().getPlayerManager().getPlayer(session.targetUuid());
    }

    public static boolean isReplayingNetworkPacket() {
        return Boolean.TRUE.equals(REPLAYING_NETWORK_PACKET.get());
    }

    public static boolean replayControllerPacket(ServerPlayerEntity controller, Consumer<ServerPlayNetworkHandler> replay) {
        if (controller == null || replay == null || controller.getServer() == null) {
            return false;
        }
        Session session = BY_CONTROLLER.get(controller.getUuid());
        if (session == null) {
            return false;
        }
        ServerPlayerEntity target = controller.getServer().getPlayerManager().getPlayer(session.targetUuid());
        if (!canContinue(controller, target)) {
            stopByController(controller, controller.getServer());
            return true;
        }

        boolean previous = isReplayingNetworkPacket();
        REPLAYING_NETWORK_PACKET.set(true);
        try {
            replay.accept(target.networkHandler);
        } finally {
            REPLAYING_NETWORK_PACKET.set(previous);
        }
        BY_CONTROLLER.put(controller.getUuid(),
                new Session(session.controllerUuid(), session.targetUuid(), target.getWorld().getTime()));
        return true;
    }

    public static void prepareTargetMovement(ServerPlayerEntity target) {
        if (isTarget(target)) {
            applyMovementInput(target, target.getPlayerInput());
        }
    }

    public static void start(ServerPlayerEntity controller, ServerPlayerEntity target) {
        if (controller == null || target == null || controller == target) {
            return;
        }
        MinecraftServer server = controller.getServer();
        if (server == null) {
            return;
        }
        if (!controller.isAlive() || !target.isAlive()) {
            controller.sendMessage(Text.literal("Target is not available."), true);
            return;
        }
        if (controller.getWorld() != target.getWorld()) {
            controller.sendMessage(Text.literal("Possession currently requires the same dimension."), true);
            return;
        }

        UUID existingController = CONTROLLER_BY_TARGET.get(target.getUuid());
        if (existingController != null && !existingController.equals(controller.getUuid())) {
            controller.sendMessage(Text.literal("Target is already possessed."), true);
            return;
        }

        stopByController(controller, server);
        BY_CONTROLLER.put(controller.getUuid(), new Session(controller.getUuid(), target.getUuid(), target.getWorld().getTime()));
        CONTROLLER_BY_TARGET.put(target.getUuid(), controller.getUuid());
        ServerPlayNetworking.send(controller, new PossessionPackets.StateS2C(true, target.getId(), target.getUuid()));
        sendHotbar(controller, target);
        sendInventory(controller, target);
        controller.sendMessage(Text.literal("Possessing " + target.getName().getString()), true);
        target.sendMessage(Text.literal("You are being possessed by " + controller.getName().getString()), true);
    }

    public static void applyInput(ServerPlayerEntity controller, PlayerInput input, float yaw, float pitch, int selectedSlot) {
        if (controller == null || input == null || controller.getServer() == null) {
            return;
        }
        Session session = BY_CONTROLLER.get(controller.getUuid());
        if (session == null) {
            return;
        }
        ServerPlayerEntity target = controller.getServer().getPlayerManager().getPlayer(session.targetUuid());
        if (!canContinue(controller, target)) {
            stopByController(controller, controller.getServer());
            return;
        }

        target.setPlayerInput(input);
        applyMovementInput(target, input);
        target.setYaw(yaw);
        target.setPitch(Math.clamp(pitch, -90.0F, 90.0F));
        target.setHeadYaw(yaw);
        target.setBodyYaw(yaw);
        target.setSprinting(input.sprint());
        target.setSneaking(input.sneak());
        if (selectedSlot >= 0 && selectedSlot < 9) {
            target.getInventory().setSelectedSlot(selectedSlot);
        }

        BY_CONTROLLER.put(controller.getUuid(),
                new Session(session.controllerUuid(), session.targetUuid(), target.getWorld().getTime()));
    }

    public static void handleAction(ServerPlayerEntity controller, int action) {
        ServerPlayerEntity target = getTarget(controller);
        if (!canContinue(controller, target)) {
            if (controller != null) {
                stopByController(controller, controller.getServer());
            }
            return;
        }

        if (action == PossessionPackets.ActionC2S.ATTACK) {
            attackFromTarget(target);
        } else if (action == PossessionPackets.ActionC2S.USE) {
            useFromTarget(target);
        } else if (action == PossessionPackets.ActionC2S.RELEASE_USE) {
            target.stopUsingItem();
        } else if (action == PossessionPackets.ActionC2S.BREAKING) {
            breakFromTarget(controller, target);
        } else if (action == PossessionPackets.ActionC2S.BREAK_ABORT) {
            abortBreaking(controller.getUuid(), target);
        } else if (action == PossessionPackets.ActionC2S.SWAP_OFFHAND) {
            swapOffhandFromTarget(target);
            sendHotbar(controller, target);
            sendInventory(controller, target);
        } else if (action == PossessionPackets.ActionC2S.DROP_ITEM) {
            dropFromTarget(target, false);
            sendHotbar(controller, target);
            sendInventory(controller, target);
        } else if (action == PossessionPackets.ActionC2S.DROP_STACK) {
            dropFromTarget(target, true);
            sendHotbar(controller, target);
            sendInventory(controller, target);
        }
    }

    public static void stopByController(ServerPlayerEntity controller, MinecraftServer server) {
        if (controller == null) {
            return;
        }
        stopByController(controller.getUuid(), server, true);
    }

    public static void cleanupForDisconnect(ServerPlayerEntity player, MinecraftServer server) {
        if (player == null) {
            return;
        }
        stopByController(player.getUuid(), server, false);
        UUID controllerUuid = CONTROLLER_BY_TARGET.get(player.getUuid());
        if (controllerUuid != null) {
            stopByController(controllerUuid, server, true);
        }
    }

    public static void tick(MinecraftServer server) {
        for (Session session : BY_CONTROLLER.values()) {
            ServerPlayerEntity controller = server.getPlayerManager().getPlayer(session.controllerUuid());
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(session.targetUuid());
            if (!canContinue(controller, target)) {
                stopByController(session.controllerUuid(), server, true);
                continue;
            }
            if (target.getWorld().getTime() - session.lastInputTick() > INPUT_TIMEOUT_TICKS) {
                stopByController(session.controllerUuid(), server, true);
                continue;
            }
            controller.setPlayerInput(PlayerInput.DEFAULT);
            controller.setSprinting(false);
            controller.setSneaking(false);
            controller.setVelocity(Vec3d.ZERO);
            controller.velocityModified = true;
            applyMovementInput(target, target.getPlayerInput());
            if (target.getWorld().getTime() % 5L == 0L) {
                sendHotbar(controller, target);
            }
            if (target.getWorld().getTime() % 10L == 0L) {
                sendInventory(controller, target);
            }
        }
    }

    private static void stopByController(UUID controllerUuid, MinecraftServer server, boolean notifyController) {
        Session session = BY_CONTROLLER.remove(controllerUuid);
        if (session == null) {
            return;
        }
        CONTROLLER_BY_TARGET.remove(session.targetUuid(), controllerUuid);
        if (server == null) {
            BREAKING_BY_CONTROLLER.remove(controllerUuid);
            return;
        }
        ServerPlayerEntity controller = server.getPlayerManager().getPlayer(controllerUuid);
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(session.targetUuid());
        abortBreaking(controllerUuid, target);
        if (controller != null) {
            ServerPlayNetworking.send(controller, PossessionPackets.StateS2C.inactive());
            if (notifyController) {
                controller.sendMessage(Text.literal("Possession ended."), true);
            }
        }
        if (target != null) {
            target.setPlayerInput(PlayerInput.DEFAULT);
            applyMovementInput(target, PlayerInput.DEFAULT);
            target.sendMessage(Text.literal("Possession ended."), true);
        }
    }

    private static void applyMovementInput(ServerPlayerEntity target, PlayerInput input) {
        float sideways = input.left() == input.right() ? 0.0F : input.left() ? 1.0F : -1.0F;
        float forward = input.forward() == input.backward() ? 0.0F : input.forward() ? 1.0F : -1.0F;
        Vec2f movement = new Vec2f(sideways, forward).normalize();
        target.sidewaysSpeed = movement.x;
        target.forwardSpeed = movement.y;
        if (target.getAbilities().flying) {
            target.upwardSpeed = input.jump() == input.sneak() ? 0.0F : input.jump() ? 1.0F : -1.0F;
            target.setJumping(false);
        } else {
            target.upwardSpeed = 0.0F;
            target.setJumping(input.jump());
        }
        target.setSprinting(input.sprint());
        target.setSneaking(input.sneak());
    }

    private static void breakFromTarget(ServerPlayerEntity controller, ServerPlayerEntity target) {
        UUID controllerUuid = controller.getUuid();
        if (raycastEntityRespectingBlocks(target) != null) {
            abortBreaking(controllerUuid, target);
            return;
        }

        HitResult hitResult = target.raycast(target.getBlockInteractionRange(), 0.0F, false);
        if (!(hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            abortBreaking(controllerUuid, target);
            return;
        }

        BlockPos pos = hit.getBlockPos();
        Direction direction = hit.getSide();
        BreakingState current = BREAKING_BY_CONTROLLER.get(controllerUuid);
        if (current == null || !current.matches(pos, direction)) {
            abortBreaking(controllerUuid, target);
            startBreaking(controllerUuid, target, pos, direction);
            return;
        }

        continueBreaking(controllerUuid, target, current);
    }

    private static void startBreaking(UUID controllerUuid, ServerPlayerEntity target, BlockPos pos, Direction direction) {
        processBreakingAction(target, pos, direction, PlayerActionC2SPacket.Action.START_DESTROY_BLOCK);
        if (!target.getWorld().getBlockState(pos).isAir()) {
            BREAKING_BY_CONTROLLER.put(controllerUuid, new BreakingState(pos.toImmutable(), direction, target.getWorld().getTime()));
        }
    }

    private static void continueBreaking(UUID controllerUuid, ServerPlayerEntity target, BreakingState current) {
        BlockState state = target.getWorld().getBlockState(current.pos());
        if (state.isAir()) {
            BREAKING_BY_CONTROLLER.remove(controllerUuid);
            return;
        }

        long elapsedTicks = target.getWorld().getTime() - current.startTick();
        float progress = state.calcBlockBreakingDelta(target, target.getWorld(), current.pos()) * (float) (elapsedTicks + 1L);
        if (progress >= 1.0F) {
            processBreakingAction(target, current.pos(), current.direction(), PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK);
            BREAKING_BY_CONTROLLER.remove(controllerUuid);
        }
    }

    private static void abortBreaking(UUID controllerUuid, ServerPlayerEntity target) {
        BreakingState current = BREAKING_BY_CONTROLLER.remove(controllerUuid);
        if (current != null && target != null) {
            processBreakingAction(target, current.pos(), current.direction(), PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK);
        }
    }

    private static void processBreakingAction(ServerPlayerEntity target, BlockPos pos, Direction direction, PlayerActionC2SPacket.Action action) {
        target.interactionManager.processBlockBreakingAction(
                pos,
                action,
                direction,
                target.getWorld().getTopYInclusive(),
                0
        );
    }

    private static void sendHotbar(ServerPlayerEntity controller, ServerPlayerEntity target) {
        List<ItemStack> hotbar = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            hotbar.add(target.getInventory().getStack(i).copy());
        }
        ServerPlayNetworking.send(controller, new PossessionPackets.HotbarS2C(
                target.getInventory().getSelectedSlot(),
                hotbar
        ));
    }

    private static void sendInventory(ServerPlayerEntity controller, ServerPlayerEntity target) {
        List<ItemStack> stacks = new ArrayList<>(PossessionPackets.InventoryS2C.MAX_SLOTS);
        int size = Math.min(PossessionPackets.InventoryS2C.MAX_SLOTS, target.getInventory().size());
        for (int i = 0; i < size; i++) {
            stacks.add(target.getInventory().getStack(i).copy());
        }
        while (stacks.size() < PossessionPackets.InventoryS2C.MAX_SLOTS) {
            stacks.add(ItemStack.EMPTY);
        }
        ServerPlayNetworking.send(controller, new PossessionPackets.InventoryS2C(
                target.getInventory().getSelectedSlot(),
                stacks
        ));
    }

    private static boolean canContinue(ServerPlayerEntity controller, ServerPlayerEntity target) {
        return controller != null
                && target != null
                && controller.isAlive()
                && target.isAlive()
                && controller.getWorld() == target.getWorld();
    }

    private static void attackFromTarget(ServerPlayerEntity target) {
        EntityHitResult entityHit = raycastEntityRespectingBlocks(target);
        if (entityHit != null) {
            target.attack(entityHit.getEntity());
            target.swingHand(Hand.MAIN_HAND);
        }
    }

    private static void useFromTarget(ServerPlayerEntity target) {
        EntityHitResult entityHit = raycastEntityRespectingBlocks(target);
        if (entityHit != null && tryUseEntity(target, entityHit)) {
            return;
        }

        HitResult blockHit = target.raycast(target.getBlockInteractionRange(), 0.0F, false);
        if (blockHit instanceof BlockHitResult hit && blockHit.getType() == HitResult.Type.BLOCK && tryUseBlock(target, hit)) {
            return;
        }

        tryUseItem(target);
    }

    private static boolean tryUseEntity(ServerPlayerEntity target, EntityHitResult hit) {
        Entity entity = hit.getEntity();
        Vec3d localHit = hit.getPos().subtract(entity.getPos());
        for (Hand hand : Hand.values()) {
            ActionResult atLocation = entity.interactAt(target, localHit, hand);
            if (atLocation.isAccepted()) {
                target.swingHand(hand);
                return true;
            }
            ActionResult result = target.interact(entity, hand);
            if (result.isAccepted()) {
                target.swingHand(hand);
                return true;
            }
        }
        return false;
    }

    private static boolean tryUseBlock(ServerPlayerEntity target, BlockHitResult hit) {
        for (Hand hand : Hand.values()) {
            ItemStack stack = target.getStackInHand(hand);
            ActionResult result = target.interactionManager.interactBlock(target, target.getWorld(), stack, hand, hit);
            if (result.isAccepted()) {
                target.swingHand(hand);
                return true;
            }
        }
        return false;
    }

    private static boolean tryUseItem(ServerPlayerEntity target) {
        for (Hand hand : Hand.values()) {
            ItemStack stack = target.getStackInHand(hand);
            ActionResult result = target.interactionManager.interactItem(target, target.getWorld(), stack, hand);
            if (result.isAccepted()) {
                target.swingHand(hand);
                return true;
            }
        }
        return false;
    }

    private static void swapOffhandFromTarget(ServerPlayerEntity target) {
        if (target.isSpectator()) {
            return;
        }
        ItemStack offhand = target.getStackInHand(Hand.OFF_HAND);
        target.setStackInHand(Hand.OFF_HAND, target.getStackInHand(Hand.MAIN_HAND));
        target.setStackInHand(Hand.MAIN_HAND, offhand);
        target.clearActiveItem();
    }

    private static void dropFromTarget(ServerPlayerEntity target, boolean entireStack) {
        if (!target.isSpectator()) {
            target.dropSelectedItem(entireStack);
        }
    }

    private static EntityHitResult raycastEntityRespectingBlocks(ServerPlayerEntity target) {
        EntityHitResult entityHit = RaycastHelper.raycastEntity(target, target.getEntityInteractionRange());
        if (entityHit == null) {
            return null;
        }
        HitResult blockHit = target.raycast(target.getBlockInteractionRange(), 0.0F, false);
        double entityDistanceSq = entityHit.getPos().squaredDistanceTo(target.getEyePos());
        double blockDistanceSq = blockHit.getType() == HitResult.Type.BLOCK
                ? blockHit.getPos().squaredDistanceTo(target.getEyePos())
                : Double.MAX_VALUE;
        return entityDistanceSq <= blockDistanceSq ? entityHit : null;
    }

    private record Session(UUID controllerUuid, UUID targetUuid, long lastInputTick) {
    }

    private record BreakingState(BlockPos pos, Direction direction, long startTick) {
        private boolean matches(BlockPos otherPos, Direction otherDirection) {
            return pos.equals(otherPos) && direction == otherDirection;
        }
    }
}
