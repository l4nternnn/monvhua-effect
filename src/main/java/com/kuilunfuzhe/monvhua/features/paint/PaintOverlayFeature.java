package com.kuilunfuzhe.monvhua.features.paint;

import com.kuilunfuzhe.monvhua.network.paint.PaintOverlayPackets;
import com.kuilunfuzhe.monvhua.item.paint.PaintItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

public final class PaintOverlayFeature {
    public static final int DEFAULT_COLOR = 0xFFFF2A4F;
    public static final int DEFAULT_RADIUS = 1;
    public static final int MIN_RADIUS = 1;
    public static final int MAX_RADIUS = 8;
    public static final int DEFAULT_PAPER_SIZE = 3;
    public static final int MIN_PAPER_SIZE = 1;
    public static final int MAX_PAPER_SIZE = 25;
    private static final double SYNC_DISTANCE_SQUARED = 128.0D * 128.0D;
    private static final Map<UUID, BrushSettings> BRUSH_SETTINGS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> PAPER_SIZES = new ConcurrentHashMap<>();
    private static final Map<UUID, PlayerSyncKey> PLAYER_SYNC_KEYS = new ConcurrentHashMap<>();

    private PaintOverlayFeature() {
    }

    public static void initialize() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> {
                    recordSyncKey(handler.getPlayer());
                    sendNearbyFullSync(handler.getPlayer());
                }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                PLAYER_SYNC_KEYS.remove(handler.getPlayer().getUuid()));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            recordSyncKey(player);
            sendNearbyFullSync(player);
        });
        ServerPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.BrushSettingsC2S.ID, (packet, context) ->
                context.server().execute(() -> setBrushSettings(context.player(), packet.color(), packet.radius())));
        ServerPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.PaperSizeC2S.ID, (packet, context) ->
                context.server().execute(() -> setPaperSize(context.player(), packet.size())));
        ServerPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.PaintStrokeC2S.ID, (packet, context) ->
                context.server().execute(() -> handlePaintStroke(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.ModelPaintStrokeC2S.ID, (packet, context) ->
                context.server().execute(() -> handleModelPaintStroke(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.FillPaintBucketC2S.ID, (packet, context) ->
                context.server().execute(() -> handleFillPaintBucket(context.player(), packet)));
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
        if (Vec3d.ofCenter(packet.pos()).squaredDistanceTo(player.getEyePos()) > 64.0D) {
            return;
        }
        if (isHoldingPaintBrush(player)) {
            paintAt(world, packet.pos(), packet.face(), packet.x(), packet.y(), getBrushSettings(player));
            return;
        }
        if (!isHoldingEraser(player)) {
            return;
        }
        if (packet.clearFace()) {
            clearFacesAround(world, packet.pos(), packet.face(), getBrushSettings(player).radius());
            return;
        }
        eraseAt(world, packet.pos(), packet.face(), packet.x(), packet.y(), getBrushSettings(player).radius());
    }

    private static boolean isHoldingPaintBrush(ServerPlayerEntity player) {
        return isPaintBrush(player.getMainHandStack()) || isPaintBrush(player.getOffHandStack());
    }

    private static boolean isPaintBrush(ItemStack stack) {
        return stack.getItem() == PaintItems.PAINT_BRUSH;
    }

    private static boolean isHoldingEraser(ServerPlayerEntity player) {
        return isEraser(player.getMainHandStack()) || isEraser(player.getOffHandStack());
    }

    private static boolean isEraser(ItemStack stack) {
        return stack.getItem() == PaintItems.ERASER;
    }

    private static void handleFillPaintBucket(ServerPlayerEntity player, PaintOverlayPackets.FillPaintBucketC2S packet) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        if (Vec3d.ofCenter(packet.pos()).squaredDistanceTo(player.getEyePos()) > 64.0D) {
            return;
        }
        if (world.getBlockEntity(packet.pos()) instanceof PaintBucketBlockEntity bucket) {
            bucket.fill(packet.color());
        }
    }

    private static void handleModelPaintStroke(ServerPlayerEntity player, PaintOverlayPackets.ModelPaintStrokeC2S packet) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        Entity entity = world.getEntityById(packet.entityId());
        if (!(entity instanceof ItemDisplayEntity display)) {
            return;
        }
        if (display.getPos().squaredDistanceTo(player.getEyePos()) > 64.0D) {
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
            BrushSettings settings = getBrushSettings(player);
            clear = false;
            color = settings.color();
            radius = settings.radius();
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
        boolean changed = ModelPaintData.paint(root, packet.surface(), packet.face(), packet.x(), packet.y(), radius, color, clear && packet.clearFace(), slim);
        if (!changed) {
            return;
        }
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));
        display.setItemStack(stack);
    }

    private static boolean isCombinedBodyStack(ItemStack stack) {
        Identifier model = stack.get(DataComponentTypes.ITEM_MODEL);
        return model != null && model.equals(Identifier.of("monvhua", "combined_body"));
    }

    public static void paintAt(ServerWorld world, BlockPos pos, Direction face, int x, int y, BrushSettings settings) {
        PaintOverlayStore store = PaintOverlayStore.get(world);
        int radius = MathHelper.clamp(settings.radius(), MIN_RADIUS, MAX_RADIUS);
        int radiusSquared = radius * radius;
        boolean changed = false;
        for (int dy = -radius + 1; dy <= radius - 1; dy++) {
            for (int dx = -radius + 1; dx <= radius - 1; dx++) {
                if (dx * dx + dy * dy > radiusSquared) {
                    continue;
                }
                changed |= store.setPixel(pos, face, x + dx, y + dy, settings.color());
            }
        }
        if (!changed) {
            return;
        }
        broadcastFace(world, pos, face, store.getPixels(pos, face));
    }

    public static void eraseAt(ServerWorld world, BlockPos pos, Direction face, int x, int y, int radius) {
        PaintOverlayStore store = PaintOverlayStore.get(world);
        radius = MathHelper.clamp(radius, MIN_RADIUS, MAX_RADIUS);
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

    public static void clearFacesAround(ServerWorld world, BlockPos pos, Direction face, int radius) {
        int blockRadius = Math.max(0, MathHelper.clamp(radius, MIN_RADIUS, MAX_RADIUS) - 1);
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

    public static boolean setFacePixels(ServerWorld world, BlockPos pos, Direction face, int[] pixels) {
        PaintOverlayStore store = PaintOverlayStore.get(world);
        if (!store.setPixels(pos, face, pixels)) {
            return false;
        }
        broadcastFace(world, pos, face, store.getPixels(pos, face));
        return true;
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
            radius = MathHelper.clamp(radius, MIN_RADIUS, MAX_RADIUS);
        }
    }

    private record PlayerSyncKey(String world, int chunkX, int chunkZ) {
    }
}
