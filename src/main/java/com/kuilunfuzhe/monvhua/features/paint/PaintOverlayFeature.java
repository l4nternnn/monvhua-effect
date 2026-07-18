package com.kuilunfuzhe.monvhua.features.paint;

import com.kuilunfuzhe.monvhua.network.paint.PaintOverlayPackets;
import com.kuilunfuzhe.monvhua.command.PaintGraffitiCommand;
import com.kuilunfuzhe.monvhua.item.config.PaintConfig;
import com.kuilunfuzhe.monvhua.item.modblock.ModBlocks;
import com.kuilunfuzhe.monvhua.item.paint.PaintBrushItem;
import com.kuilunfuzhe.monvhua.item.paint.PaintItems;
import com.kuilunfuzhe.monvhua.item.paint.PaintPaperItem;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

public final class PaintOverlayFeature {
    public static final int DEFAULT_COLOR = 0xFFFF2A4F;
    public static final int DEFAULT_RADIUS = 1;
    public static final int MIN_RADIUS = 1;
    public static final int MAX_RADIUS = 8;
    public static final int MAX_MANUAL_RADIUS = 100;
    public static final int DEFAULT_PAPER_SIZE = 3;
    public static final int MIN_PAPER_SIZE = 1;
    public static final int MAX_PAPER_SIZE = 25;
    private static final double SYNC_DISTANCE_SQUARED = 128.0D * 128.0D;
    private static final double INTERACTION_DISTANCE_SQUARED = 64.0D * 64.0D;
    private static final Map<UUID, BrushSettings> BRUSH_SETTINGS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> PAPER_SIZES = new ConcurrentHashMap<>();
    private static final Map<UUID, PlayerSyncKey> PLAYER_SYNC_KEYS = new ConcurrentHashMap<>();
    private static final Map<UUID, List<PaintOverlayPackets.PlayerPaintStrokeS2C>> PLAYER_PAINT_STROKES = new ConcurrentHashMap<>();
    private static volatile boolean allowNonFullBlockPainting = true;

    private PaintOverlayFeature() {
    }

    public static void initialize() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> {
                    recordSyncKey(handler.getPlayer());
                    sendNearbyFullSync(handler.getPlayer());
                    broadcastStoredPlayerPaint(handler.getPlayer());
                }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                PLAYER_SYNC_KEYS.remove(handler.getPlayer().getUuid()));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            recordSyncKey(player);
            sendNearbyFullSync(player);
            broadcastStoredPlayerPaint(player);
        });
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world instanceof ServerWorld serverWorld) {
                clearBlockFaces(serverWorld, pos);
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.BrushSettingsC2S.ID, (packet, context) ->
                context.server().execute(() -> setBrushSettings(context.player(), packet.color(), packet.radius())));
        ServerPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.PaperSizeC2S.ID, (packet, context) ->
                context.server().execute(() -> setPaperSize(context.player(), packet.size())));
        ServerPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.ImportPaintPaperC2S.ID, (packet, context) ->
                context.server().execute(() -> PaintGraffitiCommand.importUploadedImage(
                        context.player(), packet.filename(), packet.scale(), packet.imageBytes())));
        ServerPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.PaintStrokeC2S.ID, (packet, context) ->
                context.server().execute(() -> handlePaintStroke(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.EditorPaintStrokeC2S.ID, (packet, context) ->
                context.server().execute(() -> handleEditorPaintStroke(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.EditorModelPaintStrokeC2S.ID, (packet, context) ->
                context.server().execute(() -> handleEditorModelPaintStroke(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.EditorPaperUseC2S.ID, (packet, context) ->
                context.server().execute(() -> handleEditorPaperUse(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.RestoreFaceC2S.ID, (packet, context) ->
                context.server().execute(() -> handleRestoreFace(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.PaintRegionPatchC2S.ID, (packet, context) ->
                context.server().execute(() -> handlePaintRegionPatch(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.RestoreModelFaceC2S.ID, (packet, context) ->
                context.server().execute(() -> handleRestoreModelFace(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.ModelPaintStrokeC2S.ID, (packet, context) ->
                context.server().execute(() -> handleModelPaintStroke(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.PlayerPaintStrokeC2S.ID, (packet, context) ->
                context.server().execute(() -> handlePlayerPaintStroke(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.ClearPlayerPaintC2S.ID, (packet, context) ->
                context.server().execute(() -> handleClearPlayerPaint(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.FillPaintBucketC2S.ID, (packet, context) ->
                context.server().execute(() -> handleFillPaintBucket(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.RefillPaintBucketC2S.ID, (packet, context) ->
                context.server().execute(() -> handleRefillPaintBucket(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.LoadBrushFromBucketC2S.ID, (packet, context) ->
                context.server().execute(() -> handleLoadBrushFromBucket(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.SelectBrushSlotC2S.ID, (packet, context) ->
                context.server().execute(() -> handleSelectBrushSlot(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.StoreBrushPresetC2S.ID, (packet, context) ->
                context.server().execute(() -> handleStoreBrushPreset(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.RequestPaintConfigC2S.ID, (packet, context) ->
                context.server().execute(() -> ServerPlayNetworking.send(context.player(), new PaintOverlayPackets.PaintConfigS2C(PaintConfig.getInstance().toJson()))));
        ServerPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.UpdatePaintConfigC2S.ID, (packet, context) ->
                context.server().execute(() -> handleUpdatePaintConfig(context.player(), packet)));
        ServerTickEvents.END_WORLD_TICK.register(PaintBucketBlock::tickKicks);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                PlayerSyncKey key = syncKey(player);
                PlayerSyncKey previous = PLAYER_SYNC_KEYS.put(player.getUuid(), key);
                if (!key.equals(previous)) {
                    sendNearbyFullSync(player);
                }
            }
        });
    }

    public static BrushSettings getBrushSettings(ServerPlayerEntity player) {
        return BRUSH_SETTINGS.getOrDefault(player.getUuid(), BrushSettings.DEFAULT);
    }

    public static int getPaperSize(ServerPlayerEntity player) {
        return PAPER_SIZES.getOrDefault(player.getUuid(), DEFAULT_PAPER_SIZE);
    }

    public static boolean isNonFullBlockPaintingAllowed() {
        return allowNonFullBlockPainting;
    }

    public static void setNonFullBlockPaintingAllowed(boolean allowed) {
        allowNonFullBlockPainting = allowed;
    }

    private static void setBrushSettings(ServerPlayerEntity player, int color, int radius) {
        BRUSH_SETTINGS.put(player.getUuid(), new BrushSettings(color, radius));
    }

    private static void setPaperSize(ServerPlayerEntity player, int size) {
        PAPER_SIZES.put(player.getUuid(), MathHelper.clamp(size, MIN_PAPER_SIZE, MAX_PAPER_SIZE));
    }

    private static void handlePaintStroke(ServerPlayerEntity player, PaintOverlayPackets.PaintStrokeC2S packet) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        if (!isValidHitPoint(packet.pos(), packet.hitPos())
                || packet.hitPos().squaredDistanceTo(player.getEyePos()) > INTERACTION_DISTANCE_SQUARED) {
            return;
        }
        if (world.getBlockState(packet.pos()).getBlock() == ModBlocks.DRAWING_BOARD
                || world.getBlockState(packet.pos()).getBlock() == PaintItems.PAINT_BUCKET_BLOCK) {
            return;
        }
        if (isHoldingSprayCan(player)) {
            sprayAtByPlayer(player, world, packet.pos(), packet.face(), packet.x(), packet.y(), packet.hitPos());
            return;
        }
        if (isHoldingPaintBrush(player)) {
            paintAtByPlayer(player, world, packet.pos(), packet.face(), packet.x(), packet.y(), packet.hitPos());
            return;
        }
        if (!isHoldingEraser(player)) {
            return;
        }
        if (packet.clearFace()) {
            clearFacesAround(world, packet.pos(), packet.face(), getBrushSettings(player).radius());
            return;
        }
        eraseAt(world, packet.pos(), packet.face(), packet.x(), packet.y(), getBrushSettings(player).radius(), packet.hitPos());
    }

    private static void handleEditorPaintStroke(ServerPlayerEntity player, PaintOverlayPackets.EditorPaintStrokeC2S packet) {
        if (!(player.getWorld() instanceof ServerWorld world) || !hasPaintEditorAccess(player)) {
            return;
        }
        if (!isValidHitPoint(packet.pos(), packet.hitPos())
                || packet.hitPos().squaredDistanceTo(player.getEyePos()) > INTERACTION_DISTANCE_SQUARED) {
            return;
        }
        if (world.getBlockState(packet.pos()).getBlock() == ModBlocks.DRAWING_BOARD
                || world.getBlockState(packet.pos()).getBlock() == PaintItems.PAINT_BUCKET_BLOCK) {
            return;
        }
        if (packet.tool() == 1) {
            paintAtByPlayer(player, world, packet.pos(), packet.face(), packet.x(), packet.y(), packet.hitPos());
            return;
        }
        if (packet.tool() == 4) {
            sprayAtByPlayer(player, world, packet.pos(), packet.face(), packet.x(), packet.y(), packet.hitPos());
            return;
        }
        if (packet.tool() != 2) {
            return;
        }
        if (packet.clearFace()) {
            clearFacesAround(world, packet.pos(), packet.face(), getBrushSettings(player).radius());
            return;
        }
        eraseAt(world, packet.pos(), packet.face(), packet.x(), packet.y(), getBrushSettings(player).radius(), packet.hitPos());
    }

    private static boolean isValidHitPoint(BlockPos pos, Vec3d hitPos) {
        if (hitPos == null || !Double.isFinite(hitPos.x) || !Double.isFinite(hitPos.y) || !Double.isFinite(hitPos.z)) {
            return false;
        }
        double tolerance = 1.0E-3D;
        return hitPos.x >= pos.getX() - tolerance && hitPos.x <= pos.getX() + 1.0D + tolerance
                && hitPos.y >= pos.getY() - tolerance && hitPos.y <= pos.getY() + 1.0D + tolerance
                && hitPos.z >= pos.getZ() - tolerance && hitPos.z <= pos.getZ() + 1.0D + tolerance;
    }

    private static void handleRestoreFace(ServerPlayerEntity player, PaintOverlayPackets.RestoreFaceC2S packet) {
        if (!(player.getWorld() instanceof ServerWorld world) || !hasPaintEditorAccess(player)) {
            return;
        }
        PaintOverlayPackets.FaceData face = packet.faceData();
        if (Vec3d.ofCenter(face.pos()).squaredDistanceTo(player.getEyePos()) > INTERACTION_DISTANCE_SQUARED) {
            return;
        }
        if (!setFacePixels(world, face.pos(), face.face(), face.pixels())) {
            syncCurrentFace(world, face.pos(), face.face());
        }
    }

    private static void handlePaintRegionPatch(ServerPlayerEntity player, PaintOverlayPackets.PaintRegionPatchC2S packet) {
        if (!(player.getWorld() instanceof ServerWorld world) || !hasPaintEditorAccess(player)) {
            return;
        }
        PaintOverlayPackets.FaceRegionPatch patch = packet.patch();
        if (Vec3d.ofCenter(patch.pos()).squaredDistanceTo(player.getEyePos()) > INTERACTION_DISTANCE_SQUARED) {
            return;
        }
        if (world.getBlockState(patch.pos()).getBlock() == ModBlocks.DRAWING_BOARD
                || world.getBlockState(patch.pos()).getBlock() == PaintItems.PAINT_BUCKET_BLOCK) {
            return;
        }
        if (!applyRegionPatch(world, patch)) {
            syncCurrentFace(world, patch.pos(), patch.face());
        }
    }

    private static void handleRestoreModelFace(ServerPlayerEntity player, PaintOverlayPackets.RestoreModelFaceC2S packet) {
        if (!(player.getWorld() instanceof ServerWorld world) || !hasPaintEditorAccess(player)) {
            return;
        }
        Entity entity = world.getEntityById(packet.entityId());
        if (!(entity instanceof ItemDisplayEntity display)) {
            return;
        }
        if (display.getPos().squaredDistanceTo(player.getEyePos()) > INTERACTION_DISTANCE_SQUARED) {
            return;
        }
        ItemStack stack = display.getItemStack().copy();
        if (!isCombinedBodyStack(stack)) {
            return;
        }
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound root = component == null ? new NbtCompound() : component.copyNbt();
        boolean slim = "slim".equals(root.getString("arm_model", ""));
        if (!ModelPaintData.setFacePixels(root, packet.surface(), packet.face(), packet.pixels(), slim)) {
            return;
        }
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));
        display.setItemStack(stack);
    }

    private static boolean isHoldingPaintBrush(ServerPlayerEntity player) {
        return isPaintBrush(player.getMainHandStack()) || isPaintBrush(player.getOffHandStack());
    }

    private static ItemStack findHeldPaintBrush(ServerPlayerEntity player) {
        if (isPaintBrush(player.getMainHandStack())) {
            return player.getMainHandStack();
        }
        if (isPaintBrush(player.getOffHandStack())) {
            return player.getOffHandStack();
        }
        return ItemStack.EMPTY;
    }

    private static boolean isPaintBrush(ItemStack stack) {
        return stack.getItem() == PaintItems.PAINT_BRUSH || stack.getItem() == PaintItems.PAINT_SPRAY_CAN;
    }

    private static boolean isHoldingSprayCan(ServerPlayerEntity player) {
        return isSprayCan(player.getMainHandStack()) || isSprayCan(player.getOffHandStack());
    }

    private static boolean isSprayCan(ItemStack stack) {
        return stack.getItem() == PaintItems.PAINT_SPRAY_CAN;
    }

    private static boolean isHoldingEraser(ServerPlayerEntity player) {
        return isEraser(player.getMainHandStack()) || isEraser(player.getOffHandStack());
    }

    private static boolean isEraser(ItemStack stack) {
        return stack.getItem() == PaintItems.ERASER;
    }

    private static boolean hasPaintEditorAccess(ServerPlayerEntity player) {
        return findPaintEditorItem(player, PaintItems.PAINT_BRUSH) != ItemStack.EMPTY
                || findPaintEditorItem(player, PaintItems.PAINT_SPRAY_CAN) != ItemStack.EMPTY
                || findPaintEditorItem(player, PaintItems.ERASER) != ItemStack.EMPTY
                || findPaintEditorItem(player, PaintItems.PAINT_PAPER) != ItemStack.EMPTY;
    }

    private static ItemStack findPaintBrushLike(ServerPlayerEntity player) {
        if (isPaintBrush(player.getMainHandStack())) {
            return player.getMainHandStack();
        }
        if (isPaintBrush(player.getOffHandStack())) {
            return player.getOffHandStack();
        }
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (isPaintBrush(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack findPaintEditorItem(ServerPlayerEntity player, net.minecraft.item.Item item) {
        if (player.getMainHandStack().getItem() == item) {
            return player.getMainHandStack();
        }
        if (player.getOffHandStack().getItem() == item) {
            return player.getOffHandStack();
        }
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.getItem() == item) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static void handleEditorPaperUse(ServerPlayerEntity player, PaintOverlayPackets.EditorPaperUseC2S packet) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        if (Vec3d.ofCenter(packet.pos()).squaredDistanceTo(player.getEyePos()) > INTERACTION_DISTANCE_SQUARED) {
            return;
        }
        ItemStack paper = findPaintEditorItem(player, PaintItems.PAINT_PAPER);
        if (paper == ItemStack.EMPTY) {
            player.sendMessage(net.minecraft.text.Text.literal("缺少画纸"), true);
            return;
        }
        PaintPaperItem.useFromEditor(world, player, paper, packet.pos(), packet.face(), packet.save());
    }

    private static void handleFillPaintBucket(ServerPlayerEntity player, PaintOverlayPackets.FillPaintBucketC2S packet) {
        if (!(player.getWorld() instanceof ServerWorld world) || !player.isCreative()) {
            return;
        }
        if (Vec3d.ofCenter(packet.pos()).squaredDistanceTo(player.getEyePos()) > INTERACTION_DISTANCE_SQUARED) {
            return;
        }
        if (world.getBlockEntity(packet.pos()) instanceof PaintBucketBlockEntity bucket) {
            bucket.fill(packet.color());
        }
    }

    private static void handleSelectBrushSlot(ServerPlayerEntity player, PaintOverlayPackets.SelectBrushSlotC2S packet) {
        ItemStack brush = findPaintBrushLike(player);
        if (brush != ItemStack.EMPTY) {
            PaintBrushItem.setSelectedSlot(brush, packet.slot());
            player.getInventory().markDirty();
        }
    }

    private static void handleStoreBrushPreset(ServerPlayerEntity player, PaintOverlayPackets.StoreBrushPresetC2S packet) {
        ItemStack brush = findPaintBrushLike(player);
        if (brush != ItemStack.EMPTY) {
            PaintBrushItem.storePresetColor(brush, packet.slot(), packet.color());
            player.getInventory().markDirty();
        }
    }

    private static void handleUpdatePaintConfig(ServerPlayerEntity player, PaintOverlayPackets.UpdatePaintConfigC2S packet) {
        if (player == null || (!player.hasPermissionLevel(2) && !player.isCreative())) {
            return;
        }
        PaintConfig config = PaintConfig.fromJson(packet.json());
        PaintConfig.setInstance(config);
        if (player.getServer() != null) {
            PaintOverlayPackets.PaintConfigS2C sync = new PaintOverlayPackets.PaintConfigS2C(config.toJson());
            for (ServerPlayerEntity target : player.getServer().getPlayerManager().getPlayerList()) {
                ServerPlayNetworking.send(target, sync);
            }
        }
    }

    private static void handleRefillPaintBucket(ServerPlayerEntity player, PaintOverlayPackets.RefillPaintBucketC2S packet) {
        if (!(player.getWorld() instanceof ServerWorld world) || !player.isCreative()) {
            return;
        }
        if (Vec3d.ofCenter(packet.pos()).squaredDistanceTo(player.getEyePos()) > INTERACTION_DISTANCE_SQUARED) {
            return;
        }
        if (world.getBlockEntity(packet.pos()) instanceof PaintBucketBlockEntity bucket) {
            bucket.refillBrushLoads();
        }
    }

    private static void handleLoadBrushFromBucket(ServerPlayerEntity player, PaintOverlayPackets.LoadBrushFromBucketC2S packet) {
        if (!(player.getWorld() instanceof ServerWorld world) || player.isCreative()) {
            return;
        }
        if (Vec3d.ofCenter(packet.pos()).squaredDistanceTo(player.getEyePos()) > INTERACTION_DISTANCE_SQUARED) {
            return;
        }
        ItemStack brush = findHeldPaintBrush(player);
        if (brush == ItemStack.EMPTY) {
            player.sendMessage(net.minecraft.text.Text.literal("请手持画笔或喷漆罐"), true);
            return;
        }
        if (!(world.getBlockEntity(packet.pos()) instanceof PaintBucketBlockEntity bucket) || !bucket.isFilled()) {
            player.sendMessage(net.minecraft.text.Text.literal("染料桶没有颜色"), true);
            return;
        }
        int slot = PaintBrushItem.chooseLoadSlot(brush, bucket.getColor());
        if (slot < 0) {
            player.sendMessage(net.minecraft.text.Text.literal("容量槽已有不同颜色，或同色容量仍高于 10%"), true);
            return;
        }
        if (!bucket.takeBrushLoad()) {
            player.sendMessage(net.minecraft.text.Text.literal("这个染料桶今天已经干了"), true);
            return;
        }
        PaintBrushItem.loadPaint(brush, slot, bucket.getColor());
        player.getInventory().markDirty();
        player.sendMessage(net.minecraft.text.Text.literal("已装满容量槽，染料桶剩余 " + bucket.remainingBrushLoadsToday()
                + "/" + PaintConfig.getInstance().bucketBrushLoads), true);
    }

    private static void handleModelPaintStroke(ServerPlayerEntity player, PaintOverlayPackets.ModelPaintStrokeC2S packet) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        Entity entity = world.getEntityById(packet.entityId());
        if (!(entity instanceof ItemDisplayEntity display)) {
            return;
        }
        if (display.getPos().squaredDistanceTo(player.getEyePos()) > INTERACTION_DISTANCE_SQUARED) {
            return;
        }
        ItemStack stack = display.getItemStack().copy();
        if (!isCombinedBodyStack(stack)) {
            return;
        }

        boolean clear;
        int color;
        int radius;
        if (isHoldingPaintBrush(player)) {
            BrushUse brushUse = brushUse(player);
            if (brushUse == null) {
                return;
            }
            clear = false;
            color = brushUse.settings().color();
            radius = brushUse.settings().radius();
        } else if (isHoldingEraser(player)) {
            clear = true;
            color = 0;
            radius = getBrushSettings(player).radius();
        } else {
            return;
        }

        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound root = component == null ? new NbtCompound() : component.copyNbt();
        boolean slim = "slim".equals(root.getString("arm_model", ""));
        ModelPaintData.PaintResult result = ModelPaintData.paintLimited(root, packet.surface(), packet.face(), packet.x(), packet.y(),
                radius, color, clear && packet.clearFace(), slim, clear ? Integer.MAX_VALUE : paintBudget(player));
        if (!result.changed()) {
            return;
        }
        if (isHoldingPaintBrush(player) && !player.isCreative()) {
            ItemStack brush = findHeldPaintBrush(player);
            PaintBrushItem.consumePaint(brush, PaintBrushItem.getSelectedSlot(brush), PaintConfig.getInstance().scaledConsumption(result.changedPixels()));
            player.getInventory().markDirty();
        }
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));
        display.setItemStack(stack);
    }

    private static void handlePlayerPaintStroke(ServerPlayerEntity player, PaintOverlayPackets.PlayerPaintStrokeC2S packet) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        Entity entity = world.getEntityById(packet.entityId());
        if (!(entity instanceof ServerPlayerEntity target)) {
            return;
        }
        if (target.getUuid().equals(player.getUuid())) {
            return;
        }
        if (target.getPos().squaredDistanceTo(player.getEyePos()) > INTERACTION_DISTANCE_SQUARED) {
            return;
        }

        boolean clear;
        int color;
        int radius;
        BrushUse brushUse = null;
        if (isHoldingPaintBrush(player)) {
            brushUse = brushUse(player);
            if (brushUse == null) {
                return;
            }
            clear = false;
            color = brushUse.settings().color();
            radius = brushUse.settings().radius();
        } else if (isHoldingEraser(player)) {
            clear = true;
            color = 0;
            radius = getBrushSettings(player).radius();
        } else {
            return;
        }

        if (brushUse != null && !player.isCreative()) {
            int estimatedPixels = packet.clearFace() ? radius * radius : Math.max(1, radius * radius);
            PaintBrushItem.consumePaint(brushUse.stack(), PaintBrushItem.getSelectedSlot(brushUse.stack()),
                    PaintConfig.getInstance().scaledConsumption(estimatedPixels));
            player.getInventory().markDirty();
        }
        PaintOverlayPackets.PlayerPaintStrokeS2C syncPacket = new PaintOverlayPackets.PlayerPaintStrokeS2C(
                target.getId(), packet.surface(), packet.face(), packet.x(), packet.y(), color, radius, clear && packet.clearFace());
        recordPlayerPaintStroke(target, syncPacket);
        broadcastPlayerPaint(world, target, syncPacket);
    }

    private static void handleClearPlayerPaint(ServerPlayerEntity player, PaintOverlayPackets.ClearPlayerPaintC2S packet) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        Entity entity = world.getEntityById(packet.entityId());
        if (!(entity instanceof ServerPlayerEntity target)) {
            return;
        }
        if (!target.getUuid().equals(player.getUuid())
                && target.getPos().squaredDistanceTo(player.getEyePos()) > INTERACTION_DISTANCE_SQUARED
                && !player.hasPermissionLevel(2)) {
            return;
        }
        PLAYER_PAINT_STROKES.remove(target.getUuid());
        broadcastPlayerPaintClear(world, target);
    }

    private static void handleEditorModelPaintStroke(ServerPlayerEntity player, PaintOverlayPackets.EditorModelPaintStrokeC2S packet) {
        if (!(player.getWorld() instanceof ServerWorld world) || !hasPaintEditorAccess(player)) {
            return;
        }
        Entity entity = world.getEntityById(packet.entityId());
        if (!(entity instanceof ItemDisplayEntity display)) {
            return;
        }
        if (display.getPos().squaredDistanceTo(player.getEyePos()) > INTERACTION_DISTANCE_SQUARED) {
            return;
        }
        ItemStack stack = display.getItemStack().copy();
        if (!isCombinedBodyStack(stack)) {
            return;
        }

        boolean clear;
        int color;
        int radius = getBrushSettings(player).radius();
        BrushUse brushUse = null;
        if (packet.tool() == 1) {
            brushUse = brushUse(player);
            if (brushUse == null) {
                return;
            }
            clear = false;
            color = brushUse.settings().color();
            radius = brushUse.settings().radius();
        } else if (packet.tool() == 2) {
            clear = true;
            color = 0;
        } else {
            return;
        }

        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound root = component == null ? new NbtCompound() : component.copyNbt();
        boolean slim = "slim".equals(root.getString("arm_model", ""));
        ModelPaintData.PaintResult result = ModelPaintData.paintLimited(root, packet.surface(), packet.face(), packet.x(), packet.y(),
                radius, color, clear && packet.clearFace(), slim, clear ? Integer.MAX_VALUE : paintBudget(player));
        if (!result.changed()) {
            return;
        }
        if (brushUse != null && !player.isCreative()) {
            PaintBrushItem.consumePaint(brushUse.stack(), PaintBrushItem.getSelectedSlot(brushUse.stack()), PaintConfig.getInstance().scaledConsumption(result.changedPixels()));
            player.getInventory().markDirty();
        }
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));
        display.setItemStack(stack);
    }

    private static boolean isCombinedBodyStack(ItemStack stack) {
        Identifier model = stack.get(DataComponentTypes.ITEM_MODEL);
        return model != null && model.equals(Identifier.of("monvhua", "combined_body"));
    }

    private static BrushUse brushUse(ServerPlayerEntity player) {
        if (player.isCreative()) {
            return new BrushUse(ItemStack.EMPTY, getBrushSettings(player));
        }
        ItemStack brush = findPaintBrushLike(player);
        if (brush == ItemStack.EMPTY || !PaintBrushItem.hasPaint(brush)) {
            player.sendMessage(net.minecraft.text.Text.literal("画笔没有颜料"), true);
            return null;
        }
        int slot = PaintBrushItem.getSelectedSlot(brush);
        return new BrushUse(brush, new BrushSettings(PaintBrushItem.getPaintColor(brush, slot), getBrushSettings(player).radius()));
    }

    private static BrushUse sprayUse(ServerPlayerEntity player) {
        if (player.isCreative()) {
            return new BrushUse(ItemStack.EMPTY, getBrushSettings(player));
        }
        ItemStack sprayCan = findPaintEditorItem(player, PaintItems.PAINT_SPRAY_CAN);
        if (sprayCan == ItemStack.EMPTY || !PaintBrushItem.hasPaint(sprayCan)) {
            player.sendMessage(net.minecraft.text.Text.literal("喷漆罐没有颜料"), true);
            return null;
        }
        int slot = PaintBrushItem.getSelectedSlot(sprayCan);
        return new BrushUse(sprayCan, new BrushSettings(PaintBrushItem.getPaintColor(sprayCan, slot), getBrushSettings(player).radius()));
    }

    private static int paintBudget(ServerPlayerEntity player) {
        return paintBudget(player, findPaintBrushLike(player));
    }

    private static int paintBudget(ServerPlayerEntity player, ItemStack brush) {
        if (player.isCreative()) {
            return Integer.MAX_VALUE;
        }
        return brush == ItemStack.EMPTY ? 0 : paintPixelBudget(PaintBrushItem.getRemainingPaintPercent(brush, PaintBrushItem.getSelectedSlot(brush)));
    }

    private static int paintPixelBudget(double remainingPaint) {
        double multiplier = PaintConfig.getInstance().brushConsumptionMultiplier;
        if (remainingPaint <= 0.0D) {
            return 0;
        }
        if (multiplier <= 0.0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(1, (int) Math.floor(remainingPaint / multiplier));
    }

    public static void paintAt(ServerWorld world, BlockPos pos, Direction face, int x, int y, BrushSettings settings) {
        paintAtLimited(world, pos, face, x, y, settings, Integer.MAX_VALUE);
    }

    private static void paintAtByPlayer(ServerPlayerEntity player, ServerWorld world, BlockPos pos, Direction face,
                                        int x, int y, Vec3d hitPos) {
        BrushUse brushUse = brushUse(player);
        if (brushUse == null) {
            return;
        }
        int changedPixels = paintOnSurfacesLimited(world, hitPos, brushUse.settings(),
                paintBudget(player, brushUse.stack()));
        if (changedPixels > 0 && !player.isCreative()) {
            PaintBrushItem.consumePaint(brushUse.stack(), PaintBrushItem.getSelectedSlot(brushUse.stack()), PaintConfig.getInstance().scaledConsumption(changedPixels));
            player.getInventory().markDirty();
        }
    }

    private static void sprayAtByPlayer(ServerPlayerEntity player, ServerWorld world, BlockPos pos, Direction face,
                                        int x, int y, Vec3d hitPos) {
        BrushUse brushUse = sprayUse(player);
        if (brushUse == null) {
            return;
        }
        int changedPixels = sprayOnSurfacesLimited(world, hitPos, brushUse.settings(),
                paintBudget(player, brushUse.stack()));
        if (changedPixels > 0 && !player.isCreative()) {
            PaintBrushItem.consumePaint(brushUse.stack(), PaintBrushItem.getSelectedSlot(brushUse.stack()), PaintConfig.getInstance().scaledConsumption(changedPixels));
            player.getInventory().markDirty();
        }
    }

    private static int paintAtLimited(ServerWorld world, BlockPos pos, Direction face, int x, int y, BrushSettings settings, int budget) {
        if (budget <= 0) {
            return 0;
        }
        if (!canPlacePaint(world, pos)) {
            return 0;
        }
        PaintOverlayStore store = PaintOverlayStore.get(world);
        int radius = MathHelper.clamp(settings.radius(), MIN_RADIUS, MAX_MANUAL_RADIUS);
        int radiusSquared = radius * radius;
        int changedPixels = 0;
        for (int dy = -radius + 1; dy <= radius - 1; dy++) {
            for (int dx = -radius + 1; dx <= radius - 1; dx++) {
                if (changedPixels >= budget) {
                    break;
                }
                if (dx * dx + dy * dy > radiusSquared) {
                    continue;
                }
                if (store.setPixel(pos, face, x + dx, y + dy, settings.color())) {
                    changedPixels++;
                }
            }
        }
        if (changedPixels <= 0) {
            return 0;
        }
        broadcastFace(world, pos, face, store.getPixels(pos, face));
        return changedPixels;
    }

    private static int paintOnSurfacesLimited(ServerWorld world, Vec3d hitPos, BrushSettings settings, int budget) {
        if (budget <= 0) {
            return 0;
        }
        PaintOverlayStore store = PaintOverlayStore.get(world);
        List<PaintSurfaceTargeting.SurfacePixel> targets = PaintSurfaceTargeting.collect(
                world, hitPos, settings.radius());
        Set<PaintOverlayStore.FaceKey> changedFaces = new LinkedHashSet<>();
        int changedPixels = 0;
        for (PaintSurfaceTargeting.SurfacePixel target : targets) {
            if (changedPixels >= budget) {
                break;
            }
            PaintOverlayStore.FaceKey key = target.key();
            if (store.setPixel(key.pos(), key.face(), target.x(), target.y(), settings.color())) {
                changedFaces.add(key);
                changedPixels++;
            }
        }
        broadcastChangedFaces(world, store, changedFaces);
        return changedPixels;
    }

    private static int sprayAtLimited(ServerWorld world, BlockPos pos, Direction face, int x, int y, BrushSettings settings, int budget) {
        if (budget <= 0) {
            return 0;
        }
        if (!canPlacePaint(world, pos)) {
            return 0;
        }
        PaintOverlayStore store = PaintOverlayStore.get(world);
        int radius = MathHelper.clamp(settings.radius(), MIN_RADIUS, MAX_MANUAL_RADIUS);
        int[] pixels = store.getPixels(pos, face);
        Random random = Random.create(spraySeed(world, pos, face, x, y));
        int dotCount = MathHelper.clamp(radius * radius * 3, 6, 384);
        int changedPixels = 0;
        for (int i = 0; i < dotCount && changedPixels < budget; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = Math.sqrt(random.nextDouble()) * radius;
            double t = radius <= 0 ? 0.0D : distance / radius;
            float falloff = (float) Math.pow(1.0D - t, 1.4D);
            if (random.nextFloat() > falloff) {
                continue;
            }
            int px = x + (int) Math.round(Math.cos(angle) * distance);
            int py = y + (int) Math.round(Math.sin(angle) * distance);
            if (px < 0 || px >= PaintOverlayStore.SIZE || py < 0 || py >= PaintOverlayStore.SIZE) {
                continue;
            }
            int index = py * PaintOverlayStore.SIZE + px;
            int color = sprayColor(pixels[index], settings.color(), falloff);
            if (pixels[index] != color) {
                pixels[index] = color;
                changedPixels++;
            }
        }
        if (changedPixels <= 0 || !store.setPixels(pos, face, pixels)) {
            return 0;
        }
        broadcastFace(world, pos, face, pixels);
        return changedPixels;
    }

    private static int sprayOnSurfacesLimited(ServerWorld world, Vec3d hitPos, BrushSettings settings, int budget) {
        if (budget <= 0) {
            return 0;
        }
        List<PaintSurfaceTargeting.SurfacePixel> targets = PaintSurfaceTargeting.collect(
                world, hitPos, settings.radius());
        if (targets.isEmpty()) {
            return 0;
        }
        PaintOverlayStore store = PaintOverlayStore.get(world);
        Map<PaintOverlayStore.FaceKey, int[]> facePixels = new LinkedHashMap<>();
        Set<PaintOverlayStore.FaceKey> changedFaces = new LinkedHashSet<>();
        Random random = Random.create(spraySeed(world, BlockPos.ofFloored(hitPos), Direction.UP,
                MathHelper.floor(hitPos.x * PaintOverlayStore.SIZE), MathHelper.floor(hitPos.y * PaintOverlayStore.SIZE)));
        int radius = MathHelper.clamp(settings.radius(), MIN_RADIUS, MAX_MANUAL_RADIUS);
        int dotCount = MathHelper.clamp(radius * radius * 3, 6, 384);
        double worldRadius = PaintSurfaceTargeting.worldRadius(radius);
        int changedPixels = 0;
        for (int i = 0; i < dotCount && changedPixels < budget; i++) {
            PaintSurfaceTargeting.SurfacePixel target = targets.get(random.nextInt(targets.size()));
            double normalizedDistance = worldRadius <= 0.0D
                    ? 0.0D
                    : Math.min(1.0D, Math.sqrt(target.distanceSquared()) / worldRadius);
            float falloff = (float) Math.pow(1.0D - normalizedDistance, 1.4D);
            if (random.nextFloat() > falloff) {
                continue;
            }
            PaintOverlayStore.FaceKey key = target.key();
            int[] pixels = facePixels.computeIfAbsent(key, ignored -> store.getPixels(key.pos(), key.face()));
            int index = target.y() * PaintOverlayStore.SIZE + target.x();
            int color = sprayColor(pixels[index], settings.color(), falloff);
            if (pixels[index] != color) {
                pixels[index] = color;
                changedFaces.add(key);
                changedPixels++;
            }
        }
        for (PaintOverlayStore.FaceKey key : changedFaces) {
            int[] pixels = facePixels.get(key);
            if (pixels != null && store.setPixels(key.pos(), key.face(), pixels)) {
                broadcastFace(world, key.pos(), key.face(), pixels);
            }
        }
        return changedPixels;
    }

    private static long spraySeed(ServerWorld world, BlockPos pos, Direction face, int x, int y) {
        long seed = world.getTime();
        seed = seed * 31L + pos.asLong();
        seed = seed * 31L + face.getIndex();
        seed = seed * 31L + x;
        seed = seed * 31L + y;
        return seed;
    }

    private static int sprayColor(int existing, int color, float strength) {
        int baseAlpha = MathHelper.clamp(color >>> 24, 1, 255);
        int alpha = MathHelper.clamp((int) (baseAlpha * strength), 1, baseAlpha);
        int rgb = color & 0x00FFFFFF;
        if ((existing & 0x00FFFFFF) == rgb) {
            alpha = Math.min(baseAlpha, (existing >>> 24) + alpha);
        }
        return (alpha << 24) | rgb;
    }

    public static void eraseAt(ServerWorld world, BlockPos pos, Direction face, int x, int y, int radius) {
        PaintOverlayStore store = PaintOverlayStore.get(world);
        radius = MathHelper.clamp(radius, MIN_RADIUS, MAX_MANUAL_RADIUS);
        int radiusSquared = radius * radius;
        boolean changed = false;
        for (int dy = -radius + 1; dy <= radius - 1; dy++) {
            for (int dx = -radius + 1; dx <= radius - 1; dx++) {
                if (dx * dx + dy * dy > radiusSquared) {
                    continue;
                }
                changed |= store.setPixel(pos, face, x + dx, y + dy, 0);
            }
        }
        if (!changed) {
            return;
        }
        broadcastFace(world, pos, face, store.getPixels(pos, face));
    }

    private static void eraseAt(ServerWorld world, BlockPos pos, Direction face, int x, int y, int radius, Vec3d hitPos) {
        PaintOverlayStore store = PaintOverlayStore.get(world);
        Set<PaintOverlayStore.FaceKey> changedFaces = new LinkedHashSet<>();
        for (PaintSurfaceTargeting.SurfacePixel target : PaintSurfaceTargeting.collect(world, hitPos, radius)) {
            PaintOverlayStore.FaceKey key = target.key();
            if (store.setPixel(key.pos(), key.face(), target.x(), target.y(), 0)) {
                changedFaces.add(key);
            }
        }
        broadcastChangedFaces(world, store, changedFaces);
    }

    public static void clearFacesAround(ServerWorld world, BlockPos pos, Direction face, int radius) {
        int blockRadius = Math.max(0, MathHelper.clamp(radius, MIN_RADIUS, MAX_MANUAL_RADIUS) - 1);
        for (int a = -blockRadius; a <= blockRadius; a++) {
            for (int b = -blockRadius; b <= blockRadius; b++) {
                if (a * a + b * b > blockRadius * blockRadius) {
                    continue;
                }
                clearFace(world, offsetOnFacePlane(pos, face, a, b), face);
            }
        }
    }

    private static BlockPos offsetOnFacePlane(BlockPos pos, Direction face, int a, int b) {
        return switch (face) {
            case UP, DOWN -> pos.add(a, 0, b);
            case NORTH, SOUTH -> pos.add(a, b, 0);
            case WEST, EAST -> pos.add(0, b, a);
        };
    }

    public static void paintBlob(ServerWorld world, BlockPos pos, Direction face, int centerX, int centerY,
                                 int radiusX, int radiusY, int color, int roughness) {
        if (color != 0 && !canPlacePaint(world, pos)) {
            return;
        }
        PaintOverlayStore store = PaintOverlayStore.get(world);
        radiusX = MathHelper.clamp(radiusX, 1, PaintOverlayStore.SIZE);
        radiusY = MathHelper.clamp(radiusY, 1, PaintOverlayStore.SIZE);
        boolean changed = false;
        int minX = centerX - radiusX - 1;
        int maxX = centerX + radiusX + 1;
        int minY = centerY - radiusY - 1;
        int maxY = centerY + radiusY + 1;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double nx = (x - centerX) / (double) radiusX;
                double ny = (y - centerY) / (double) radiusY;
                double edgeNoise = (((x * 734287 + y * 912271 + roughness * 438289) & 15) - 7) * 0.035D;
                if (nx * nx + ny * ny > 1.0D + edgeNoise) {
                    continue;
                }
                changed |= store.setPixel(pos, face, x, y, color);
            }
        }
        if (changed) {
            broadcastFace(world, pos, face, store.getPixels(pos, face));
        }
    }

    public static void paintPixels(ServerWorld world, BlockPos pos, Direction face, int color, BiPredicate<Integer, Integer> predicate) {
        if (color != 0 && !canPlacePaint(world, pos)) {
            return;
        }
        PaintOverlayStore store = PaintOverlayStore.get(world);
        boolean changed = false;
        for (int y = 0; y < PaintOverlayStore.SIZE; y++) {
            for (int x = 0; x < PaintOverlayStore.SIZE; x++) {
                if (predicate.test(x, y)) {
                    changed |= store.setPixel(pos, face, x, y, color);
                }
            }
        }
        if (changed) {
            broadcastFace(world, pos, face, store.getPixels(pos, face));
        }
    }

    public static void clearFace(ServerWorld world, BlockPos pos, Direction face) {
        PaintOverlayStore store = PaintOverlayStore.get(world);
        if (!store.clearFace(pos, face)) {
            return;
        }
        broadcastFace(world, pos, face, new int[PaintOverlayStore.FACE_PIXELS]);
    }

    public static void clearBlockFaces(ServerWorld world, BlockPos pos) {
        for (Direction face : Direction.values()) {
            clearFace(world, pos, face);
        }
    }

    public static boolean setFacePixels(ServerWorld world, BlockPos pos, Direction face, int[] pixels) {
        if (hasPaintPixels(pixels) && !canPlacePaint(world, pos)) {
            return false;
        }
        PaintOverlayStore store = PaintOverlayStore.get(world);
        if (!store.setPixels(pos, face, pixels)) {
            return false;
        }
        broadcastFace(world, pos, face, store.getPixels(pos, face));
        return true;
    }

    public static boolean applyRegionPatch(ServerWorld world, PaintOverlayPackets.FaceRegionPatch patch) {
        if (hasPaintPixels(patch.pixels()) && !canPlacePaint(world, patch.pos())) {
            return false;
        }
        PaintOverlayStore store = PaintOverlayStore.get(world);
        int[] before = store.getPixels(patch.pos(), patch.face());
        int[] after = before.clone();
        int[] patchPixels = patch.pixels();
        boolean changed = false;
        for (int y = 0; y < patch.height(); y++) {
            for (int x = 0; x < patch.width(); x++) {
                int sourceIndex = y * patch.width() + x;
                int targetX = patch.startX() + x;
                int targetY = patch.startY() + y;
                if (sourceIndex < 0 || sourceIndex >= patchPixels.length
                        || targetX < 0 || targetX >= PaintOverlayStore.SIZE
                        || targetY < 0 || targetY >= PaintOverlayStore.SIZE) {
                    continue;
                }
                int targetIndex = targetY * PaintOverlayStore.SIZE + targetX;
                int color = patchPixels[sourceIndex];
                if (after[targetIndex] != color) {
                    after[targetIndex] = color;
                    changed = true;
                }
            }
        }
        if (!changed || !store.setPixels(patch.pos(), patch.face(), after)) {
            return false;
        }
        broadcastRegionPatch(world, patch);
        return true;
    }

    public static boolean canPlacePaint(net.minecraft.world.BlockView world, BlockPos pos) {
        return allowNonFullBlockPainting || world.getBlockState(pos).isFullCube(world, pos);
    }

    private static boolean hasPaintPixels(int[] pixels) {
        if (pixels == null) {
            return false;
        }
        for (int pixel : pixels) {
            if (pixel != 0) {
                return true;
            }
        }
        return false;
    }

    private static void sendNearbyFullSync(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        List<PaintOverlayPackets.FaceData> faces = PaintOverlayStore.get(world).toStoredFaces().stream()
                .filter(face -> isNear(player, face.pos()))
                .map(face -> new PaintOverlayPackets.FaceData(face.pos(), face.face(), face.pixels()))
                .toList();
        ServerPlayNetworking.send(player, new PaintOverlayPackets.FullSyncS2C(faces));
        sendNearbyPlayerPaintSync(player, world);
    }

    private static void broadcastFace(ServerWorld world, BlockPos pos, Direction face, int[] pixels) {
        PaintOverlayPackets.FaceUpdateS2C packet = new PaintOverlayPackets.FaceUpdateS2C(
                new PaintOverlayPackets.FaceData(pos, face, pixels));
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (isNear(player, pos)) {
                ServerPlayNetworking.send(player, packet);
            }
        }
    }

    private static void broadcastChangedFaces(ServerWorld world, PaintOverlayStore store,
                                              Set<PaintOverlayStore.FaceKey> changedFaces) {
        for (PaintOverlayStore.FaceKey key : changedFaces) {
            broadcastFace(world, key.pos(), key.face(), store.getPixels(key.pos(), key.face()));
        }
    }

    private static void syncCurrentFace(ServerWorld world, BlockPos pos, Direction face) {
        broadcastFace(world, pos, face, PaintOverlayStore.get(world).getPixels(pos, face));
    }

    private static void broadcastRegionPatch(ServerWorld world, PaintOverlayPackets.FaceRegionPatch patch) {
        PaintOverlayPackets.FaceRegionPatchS2C packet = new PaintOverlayPackets.FaceRegionPatchS2C(patch);
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (isNear(player, patch.pos())) {
                ServerPlayNetworking.send(player, packet);
            }
        }
    }

    private static void broadcastPlayerPaint(ServerWorld world, ServerPlayerEntity target, PaintOverlayPackets.PlayerPaintStrokeS2C packet) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.getEyePos().squaredDistanceTo(target.getPos()) <= SYNC_DISTANCE_SQUARED) {
                ServerPlayNetworking.send(player, packet);
            }
        }
    }

    private static void broadcastPlayerPaintClear(ServerWorld world, ServerPlayerEntity target) {
        PaintOverlayPackets.ClearPlayerPaintS2C packet = new PaintOverlayPackets.ClearPlayerPaintS2C(target.getId());
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.getEyePos().squaredDistanceTo(target.getPos()) <= SYNC_DISTANCE_SQUARED) {
                ServerPlayNetworking.send(player, packet);
            }
        }
    }

    private static void recordPlayerPaintStroke(ServerPlayerEntity target, PaintOverlayPackets.PlayerPaintStrokeS2C stroke) {
        List<PaintOverlayPackets.PlayerPaintStrokeS2C> strokes = PLAYER_PAINT_STROKES.computeIfAbsent(target.getUuid(),
                ignored -> new java.util.concurrent.CopyOnWriteArrayList<>());
        strokes.add(stroke);
        int maxStrokes = 2048;
        while (strokes.size() > maxStrokes) {
            strokes.remove(0);
        }
    }

    private static void sendNearbyPlayerPaintSync(ServerPlayerEntity receiver, ServerWorld world) {
        for (ServerPlayerEntity target : world.getPlayers()) {
            List<PaintOverlayPackets.PlayerPaintStrokeS2C> strokes = PLAYER_PAINT_STROKES.get(target.getUuid());
            if (strokes == null || strokes.isEmpty()) {
                continue;
            }
            if (receiver.getEyePos().squaredDistanceTo(target.getPos()) <= SYNC_DISTANCE_SQUARED) {
                ServerPlayNetworking.send(receiver, new PaintOverlayPackets.PlayerPaintDataS2C(target.getId(), strokes));
            }
        }
    }

    private static void broadcastStoredPlayerPaint(ServerPlayerEntity target) {
        if (!(target.getWorld() instanceof ServerWorld world)) {
            return;
        }
        List<PaintOverlayPackets.PlayerPaintStrokeS2C> strokes = PLAYER_PAINT_STROKES.get(target.getUuid());
        if (strokes == null || strokes.isEmpty()) {
            return;
        }
        PaintOverlayPackets.PlayerPaintDataS2C packet = new PaintOverlayPackets.PlayerPaintDataS2C(target.getId(), strokes);
        for (ServerPlayerEntity receiver : world.getPlayers()) {
            if (receiver.getEyePos().squaredDistanceTo(target.getPos()) <= SYNC_DISTANCE_SQUARED) {
                ServerPlayNetworking.send(receiver, packet);
            }
        }
    }

    private static boolean isNear(ServerPlayerEntity player, BlockPos pos) {
        return Vec3d.ofCenter(pos).squaredDistanceTo(player.getEyePos()) <= SYNC_DISTANCE_SQUARED;
    }

    private static void recordSyncKey(ServerPlayerEntity player) {
        PLAYER_SYNC_KEYS.put(player.getUuid(), syncKey(player));
    }

    private static PlayerSyncKey syncKey(ServerPlayerEntity player) {
        ChunkPos chunkPos = player.getChunkPos();
        return new PlayerSyncKey(player.getWorld().getRegistryKey().getValue().toString(), chunkPos.x, chunkPos.z);
    }

    public record BrushSettings(int color, int radius) {
        public static final BrushSettings DEFAULT = new BrushSettings(DEFAULT_COLOR, DEFAULT_RADIUS);

        public BrushSettings {
            if ((color >>> 24) == 0) {
                color |= 0xFF000000;
            }
            radius = MathHelper.clamp(radius, MIN_RADIUS, MAX_MANUAL_RADIUS);
        }
    }

    private record PlayerSyncKey(String world, int chunkX, int chunkZ) {
    }

    private record BrushUse(ItemStack stack, BrushSettings settings) {
    }
}
