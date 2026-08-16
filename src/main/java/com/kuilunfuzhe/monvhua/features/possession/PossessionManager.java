package com.kuilunfuzhe.monvhua.features.possession;

import com.kuilunfuzhe.monvhua.util.RaycastHelper;
import com.kuilunfuzhe.monvhua.network.portal.PortalPackets;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PossessionManager {
    private static final long INPUT_TIMEOUT_TICKS = 100L;
    private static final long INPUT_STALE_TICKS = 5L;
    private static final long USE_INTERVAL_TICKS = 4L;
    private static final Map<UUID, Session> BY_CONTROLLER = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> CONTROLLER_BY_TARGET = new ConcurrentHashMap<>();
    private static final Map<UUID, BreakingState> BREAKING_BY_CONTROLLER = new ConcurrentHashMap<>();
    private static final Map<UUID, ChunkPos> REMOTE_CHUNK_CENTERS = new ConcurrentHashMap<>();
    private static final int REMOTE_CHUNK_RADIUS = 2;

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

    public static ServerPlayerEntity getController(ServerPlayerEntity target) {
        if (target == null || target.getServer() == null) {
            return null;
        }
        UUID controllerUuid = CONTROLLER_BY_TARGET.get(target.getUuid());
        return controllerUuid == null
                ? null
                : target.getServer().getPlayerManager().getPlayer(controllerUuid);
    }

    public static void refreshControllerView(ServerPlayerEntity target) {
        ServerPlayerEntity controller = getController(target);
        if (controller != null) {
            sendHotbar(controller, target);
            sendInventory(controller, target);
        }
    }

    public static void syncObservedBlock(ServerWorld world, BlockPos pos) {
        if (world == null || pos == null) {
            return;
        }
        for (Session session : BY_CONTROLLER.values()) {
            ServerPlayerEntity controller = world.getServer().getPlayerManager().getPlayer(session.controllerUuid());
            ServerPlayerEntity target = world.getServer().getPlayerManager().getPlayer(session.targetUuid());
            if (controller == null || target == null || target.getWorld() != world || !isObservedChunk(target, pos)) {
                continue;
            }
            sendBlockFeedback(controller, world, pos);
        }
    }

    public static void syncObservedEntityPacket(ServerWorld world, Entity entity, Packet<?> packet) {
        if (world == null || entity == null || packet == null) {
            return;
        }
        for (Session session : BY_CONTROLLER.values()) {
            ServerPlayerEntity controller = world.getServer().getPlayerManager().getPlayer(session.controllerUuid());
            ServerPlayerEntity target = world.getServer().getPlayerManager().getPlayer(session.targetUuid());
            if (controller == null || target == null || target.getWorld() != world
                    || target.squaredDistanceTo(entity) > (REMOTE_CHUNK_RADIUS * 16.0D + 16.0D) * (REMOTE_CHUNK_RADIUS * 16.0D + 16.0D)) {
                continue;
            }
            controller.networkHandler.sendPacket(packet);
        }
    }

    public static void prepareTargetMovement(ServerPlayerEntity target) {
        UUID controllerUuid = target == null ? null : CONTROLLER_BY_TARGET.get(target.getUuid());
        Session session = controllerUuid == null ? null : BY_CONTROLLER.get(controllerUuid);
        if (session != null) {
            applySessionInput(target, session);
        }
    }

    public static void start(ServerPlayerEntity controller, ServerPlayerEntity target, Hand hand) {
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

        int wandSlot = hand == Hand.MAIN_HAND ? controller.getInventory().getSelectedSlot() : -1;
        if (wandSlot >= 0 && !controller.getInventory().getStack(wandSlot).isOf(PossessionFeature.POSSESSION_ITEM)) {
            return;
        }

        stopByController(controller, server);
        boolean targetWasFlying = target.getAbilities().flying;
        boolean targetWasNoGravity = target.hasNoGravity();
        BY_CONTROLLER.put(controller.getUuid(), new Session(
                controller.getUuid(), target.getUuid(), wandSlot, targetWasFlying, targetWasNoGravity,
                PlayerInput.DEFAULT, target.getYaw(), target.getPitch(), -1, target.getWorld().getTime(),
                target.getWorld().getTime() - USE_INTERVAL_TICKS));
        CONTROLLER_BY_TARGET.put(target.getUuid(), controller.getUuid());
        enforceGroundMovement(target);
        ServerPlayNetworking.send(controller, new PossessionPackets.StateS2C(true, target.getId(), target.getUuid(), wandSlot));
        sendVisualState(controller, target);
        sendHotbar(controller, target);
        sendInventory(controller, target);
        syncRemoteChunks(controller, target, true);
        controller.sendMessage(Text.literal("Possessing " + target.getName().getString()), true);
        target.sendMessage(Text.literal("You are being possessed by " + controller.getName().getString()), true);
    }

    public static void applyInput(ServerPlayerEntity controller, int sequence, PlayerInput input, float yaw, float pitch) {
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
        if (sequence <= session.lastInputSequence()) {
            return;
        }

        BY_CONTROLLER.put(controller.getUuid(), session.withInput(
                sequence,
                input,
                yaw,
                Math.clamp(pitch, -90.0F, 90.0F),
                target.getWorld().getTime()
        ));
    }

    public static void selectTargetSlot(ServerPlayerEntity controller, int slot) {
        if (slot < 0 || slot >= 9) {
            return;
        }
        ServerPlayerEntity target = getTarget(controller);
        if (!canContinue(controller, target)) {
            if (controller != null && controller.getServer() != null) {
                stopByController(controller, controller.getServer());
            }
            return;
        }
        target.getInventory().setSelectedSlot(slot);
        sendHotbar(controller, target);
        sendInventory(controller, target);
    }

    public static void handleAction(ServerPlayerEntity controller, int action) {
        ServerPlayerEntity target = getTarget(controller);
        if (!canContinue(controller, target)) {
            if (controller != null) {
                stopByController(controller, controller.getServer());
            }
            return;
        }
        Session session = BY_CONTROLLER.get(controller.getUuid());
        if (session == null) {
            return;
        }
        long now = target.getWorld().getTime();
        if (action == PossessionPackets.ActionC2S.USE) {
            if (now - session.lastUseTick() < USE_INTERVAL_TICKS) {
                return;
            }
            BY_CONTROLLER.put(controller.getUuid(), session.withUseTick(now));
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
        refreshControllerView(target);
        sendVisualState(controller, target);
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
            if (!keepControllerWandLocked(controller, session)) {
                stopByController(session.controllerUuid(), server, true);
                continue;
            }
            syncRemoteChunks(controller, target, false);
            if (target.getWorld().getTime() % 2L == 0L) {
                sendVisualState(controller, target);
            }
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
        REMOTE_CHUNK_CENTERS.remove(controllerUuid);
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
            target.getAbilities().flying = session.targetWasFlying();
            target.setNoGravity(session.targetWasNoGravity());
            target.sendAbilitiesUpdate();
            target.sendMessage(Text.literal("Possession ended."), true);
        }
    }

    private static void applyMovementInput(ServerPlayerEntity target, PlayerInput input) {
        float sideways = input.left() == input.right() ? 0.0F : input.left() ? 1.0F : -1.0F;
        float forward = input.forward() == input.backward() ? 0.0F : input.forward() ? 1.0F : -1.0F;
        Vec2f movement = new Vec2f(sideways, forward).normalize();
        float movementScale = input.sneak() ? 0.3F : 1.0F;
        target.sidewaysSpeed = movement.x * movementScale;
        target.forwardSpeed = movement.y * movementScale;
        target.upwardSpeed = 0.0F;
        target.setJumping(input.jump());
        target.setSprinting(input.sprint() && input.forward() && !input.backward() && !input.sneak());
        target.setSneaking(input.sneak());
    }

    private static void applySessionInput(ServerPlayerEntity target, Session session) {
        long age = target.getWorld().getTime() - session.lastInputTick();
        PlayerInput input = age > INPUT_STALE_TICKS ? PlayerInput.DEFAULT : session.input();
        enforceGroundMovement(target);
        target.setPlayerInput(input);
        applyMovementInput(target, input);
        target.setYaw(session.yaw());
        target.setPitch(session.pitch());
        target.setHeadYaw(session.yaw());
        target.setBodyYaw(session.yaw());
    }

    public static void enforceGroundMovement(ServerPlayerEntity target) {
        boolean changed = target.getAbilities().flying || target.hasNoGravity();
        target.getAbilities().flying = false;
        target.setNoGravity(false);
        if (changed) {
            target.sendAbilitiesUpdate();
        }
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
            syncBlockFeedback(target, current.pos(), current.direction());
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

    private static void sendVisualState(ServerPlayerEntity controller, ServerPlayerEntity target) {
        if (controller == null || target == null) {
            return;
        }
        ServerPlayNetworking.send(controller, new PossessionPackets.VisualStateS2C(
                target.isSprinting(),
                target.isSneaking(),
                target.isUsingItem(),
                target.isUsingItem() ? target.getActiveHand() : Hand.MAIN_HAND
        ));
    }

    private static void syncRemoteChunks(ServerPlayerEntity controller, ServerPlayerEntity target, boolean force) {
        if (!(target.getWorld() instanceof ServerWorld world)) {
            return;
        }
        ChunkPos center = new ChunkPos(target.getBlockPos());
        ChunkPos previous = REMOTE_CHUNK_CENTERS.get(controller.getUuid());
        if (!force && center.equals(previous)) {
            return;
        }
        REMOTE_CHUNK_CENTERS.put(controller.getUuid(), center);

        for (int dx = -REMOTE_CHUNK_RADIUS; dx <= REMOTE_CHUNK_RADIUS; dx++) {
            for (int dz = -REMOTE_CHUNK_RADIUS; dz <= REMOTE_CHUNK_RADIUS; dz++) {
                WorldChunk chunk = world.getChunkManager().getWorldChunk(center.x + dx, center.z + dz);
                if (chunk == null) {
                    continue;
                }
                ServerPlayNetworking.send(controller, new PortalPackets.RemoteChunkS2C(
                        target.getBlockPos(),
                        world.getTime(),
                        chunk
                ));
            }
        }
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
            target.swingHand(Hand.MAIN_HAND, true);
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
                swingForInteractionResult(target, hand, atLocation);
                return true;
            }
            ActionResult result = target.interact(entity, hand);
            if (result.isAccepted()) {
                swingForInteractionResult(target, hand, result);
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
                swingForInteractionResult(target, hand, result);
                syncBlockFeedback(target, hit);
                return true;
            }
        }
        return false;
    }

    private static void syncBlockFeedback(ServerPlayerEntity target, BlockHitResult hit) {
        syncBlockFeedback(target, hit.getBlockPos(), hit.getSide());
    }

    private static void syncBlockFeedback(ServerPlayerEntity target, BlockPos clicked, Direction side) {
        ServerPlayerEntity controller = getController(target);
        if (controller == null || !(target.getWorld() instanceof ServerWorld world)) {
            return;
        }

        sendBlockFeedback(controller, world, clicked);
        sendBlockFeedback(controller, world, clicked.offset(side));
        sendBlockFeedback(controller, world, clicked.up());
        sendBlockFeedback(controller, world, clicked.down());
    }

    private static void sendBlockFeedback(ServerPlayerEntity controller, ServerWorld world, BlockPos pos) {
        controller.networkHandler.sendPacket(new BlockUpdateS2CPacket(pos, world.getBlockState(pos)));
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity == null) {
            return;
        }
        var packet = blockEntity.toUpdatePacket();
        if (packet != null) {
            controller.networkHandler.sendPacket(packet);
        }
    }

    private static boolean isObservedChunk(ServerPlayerEntity target, BlockPos pos) {
        ChunkPos center = new ChunkPos(target.getBlockPos());
        ChunkPos changed = new ChunkPos(pos);
        return Math.abs(center.x - changed.x) <= REMOTE_CHUNK_RADIUS
                && Math.abs(center.z - changed.z) <= REMOTE_CHUNK_RADIUS;
    }

    private static boolean tryUseItem(ServerPlayerEntity target) {
        for (Hand hand : Hand.values()) {
            ItemStack stack = target.getStackInHand(hand);
            ActionResult result = target.interactionManager.interactItem(target, target.getWorld(), stack, hand);
            if (result.isAccepted()) {
                swingForInteractionResult(target, hand, result);
                return true;
            }
        }
        return false;
    }

    private static void swingForInteractionResult(ServerPlayerEntity target, Hand hand, ActionResult result) {
        if (result instanceof ActionResult.Success success
                && success.swingSource() != ActionResult.SwingSource.NONE) {
            target.swingHand(hand, true);
        }
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

    private static boolean keepControllerWandLocked(ServerPlayerEntity controller, Session session) {
        if (session.wandSlot() < 0) {
            return true;
        }
        if (!controller.getInventory().getStack(session.wandSlot()).isOf(PossessionFeature.POSSESSION_ITEM)) {
            return false;
        }
        controller.getInventory().setSelectedSlot(session.wandSlot());
        return true;
    }

    private record Session(UUID controllerUuid, UUID targetUuid, int wandSlot, boolean targetWasFlying,
                           boolean targetWasNoGravity, PlayerInput input, float yaw, float pitch,
                           int lastInputSequence, long lastInputTick, long lastUseTick) {
        private Session withInput(int sequence, PlayerInput input, float yaw, float pitch, long tick) {
            return new Session(
                    controllerUuid,
                    targetUuid,
                    wandSlot,
                    targetWasFlying,
                    targetWasNoGravity,
                    input,
                    yaw,
                    pitch,
                    sequence,
                    tick,
                    lastUseTick
            );
        }

        private Session withUseTick(long tick) {
            return new Session(
                    controllerUuid,
                    targetUuid,
                    wandSlot,
                    targetWasFlying,
                    targetWasNoGravity,
                    input,
                    yaw,
                    pitch,
                    lastInputSequence,
                    lastInputTick,
                    tick
            );
        }
    }

    private record BreakingState(BlockPos pos, Direction direction, long startTick) {
        private boolean matches(BlockPos otherPos, Direction otherDirection) {
            return pos.equals(otherPos) && direction == otherDirection;
        }
    }
}
