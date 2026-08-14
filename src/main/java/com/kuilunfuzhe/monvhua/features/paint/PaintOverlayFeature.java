package com.kuilunfuzhe.monvhua.features.paint;

import com.kuilunfuzhe.monvhua.network.paint.PaintOverlayPackets;
import com.kuilunfuzhe.monvhua.features.dissolve.DissolveFeature;
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
import net.minecraft.server.MinecraftServer;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import javax.imageio.ImageIO;

public final class PaintOverlayFeature {
    public static final int DEFAULT_COLOR = 0xFFFF2A4F;
    public static final int DEFAULT_RADIUS = 1;
    public static final int MIN_RADIUS = 1;
    public static final int MAX_RADIUS = 16;
    public static final int MAX_MANUAL_RADIUS = 200;
    public static final int DEFAULT_PAPER_SIZE = 3;
    public static final int MIN_PAPER_SIZE = 1;
    public static final int MAX_PAPER_SIZE = 25;
    private static final double PAINT_SYNC_RADIUS = 20.0D;
    private static final double PAINT_SYNC_DISTANCE_SQUARED = PAINT_SYNC_RADIUS * PAINT_SYNC_RADIUS;
    private static final double PAINT_RETAIN_RADIUS = 22.0D;
    private static final double PAINT_RETAIN_DISTANCE_SQUARED = PAINT_RETAIN_RADIUS * PAINT_RETAIN_RADIUS;
    private static final double PLAYER_PAINT_SYNC_DISTANCE_SQUARED = 128.0D * 128.0D;
    private static final double INTERACTION_DISTANCE_SQUARED = 64.0D * 64.0D;
    private static final int MAX_SYNC_FACES_PER_PACKET = 32;
    private static final Map<UUID, BrushSettings> BRUSH_SETTINGS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> PAPER_SIZES = new ConcurrentHashMap<>();
    private static final Map<UUID, PlayerSyncState> PLAYER_SYNC_STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, PlayerPaintWindow> PLAYER_PAINT_WINDOWS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> PAINT_SYNC_GENERATIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, List<PaintOverlayPackets.PlayerPaintStrokeS2C>> PLAYER_PAINT_STROKES = new ConcurrentHashMap<>();
    private static final Map<UUID, ImportedPaperUpload> IMPORTED_PAPER_UPLOADS = new ConcurrentHashMap<>();
    private static volatile boolean allowNonFullBlockPainting = true;

    private PaintOverlayFeature() {
    }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = System.currentTimeMillis();
            IMPORTED_PAPER_UPLOADS.entrySet().removeIf(entry -> now - entry.getValue().createdAt() > 30_000L);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> {
                    recordSyncState(handler.getPlayer());
                    queueNearbySnapshot(handler.getPlayer());
                    broadcastStoredPlayerPaint(handler.getPlayer());
                }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID playerId = handler.getPlayer().getUuid();
            PLAYER_SYNC_STATES.remove(playerId);
            PLAYER_PAINT_WINDOWS.remove(playerId);
            PAINT_SYNC_GENERATIONS.remove(playerId);
            IMPORTED_PAPER_UPLOADS.remove(playerId);
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            recordSyncState(player);
            queueNearbySnapshot(player);
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
                        context.player(), packet.filename(), packet.imageBytes())));
        ServerPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.PlaceImportedPaperC2S.ID, (packet, context) ->
                context.server().execute(() -> handleImportedPaperPlacement(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.PlaceImportedPaperBeginC2S.ID, (packet, context) ->
                context.server().execute(() -> beginImportedPaperUpload(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.PlaceImportedPaperChunkC2S.ID, (packet, context) ->
                context.server().execute(() -> receiveImportedPaperChunk(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.PlaceImportedPaperCommitC2S.ID, (packet, context) ->
                context.server().execute(() -> commitImportedPaperUpload(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.RequestImportedPaperImageC2S.ID, (packet, context) ->
                context.server().execute(() -> sendImportedPaperPreview(context.player(), packet.imageId())));
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
                PlayerSyncState current = syncState(player);
                PlayerSyncState previous = PLAYER_SYNC_STATES.put(player.getUuid(), current);
                if (previous != null && current.hasMovedFrom(previous)) {
                    refreshPaintWindow(player);
                }
                sendPaintWindowDelta(player);
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

    private static void handleImportedPaperPlacement(ServerPlayerEntity player, PaintOverlayPackets.PlaceImportedPaperC2S packet) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        if (Vec3d.ofCenter(packet.pos()).squaredDistanceTo(player.getEyePos()) > INTERACTION_DISTANCE_SQUARED
                || !PaintOverlayFeature.canPlacePaint(world, packet.pos())) {
            return;
        }
        ItemStack paper = findImportedImagePaper(player);
        if (paper == ItemStack.EMPTY) {
            return;
        }
        java.util.UUID imageId = PaintPaperItem.getImportedImageId(paper);
        if (imageId == null) {
            return;
        }
        PaintPaperStore.ImportedImage image = PaintPaperStore.get(world).getImportedImage(imageId);
        if (image == null || !image.isUsable()) {
            return;
        }
        PaintPaperItem.placeImportedImage(world, player, image, packet.pos(), packet.face(), packet.microX(), packet.microY(),
                image.width(), image.height(), 0);
    }

    private static void beginImportedPaperUpload(ServerPlayerEntity player, PaintOverlayPackets.PlaceImportedPaperBeginC2S packet) {
        if (!(player.getWorld() instanceof ServerWorld world) || !hasHeldPaintPaper(player)
                || Vec3d.ofCenter(packet.pos()).squaredDistanceTo(player.getEyePos()) > INTERACTION_DISTANCE_SQUARED
                || !PaintOverlayFeature.canPlacePaint(world, packet.pos())) {
            sendImportedPaperResult(player, packet.imageId(), false, "画纸放置目标无效");
            return;
        }
        if (packet.imageId() == null || packet.width() <= 0 || packet.height() <= 0
                || (long) packet.width() * packet.height() > PaintPaperStore.MAX_IMPORTED_IMAGE_PIXELS
                || packet.totalBytes() <= 0 || packet.sha256().length != 32) {
            sendImportedPaperResult(player, packet.imageId(), false, "图片尺寸或校验信息无效");
            return;
        }
        IMPORTED_PAPER_UPLOADS.put(player.getUuid(), new ImportedPaperUpload(
                packet.imageId(), packet.name(), packet.width(), packet.height(), packet.totalBytes(),
                packet.sha256(), packet.pos(), packet.face(), packet.microX(), packet.microY(), packet.targetWidth(), packet.targetHeight(),
                packet.rotation(), new ByteArrayOutputStream(packet.totalBytes()), 0, System.currentTimeMillis()));
    }

    private static void receiveImportedPaperChunk(ServerPlayerEntity player, PaintOverlayPackets.PlaceImportedPaperChunkC2S packet) {
        ImportedPaperUpload upload = IMPORTED_PAPER_UPLOADS.get(player.getUuid());
        if (upload == null || !upload.imageId().equals(packet.imageId())
                || packet.sequence() != upload.nextSequence()
                || packet.bytes().length == 0
                || upload.bytes().size() + packet.bytes().length > upload.totalBytes()) {
            return;
        }
        upload.bytes().writeBytes(packet.bytes());
        upload.nextSequence(upload.nextSequence() + 1);
    }

    private static void commitImportedPaperUpload(ServerPlayerEntity player, PaintOverlayPackets.PlaceImportedPaperCommitC2S packet) {
        ImportedPaperUpload upload = IMPORTED_PAPER_UPLOADS.remove(player.getUuid());
        if (upload == null || !upload.imageId().equals(packet.imageId()) || upload.bytes().size() != upload.totalBytes()) {
            sendImportedPaperResult(player, packet.imageId(), false, "图片分片未完整接收");
            return;
        }
        byte[] bytes = upload.bytes().toByteArray();
        CompletableFuture.supplyAsync(() -> decodeUploadedPaper(upload, bytes))
                .handle((result, error) -> error == null ? result : ImportedPaperDecodeResult.error("图片处理失败"))
                .thenAccept(result -> {
                    MinecraftServer server = player.getServer();
                    if (server != null) {
                        server.execute(() -> finishImportedPaperUpload(player, upload, result));
                    }
                });
    }

    private static ImportedPaperDecodeResult decodeUploadedPaper(ImportedPaperUpload upload, byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            if (!Arrays.equals(upload.sha256(), digest.digest(bytes))) {
                return ImportedPaperDecodeResult.error("图片校验失败");
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() != upload.width() || image.getHeight() != upload.height()) {
                return ImportedPaperDecodeResult.error("图片尺寸校验失败");
            }
            int[] pixels = new int[upload.width() * upload.height()];
            image.getRGB(0, 0, upload.width(), upload.height(), pixels, 0, upload.width());
            return new ImportedPaperDecodeResult(null, pixels);
        } catch (IOException | NoSuchAlgorithmException e) {
            return ImportedPaperDecodeResult.error("图片解码失败");
        }
    }

    private static void finishImportedPaperUpload(ServerPlayerEntity player, ImportedPaperUpload upload, ImportedPaperDecodeResult result) {
        if (result.error() != null) {
            sendImportedPaperResult(player, upload.imageId(), false, result.error());
            return;
        }
        if (!(player.getWorld() instanceof ServerWorld world) || !hasHeldPaintPaper(player)
                || Vec3d.ofCenter(upload.pos()).squaredDistanceTo(player.getEyePos()) > INTERACTION_DISTANCE_SQUARED
                || !canPlaceImportedImage(world, upload)) {
            sendImportedPaperResult(player, upload.imageId(), false, "放置目标已失效");
            return;
        }
        PaintPaperStore.ImportedImage image = new PaintPaperStore.ImportedImage(
                upload.imageId(), upload.name(), upload.width(), upload.height(), result.pixels());
        if (!PaintPaperItem.placeImportedImage(world, player, image, upload.pos(), upload.face(), upload.microX(), upload.microY(),
                upload.targetWidth(), upload.targetHeight(), upload.rotation())) {
            sendImportedPaperResult(player, upload.imageId(), false, "图片没有可放置的像素或目标无效");
            return;
        }
        consumeHeldPaintPaper(player);
        sendImportedPaperResult(player, upload.imageId(), true, "图片已放置并消耗一次性画纸");
    }

    private static boolean hasHeldPaintPaper(ServerPlayerEntity player) {
        return player.getMainHandStack().isOf(PaintItems.PAINT_PAPER) || player.getOffHandStack().isOf(PaintItems.PAINT_PAPER);
    }

    private static void consumeHeldPaintPaper(ServerPlayerEntity player) {
        if (player.getMainHandStack().isOf(PaintItems.PAINT_PAPER)) {
            player.getMainHandStack().decrement(1);
        } else if (player.getOffHandStack().isOf(PaintItems.PAINT_PAPER)) {
            player.getOffHandStack().decrement(1);
        }
    }

    private static boolean canPlaceImportedImage(ServerWorld world, ImportedPaperUpload upload) {
        int minX = Math.floorDiv(upload.microX(), PaintOverlayStore.SIZE);
        int minY = Math.floorDiv(upload.microY(), PaintOverlayStore.SIZE);
        int maxX = Math.floorDiv(upload.microX() + upload.targetWidth() - 1, PaintOverlayStore.SIZE);
        int maxY = Math.floorDiv(upload.microY() + upload.targetHeight() - 1, PaintOverlayStore.SIZE);
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                BlockPos target = PaintPaperItem.areaPositionForImport(upload.pos(), upload.face(), x, y);
                if (world.isAir(target) || !canPlacePaint(world, target)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void sendImportedPaperResult(ServerPlayerEntity player, UUID imageId, boolean success, String message) {
        if (imageId != null) {
            ServerPlayNetworking.send(player, new PaintOverlayPackets.PlaceImportedPaperResultS2C(imageId, success, message));
        }
        player.sendMessage(net.minecraft.text.Text.literal(message), true);
    }

    private static final class ImportedPaperUpload {
        private final UUID imageId;
        private final String name;
        private final int width;
        private final int height;
        private final int totalBytes;
        private final byte[] sha256;
        private final BlockPos pos;
        private final Direction face;
        private final int microX;
        private final int microY;
        private final int targetWidth;
        private final int targetHeight;
        private final int rotation;
        private final ByteArrayOutputStream bytes;
        private int nextSequence;
        private final long createdAt;

        private ImportedPaperUpload(UUID imageId, String name, int width, int height, int totalBytes, byte[] sha256,
                                    BlockPos pos, Direction face, int microX, int microY, int targetWidth, int targetHeight,
                                    int rotation, ByteArrayOutputStream bytes,
                                    int nextSequence, long createdAt) {
            this.imageId = imageId; this.name = name; this.width = width; this.height = height;
            this.totalBytes = totalBytes; this.sha256 = sha256.clone(); this.pos = pos.toImmutable(); this.face = face;
            this.microX = microX; this.microY = microY; this.targetWidth = targetWidth; this.targetHeight = targetHeight;
            this.rotation = Math.floorMod(rotation, 4);
            this.bytes = bytes; this.nextSequence = nextSequence; this.createdAt = createdAt;
        }
        private UUID imageId() { return imageId; }
        private String name() { return name; }
        private int width() { return width; }
        private int height() { return height; }
        private int totalBytes() { return totalBytes; }
        private byte[] sha256() { return sha256; }
        private BlockPos pos() { return pos; }
        private Direction face() { return face; }
        private int microX() { return microX; }
        private int microY() { return microY; }
        private int targetWidth() { return targetWidth; }
        private int targetHeight() { return targetHeight; }
        private int rotation() { return rotation; }
        private ByteArrayOutputStream bytes() { return bytes; }
        private int nextSequence() { return nextSequence; }
        private void nextSequence(int value) { nextSequence = value; }
        private long createdAt() { return createdAt; }
    }

    private record ImportedPaperDecodeResult(String error, int[] pixels) {
        private static ImportedPaperDecodeResult error(String message) { return new ImportedPaperDecodeResult(message, new int[0]); }
    }

    private static ItemStack findImportedImagePaper(ServerPlayerEntity player) {
        if (PaintPaperItem.isImportedImage(player.getMainHandStack())) {
            return player.getMainHandStack();
        }
        if (PaintPaperItem.isImportedImage(player.getOffHandStack())) {
            return player.getOffHandStack();
        }
        return ItemStack.EMPTY;
    }

    private static void sendImportedPaperPreview(ServerPlayerEntity player, UUID imageId) {
        ItemStack paper = findImportedImagePaper(player);
        if (paper == ItemStack.EMPTY || !imageId.equals(PaintPaperItem.getImportedImageId(paper))) {
            return;
        }
        PaintPaperStore.ImportedImage image = PaintPaperStore.get(player.getWorld()).getImportedImage(imageId);
        if (image == null || !image.isUsable()) {
            return;
        }
        byte[] pngBytes = encodePreviewPng(image);
        if (pngBytes.length == 0) {
            return;
        }
        ServerPlayNetworking.send(player, new PaintOverlayPackets.ImportedPaperImageS2C(
                image.id(), image.width(), image.height(), pngBytes));
    }

    private static byte[] encodePreviewPng(PaintPaperStore.ImportedImage image) {
        BufferedImage buffered = new BufferedImage(image.width(), image.height(), BufferedImage.TYPE_INT_ARGB);
        buffered.setRGB(0, 0, image.width(), image.height(), image.pixels(), 0, image.width());
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(buffered, "png", output) || output.size() > 8 * 1024 * 1024) {
                return new byte[0];
            }
            return output.toByteArray();
        } catch (IOException ignored) {
            return new byte[0];
        }
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
        if (!DissolveFeature.canPaintPlayer(target.getUuid())) {
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
        if (!DissolveFeature.canPaintPlayer(target.getUuid())) {
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
        int budget = paintBudget(player, brushUse.stack());
        int radius = MathHelper.clamp(brushUse.settings().radius(), MIN_RADIUS, MAX_MANUAL_RADIUS);
        int changedPixels = radius <= MIN_RADIUS
                ? paintAtLimited(world, pos, face, x, y, brushUse.settings(), budget)
                : paintOnSurfacesLimited(world, hitPos, brushUse.settings(), budget);
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
        int budget = paintBudget(player, brushUse.stack());
        int radius = MathHelper.clamp(brushUse.settings().radius(), MIN_RADIUS, MAX_MANUAL_RADIUS);
        int changedPixels = radius <= MIN_RADIUS
                ? sprayAtLimited(world, pos, face, x, y, brushUse.settings(), budget)
                : sprayOnSurfacesLimited(world, hitPos, brushUse.settings(), budget);
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
        int[] before = store.getPixels(pos, face);
        int radius = MathHelper.clamp(settings.radius(), MIN_RADIUS, MAX_MANUAL_RADIUS);
        if (radius == MIN_RADIUS) {
            if (store.setPixel(pos, face, x, y, settings.color())) {
                broadcastPixelChanges(world, pos, face, before, store.getPixels(pos, face));
                return 1;
            }
            return 0;
        }
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
        broadcastPixelChanges(world, pos, face, before, store.getPixels(pos, face));
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
        int[] before = pixels.clone();
        if (radius <= MIN_RADIUS) {
            if (x < 0 || x >= PaintOverlayStore.SIZE || y < 0 || y >= PaintOverlayStore.SIZE) {
                return 0;
            }
            int index = y * PaintOverlayStore.SIZE + x;
            int color = sprayColor(pixels[index], settings.color(), 1.0F);
            if (!store.setPixel(pos, face, x, y, color)) {
                return 0;
            }
            broadcastPixelChanges(world, pos, face, before, store.getPixels(pos, face));
            return 1;
        }
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
        broadcastPixelChanges(world, pos, face, before, pixels);
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
        Map<PaintOverlayStore.FaceKey, int[]> previousPixels = new LinkedHashMap<>();
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
            int[] pixels = facePixels.computeIfAbsent(key, ignored -> {
                int[] existing = store.getPixels(key.pos(), key.face());
                previousPixels.put(key, existing.clone());
                return existing;
            });
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
                broadcastPixelChanges(world, key.pos(), key.face(), previousPixels.get(key), pixels);
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
        int[] before = store.getPixels(pos, face);
        if (radius == MIN_RADIUS) {
            if (store.setPixel(pos, face, x, y, 0)) {
                broadcastPixelChanges(world, pos, face, before, store.getPixels(pos, face));
            }
            return;
        }
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
        broadcastPixelChanges(world, pos, face, before, store.getPixels(pos, face));
    }

    private static void eraseAt(ServerWorld world, BlockPos pos, Direction face, int x, int y, int radius, Vec3d hitPos) {
        radius = MathHelper.clamp(radius, MIN_RADIUS, MAX_MANUAL_RADIUS);
        if (radius <= MIN_RADIUS) {
            eraseAt(world, pos, face, x, y, radius);
            return;
        }
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

    private static void queueNearbySnapshot(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        int generation = PAINT_SYNC_GENERATIONS.merge(player.getUuid(), 1, Integer::sum);
        PlayerPaintWindow window = new PlayerPaintWindow(generation, true);
        window.refresh(collectFacesNear(world, player.getEyePos()), player.getEyePos(), true);
        PLAYER_PAINT_WINDOWS.put(player.getUuid(), window);
        sendNearbyPlayerPaintSync(player, world);
    }

    private static void refreshPaintWindow(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            queueNearbySnapshot(player);
            return;
        }
        int generation = PAINT_SYNC_GENERATIONS.getOrDefault(player.getUuid(), 0);
        List<PaintOverlayPackets.FaceData> visibleFaces = collectFacesNear(world, player.getEyePos());
        PLAYER_PAINT_WINDOWS.compute(player.getUuid(), (id, window) -> {
            if (window == null || window.generation() != generation) {
                PlayerPaintWindow replacement = new PlayerPaintWindow(generation, false);
                replacement.refresh(visibleFaces, player.getEyePos(), false);
                return replacement;
            }
            window.refresh(visibleFaces, player.getEyePos(), false);
            return window;
        });
    }

    private static List<PaintOverlayPackets.FaceData> collectFacesNear(ServerWorld world, Vec3d center) {
        int chunkRadius = (int) Math.ceil(PAINT_SYNC_RADIUS / 16.0D);
        return PaintOverlayStore.get(world)
                .getStoredFacesNear(new ChunkPos(BlockPos.ofFloored(center)), chunkRadius).stream()
                .filter(face -> isWithinPaintSync(center, face.pos()))
                .map(face -> new PaintOverlayPackets.FaceData(face.pos(), face.face(), face.pixels()))
                .toList();
    }

    private static void sendPaintWindowDelta(ServerPlayerEntity player) {
        PlayerPaintWindow window = PLAYER_PAINT_WINDOWS.get(player.getUuid());
        if (window == null) {
            return;
        }
        PaintOverlayPackets.FullSyncS2C packet = window.nextPacket(player.getEyePos());
        if (packet != null) {
            ServerPlayNetworking.send(player, packet);
        }
    }

    private static void broadcastFace(ServerWorld world, BlockPos pos, Direction face, int[] pixels) {
        updateQueuedFace(pos, face, pixels);
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
        updateQueuedFace(patch.pos(), patch.face(), PaintOverlayStore.get(world).getPixels(patch.pos(), patch.face()));
        PaintOverlayPackets.FaceRegionPatchS2C packet = new PaintOverlayPackets.FaceRegionPatchS2C(patch);
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (isNear(player, patch.pos())) {
                ServerPlayNetworking.send(player, packet);
            }
        }
    }

    private static void broadcastPixelChanges(ServerWorld world, BlockPos pos, Direction face,
                                              int[] before, int[] after) {
        int minX = PaintOverlayStore.SIZE;
        int minY = PaintOverlayStore.SIZE;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < PaintOverlayStore.SIZE; y++) {
            for (int x = 0; x < PaintOverlayStore.SIZE; x++) {
                int index = y * PaintOverlayStore.SIZE + x;
                if (before[index] == after[index]) {
                    continue;
                }
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        if (maxX < minX || maxY < minY) {
            return;
        }
        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        if (width * height * 4 >= PaintOverlayStore.FACE_PIXELS * 3) {
            broadcastFace(world, pos, face, after);
            return;
        }
        int[] patchPixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            System.arraycopy(after, (minY + y) * PaintOverlayStore.SIZE + minX,
                    patchPixels, y * width, width);
        }
        broadcastRegionPatch(world, new PaintOverlayPackets.FaceRegionPatch(
                pos, face, minX, minY, width, height, patchPixels));
    }

    private static void broadcastPlayerPaint(ServerWorld world, ServerPlayerEntity target, PaintOverlayPackets.PlayerPaintStrokeS2C packet) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.getEyePos().squaredDistanceTo(target.getPos()) <= PLAYER_PAINT_SYNC_DISTANCE_SQUARED) {
                ServerPlayNetworking.send(player, packet);
            }
        }
    }

    private static void broadcastPlayerPaintClear(ServerWorld world, ServerPlayerEntity target) {
        PaintOverlayPackets.ClearPlayerPaintS2C packet = new PaintOverlayPackets.ClearPlayerPaintS2C(target.getId());
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.getEyePos().squaredDistanceTo(target.getPos()) <= PLAYER_PAINT_SYNC_DISTANCE_SQUARED) {
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
            if (receiver.getEyePos().squaredDistanceTo(target.getPos()) <= PLAYER_PAINT_SYNC_DISTANCE_SQUARED) {
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
            if (receiver.getEyePos().squaredDistanceTo(target.getPos()) <= PLAYER_PAINT_SYNC_DISTANCE_SQUARED) {
                ServerPlayNetworking.send(receiver, packet);
            }
        }
    }

    private static boolean isNear(ServerPlayerEntity player, BlockPos pos) {
        return isWithinPaintSync(player.getEyePos(), pos);
    }

    private static boolean isWithinPaintSync(Vec3d center, BlockPos pos) {
        return Vec3d.ofCenter(pos).squaredDistanceTo(center) <= PAINT_SYNC_DISTANCE_SQUARED;
    }

    private static boolean isWithinPaintRetain(Vec3d center, BlockPos pos) {
        return Vec3d.ofCenter(pos).squaredDistanceTo(center) <= PAINT_RETAIN_DISTANCE_SQUARED;
    }

    private static void updateQueuedFace(BlockPos pos, Direction face, int[] pixels) {
        PaintOverlayStore.FaceKey key = new PaintOverlayStore.FaceKey(pos, face);
        PaintOverlayPackets.FaceData data = hasPaintPixels(pixels)
                ? new PaintOverlayPackets.FaceData(pos, face, pixels)
                : null;
        for (PlayerPaintWindow window : PLAYER_PAINT_WINDOWS.values()) {
            window.applyLiveFace(key, data);
        }
    }

    private static void recordSyncState(ServerPlayerEntity player) {
        PLAYER_SYNC_STATES.put(player.getUuid(), syncState(player));
    }

    private static PlayerSyncState syncState(ServerPlayerEntity player) {
        return new PlayerSyncState(player.getWorld().getRegistryKey().getValue().toString(), player.getBlockPos(), player.getEyePos());
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

    private record PlayerSyncState(String world, BlockPos blockPos, Vec3d eyePos) {
        private PlayerSyncState {
            blockPos = blockPos.toImmutable();
        }

        private boolean hasMovedFrom(PlayerSyncState previous) {
            return !world.equals(previous.world) || !blockPos.equals(previous.blockPos);
        }
    }

    private static final class PlayerPaintWindow {
        private final int generation;
        private final Map<PaintOverlayStore.FaceKey, PaintOverlayPackets.FaceData> pendingUpserts = new LinkedHashMap<>();
        private final Set<PaintOverlayStore.FaceKey> pendingRemovals = new LinkedHashSet<>();
        private final Set<PaintOverlayStore.FaceKey> knownFaces = new java.util.HashSet<>();
        private boolean clearExistingPending;

        private PlayerPaintWindow(int generation, boolean clearExisting) {
            this.generation = generation;
            this.clearExistingPending = clearExisting;
        }

        private int generation() {
            return generation;
        }

        private void refresh(List<PaintOverlayPackets.FaceData> faces, Vec3d center, boolean reset) {
            if (reset) {
                pendingUpserts.clear();
                pendingRemovals.clear();
                knownFaces.clear();
            }
            pendingUpserts.entrySet().removeIf(entry -> !isWithinPaintRetain(center, entry.getKey().pos()));
            pendingRemovals.removeIf(key -> !isWithinPaintRetain(center, key.pos()));
            knownFaces.removeIf(key -> !isWithinPaintRetain(center, key.pos()));

            Set<PaintOverlayStore.FaceKey> visible = new java.util.HashSet<>();
            for (PaintOverlayPackets.FaceData face : faces) {
                PaintOverlayStore.FaceKey key = new PaintOverlayStore.FaceKey(face.pos(), face.face());
                visible.add(key);
                pendingRemovals.remove(key);
                if (!knownFaces.contains(key)) {
                    pendingUpserts.put(key, face);
                }
            }

            for (PaintOverlayStore.FaceKey key : new java.util.ArrayList<>(knownFaces)) {
                if (isWithinPaintSync(center, key.pos()) && !visible.contains(key)) {
                    knownFaces.remove(key);
                    pendingUpserts.remove(key);
                    pendingRemovals.add(key);
                }
            }
        }

        private void applyLiveFace(PaintOverlayStore.FaceKey key, PaintOverlayPackets.FaceData face) {
            if (face == null) {
                pendingUpserts.remove(key);
                pendingRemovals.remove(key);
                knownFaces.remove(key);
            } else {
                pendingRemovals.remove(key);
                if (pendingUpserts.containsKey(key)) {
                    pendingUpserts.put(key, face);
                }
            }
        }

        private PaintOverlayPackets.FullSyncS2C nextPacket(Vec3d center) {
            if (pendingUpserts.isEmpty() && pendingRemovals.isEmpty()) {
                if (!clearExistingPending) {
                    return null;
                }
                clearExistingPending = false;
                return new PaintOverlayPackets.FullSyncS2C(generation, true, List.of(), List.of());
            }
            List<Map.Entry<PaintOverlayStore.FaceKey, PaintOverlayPackets.FaceData>> candidates = new java.util.ArrayList<>(pendingUpserts.entrySet());
            candidates.sort(java.util.Comparator.comparingDouble(entry -> Vec3d.ofCenter(entry.getKey().pos()).squaredDistanceTo(center)));
            int count = Math.min(MAX_SYNC_FACES_PER_PACKET, candidates.size());
            List<PaintOverlayPackets.FaceData> batch = new java.util.ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                Map.Entry<PaintOverlayStore.FaceKey, PaintOverlayPackets.FaceData> entry = candidates.get(index);
                pendingUpserts.remove(entry.getKey());
                knownFaces.add(entry.getKey());
                batch.add(entry.getValue());
            }
            List<PaintOverlayPackets.FaceKeyData> removals = new java.util.ArrayList<>(Math.min(MAX_SYNC_FACES_PER_PACKET, pendingRemovals.size()));
            var removalIterator = pendingRemovals.iterator();
            while (removalIterator.hasNext() && removals.size() < MAX_SYNC_FACES_PER_PACKET) {
                PaintOverlayStore.FaceKey key = removalIterator.next();
                removals.add(new PaintOverlayPackets.FaceKeyData(key.pos(), key.face()));
                removalIterator.remove();
            }
            boolean clear = clearExistingPending;
            clearExistingPending = false;
            return new PaintOverlayPackets.FullSyncS2C(generation, clear, batch, removals);
        }
    }

    private record BrushUse(ItemStack stack, BrushSettings settings) {
    }
}
