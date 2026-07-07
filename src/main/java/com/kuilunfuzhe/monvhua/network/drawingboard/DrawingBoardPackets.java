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
        PatchS2C.register();
    }

    public static void registerC2S() {
        StrokeC2S.register();
        PatchC2S.register();
        ApplyPixelsC2S.register();
        ResizeC2S.register();
        RequestSyncC2S.register();
        SaveToPaperC2S.register();
    }

    public static void registerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(StrokeC2S.ID, (packet, context) ->
                context.server().execute(() -> handleStroke(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(PatchC2S.ID, (packet, context) ->
                context.server().execute(() -> handlePatch(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(ApplyPixelsC2S.ID, (packet, context) ->
                context.server().execute(() -> handleApplyPixels(context.player(), packet)));
        ServerPlayNetworking.registerGlobalReceiver(ResizeC2S.ID, (packet, context) ->
                context.server().execute(() -> handleResize(context.player(), packet)));
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
                PaintSourceSlots slots = nearestPaintSlots(brush, packet.color());
                if (slots.isEmpty()) {
                    player.sendMessage(Text.literal("Drawing board: brush has no paint"), true);
                    return;
                }
                int budget = paintPixelBudget(slots.totalRemaining());
                DrawingBoardBlockEntity.PixelPatch patch = board.paintPatch(packet.x(), packet.y(), packet.radius(), packet.color(), budget);
                if (patch.isEmpty()) {
                    return;
                }
                int changedPixels = patch.changedCount();
                double consumption = PaintConfig.getInstance().scaledConsumption(changedPixels);
                consumePaintSplit(brush, slots, consumption);
                player.getInventory().markDirty();
                broadcastPatch(world, packet.pos(), board.getCanvasWidth(), board.getCanvasHeight(), patch.indices(), patch.colors());
                return;
            }
            DrawingBoardBlockEntity.PixelPatch patch = board.paintPatch(packet.x(), packet.y(), packet.radius(), packet.color(), Integer.MAX_VALUE);
            changed = !patch.isEmpty();
            if (changed) {
                broadcastPatch(world, packet.pos(), board.getCanvasWidth(), board.getCanvasHeight(), patch.indices(), patch.colors());
                return;
            }
        }
        if (changed) {
            broadcast(world, packet.pos(), board.getCanvasWidth(), board.getCanvasHeight(), board.copyPixels());
        }
    }

    private static void handlePatch(ServerPlayerEntity player, PatchC2S packet) {
        if (!(player.getWorld() instanceof ServerWorld world)
                || Vec3d.ofCenter(packet.pos()).squaredDistanceTo(player.getEyePos()) > INTERACTION_DISTANCE_SQUARED
                || !(world.getBlockEntity(packet.pos()) instanceof DrawingBoardBlockEntity board)
                || board.getCanvasWidth() != packet.width()
                || board.getCanvasHeight() != packet.height()) {
            return;
        }
        if (board.applyPatch(packet.indices(), packet.colors())) {
            broadcastPatch(world, packet.pos(), board.getCanvasWidth(), board.getCanvasHeight(), packet.indices(), packet.colors());
        }
    }

    private static void handleApplyPixels(ServerPlayerEntity player, ApplyPixelsC2S packet) {
        if (!(player.getWorld() instanceof ServerWorld world)
                || Vec3d.ofCenter(packet.pos()).squaredDistanceTo(player.getEyePos()) > INTERACTION_DISTANCE_SQUARED
                || !(world.getBlockEntity(packet.pos()) instanceof DrawingBoardBlockEntity board)) {
            return;
        }
        board.setPixels(packet.width(), packet.height(), packet.pixels());
        broadcast(world, packet.pos(), board.getCanvasWidth(), board.getCanvasHeight(), board.copyPixels());
    }

    private static void handleResize(ServerPlayerEntity player, ResizeC2S packet) {
        if (!(player.getWorld() instanceof ServerWorld world)
                || Vec3d.ofCenter(packet.pos()).squaredDistanceTo(player.getEyePos()) > INTERACTION_DISTANCE_SQUARED
                || !(world.getBlockEntity(packet.pos()) instanceof DrawingBoardBlockEntity board)) {
            return;
        }
        board.resizeToWidth(packet.width());
        broadcast(world, packet.pos(), board.getCanvasWidth(), board.getCanvasHeight(), board.copyPixels());
    }

    private static void sendSync(ServerPlayerEntity player, BlockPos pos) {
        if (player.getWorld().getBlockEntity(pos) instanceof DrawingBoardBlockEntity board) {
            ServerPlayNetworking.send(player, new SyncS2C(pos, board.getCanvasWidth(), board.getCanvasHeight(), board.copyPixels()));
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

        int boardWidth = board.getCanvasWidth();
        int boardHeight = board.getCanvasHeight();
        List<PaintPaperStore.Cell> cells = createPaperCells(board.copyPixels(), boardWidth, boardHeight);
        if (cells.isEmpty()) {
            player.sendMessage(Text.literal("Drawing board: nothing to copy"), true);
            return;
        }

        int paperSize = Math.max(
                MathHelper.ceil((float) boardWidth / PaintOverlayStore.SIZE),
                MathHelper.ceil((float) boardHeight / PaintOverlayStore.SIZE)
        );
        ItemStack saved = PaintPaperItem.createSavedPaper(world, "Drawing Board Paper", paperSize, cells);
        if (replacePaintPaper(player, saved)) {
            player.sendMessage(Text.literal("Drawing board: copied to paint paper"), true);
        }
    }

    private static List<PaintPaperStore.Cell> createPaperCells(int[] boardPixels, int boardWidth, int boardHeight) {
        List<PaintPaperStore.Cell> cells = new ArrayList<>();
        int cellsX = MathHelper.ceil((float) boardWidth / PaintOverlayStore.SIZE);
        int cellsY = MathHelper.ceil((float) boardHeight / PaintOverlayStore.SIZE);
        for (int cellY = 0; cellY < cellsY; cellY++) {
            for (int cellX = 0; cellX < cellsX; cellX++) {
                int[] pixels = new int[PaintOverlayStore.FACE_PIXELS];
                boolean visible = false;
                for (int y = 0; y < PaintOverlayStore.SIZE; y++) {
                    for (int x = 0; x < PaintOverlayStore.SIZE; x++) {
                        int boardX = cellX * PaintOverlayStore.SIZE + x;
                        int boardY = cellY * PaintOverlayStore.SIZE + y;
                        int color = EMPTY_COLOR;
                        if (boardX < boardWidth && boardY < boardHeight) {
                            color = boardPixels[boardY * boardWidth + boardX];
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

    private static PaintSourceSlots nearestPaintSlots(ItemStack brush, int color) {
        if (brush == ItemStack.EMPTY) {
            return PaintSourceSlots.EMPTY;
        }
        int rgb = color & 0xFFFFFF;
        int firstSlot = -1;
        int secondSlot = -1;
        long firstDistance = Long.MAX_VALUE;
        long secondDistance = Long.MAX_VALUE;
        for (int slot = 0; slot < PaintBrushItem.COLOR_SLOTS; slot++) {
            double remaining = PaintBrushItem.getRemainingPaintPercent(brush, slot);
            if (remaining <= 0.0D) {
                continue;
            }
            long distance = rgbDistanceSquared(rgb, PaintBrushItem.getPaintColor(brush, slot) & 0xFFFFFF);
            if (distance < firstDistance) {
                secondDistance = firstDistance;
                secondSlot = firstSlot;
                firstDistance = distance;
                firstSlot = slot;
            } else if (distance < secondDistance) {
                secondDistance = distance;
                secondSlot = slot;
            }
        }
        if (firstSlot < 0) {
            return PaintSourceSlots.EMPTY;
        }
        double firstRemaining = PaintBrushItem.getRemainingPaintPercent(brush, firstSlot);
        double secondRemaining = secondSlot < 0 ? 0.0D : PaintBrushItem.getRemainingPaintPercent(brush, secondSlot);
        return new PaintSourceSlots(firstSlot, secondSlot, firstRemaining, secondRemaining);
    }

    private static long rgbDistanceSquared(int a, int b) {
        int dr = ((a >>> 16) & 0xFF) - ((b >>> 16) & 0xFF);
        int dg = ((a >>> 8) & 0xFF) - ((b >>> 8) & 0xFF);
        int db = (a & 0xFF) - (b & 0xFF);
        return (long) dr * dr + (long) dg * dg + (long) db * db;
    }

    private static void consumePaintSplit(ItemStack brush, PaintSourceSlots slots, double amount) {
        if (amount <= 0.0D || slots.isEmpty()) {
            return;
        }
        if (slots.secondSlot < 0) {
            PaintBrushItem.consumePaint(brush, slots.firstSlot, amount);
            return;
        }
        double half = amount * 0.5D;
        double firstUsed = PaintBrushItem.consumePaint(brush, slots.firstSlot, half);
        double secondUsed = PaintBrushItem.consumePaint(brush, slots.secondSlot, half);
        double remainder = Math.max(0.0D, amount - firstUsed - secondUsed);
        if (remainder > 0.000001D) {
            double firstRemaining = PaintBrushItem.getRemainingPaintPercent(brush, slots.firstSlot);
            double secondRemaining = PaintBrushItem.getRemainingPaintPercent(brush, slots.secondSlot);
            if (firstRemaining >= secondRemaining) {
                remainder -= PaintBrushItem.consumePaint(brush, slots.firstSlot, remainder);
                if (remainder > 0.000001D) {
                    PaintBrushItem.consumePaint(brush, slots.secondSlot, remainder);
                }
            } else {
                remainder -= PaintBrushItem.consumePaint(brush, slots.secondSlot, remainder);
                if (remainder > 0.000001D) {
                    PaintBrushItem.consumePaint(brush, slots.firstSlot, remainder);
                }
            }
        }
    }

    private record PaintSourceSlots(int firstSlot, int secondSlot, double firstRemaining, double secondRemaining) {
        private static final PaintSourceSlots EMPTY = new PaintSourceSlots(-1, -1, 0.0D, 0.0D);

        private boolean isEmpty() {
            return firstSlot < 0;
        }

        private double totalRemaining() {
            return firstRemaining + secondRemaining;
        }
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

    private static void broadcast(ServerWorld world, BlockPos pos, int width, int height, int[] pixels) {
        SyncS2C packet = new SyncS2C(pos, width, height, pixels);
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (Vec3d.ofCenter(pos).squaredDistanceTo(player.getEyePos()) <= 128.0D * 128.0D) {
                ServerPlayNetworking.send(player, packet);
            }
        }
    }

    private static void broadcastPatch(ServerWorld world, BlockPos pos, int width, int height, int[] indices, int[] colors) {
        PatchS2C packet = new PatchS2C(pos, width, height, indices, colors);
        if (packet.indices().length == 0) {
            return;
        }
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

    public record SyncS2C(BlockPos pos, int width, int height, int[] pixels) implements CustomPayload {
        public static final Id<SyncS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "drawing_board_sync"));
        public static final PacketCodec<RegistryByteBuf, SyncS2C> CODEC = PacketCodec.of(SyncS2C::write, SyncS2C::new);
        private static boolean registered;

        public SyncS2C {
            pos = pos.toImmutable();
            width = DrawingBoardBlockEntity.sanitizeWidth(width);
            height = sanitizeHeight(width, height);
            pixels = sanitizePixels(width, height, pixels);
        }

        private SyncS2C(RegistryByteBuf buf) {
            this(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), readPixels(buf));
        }

        private void write(RegistryByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeVarInt(width);
            buf.writeVarInt(height);
            buf.writeVarInt(pixels.length);
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
            x = Math.max(0, x);
            y = Math.max(0, y);
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

    public record PatchS2C(BlockPos pos, int width, int height, int[] indices, int[] colors) implements CustomPayload {
        public static final Id<PatchS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "drawing_board_patch"));
        public static final PacketCodec<RegistryByteBuf, PatchS2C> CODEC = PacketCodec.of(PatchS2C::write, PatchS2C::new);
        private static boolean registered;

        public PatchS2C {
            PatchData data = sanitizePatch(pos, width, height, indices, colors);
            pos = data.pos();
            width = data.width();
            height = data.height();
            indices = data.indices();
            colors = data.colors();
        }

        private PatchS2C(PatchData data) {
            this(data.pos(), data.width(), data.height(), data.indices(), data.colors());
        }

        private PatchS2C(RegistryByteBuf buf) {
            this(readPatchData(buf));
        }

        private void write(RegistryByteBuf buf) {
            writePatchData(buf, pos, width, height, indices, colors);
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

    public record PatchC2S(BlockPos pos, int width, int height, int[] indices, int[] colors) implements CustomPayload {
        public static final Id<PatchC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "drawing_board_patch_apply"));
        public static final PacketCodec<RegistryByteBuf, PatchC2S> CODEC = PacketCodec.of(PatchC2S::write, PatchC2S::new);
        private static boolean registered;

        public PatchC2S {
            PatchData data = sanitizePatch(pos, width, height, indices, colors);
            pos = data.pos();
            width = data.width();
            height = data.height();
            indices = data.indices();
            colors = data.colors();
        }

        private PatchC2S(PatchData data) {
            this(data.pos(), data.width(), data.height(), data.indices(), data.colors());
        }

        private PatchC2S(RegistryByteBuf buf) {
            this(readPatchData(buf));
        }

        private void write(RegistryByteBuf buf) {
            writePatchData(buf, pos, width, height, indices, colors);
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

    public record ApplyPixelsC2S(BlockPos pos, int width, int height, int[] pixels) implements CustomPayload {
        public static final Id<ApplyPixelsC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "drawing_board_apply_pixels"));
        public static final PacketCodec<RegistryByteBuf, ApplyPixelsC2S> CODEC = PacketCodec.of(ApplyPixelsC2S::write, ApplyPixelsC2S::new);
        private static boolean registered;

        public ApplyPixelsC2S {
            pos = pos.toImmutable();
            width = DrawingBoardBlockEntity.sanitizeWidth(width);
            height = sanitizeHeight(width, height);
            pixels = sanitizePixels(width, height, pixels);
        }

        private ApplyPixelsC2S(RegistryByteBuf buf) {
            this(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), readPixels(buf));
        }

        private void write(RegistryByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeVarInt(width);
            buf.writeVarInt(height);
            buf.writeVarInt(pixels.length);
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

    public record ResizeC2S(BlockPos pos, int width) implements CustomPayload {
        public static final Id<ResizeC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "drawing_board_resize"));
        public static final PacketCodec<RegistryByteBuf, ResizeC2S> CODEC = PacketCodec.of(ResizeC2S::write, ResizeC2S::new);
        private static boolean registered;

        public ResizeC2S {
            pos = pos.toImmutable();
            width = DrawingBoardBlockEntity.sanitizeWidth(width);
        }

        private ResizeC2S(RegistryByteBuf buf) {
            this(buf.readBlockPos(), buf.readVarInt());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeVarInt(width);
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
        int length = MathHelper.clamp(buf.readVarInt(), 0, DrawingBoardBlockEntity.MAX_WIDTH * DrawingBoardBlockEntity.heightForWidth(DrawingBoardBlockEntity.MAX_WIDTH));
        int[] pixels = new int[length];
        for (int i = 0; i < length; i++) {
            pixels[i] = buf.readInt();
        }
        return pixels;
    }

    private static PatchData readPatchData(RegistryByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int width = DrawingBoardBlockEntity.sanitizeWidth(buf.readVarInt());
        int height = sanitizeHeight(width, buf.readVarInt());
        int maxLength = Math.max(0, width * height);
        int length = MathHelper.clamp(buf.readVarInt(), 0, maxLength);
        int[] indices = new int[length];
        int[] colors = new int[length];
        for (int i = 0; i < length; i++) {
            indices[i] = buf.readVarInt();
            colors[i] = buf.readInt();
        }
        return sanitizePatch(pos, width, height, indices, colors);
    }

    private static void writePatchData(RegistryByteBuf buf, BlockPos pos, int width, int height, int[] indices, int[] colors) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(width);
        buf.writeVarInt(height);
        int length = Math.min(indices.length, colors.length);
        buf.writeVarInt(length);
        for (int i = 0; i < length; i++) {
            buf.writeVarInt(indices[i]);
            buf.writeInt(colors[i]);
        }
    }

    private static PatchData sanitizePatch(BlockPos pos, int width, int height, int[] sourceIndices, int[] sourceColors) {
        pos = pos.toImmutable();
        width = DrawingBoardBlockEntity.sanitizeWidth(width);
        height = sanitizeHeight(width, height);
        int maxPixels = Math.max(0, width * height);
        int length = Math.min(sourceIndices == null ? 0 : sourceIndices.length, sourceColors == null ? 0 : sourceColors.length);
        length = MathHelper.clamp(length, 0, maxPixels);
        int[] indices = new int[length];
        int[] colors = new int[length];
        int out = 0;
        for (int i = 0; i < length; i++) {
            int index = sourceIndices[i];
            if (index < 0 || index >= maxPixels) {
                continue;
            }
            int color = sourceColors[i];
            if ((color >>> 24) == 0) {
                color |= 0xFF000000;
            }
            indices[out] = index;
            colors[out] = color;
            out++;
        }
        if (out != length) {
            indices = java.util.Arrays.copyOf(indices, out);
            colors = java.util.Arrays.copyOf(colors, out);
        }
        return new PatchData(pos, width, height, indices, colors);
    }

    private static int sanitizeHeight(int width, int height) {
        return height <= 0 ? DrawingBoardBlockEntity.heightForWidth(width) : MathHelper.clamp(height, 1, DrawingBoardBlockEntity.heightForWidth(DrawingBoardBlockEntity.MAX_WIDTH));
    }

    private static int[] sanitizePixels(int width, int height, int[] source) {
        int[] pixels = new int[Math.max(1, width * height)];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = 0xFFFFFFFF;
        }
        if (source != null) {
            System.arraycopy(source, 0, pixels, 0, Math.min(source.length, pixels.length));
        }
        return pixels;
    }

    private record PatchData(BlockPos pos, int width, int height, int[] indices, int[] colors) {
    }
}
