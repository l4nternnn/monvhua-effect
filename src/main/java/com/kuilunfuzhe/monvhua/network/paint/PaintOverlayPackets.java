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
        PaintStrokeC2S.register();
        FillPaintBucketC2S.register();
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

    private static int[] sanitizePixels(int[] source) {
        int[] pixels = new int[PaintOverlayStore.FACE_PIXELS];
        System.arraycopy(source, 0, pixels, 0, Math.min(source.length, pixels.length));
        return pixels;
    }
}
