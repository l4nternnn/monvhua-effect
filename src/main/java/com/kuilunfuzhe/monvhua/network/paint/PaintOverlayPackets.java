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
import java.util.UUID;

public final class PaintOverlayPackets {
    private static final int MAX_FACE_UPDATES = 4096;
    public static final int MAX_IMPORTED_IMAGE_BYTES = 16 * 1024 * 1024;
    public static final int IMPORTED_IMAGE_CHUNK_BYTES = 64 * 1024;

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
        ImportedPaperImageS2C.register();
        ImportPaintPaperResultS2C.register();
        PlaceImportedPaperResultS2C.register();
    }

    public static void registerC2S() {
        BrushSettingsC2S.register();
        PaperSizeC2S.register();
        ImportPaintPaperC2S.register();
        PlaceImportedPaperC2S.register();
        PlaceImportedPaperBeginC2S.register();
        PlaceImportedPaperChunkC2S.register();
        PlaceImportedPaperCommitC2S.register();
        RequestImportedPaperImageC2S.register();
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
            slot = 0;
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
            slot = 0;
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

    public record ImportPaintPaperC2S(String filename, byte[] imageBytes) implements CustomPayload {
        private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
        public static final Id<ImportPaintPaperC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "import_paint_paper"));
        public static final PacketCodec<RegistryByteBuf, ImportPaintPaperC2S> CODEC = PacketCodec.of(ImportPaintPaperC2S::write, ImportPaintPaperC2S::new);
        private static boolean registered = false;

        public ImportPaintPaperC2S {
            filename = filename == null ? "image.png" : filename;
            if (filename.length() > 128) {
                filename = filename.substring(0, 128);
            }
            imageBytes = imageBytes == null ? new byte[0] : imageBytes.clone();
            if (imageBytes.length > MAX_IMAGE_BYTES) {
                byte[] limited = new byte[MAX_IMAGE_BYTES];
                System.arraycopy(imageBytes, 0, limited, 0, MAX_IMAGE_BYTES);
                imageBytes = limited;
            }
        }

        private ImportPaintPaperC2S(RegistryByteBuf buf) {
            this(buf.readString(128), buf.readByteArray(MAX_IMAGE_BYTES));
        }

        private void write(RegistryByteBuf buf) {
            buf.writeString(filename);
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

    /** Confirms a two-stage imported-image paper placement. Coordinates are the image's top-left microcell. */
    public record PlaceImportedPaperC2S(BlockPos pos, Direction face, int microX, int microY) implements CustomPayload {
        public static final Id<PlaceImportedPaperC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "place_imported_paint_paper"));
        public static final PacketCodec<RegistryByteBuf, PlaceImportedPaperC2S> CODEC = PacketCodec.of(PlaceImportedPaperC2S::write, PlaceImportedPaperC2S::new);
        private static boolean registered = false;

        public PlaceImportedPaperC2S {
            pos = pos.toImmutable();
            face = face == null ? Direction.UP : face;
            microX = MathHelper.clamp(microX, 0, PaintOverlayStore.SIZE - 1);
            microY = MathHelper.clamp(microY, 0, PaintOverlayStore.SIZE - 1);
        }

        private PlaceImportedPaperC2S(RegistryByteBuf buf) {
            this(buf.readBlockPos(), Direction.byIndex(buf.readVarInt()), buf.readVarInt(), buf.readVarInt());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeVarInt(face.getIndex());
            buf.writeVarInt(microX);
            buf.writeVarInt(microY);
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

    public record PlaceImportedPaperBeginC2S(UUID imageId, String name, int width, int height, int totalBytes,
                                             byte[] sha256, BlockPos pos, Direction face, int microX, int microY,
                                             int targetWidth, int targetHeight) implements CustomPayload {
        private static final int HASH_LENGTH = 32;
        public static final Id<PlaceImportedPaperBeginC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "place_imported_paper_begin"));
        public static final PacketCodec<RegistryByteBuf, PlaceImportedPaperBeginC2S> CODEC = PacketCodec.of(PlaceImportedPaperBeginC2S::write, PlaceImportedPaperBeginC2S::new);
        private static boolean registered = false;

        public PlaceImportedPaperBeginC2S {
            name = name == null ? "image" : name.substring(0, Math.min(name.length(), 128));
            width = Math.max(0, width);
            height = Math.max(0, height);
            totalBytes = MathHelper.clamp(totalBytes, 0, MAX_IMPORTED_IMAGE_BYTES);
            sha256 = sha256 == null ? new byte[0] : Arrays.copyOf(sha256, Math.min(sha256.length, HASH_LENGTH));
            pos = pos.toImmutable();
            face = face == null ? Direction.UP : face;
            microX = MathHelper.clamp(microX, 0, PaintOverlayStore.SIZE - 1);
            microY = MathHelper.clamp(microY, 0, PaintOverlayStore.SIZE - 1);
            targetWidth = MathHelper.clamp(targetWidth, 1, Math.max(1, width));
            targetHeight = MathHelper.clamp(targetHeight, 1, Math.max(1, height));
        }

        private PlaceImportedPaperBeginC2S(RegistryByteBuf buf) {
            this(buf.readUuid(), buf.readString(128), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readByteArray(HASH_LENGTH), buf.readBlockPos(), Direction.byIndex(buf.readVarInt()),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeUuid(imageId);
            buf.writeString(name);
            buf.writeVarInt(width);
            buf.writeVarInt(height);
            buf.writeVarInt(totalBytes);
            buf.writeByteArray(sha256);
            buf.writeBlockPos(pos);
            buf.writeVarInt(face.getIndex());
            buf.writeVarInt(microX);
            buf.writeVarInt(microY);
            buf.writeVarInt(targetWidth);
            buf.writeVarInt(targetHeight);
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

    public record PlaceImportedPaperChunkC2S(UUID imageId, int sequence, byte[] bytes) implements CustomPayload {
        public static final Id<PlaceImportedPaperChunkC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "place_imported_paper_chunk"));
        public static final PacketCodec<RegistryByteBuf, PlaceImportedPaperChunkC2S> CODEC = PacketCodec.of(PlaceImportedPaperChunkC2S::write, PlaceImportedPaperChunkC2S::new);
        private static boolean registered = false;

        public PlaceImportedPaperChunkC2S {
            sequence = Math.max(0, sequence);
            bytes = bytes == null ? new byte[0] : Arrays.copyOf(bytes, Math.min(bytes.length, IMPORTED_IMAGE_CHUNK_BYTES));
        }

        private PlaceImportedPaperChunkC2S(RegistryByteBuf buf) {
            this(buf.readUuid(), buf.readVarInt(), buf.readByteArray(IMPORTED_IMAGE_CHUNK_BYTES));
        }

        private void write(RegistryByteBuf buf) {
            buf.writeUuid(imageId);
            buf.writeVarInt(sequence);
            buf.writeByteArray(bytes);
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

    public record PlaceImportedPaperCommitC2S(UUID imageId) implements CustomPayload {
        public static final Id<PlaceImportedPaperCommitC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "place_imported_paper_commit"));
        public static final PacketCodec<RegistryByteBuf, PlaceImportedPaperCommitC2S> CODEC = PacketCodec.of(PlaceImportedPaperCommitC2S::write, PlaceImportedPaperCommitC2S::new);
        private static boolean registered = false;

        private PlaceImportedPaperCommitC2S(RegistryByteBuf buf) {
            this(buf.readUuid());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeUuid(imageId);
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

    public record RequestImportedPaperImageC2S(UUID imageId) implements CustomPayload {
        public static final Id<RequestImportedPaperImageC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "request_imported_paint_paper_image"));
        public static final PacketCodec<RegistryByteBuf, RequestImportedPaperImageC2S> CODEC = PacketCodec.of(RequestImportedPaperImageC2S::write, RequestImportedPaperImageC2S::new);
        private static boolean registered = false;

        private RequestImportedPaperImageC2S(RegistryByteBuf buf) {
            this(buf.readUuid());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeUuid(imageId);
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

    public record ImportedPaperImageS2C(UUID imageId, int width, int height, byte[] pngBytes) implements CustomPayload {
        private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
        public static final Id<ImportedPaperImageS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "imported_paint_paper_image"));
        public static final PacketCodec<RegistryByteBuf, ImportedPaperImageS2C> CODEC = PacketCodec.of(ImportedPaperImageS2C::write, ImportedPaperImageS2C::new);
        private static boolean registered = false;

        public ImportedPaperImageS2C {
            width = Math.max(0, width);
            height = Math.max(0, height);
            pngBytes = pngBytes == null ? new byte[0] : Arrays.copyOf(pngBytes, Math.min(pngBytes.length, MAX_IMAGE_BYTES));
        }

        private ImportedPaperImageS2C(RegistryByteBuf buf) {
            this(buf.readUuid(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(MAX_IMAGE_BYTES));
        }

        private void write(RegistryByteBuf buf) {
            buf.writeUuid(imageId);
            buf.writeVarInt(width);
            buf.writeVarInt(height);
            buf.writeByteArray(pngBytes);
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

    /** Completes the asynchronous image-paper import request on the client. */
    public record ImportPaintPaperResultS2C(boolean success, String message, int width, int height) implements CustomPayload {
        private static final int MAX_MESSAGE_LENGTH = 256;
        public static final Id<ImportPaintPaperResultS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "import_paint_paper_result"));
        public static final PacketCodec<RegistryByteBuf, ImportPaintPaperResultS2C> CODEC = PacketCodec.of(ImportPaintPaperResultS2C::write, ImportPaintPaperResultS2C::new);
        private static boolean registered = false;

        public ImportPaintPaperResultS2C {
            message = message == null ? "" : message;
            if (message.length() > MAX_MESSAGE_LENGTH) {
                message = message.substring(0, MAX_MESSAGE_LENGTH);
            }
            width = Math.max(0, width);
            height = Math.max(0, height);
        }

        private ImportPaintPaperResultS2C(RegistryByteBuf buf) {
            this(buf.readBoolean(), buf.readString(MAX_MESSAGE_LENGTH), buf.readVarInt(), buf.readVarInt());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeBoolean(success);
            buf.writeString(message);
            buf.writeVarInt(width);
            buf.writeVarInt(height);
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

    public record PlaceImportedPaperResultS2C(UUID imageId, boolean success, String message) implements CustomPayload {
        private static final int MAX_MESSAGE_LENGTH = 256;
        public static final Id<PlaceImportedPaperResultS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "place_imported_paper_result"));
        public static final PacketCodec<RegistryByteBuf, PlaceImportedPaperResultS2C> CODEC = PacketCodec.of(PlaceImportedPaperResultS2C::write, PlaceImportedPaperResultS2C::new);
        private static boolean registered = false;

        public PlaceImportedPaperResultS2C {
            message = message == null ? "" : message.substring(0, Math.min(message.length(), MAX_MESSAGE_LENGTH));
        }

        private PlaceImportedPaperResultS2C(RegistryByteBuf buf) {
            this(buf.readUuid(), buf.readBoolean(), buf.readString(MAX_MESSAGE_LENGTH));
        }

        private void write(RegistryByteBuf buf) {
            buf.writeUuid(imageId);
            buf.writeBoolean(success);
            buf.writeString(message);
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
