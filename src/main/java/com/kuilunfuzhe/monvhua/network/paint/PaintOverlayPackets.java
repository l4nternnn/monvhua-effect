package com.kuilunfuzhe.monvhua.network.paint;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.features.paint.PaintOverlayStore;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PaintOverlayPackets {
    private static final int MAX_FACE_UPDATES = 4096;

    private PaintOverlayPackets() {
    }

    public static void registerS2C() {
        FullSyncS2C.register();
        FaceUpdateS2C.register();
        FaceRegionPatchS2C.register();
        PlayerPaintStrokeS2C.register();
        PlayerPaintDataS2C.register();
        ClearPlayerPaintS2C.register();
        PaintBucketCarryS2C.register();
        PaintConfigS2C.register();
    }

    public static void registerC2S() {
        BrushSettingsC2S.register();
        PaperSizeC2S.register();
        ImportPaintPaperC2S.register();
        PaintStrokeC2S.register();
        EditorPaintStrokeC2S.register();
        EditorModelPaintStrokeC2S.register();
        EditorPaperUseC2S.register();
        RestoreFaceC2S.register();
        PaintRegionPatchC2S.register();
        RestoreModelFaceC2S.register();
        ModelPaintStrokeC2S.register();
        PlayerPaintStrokeC2S.register();
        ClearPlayerPaintC2S.register();
        FillPaintBucketC2S.register();
        RefillPaintBucketC2S.register();
        LoadBrushFromBucketC2S.register();
        SelectBrushSlotC2S.register();
        StoreBrushPresetC2S.register();
        RequestPaintConfigC2S.register();
        UpdatePaintConfigC2S.register();
    }

    public record FaceData(BlockPos pos, Direction face, int[] pixels) {
        public FaceData {
            pos = pos.toImmutable();
            pixels = sanitizePixels(pixels);
        }

        private static FaceData read(RegistryByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            Direction face = Direction.byIndex(buf.readVarInt());
            int length = Math.max(0, buf.readVarInt());
            int[] pixels = new int[PaintOverlayStore.FACE_PIXELS];
            for (int i = 0; i < length; i++) {
                int pixel = buf.readInt();
                if (i < PaintOverlayStore.FACE_PIXELS) {
                    pixels[i] = pixel;
                }
            }
            return new FaceData(pos, face, pixels);
        }

        private void write(RegistryByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeVarInt(face.getIndex());
            buf.writeVarInt(PaintOverlayStore.FACE_PIXELS);
            for (int pixel : pixels) {
                buf.writeInt(pixel);
            }
        }
    }

    public record FaceRegionPatch(BlockPos pos, Direction face, int startX, int startY, int width, int height, int[] pixels) {
        private static final int MAX_PATCH_PIXELS = PaintOverlayStore.FACE_PIXELS;

        public FaceRegionPatch {
            pos = pos.toImmutable();
            face = face == null ? Direction.UP : face;
            startX = MathHelper.clamp(startX, 0, PaintOverlayStore.SIZE - 1);
            startY = MathHelper.clamp(startY, 0, PaintOverlayStore.SIZE - 1);
            width = MathHelper.clamp(width, 1, PaintOverlayStore.SIZE - startX);
            height = MathHelper.clamp(height, 1, PaintOverlayStore.SIZE - startY);
            int expected = Math.min(MAX_PATCH_PIXELS, width * height);
            int[] clean = new int[expected];
            if (pixels != null) {
                System.arraycopy(pixels, 0, clean, 0, Math.min(pixels.length, clean.length));
            }
            pixels = clean;
        }

        private static FaceRegionPatch read(RegistryByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            Direction face = Direction.byIndex(buf.readVarInt());
            int startX = buf.readVarInt();
            int startY = buf.readVarInt();
            int width = buf.readVarInt();
            int height = buf.readVarInt();
            int length = Math.max(0, buf.readVarInt());
            int[] pixels = new int[Math.min(length, MAX_PATCH_PIXELS)];
            for (int i = 0; i < length; i++) {
                int pixel = buf.readInt();
                if (i < pixels.length) {
                    pixels[i] = pixel;
                }
            }
            return new FaceRegionPatch(pos, face, startX, startY, width, height, pixels);
        }

        private void write(RegistryByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeVarInt(face.getIndex());
            buf.writeVarInt(startX);
            buf.writeVarInt(startY);
            buf.writeVarInt(width);
            buf.writeVarInt(height);
            buf.writeVarInt(pixels.length);
            for (int pixel : pixels) {
                buf.writeInt(pixel);
            }
        }
    }

    public record FullSyncS2C(List<FaceData> faces) implements CustomPayload {
        public static final Id<FullSyncS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "paint_overlay_full_sync"));
        public static final PacketCodec<RegistryByteBuf, FullSyncS2C> CODEC = PacketCodec.of(FullSyncS2C::write, FullSyncS2C::new);
        private static boolean registered = false;

        public FullSyncS2C {
            faces = List.copyOf(faces.size() > MAX_FACE_UPDATES ? faces.subList(0, MAX_FACE_UPDATES) : faces);
        }

        private FullSyncS2C(RegistryByteBuf buf) {
            this(readFaces(buf));
        }

        private void write(RegistryByteBuf buf) {
            buf.writeVarInt(Math.min(faces.size(), MAX_FACE_UPDATES));
            for (int i = 0; i < Math.min(faces.size(), MAX_FACE_UPDATES); i++) {
                faces.get(i).write(buf);
            }
        }

        private static List<FaceData> readFaces(RegistryByteBuf buf) {
            int count = Math.min(buf.readVarInt(), MAX_FACE_UPDATES);
            List<FaceData> faces = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                faces.add(FaceData.read(buf));
            }
            return faces;
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playS2C().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record FaceUpdateS2C(FaceData faceData) implements CustomPayload {
        public static final Id<FaceUpdateS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "paint_overlay_face_update"));
        public static final PacketCodec<RegistryByteBuf, FaceUpdateS2C> CODEC = PacketCodec.of(FaceUpdateS2C::write, FaceUpdateS2C::new);
        private static boolean registered = false;

        private FaceUpdateS2C(RegistryByteBuf buf) {
            this(FaceData.read(buf));
        }

        private void write(RegistryByteBuf buf) {
            faceData.write(buf);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playS2C().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record PaintBucketCarryS2C(int entityId, boolean active, boolean filled, int color) implements CustomPayload {
        public static final Id<PaintBucketCarryS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "paint_bucket_carry"));
        public static final PacketCodec<RegistryByteBuf, PaintBucketCarryS2C> CODEC = PacketCodec.of(PaintBucketCarryS2C::write, PaintBucketCarryS2C::new);
        private static boolean registered = false;

        private PaintBucketCarryS2C(RegistryByteBuf buf) {
            this(buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readInt());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeBoolean(active);
            buf.writeBoolean(filled);
            buf.writeInt(color);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playS2C().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record PaintConfigS2C(String json) implements CustomPayload {
        public static final Id<PaintConfigS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "paint_config"));
        public static final PacketCodec<RegistryByteBuf, PaintConfigS2C> CODEC = PacketCodec.of(PaintConfigS2C::write, PaintConfigS2C::new);
        private static boolean registered = false;

        private PaintConfigS2C(RegistryByteBuf buf) {
            this(buf.readString());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeString(json);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playS2C().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record RequestPaintConfigC2S() implements CustomPayload {
        public static final Id<RequestPaintConfigC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "request_paint_config"));
        public static final PacketCodec<RegistryByteBuf, RequestPaintConfigC2S> CODEC = PacketCodec.unit(new RequestPaintConfigC2S());
        private static boolean registered = false;

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record UpdatePaintConfigC2S(String json) implements CustomPayload {
        public static final Id<UpdatePaintConfigC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "update_paint_config"));
        public static final PacketCodec<RegistryByteBuf, UpdatePaintConfigC2S> CODEC = PacketCodec.of(UpdatePaintConfigC2S::write, UpdatePaintConfigC2S::new);
        private static boolean registered = false;

        private UpdatePaintConfigC2S(RegistryByteBuf buf) {
            this(buf.readString());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeString(json);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record BrushSettingsC2S(int color, int radius) implements CustomPayload {
        public static final Id<BrushSettingsC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "paint_brush_settings"));
        public static final PacketCodec<RegistryByteBuf, BrushSettingsC2S> CODEC = PacketCodec.of(BrushSettingsC2S::write, BrushSettingsC2S::new);
        private static boolean registered = false;

        private BrushSettingsC2S(RegistryByteBuf buf) {
            this(buf.readInt(), buf.readVarInt());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeInt(color);
            buf.writeVarInt(radius);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record SelectBrushSlotC2S(int slot) implements CustomPayload {
        public static final Id<SelectBrushSlotC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "paint_brush_slot"));
        public static final PacketCodec<RegistryByteBuf, SelectBrushSlotC2S> CODEC = PacketCodec.of(SelectBrushSlotC2S::write, SelectBrushSlotC2S::new);
        private static boolean registered = false;

        public SelectBrushSlotC2S {
            slot = MathHelper.clamp(slot, 0, 8);
        }

        private SelectBrushSlotC2S(RegistryByteBuf buf) {
            this(buf.readVarInt());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeVarInt(slot);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record FaceRegionPatchS2C(FaceRegionPatch patch) implements CustomPayload {
        public static final Id<FaceRegionPatchS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "paint_region_patch_sync"));
        public static final PacketCodec<RegistryByteBuf, FaceRegionPatchS2C> CODEC = PacketCodec.of(FaceRegionPatchS2C::write, FaceRegionPatchS2C::new);
        private static boolean registered = false;

        private FaceRegionPatchS2C(RegistryByteBuf buf) {
            this(FaceRegionPatch.read(buf));
        }

        private void write(RegistryByteBuf buf) {
            patch.write(buf);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playS2C().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record PlayerPaintStrokeS2C(int entityId, String surface, Direction face, int x, int y,
                                       int color, int radius, boolean clearFace) implements CustomPayload {
        public static final Id<PlayerPaintStrokeS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "player_paint_stroke_sync"));
        public static final PacketCodec<RegistryByteBuf, PlayerPaintStrokeS2C> CODEC = PacketCodec.of(PlayerPaintStrokeS2C::write, PlayerPaintStrokeS2C::new);
        private static boolean registered = false;

        public PlayerPaintStrokeS2C {
            surface = surface == null ? "" : surface;
            radius = MathHelper.clamp(radius, 1, 8);
        }

        private PlayerPaintStrokeS2C(RegistryByteBuf buf) {
            this(buf.readVarInt(), buf.readString(64), Direction.byIndex(buf.readVarInt()),
                    buf.readVarInt(), buf.readVarInt(), buf.readInt(), buf.readVarInt(), buf.readBoolean());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeString(surface);
            buf.writeVarInt(face.getIndex());
            buf.writeVarInt(x);
            buf.writeVarInt(y);
            buf.writeInt(color);
            buf.writeVarInt(radius);
            buf.writeBoolean(clearFace);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playS2C().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record PlayerPaintDataS2C(int entityId, List<PlayerPaintStrokeS2C> strokes) implements CustomPayload {
        private static final int MAX_PLAYER_PAINT_STROKES = 2048;
        public static final Id<PlayerPaintDataS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "player_paint_data_sync"));
        public static final PacketCodec<RegistryByteBuf, PlayerPaintDataS2C> CODEC = PacketCodec.of(PlayerPaintDataS2C::write, PlayerPaintDataS2C::new);
        private static boolean registered = false;

        public PlayerPaintDataS2C {
            strokes = List.copyOf(strokes.size() > MAX_PLAYER_PAINT_STROKES
                    ? strokes.subList(strokes.size() - MAX_PLAYER_PAINT_STROKES, strokes.size())
                    : strokes);
        }

        private PlayerPaintDataS2C(RegistryByteBuf buf) {
            this(buf.readVarInt(), readPlayerPaintStrokes(buf));
        }

        private void write(RegistryByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeVarInt(Math.min(strokes.size(), MAX_PLAYER_PAINT_STROKES));
            for (int i = 0; i < Math.min(strokes.size(), MAX_PLAYER_PAINT_STROKES); i++) {
                strokes.get(i).write(buf);
            }
        }

        private static List<PlayerPaintStrokeS2C> readPlayerPaintStrokes(RegistryByteBuf buf) {
            int count = Math.min(buf.readVarInt(), MAX_PLAYER_PAINT_STROKES);
            List<PlayerPaintStrokeS2C> strokes = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                strokes.add(new PlayerPaintStrokeS2C(buf));
            }
            return strokes;
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playS2C().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ClearPlayerPaintS2C(int entityId) implements CustomPayload {
        public static final Id<ClearPlayerPaintS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "clear_player_paint_sync"));
        public static final PacketCodec<RegistryByteBuf, ClearPlayerPaintS2C> CODEC = PacketCodec.of(ClearPlayerPaintS2C::write, ClearPlayerPaintS2C::new);
        private static boolean registered = false;

        private ClearPlayerPaintS2C(RegistryByteBuf buf) {
            this(buf.readVarInt());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeVarInt(entityId);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playS2C().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record StoreBrushPresetC2S(int slot, int color) implements CustomPayload {
        public static final Id<StoreBrushPresetC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "paint_brush_preset_store"));
        public static final PacketCodec<RegistryByteBuf, StoreBrushPresetC2S> CODEC = PacketCodec.of(StoreBrushPresetC2S::write, StoreBrushPresetC2S::new);
        private static boolean registered = false;

        public StoreBrushPresetC2S {
            slot = MathHelper.clamp(slot, 0, 8);
        }

        private StoreBrushPresetC2S(RegistryByteBuf buf) {
            this(buf.readVarInt(), buf.readInt());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeVarInt(slot);
            buf.writeInt(color);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record RefillPaintBucketC2S(BlockPos pos) implements CustomPayload {
        public static final Id<RefillPaintBucketC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "refill_paint_bucket"));
        public static final PacketCodec<RegistryByteBuf, RefillPaintBucketC2S> CODEC = PacketCodec.of(RefillPaintBucketC2S::write, RefillPaintBucketC2S::new);
        private static boolean registered = false;

        public RefillPaintBucketC2S {
            pos = pos.toImmutable();
        }

        private RefillPaintBucketC2S(RegistryByteBuf buf) {
            this(buf.readBlockPos());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeBlockPos(pos);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record PaintStrokeC2S(BlockPos pos, Direction face, int x, int y, boolean clearFace, Vec3d hitPos) implements CustomPayload {
        public static final Id<PaintStrokeC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "paint_stroke"));
        public static final PacketCodec<RegistryByteBuf, PaintStrokeC2S> CODEC = PacketCodec.of(PaintStrokeC2S::write, PaintStrokeC2S::new);
        private static boolean registered = false;

        public PaintStrokeC2S {
            pos = pos.toImmutable();
        }

        private PaintStrokeC2S(RegistryByteBuf buf) {
            this(buf.readBlockPos(), Direction.byIndex(buf.readVarInt()), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                    new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()));
        }

        private void write(RegistryByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeVarInt(face.getIndex());
            buf.writeVarInt(x);
            buf.writeVarInt(y);
            buf.writeBoolean(clearFace);
            buf.writeDouble(hitPos.x);
            buf.writeDouble(hitPos.y);
            buf.writeDouble(hitPos.z);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record PaperSizeC2S(int size) implements CustomPayload {
        public static final Id<PaperSizeC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "paint_paper_size"));
        public static final PacketCodec<RegistryByteBuf, PaperSizeC2S> CODEC = PacketCodec.of(PaperSizeC2S::write, PaperSizeC2S::new);
        private static boolean registered = false;

        private PaperSizeC2S(RegistryByteBuf buf) {
            this(buf.readVarInt());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeVarInt(size);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ImportPaintPaperC2S(String filename, double scale, byte[] imageBytes) implements CustomPayload {
        private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
        public static final Id<ImportPaintPaperC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "import_paint_paper"));
        public static final PacketCodec<RegistryByteBuf, ImportPaintPaperC2S> CODEC = PacketCodec.of(ImportPaintPaperC2S::write, ImportPaintPaperC2S::new);
        private static boolean registered = false;

        public ImportPaintPaperC2S {
            filename = filename == null ? "image.png" : filename;
            if (filename.length() > 128) {
                filename = filename.substring(0, 128);
            }
            scale = Math.max(0.05D, Math.min(8.0D, scale));
            imageBytes = imageBytes == null ? new byte[0] : imageBytes.clone();
            if (imageBytes.length > MAX_IMAGE_BYTES) {
                byte[] limited = new byte[MAX_IMAGE_BYTES];
                System.arraycopy(imageBytes, 0, limited, 0, MAX_IMAGE_BYTES);
                imageBytes = limited;
            }
        }

        private ImportPaintPaperC2S(RegistryByteBuf buf) {
            this(buf.readString(128), buf.readDouble(), buf.readByteArray(MAX_IMAGE_BYTES));
        }

        private void write(RegistryByteBuf buf) {
            buf.writeString(filename);
            buf.writeDouble(scale);
            buf.writeByteArray(imageBytes);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record EditorPaintStrokeC2S(BlockPos pos, Direction face, int x, int y, int tool, boolean clearFace, Vec3d hitPos) implements CustomPayload {
        public static final Id<EditorPaintStrokeC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "editor_paint_stroke"));
        public static final PacketCodec<RegistryByteBuf, EditorPaintStrokeC2S> CODEC = PacketCodec.of(EditorPaintStrokeC2S::write, EditorPaintStrokeC2S::new);
        private static boolean registered = false;

        public EditorPaintStrokeC2S {
            pos = pos.toImmutable();
        }

        private EditorPaintStrokeC2S(RegistryByteBuf buf) {
            this(buf.readBlockPos(), Direction.byIndex(buf.readVarInt()), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                    new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()));
        }

        private void write(RegistryByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeVarInt(face.getIndex());
            buf.writeVarInt(x);
            buf.writeVarInt(y);
            buf.writeVarInt(tool);
            buf.writeBoolean(clearFace);
            buf.writeDouble(hitPos.x);
            buf.writeDouble(hitPos.y);
            buf.writeDouble(hitPos.z);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record EditorModelPaintStrokeC2S(int entityId, String surface, Direction face, int x, int y, int tool, boolean clearFace) implements CustomPayload {
        public static final Id<EditorModelPaintStrokeC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "editor_model_paint_stroke"));
        public static final PacketCodec<RegistryByteBuf, EditorModelPaintStrokeC2S> CODEC = PacketCodec.of(EditorModelPaintStrokeC2S::write, EditorModelPaintStrokeC2S::new);
        private static boolean registered = false;

        public EditorModelPaintStrokeC2S {
            surface = surface == null ? "" : surface;
        }

        private EditorModelPaintStrokeC2S(RegistryByteBuf buf) {
            this(buf.readVarInt(), buf.readString(64), Direction.byIndex(buf.readVarInt()), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeString(surface);
            buf.writeVarInt(face.getIndex());
            buf.writeVarInt(x);
            buf.writeVarInt(y);
            buf.writeVarInt(tool);
            buf.writeBoolean(clearFace);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record EditorPaperUseC2S(BlockPos pos, Direction face, boolean save) implements CustomPayload {
        public static final Id<EditorPaperUseC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "editor_paper_use"));
        public static final PacketCodec<RegistryByteBuf, EditorPaperUseC2S> CODEC = PacketCodec.of(EditorPaperUseC2S::write, EditorPaperUseC2S::new);
        private static boolean registered = false;

        public EditorPaperUseC2S {
            pos = pos.toImmutable();
        }

        private EditorPaperUseC2S(RegistryByteBuf buf) {
            this(buf.readBlockPos(), Direction.byIndex(buf.readVarInt()), buf.readBoolean());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeVarInt(face.getIndex());
            buf.writeBoolean(save);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record RestoreFaceC2S(FaceData faceData) implements CustomPayload {
        public static final Id<RestoreFaceC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "restore_paint_face"));
        public static final PacketCodec<RegistryByteBuf, RestoreFaceC2S> CODEC = PacketCodec.of(RestoreFaceC2S::write, RestoreFaceC2S::new);
        private static boolean registered = false;

        private RestoreFaceC2S(RegistryByteBuf buf) {
            this(FaceData.read(buf));
        }

        private void write(RegistryByteBuf buf) {
            faceData.write(buf);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record PaintRegionPatchC2S(FaceRegionPatch patch) implements CustomPayload {
        public static final Id<PaintRegionPatchC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "paint_region_patch"));
        public static final PacketCodec<RegistryByteBuf, PaintRegionPatchC2S> CODEC = PacketCodec.of(PaintRegionPatchC2S::write, PaintRegionPatchC2S::new);
        private static boolean registered = false;

        private PaintRegionPatchC2S(RegistryByteBuf buf) {
            this(FaceRegionPatch.read(buf));
        }

        private void write(RegistryByteBuf buf) {
            patch.write(buf);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record RestoreModelFaceC2S(int entityId, String surface, Direction face, int[] pixels) implements CustomPayload {
        public static final Id<RestoreModelFaceC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "restore_model_paint_face"));
        public static final PacketCodec<RegistryByteBuf, RestoreModelFaceC2S> CODEC = PacketCodec.of(RestoreModelFaceC2S::write, RestoreModelFaceC2S::new);
        private static final int MAX_MODEL_FACE_PIXELS = 4096;
        private static boolean registered = false;

        public RestoreModelFaceC2S {
            surface = surface == null ? "" : surface;
            pixels = pixels == null ? new int[0] : Arrays.copyOf(pixels, pixels.length);
            if (pixels.length > MAX_MODEL_FACE_PIXELS) {
                int[] clipped = new int[MAX_MODEL_FACE_PIXELS];
                System.arraycopy(pixels, 0, clipped, 0, clipped.length);
                pixels = clipped;
            }
        }

        private RestoreModelFaceC2S(RegistryByteBuf buf) {
            this(buf.readVarInt(), buf.readString(64), Direction.byIndex(buf.readVarInt()), readModelPixels(buf));
        }

        private void write(RegistryByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeString(surface);
            buf.writeVarInt(face.getIndex());
            buf.writeVarInt(Math.min(pixels.length, MAX_MODEL_FACE_PIXELS));
            for (int i = 0; i < Math.min(pixels.length, MAX_MODEL_FACE_PIXELS); i++) {
                buf.writeInt(pixels[i]);
            }
        }

        private static int[] readModelPixels(RegistryByteBuf buf) {
            int length = Math.max(0, buf.readVarInt());
            int[] pixels = new int[Math.min(length, MAX_MODEL_FACE_PIXELS)];
            for (int i = 0; i < length; i++) {
                int pixel = buf.readInt();
                if (i < pixels.length) {
                    pixels[i] = pixel;
                }
            }
            return pixels;
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ModelPaintStrokeC2S(int entityId, String surface, Direction face, int x, int y, boolean clearFace) implements CustomPayload {
        public static final Id<ModelPaintStrokeC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "model_paint_stroke"));
        public static final PacketCodec<RegistryByteBuf, ModelPaintStrokeC2S> CODEC = PacketCodec.of(ModelPaintStrokeC2S::write, ModelPaintStrokeC2S::new);
        private static boolean registered = false;

        public ModelPaintStrokeC2S {
            surface = surface == null ? "" : surface;
        }

        private ModelPaintStrokeC2S(RegistryByteBuf buf) {
            this(buf.readVarInt(), buf.readString(64), Direction.byIndex(buf.readVarInt()), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeString(surface);
            buf.writeVarInt(face.getIndex());
            buf.writeVarInt(x);
            buf.writeVarInt(y);
            buf.writeBoolean(clearFace);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record PlayerPaintStrokeC2S(int entityId, String surface, Direction face, int x, int y, boolean clearFace) implements CustomPayload {
        public static final Id<PlayerPaintStrokeC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "player_paint_stroke"));
        public static final PacketCodec<RegistryByteBuf, PlayerPaintStrokeC2S> CODEC = PacketCodec.of(PlayerPaintStrokeC2S::write, PlayerPaintStrokeC2S::new);
        private static boolean registered = false;

        public PlayerPaintStrokeC2S {
            surface = surface == null ? "" : surface;
        }

        private PlayerPaintStrokeC2S(RegistryByteBuf buf) {
            this(buf.readVarInt(), buf.readString(64), Direction.byIndex(buf.readVarInt()),
                    buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeString(surface);
            buf.writeVarInt(face.getIndex());
            buf.writeVarInt(x);
            buf.writeVarInt(y);
            buf.writeBoolean(clearFace);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ClearPlayerPaintC2S(int entityId) implements CustomPayload {
        public static final Id<ClearPlayerPaintC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "clear_player_paint"));
        public static final PacketCodec<RegistryByteBuf, ClearPlayerPaintC2S> CODEC = PacketCodec.of(ClearPlayerPaintC2S::write, ClearPlayerPaintC2S::new);
        private static boolean registered = false;

        private ClearPlayerPaintC2S(RegistryByteBuf buf) {
            this(buf.readVarInt());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeVarInt(entityId);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record FillPaintBucketC2S(BlockPos pos, int color) implements CustomPayload {
        public static final Id<FillPaintBucketC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "fill_paint_bucket"));
        public static final PacketCodec<RegistryByteBuf, FillPaintBucketC2S> CODEC = PacketCodec.of(FillPaintBucketC2S::write, FillPaintBucketC2S::new);
        private static boolean registered = false;

        public FillPaintBucketC2S {
            pos = pos.toImmutable();
            color &= 0xFFFFFF;
        }

        private FillPaintBucketC2S(RegistryByteBuf buf) {
            this(buf.readBlockPos(), buf.readInt());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeInt(color);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record LoadBrushFromBucketC2S(BlockPos pos) implements CustomPayload {
        public static final Id<LoadBrushFromBucketC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "load_brush_from_bucket"));
        public static final PacketCodec<RegistryByteBuf, LoadBrushFromBucketC2S> CODEC = PacketCodec.of(LoadBrushFromBucketC2S::write, LoadBrushFromBucketC2S::new);
        private static boolean registered = false;

        public LoadBrushFromBucketC2S {
            pos = pos.toImmutable();
        }

        private LoadBrushFromBucketC2S(RegistryByteBuf buf) {
            this(buf.readBlockPos());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeBlockPos(pos);
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private static int[] sanitizePixels(int[] source) {
        int[] pixels = new int[PaintOverlayStore.FACE_PIXELS];
        System.arraycopy(source, 0, pixels, 0, Math.min(source.length, pixels.length));
        return pixels;
    }
}
