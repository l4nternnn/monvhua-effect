package com.kuilunfuzhe.monvhua.network.drawingboard;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.features.paint.PaintOverlayStore;
import com.kuilunfuzhe.monvhua.features.paint.PaintPaperStore;
import com.kuilunfuzhe.monvhua.features.paint.drawingboard.DrawingBoardBlockEntity;
import com.kuilunfuzhe.monvhua.item.config.PaintConfig;
import com.kuilunfuzhe.monvhua.item.paint.PaintBrushItem;
import com.kuilunfuzhe.monvhua.item.paint.PaintItems;
import com.kuilunfuzhe.monvhua.item.paint.PaintPaperItem;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public final class DrawingBoardPackets {
    private static final double INTERACTION_DISTANCE_SQUARED = 64.0D;
    private static final int EMPTY_COLOR = 0;
    private static final int WHITE_COLOR = 0xFFFFFFFF;

    private DrawingBoardPackets() {
    }

    public static void registerS2C() {
        OpenS2C.register();
        SyncS2C.register();
    }

    public static void registerC2S() {
        StrokeC2S.register();
        ApplyPixelsC2S.register();
        RequestSyncC2S.register();
        SaveToPaperC2S.register();
    }

    public static void registerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(StrokeC2S.ID, (packet, context) ->
                context.server().execute(() -> handleStroke(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(ApplyPixelsC2S.ID, (packet, context) ->
                context.server().execute(() -> handleApplyPixels(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(RequestSyncC2S.ID, (packet, context) ->
                context.server().execute(() -> sendSync(context.player(), packet.pos())));
        ServerPlayNetworking.registerGlobalReceiver(SaveToPaperC2S.ID, (packet, context) ->
                context.server().execute(() -> saveToPaper(context.player(), packet.pos())));
    }

    private static void handleStroke(ServerPlayerEntity player, StrokeC2S packet) {
        if (!(player.getWorld() instanceof ServerWorld world)
                || Vec3d.ofCenter(packet.pos()).squaredDistanceTo(player.getEyePos()) > INTERACTION_DISTANCE_SQUARED
                || !(world.getBlockEntity(packet.pos()) instanceof DrawingBoardBlockEntity board)) {
            return;
        }
        boolean changed;
        if (packet.clear()) {
            board.clear();
            changed = true;
        } else {
            if (!player.isCreative()) {
                ItemStack brush = findPaintBrush(player);
                int slot = brush == ItemStack.EMPTY ? 0 : PaintBrushItem.getSelectedSlot(brush);
                int expectedColor = brush == ItemStack.EMPTY ? 0 : (PaintBrushItem.getPaintColor(brush, slot));
                if (brush == ItemStack.EMPTY || PaintBrushItem.getRemainingPaintPercent(brush, slot) <= 0.0D || expectedColor != packet.color()) {
                    player.sendMessage(Text.literal("Drawing board: brush has no paint"), true);
                    return;
                }
                int budget = paintPixelBudget(PaintBrushItem.getRemainingPaintPercent(brush, slot));
                int changedPixels = board.paintLimited(packet.x(), packet.y(), packet.radius(), packet.color(), budget);
                if (changedPixels <= 0) {
                    return;
                }
                PaintBrushItem.consumePaint(brush, slot, PaintConfig.getInstance().scaledConsumption(changedPixels));
                player.getInventory().markDirty();
                broadcast(world, packet.pos(), board.copyPixels());
                return;
            }
            changed = board.paint(packet.x(), packet.y(), packet.radius(), packet.color());
        }
        if (changed) {
            broadcast(world, packet.pos(), board.copyPixels());
        }
    }

    private static void handleApplyPixels(ServerPlayerEntity player, ApplyPixelsC2S packet) {
        if (!(player.getWorld() instanceof ServerWorld world)
                || Vec3d.ofCenter(packet.pos()).squaredDistanceTo(player.getEyePos()) > INTERACTION_DISTANCE_SQUARED
                || !(world.getBlockEntity(packet.pos()) instanceof DrawingBoardBlockEntity board)) {
            return;
        }
        board.setPixels(packet.pixels());
        broadcast(world, packet.pos(), board.copyPixels());
    }

    private static void sendSync(ServerPlayerEntity player, BlockPos pos) {
        if (player.getWorld().getBlockEntity(pos) instanceof DrawingBoardBlockEntity board) {
            ServerPlayNetworking.send(player, new SyncS2C(pos, board.copyPixels()));
        }
    }

    private static void saveToPaper(ServerPlayerEntity player, BlockPos pos) {
        if (!(player.getWorld() instanceof ServerWorld world)
                || Vec3d.ofCenter(pos).squaredDistanceTo(player.getEyePos()) > INTERACTION_DISTANCE_SQUARED
                || !(world.getBlockEntity(pos) instanceof DrawingBoardBlockEntity board)) {
            return;
        }
        if (!hasPaintPaper(player)) {
            player.sendMessage(Text.literal("Drawing board: need paint paper"), true);
            return;
        }

        List<PaintPaperStore.Cell> cells = createPaperCells(board.copyPixels());
        if (cells.isEmpty()) {
            player.sendMessage(Text.literal("Drawing board: nothing to copy"), true);
            return;
        }

        int paperSize = Math.max(
                MathHelper.ceil((float) DrawingBoardBlockEntity.WIDTH / PaintOverlayStore.SIZE),
                MathHelper.ceil((float) DrawingBoardBlockEntity.HEIGHT / PaintOverlayStore.SIZE)
        );
        ItemStack saved = PaintPaperItem.createSavedPaper(world, "Drawing Board Paper", paperSize, cells);
        if (replacePaintPaper(player, saved)) {
            player.sendMessage(Text.literal("Drawing board: copied to paint paper"), true);
        }
    }

    private static List<PaintPaperStore.Cell> createPaperCells(int[] boardPixels) {
        List<PaintPaperStore.Cell> cells = new ArrayList<>();
        int cellsX = MathHelper.ceil((float) DrawingBoardBlockEntity.WIDTH / PaintOverlayStore.SIZE);
        int cellsY = MathHelper.ceil((float) DrawingBoardBlockEntity.HEIGHT / PaintOverlayStore.SIZE);
        for (int cellY = 0; cellY < cellsY; cellY++) {
            for (int cellX = 0; cellX < cellsX; cellX++) {
                int[] pixels = new int[PaintOverlayStore.FACE_PIXELS];
                boolean visible = false;
                for (int y = 0; y < PaintOverlayStore.SIZE; y++) {
                    for (int x = 0; x < PaintOverlayStore.SIZE; x++) {
                        int boardX = cellX * PaintOverlayStore.SIZE + x;
                        int boardY = cellY * PaintOverlayStore.SIZE + y;
                        int color = EMPTY_COLOR;
                        if (boardX < DrawingBoardBlockEntity.WIDTH && boardY < DrawingBoardBlockEntity.HEIGHT) {
                            color = boardPixels[boardY * DrawingBoardBlockEntity.WIDTH + boardX];
                            if (color == WHITE_COLOR || (color >>> 24) == 0) {
                                color = EMPTY_COLOR;
                            }
                        }
                        pixels[y * PaintOverlayStore.SIZE + x] = color;
                        visible |= color != EMPTY_COLOR;
                    }
                }
                if (visible) {
                    cells.add(new PaintPaperStore.Cell(cellX, cellY, pixels));
                }
            }
        }
        return cells;
    }

    private static boolean hasPaintPaper(ServerPlayerEntity player) {
        if (isPaintPaper(player.getMainHandStack()) || isPaintPaper(player.getOffHandStack())) {
            return true;
        }
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (isPaintPaper(player.getInventory().getStack(slot))) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack findPaintBrush(ServerPlayerEntity player) {
        if (isPaintBrush(player.getMainHandStack())) {
            return player.getMainHandStack();
        }
        if (isPaintBrush(player.getOffHandStack())) {
            return player.getOffHandStack();
        }
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (isPaintBrush(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean isPaintBrush(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == PaintItems.PAINT_BRUSH;
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

    private static boolean replacePaintPaper(ServerPlayerEntity player, ItemStack saved) {
        if (isPaintPaper(player.getMainHandStack())) {
            player.setStackInHand(Hand.MAIN_HAND, saved);
            return true;
        }
        if (isPaintPaper(player.getOffHandStack())) {
            player.setStackInHand(Hand.OFF_HAND, saved);
            return true;
        }
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (isPaintPaper(player.getInventory().getStack(slot))) {
                player.getInventory().setStack(slot, saved);
                return true;
            }
        }
        return false;
    }

    private static boolean isPaintPaper(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == PaintItems.PAINT_PAPER;
    }

    private static void broadcast(ServerWorld world, BlockPos pos, int[] pixels) {
        SyncS2C packet = new SyncS2C(pos, pixels);
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (Vec3d.ofCenter(pos).squaredDistanceTo(player.getEyePos()) <= 128.0D * 128.0D) {
                ServerPlayNetworking.send(player, packet);
            }
        }
    }

    public record OpenS2C(BlockPos pos) implements CustomPayload {
        public static final Id<OpenS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "drawing_board_open"));
        public static final PacketCodec<RegistryByteBuf, OpenS2C> CODEC = PacketCodec.of(OpenS2C::write, OpenS2C::new);
        private static boolean registered;

        public OpenS2C {
            pos = pos.toImmutable();
        }

        private OpenS2C(RegistryByteBuf buf) {
            this(buf.readBlockPos());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeBlockPos(pos);
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

    public record SyncS2C(BlockPos pos, int[] pixels) implements CustomPayload {
        public static final Id<SyncS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "drawing_board_sync"));
        public static final PacketCodec<RegistryByteBuf, SyncS2C> CODEC = PacketCodec.of(SyncS2C::write, SyncS2C::new);
        private static boolean registered;

        public SyncS2C {
            pos = pos.toImmutable();
            pixels = sanitizePixels(pixels);
        }

        private SyncS2C(RegistryByteBuf buf) {
            this(buf.readBlockPos(), readPixels(buf));
        }

        private void write(RegistryByteBuf buf) {
            buf.writeBlockPos(pos);
            for (int pixel : pixels) {
                buf.writeInt(pixel);
            }
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

    public record StrokeC2S(BlockPos pos, int x, int y, int radius, int color, boolean clear) implements CustomPayload {
        public static final Id<StrokeC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "drawing_board_stroke"));
        public static final PacketCodec<RegistryByteBuf, StrokeC2S> CODEC = PacketCodec.of(StrokeC2S::write, StrokeC2S::new);
        private static boolean registered;

        public StrokeC2S {
            pos = pos.toImmutable();
            x = MathHelper.clamp(x, 0, DrawingBoardBlockEntity.WIDTH - 1);
            y = MathHelper.clamp(y, 0, DrawingBoardBlockEntity.HEIGHT - 1);
            radius = MathHelper.clamp(radius, 1, 12);
            if ((color >>> 24) == 0) {
                color |= 0xFF000000;
            }
        }

        private StrokeC2S(RegistryByteBuf buf) {
            this(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readInt(), buf.readBoolean());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeVarInt(x);
            buf.writeVarInt(y);
            buf.writeVarInt(radius);
            buf.writeInt(color);
            buf.writeBoolean(clear);
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

    public record RequestSyncC2S(BlockPos pos) implements CustomPayload {
        public static final Id<RequestSyncC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "drawing_board_request_sync"));
        public static final PacketCodec<RegistryByteBuf, RequestSyncC2S> CODEC = PacketCodec.of(RequestSyncC2S::write, RequestSyncC2S::new);
        private static boolean registered;

        public RequestSyncC2S {
            pos = pos.toImmutable();
        }

        private RequestSyncC2S(RegistryByteBuf buf) {
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

    public record ApplyPixelsC2S(BlockPos pos, int[] pixels) implements CustomPayload {
        public static final Id<ApplyPixelsC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "drawing_board_apply_pixels"));
        public static final PacketCodec<RegistryByteBuf, ApplyPixelsC2S> CODEC = PacketCodec.of(ApplyPixelsC2S::write, ApplyPixelsC2S::new);
        private static boolean registered;

        public ApplyPixelsC2S {
            pos = pos.toImmutable();
            pixels = sanitizePixels(pixels);
        }

        private ApplyPixelsC2S(RegistryByteBuf buf) {
            this(buf.readBlockPos(), readPixels(buf));
        }

        private void write(RegistryByteBuf buf) {
            buf.writeBlockPos(pos);
            for (int pixel : pixels) {
                buf.writeInt(pixel);
            }
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

    public record SaveToPaperC2S(BlockPos pos) implements CustomPayload {
        public static final Id<SaveToPaperC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "drawing_board_save_to_paper"));
        public static final PacketCodec<RegistryByteBuf, SaveToPaperC2S> CODEC = PacketCodec.of(SaveToPaperC2S::write, SaveToPaperC2S::new);
        private static boolean registered;

        public SaveToPaperC2S {
            pos = pos.toImmutable();
        }

        private SaveToPaperC2S(RegistryByteBuf buf) {
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

    private static int[] readPixels(RegistryByteBuf buf) {
        int[] pixels = new int[DrawingBoardBlockEntity.PIXELS];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = buf.readInt();
        }
        return pixels;
    }

    private static int[] sanitizePixels(int[] source) {
        int[] pixels = new int[DrawingBoardBlockEntity.PIXELS];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = 0xFFFFFFFF;
        }
        if (source != null) {
            System.arraycopy(source, 0, pixels, 0, Math.min(source.length, pixels.length));
        }
        return pixels;
    }
}
