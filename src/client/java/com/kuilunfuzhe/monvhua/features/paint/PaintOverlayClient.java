package com.kuilunfuzhe.monvhua.features.paint;

import com.kuilunfuzhe.monvhua.network.paint.PaintOverlayPackets;
import com.kuilunfuzhe.monvhua.item.paint.PaintBrushItem;
import com.kuilunfuzhe.monvhua.item.paint.PaintItems;
import com.kuilunfuzhe.monvhua.network.SafeClientNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWScrollCallbackI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PaintOverlayClient {
    private static final double MAX_RENDER_DISTANCE_SQUARED = 96.0D * 96.0D;
    private static final float STEP = 1.0F / PaintOverlayStore.SIZE;
    private static final float OFFSET = 0.003F;
    private static final int MAX_RECENT_COLORS = 3;
    private static final Map<PaintOverlayStore.FaceKey, int[]> FACES = new HashMap<>();
    private static final List<Integer> RECENT_COLORS = new ArrayList<>();
    private static final List<Integer> FAVORITE_COLORS = new ArrayList<>();
    private static GLFWScrollCallbackI previousScrollCallback;
    private static boolean scrollCallbackRegistered = false;
    private static int selectedRadius = 1;
    private static int selectedPaperSize = PaintOverlayFeature.DEFAULT_PAPER_SIZE;
    private static int selectedColor = 0xCCFF2A4F;
    private static boolean eraserFaceMode = false;
    private static boolean wasUsePressed = false;
    private static StrokeKey lastStrokeKey;
    private static int repeatedStrokeTicks;

    private PaintOverlayClient() {
    }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!scrollCallbackRegistered && client.getWindow() != null) {
                registerScrollCallback(client);
            }
            tickContinuousPainting(client);
        });
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClient() || player == null || world.getBlockState(hitResult.getBlockPos()).getBlock() != PaintItems.PAINT_BUCKET_BLOCK) {
                return ActionResult.PASS;
            }
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.currentScreen == null) {
                openPaintBucketColorScreen(client, hitResult.getBlockPos());
            }
            return ActionResult.SUCCESS;
        });
        BlockEntityRendererFactories.register(
                PaintBucketBlockEntities.PAINT_BUCKET_BLOCK_ENTITY,
                context -> new PaintBucketBlockEntityRenderer()
        );
        HudRenderCallback.EVENT.register(PaintOverlayClient::renderHud);
        ClientPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.FullSyncS2C.ID, (packet, context) ->
                context.client().execute(() -> applyFullSync(packet.faces())));
        ClientPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.FaceUpdateS2C.ID, (packet, context) ->
                context.client().execute(() -> applyFace(packet.faceData())));
    }

    public static boolean tryOpenColorScreen(MinecraftClient client) {
        if (client.player == null) {
            return false;
        }
        if (isHoldingPaintBrush(client)) {
            client.setScreen(new PaintBrushColorScreen());
            return true;
        }
        if (isHoldingPaintPaper(client)) {
            client.setScreen(new PaintPaperImportScreen());
            return true;
        }
        return false;
    }

    public static void openPaintBucketColorScreen(MinecraftClient client, BlockPos pos) {
        if (client.player == null || client.world == null || client.world.getBlockState(pos).getBlock() != PaintItems.PAINT_BUCKET_BLOCK) {
            return;
        }
        BlockEntity blockEntity = client.world.getBlockEntity(pos);
        if (blockEntity instanceof PaintBucketBlockEntity bucket && bucket.isFilled()) {
            setSelectedColor(0xCC000000 | bucket.getColor());
        }
        client.setScreen(new PaintBrushColorScreen(pos));
    }

    public static int selectedRadius() {
        return selectedRadius;
    }

    public static int selectedColor() {
        return selectedColor;
    }

    public static void setSelectedColor(int color) {
        int nextColor = ensureAlpha(color);
        if (nextColor == selectedColor) {
            return;
        }
        selectedColor = nextColor;
        syncSettings();
    }

    public static void recordPickedColor(int color) {
        int normalized = ensureAlpha(color);
        RECENT_COLORS.remove(Integer.valueOf(normalized));
        RECENT_COLORS.add(0, normalized);
        while (RECENT_COLORS.size() > MAX_RECENT_COLORS) {
            RECENT_COLORS.remove(RECENT_COLORS.size() - 1);
        }
    }

    public static List<Integer> recentColors() {
        return List.copyOf(RECENT_COLORS);
    }

    public static List<Integer> favoriteColors() {
        return List.copyOf(FAVORITE_COLORS);
    }

    public static boolean isFavoriteColor(int color) {
        return FAVORITE_COLORS.contains(ensureAlpha(color));
    }

    public static void toggleFavoriteColor(int color) {
        int normalized = ensureAlpha(color);
        if (FAVORITE_COLORS.remove(Integer.valueOf(normalized))) {
            return;
        }
        FAVORITE_COLORS.add(normalized);
    }

    public static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || FACES.isEmpty()) {
            return;
        }

        Vec3d camera = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer vertices = context.consumers().getBuffer(RenderLayer.getDebugQuads());

        for (Map.Entry<PaintOverlayStore.FaceKey, int[]> entry : FACES.entrySet()) {
            BlockPos pos = entry.getKey().pos();
            if (Vec3d.ofCenter(pos).squaredDistanceTo(camera) > MAX_RENDER_DISTANCE_SQUARED) {
                continue;
            }
            drawFace(vertices, matrix, Vec3d.of(pos).subtract(camera), entry.getKey().face(), entry.getValue());
        }
    }

    private static void registerScrollCallback(MinecraftClient client) {
        long window = client.getWindow().getHandle();
        previousScrollCallback = GLFW.glfwSetScrollCallback(window, (handle, xOffset, yOffset) -> {
            if (handlePaintBrushScroll(yOffset)) {
                return;
            }
            if (previousScrollCallback != null) {
                previousScrollCallback.invoke(handle, xOffset, yOffset);
            }
        });
        scrollCallbackRegistered = true;
    }

    private static boolean handlePaintBrushScroll(double yOffset) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.currentScreen != null || !isCtrlDown(client)) {
            return false;
        }
        int delta = yOffset > 0 ? 1 : -1;
        if (isHoldingPaintPaper(client)) {
            int nextSize = MathHelper.clamp(selectedPaperSize + delta, PaintOverlayFeature.MIN_PAPER_SIZE, PaintOverlayFeature.MAX_PAPER_SIZE);
            if (nextSize == selectedPaperSize) {
                return true;
            }
            selectedPaperSize = nextSize;
            syncPaperSize();
            return true;
        }
        if (!isHoldingPaintTool(client)) {
            return false;
        }
        int nextRadius = MathHelper.clamp(selectedRadius + delta, 1, 8);
        if (nextRadius == selectedRadius) {
            return true;
        }
        selectedRadius = nextRadius;
        syncSettings();
        return true;
    }

    private static void tickContinuousPainting(MinecraftClient client) {
        boolean usePressed = client.options.useKey.isPressed();
        boolean useStarted = usePressed && !wasUsePressed;
        wasUsePressed = usePressed;

        if (client.player == null || client.currentScreen != null || !usePressed || !isHoldingPaintTool(client)) {
            lastStrokeKey = null;
            repeatedStrokeTicks = 0;
            return;
        }
        boolean eraser = isHoldingEraser(client);
        if (eraser && useStarted && shouldToggleEraserMode(client)) {
            toggleEraserMode(client);
            lastStrokeKey = null;
            repeatedStrokeTicks = 0;
            return;
        }
        if (!(client.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            lastStrokeKey = null;
            repeatedStrokeTicks = 0;
            return;
        }

        BlockPos pos = hit.getBlockPos();
        Direction face = hit.getSide();
        int[] pixel = PaintBrushItem.getPixel(hit.getPos(), pos, face);
        boolean clear = eraser && eraserFaceMode;
        StrokeKey key = new StrokeKey(pos, face, pixel[0], pixel[1], clear);
        repeatedStrokeTicks++;
        if (key.equals(lastStrokeKey) && repeatedStrokeTicks < 3) {
            return;
        }
        lastStrokeKey = key;
        repeatedStrokeTicks = 0;
        SafeClientNetworking.send(new PaintOverlayPackets.PaintStrokeC2S(pos, face, pixel[0], pixel[1], clear));
    }

    private static boolean shouldToggleEraserMode(MinecraftClient client) {
        return client.player != null && (client.player.isSneaking()
                || !(client.crosshairTarget instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK);
    }

    private static void toggleEraserMode(MinecraftClient client) {
        eraserFaceMode = !eraserFaceMode;
        if (client.player != null) {
            client.player.sendMessage(Text.literal(eraserFaceMode ? "橡皮: 方块面擦除" : "橡皮: 像素擦除"), true);
        }
    }

    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || (!isHoldingPaintTool(client) && !isHoldingPaintPaper(client))) {
            return;
        }

        boolean paper = isHoldingPaintPaper(client);
        String radiusText = paper ? "P " + selectedPaperSize + "x" + selectedPaperSize : "R " + selectedRadius;
        boolean eraser = isHoldingEraser(client);
        String colorText = paper ? "PAPER" : (eraser ? (eraserFaceMode ? "FACE" : "PIXEL") : toHex(selectedColor));
        int width = 96;
        int height = 30;
        int hotbarLeft = context.getScaledWindowWidth() / 2 - 91;
        int x = hotbarLeft - 40 - width;
        int y = context.getScaledWindowHeight() - 60;
        if (x < 4) {
            x = 4;
        }

        context.fill(x, y, x + width, y + height, 0xAA101015);
        context.fill(x + 6, y + 6, x + 24, y + 24, paper ? 0xFFF7E7B7 : (eraser ? 0xFFE6E6E6 : selectedColor));
        context.fill(x + 6, y + 6, x + 24, y + 7, 0xFFFFFFFF);
        context.fill(x + 6, y + 23, x + 24, y + 24, 0xFF000000);
        context.fill(x + 6, y + 6, x + 7, y + 24, 0xFFFFFFFF);
        context.fill(x + 23, y + 6, x + 24, y + 24, 0xFF000000);
        context.drawText(client.textRenderer, Text.literal(radiusText), x + 30, y + 5, 0xFFFFFFFF, false);
        context.drawText(client.textRenderer, Text.literal(colorText), x + 30, y + 17, 0xFFE6E6E6, false);

        int slotY = y + height + 4;
        for (int i = 0; i < 3; i++) {
            int slotX = x + i * 20;
            context.fill(slotX, slotY, slotX + 18, slotY + 18, 0x66101015);
            context.fill(slotX, slotY, slotX + 18, slotY + 1, 0x99FFFFFF);
            context.fill(slotX, slotY + 17, slotX + 18, slotY + 18, 0x99000000);
            context.fill(slotX, slotY, slotX + 1, slotY + 18, 0x99FFFFFF);
            context.fill(slotX + 17, slotY, slotX + 18, slotY + 18, 0x99000000);
        }
    }

    private static boolean isHoldingPaintBrush(MinecraftClient client) {
        if (client.player == null) {
            return false;
        }
        return isPaintBrush(client.player.getMainHandStack()) || isPaintBrush(client.player.getOffHandStack());
    }

    private static boolean isPaintBrush(ItemStack stack) {
        return stack.getItem() == PaintItems.PAINT_BRUSH;
    }

    private static boolean isHoldingEraser(MinecraftClient client) {
        if (client.player == null) {
            return false;
        }
        return isEraser(client.player.getMainHandStack()) || isEraser(client.player.getOffHandStack());
    }

    private static boolean isEraser(ItemStack stack) {
        return stack.getItem() == PaintItems.ERASER;
    }

    private static boolean isHoldingPaintTool(MinecraftClient client) {
        return isHoldingPaintBrush(client) || isHoldingEraser(client);
    }

    private static boolean isHoldingPaintPaper(MinecraftClient client) {
        if (client.player == null) {
            return false;
        }
        return isPaintPaper(client.player.getMainHandStack()) || isPaintPaper(client.player.getOffHandStack());
    }

    private static boolean isPaintPaper(ItemStack stack) {
        return stack.getItem() == PaintItems.PAINT_PAPER;
    }

    private static boolean isCtrlDown(MinecraftClient client) {
        long handle = client.getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private static void syncSettings() {
        SafeClientNetworking.send(new PaintOverlayPackets.BrushSettingsC2S(selectedColor, selectedRadius));
    }

    private static void syncPaperSize() {
        SafeClientNetworking.send(new PaintOverlayPackets.PaperSizeC2S(selectedPaperSize));
    }

    private static int ensureAlpha(int color) {
        return (color >>> 24) == 0 ? color | 0xCC000000 : color;
    }

    private static String toHex(int color) {
        return String.format("#%06X", color & 0xFFFFFF);
    }

    private static void applyFullSync(List<PaintOverlayPackets.FaceData> faces) {
        FACES.clear();
        for (PaintOverlayPackets.FaceData face : faces) {
            applyFace(face);
        }
    }

    private static void applyFace(PaintOverlayPackets.FaceData face) {
        PaintOverlayStore.FaceKey key = new PaintOverlayStore.FaceKey(face.pos(), face.face());
        if (!hasPixels(face.pixels())) {
            FACES.remove(key);
            return;
        }
        FACES.put(key, Arrays.copyOf(face.pixels(), PaintOverlayStore.FACE_PIXELS));
    }

    private static boolean hasPixels(int[] pixels) {
        for (int pixel : pixels) {
            if (pixel != 0) {
                return true;
            }
        }
        return false;
    }

    private static void drawFace(VertexConsumer vertices, Matrix4f matrix, Vec3d origin, Direction face, int[] pixels) {
        for (int y = 0; y < PaintOverlayStore.SIZE; y++) {
            for (int x = 0; x < PaintOverlayStore.SIZE; x++) {
                int color = pixels[y * PaintOverlayStore.SIZE + x];
                if (color == 0) {
                    continue;
                }
                drawPixel(vertices, matrix, origin, face, x, y, color);
            }
        }
    }

    private static void drawPixel(VertexConsumer vertices, Matrix4f matrix, Vec3d origin, Direction face, int x, int y, int color) {
        float minU = x * STEP;
        float maxU = minU + STEP;
        float minV = y * STEP;
        float maxV = minV + STEP;
        float a = ((color >>> 24) & 0xFF);
        float r = (color >>> 16) & 0xFF;
        float g = (color >>> 8) & 0xFF;
        float b = color & 0xFF;

        Vec3d p0;
        Vec3d p1;
        Vec3d p2;
        Vec3d p3;
        switch (face) {
            case UP -> {
                p0 = origin.add(minU, 1.0F + OFFSET, minV);
                p1 = origin.add(maxU, 1.0F + OFFSET, minV);
                p2 = origin.add(maxU, 1.0F + OFFSET, maxV);
                p3 = origin.add(minU, 1.0F + OFFSET, maxV);
            }
            case DOWN -> {
                p0 = origin.add(minU, -OFFSET, 1.0F - minV);
                p1 = origin.add(maxU, -OFFSET, 1.0F - minV);
                p2 = origin.add(maxU, -OFFSET, 1.0F - maxV);
                p3 = origin.add(minU, -OFFSET, 1.0F - maxV);
            }
            case NORTH -> {
                p0 = origin.add(1.0F - minU, 1.0F - minV, -OFFSET);
                p1 = origin.add(1.0F - maxU, 1.0F - minV, -OFFSET);
                p2 = origin.add(1.0F - maxU, 1.0F - maxV, -OFFSET);
                p3 = origin.add(1.0F - minU, 1.0F - maxV, -OFFSET);
            }
            case SOUTH -> {
                p0 = origin.add(minU, 1.0F - minV, 1.0F + OFFSET);
                p1 = origin.add(maxU, 1.0F - minV, 1.0F + OFFSET);
                p2 = origin.add(maxU, 1.0F - maxV, 1.0F + OFFSET);
                p3 = origin.add(minU, 1.0F - maxV, 1.0F + OFFSET);
            }
            case WEST -> {
                p0 = origin.add(-OFFSET, 1.0F - minV, minU);
                p1 = origin.add(-OFFSET, 1.0F - minV, maxU);
                p2 = origin.add(-OFFSET, 1.0F - maxV, maxU);
                p3 = origin.add(-OFFSET, 1.0F - maxV, minU);
            }
            case EAST -> {
                p0 = origin.add(1.0F + OFFSET, 1.0F - minV, 1.0F - minU);
                p1 = origin.add(1.0F + OFFSET, 1.0F - minV, 1.0F - maxU);
                p2 = origin.add(1.0F + OFFSET, 1.0F - maxV, 1.0F - maxU);
                p3 = origin.add(1.0F + OFFSET, 1.0F - maxV, 1.0F - minU);
            }
            default -> {
                return;
            }
        }

        vertex(vertices, matrix, p0, r, g, b, a);
        vertex(vertices, matrix, p1, r, g, b, a);
        vertex(vertices, matrix, p2, r, g, b, a);
        vertex(vertices, matrix, p3, r, g, b, a);
    }

    private static void vertex(VertexConsumer vertices, Matrix4f matrix, Vec3d pos, float r, float g, float b, float a) {
        vertices.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z)
                .color((int) r, (int) g, (int) b, (int) a);
    }

    private record StrokeKey(BlockPos pos, Direction face, int x, int y, boolean clear) {
        private StrokeKey {
            pos = pos.toImmutable();
        }
    }
}
