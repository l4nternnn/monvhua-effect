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

import java.util.ArrayList;
import java.util.List;

public final class PaintOverlayPackets {
    private static final int MAX_FACE_UPDATES = 4096;

    private PaintOverlayPackets() {
    }

    public static void registerS2C() {
        FullSyncS2C.register();
        FaceUpdateS2C.register();
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
        ModelPaintStrokeC2S.register();
        FillPaintBucketC2S.register();
        LoadBrushFromBucketC2S.register();
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

    public record PaintStrokeC2S(BlockPos pos, Direction face, int x, int y, boolean clearFace) implements CustomPayload {
        public static final Id<PaintStrokeC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "paint_stroke"));
        public static final PacketCodec<RegistryByteBuf, PaintStrokeC2S> CODEC = PacketCodec.of(PaintStrokeC2S::write, PaintStrokeC2S::new);
        private static boolean registered = false;

        public PaintStrokeC2S {
            pos = pos.toImmutable();
        }

        private PaintStrokeC2S(RegistryByteBuf buf) {
            this(buf.readBlockPos(), Direction.byIndex(buf.readVarInt()), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeBlockPos(pos);
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

    public record EditorPaintStrokeC2S(BlockPos pos, Direction face, int x, int y, int tool, boolean clearFace) implements CustomPayload {
        public static final Id<EditorPaintStrokeC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "editor_paint_stroke"));
        public static final PacketCodec<RegistryByteBuf, EditorPaintStrokeC2S> CODEC = PacketCodec.of(EditorPaintStrokeC2S::write, EditorPaintStrokeC2S::new);
        private static boolean registered = false;

        public EditorPaintStrokeC2S {
            pos = pos.toImmutable();
        }

        private EditorPaintStrokeC2S(RegistryByteBuf buf) {
            this(buf.readBlockPos(), Direction.byIndex(buf.readVarInt()), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeBlockPos(pos);
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
