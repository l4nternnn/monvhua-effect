package com.kuilunfuzhe.monvhua.features.paint;

import com.kuilunfuzhe.monvhua.mixin.SpriteContentsAccessor;
import com.kuilunfuzhe.monvhua.network.paint.PaintOverlayPackets;
import com.kuilunfuzhe.monvhua.item.paint.PaintBrushItem;
import com.kuilunfuzhe.monvhua.item.paint.PaintItems;
import com.kuilunfuzhe.monvhua.network.SafeClientNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWScrollCallbackI;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PaintOverlayClient {
    private static final double MAX_RENDER_DISTANCE_SQUARED = 96.0D * 96.0D;
    private static final float STEP = 1.0F / PaintOverlayStore.SIZE;
    private static final float OFFSET = 0.003F;
    private static final int MAX_RECENT_COLORS = 3;
    private static final int MAX_FRAME_PAINT_VERTICES = 180_000;
    private static final int DENSE_CHUNK_VERTEX_THRESHOLD = 24_000;
    private static final double EDITOR_RAY_DISTANCE = 64.0D;
    private static final int REPEATED_STROKE_SKIP_CALLS = 1;
    private static final double FULL_DETAIL_DISTANCE_SQUARED = 12.0D * 12.0D;
    private static final double MEDIUM_DETAIL_DISTANCE_SQUARED = 32.0D * 32.0D;
    private static final MeshData EMPTY_MESH = new MeshData(new float[0], new int[0], new float[0]);
    private static final Map<PaintOverlayStore.FaceKey, int[]> FACE_PIXELS = new HashMap<>();
    private static final Map<PaintOverlayStore.FaceKey, FaceMesh> FACE_MESHES = new HashMap<>();
    private static final Map<ChunkPos, ChunkMesh> CHUNK_MESHES = new HashMap<>();
    private static final Map<ChunkPos, Set<PaintOverlayStore.FaceKey>> CHUNK_FACE_KEYS = new HashMap<>();
    private static final List<Integer> RECENT_COLORS = new ArrayList<>();
    private static final List<Integer> FAVORITE_COLORS = new ArrayList<>();
    private static final Deque<PaintOverlayPackets.FaceData> EDITOR_UNDO = new ArrayDeque<>();
    private static GLFWScrollCallbackI previousScrollCallback;
    private static boolean scrollCallbackRegistered = false;
    private static int selectedRadius = 1;
    private static int selectedPaperSize = PaintOverlayFeature.DEFAULT_PAPER_SIZE;
    private static int selectedColor = 0xFFFF2A4F;
    private static boolean eraserFaceMode = false;
    private static boolean wasUsePressed = false;
    private static Object lastStrokeKey;
    private static int repeatedStrokeTicks;
    private static Boolean editorPreviousNoClip;
    private static Boolean editorPreviousNoGravity;
    private static int editorKeyCooldown;
    private static boolean editorViewPassthrough;
    private static PaintEditorScreen suspendedPaintEditorScreen;

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
            if (!world.isClient() || player == null
                    || world.getBlockState(hitResult.getBlockPos()).getBlock() != PaintItems.PAINT_BUCKET_BLOCK) {
                return ActionResult.PASS;
            }
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.currentScreen != null) {
                return ActionResult.SUCCESS;
            }
            if (player.isCreative()) {
                openPaintBucketColorScreen(client, hitResult.getBlockPos());
                return ActionResult.SUCCESS;
            }
            if (!isPaintBrush(player.getMainHandStack()) && !isPaintBrush(player.getOffHandStack())) {
                return ActionResult.PASS;
            }
            if (world.getBlockEntity(hitResult.getBlockPos()) instanceof PaintBucketBlockEntity bucket && bucket.isFilled()) {
                int color = 0xFF000000 | bucket.getColor();
                setSelectedColor(color);
                recordPickedColor(color);
            }
            SafeClientNetworking.send(new PaintOverlayPackets.LoadBrushFromBucketC2S(hitResult.getBlockPos()));
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
        if (client.player == null || (!client.player.isCreative() && !client.player.isSpectator())) {
            return false;
        }
        if (isHoldingPaintBrush(client)) {
            client.setScreen(new PaintEditorScreen());
            return true;
        }
        if (isHoldingPaintPaper(client)) {
            client.setScreen(new PaintPaperImportScreen());
            return true;
        }
        return false;
    }

    public static boolean isPaintEditorActive(MinecraftClient client) {
        return client != null && client.currentScreen instanceof PaintEditorScreen;
    }

    public static boolean shouldSuppressVanillaCrosshair(MinecraftClient client) {
        return (isPaintEditorActive(client) || suspendedPaintEditorScreen != null) && !editorViewPassthrough;
    }

    public static boolean isEditorViewPassthrough() {
        return editorViewPassthrough;
    }

    public static boolean isSuspendedPaintEditor(PaintEditorScreen screen) {
        return editorViewPassthrough && suspendedPaintEditorScreen == screen;
    }

    public static void beginEditorViewPassthrough(PaintEditorScreen screen) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || editorViewPassthrough) {
            return;
        }
        suspendedPaintEditorScreen = screen;
        editorViewPassthrough = true;
        client.mouse.lockCursor();
    }

    public static void endEditorViewPassthrough() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!editorViewPassthrough) {
            return;
        }
        PaintEditorScreen screen = suspendedPaintEditorScreen;
        editorViewPassthrough = false;
        suspendedPaintEditorScreen = null;
        if (client != null) {
            client.mouse.unlockCursor();
            if (client.player != null && client.currentScreen == null && screen != null) {
                client.setScreen(screen);
            }
        }
    }

    public static void markPaintEditorKeyConsumed() {
        editorKeyCooldown = 2;
    }

    public static boolean consumePaintEditorKeyCooldown() {
        if (editorKeyCooldown <= 0) {
            return false;
        }
        editorKeyCooldown--;
        return true;
    }

    public static void enterPaintEditor(MinecraftClient client) {
        if (client.player == null) {
            return;
        }
        if (editorPreviousNoClip == null) {
            editorPreviousNoClip = client.player.noClip;
        }
        if (editorPreviousNoGravity == null) {
            editorPreviousNoGravity = client.player.hasNoGravity();
        }
        client.player.noClip = true;
        client.player.setNoGravity(true);
        client.player.fallDistance = 0.0F;
    }

    public static void exitPaintEditor(MinecraftClient client) {
        if (editorViewPassthrough) {
            editorViewPassthrough = false;
            suspendedPaintEditorScreen = null;
        }
        if (client.player == null) {
            editorPreviousNoClip = null;
            editorPreviousNoGravity = null;
            lastStrokeKey = null;
            repeatedStrokeTicks = 0;
            return;
        }
        if (editorPreviousNoClip != null) {
            client.player.noClip = editorPreviousNoClip;
            editorPreviousNoClip = null;
        }
        if (editorPreviousNoGravity != null) {
            client.player.setNoGravity(editorPreviousNoGravity);
            editorPreviousNoGravity = null;
        }
        client.player.fallDistance = 0.0F;
        lastStrokeKey = null;
        repeatedStrokeTicks = 0;
    }

    public static void openPaintBucketColorScreen(MinecraftClient client, BlockPos pos) {
        if (client.player == null || !client.player.isCreative() || client.world == null || client.world.getBlockState(pos).getBlock() != PaintItems.PAINT_BUCKET_BLOCK) {
            return;
        }
        BlockEntity blockEntity = client.world.getBlockEntity(pos);
        if (blockEntity instanceof PaintBucketBlockEntity bucket && bucket.isFilled()) {
            setSelectedColor(0xFF000000 | bucket.getColor());
        }
        client.setScreen(new PaintBrushColorScreen(pos));
    }

    public static int selectedRadius() {
        return selectedRadius;
    }

    public static int selectedPaperSize() {
        return selectedPaperSize;
    }

    public static boolean eraserFaceMode() {
        return eraserFaceMode;
    }

    public static void setSelectedRadius(int radius) {
        int nextRadius = MathHelper.clamp(radius, PaintOverlayFeature.MIN_RADIUS, PaintOverlayFeature.MAX_RADIUS);
        if (nextRadius == selectedRadius) {
            return;
        }
        selectedRadius = nextRadius;
        syncSettings();
    }

    public static void setSelectedPaperSize(int size) {
        int nextSize = MathHelper.clamp(size, PaintOverlayFeature.MIN_PAPER_SIZE, PaintOverlayFeature.MAX_PAPER_SIZE);
        if (nextSize == selectedPaperSize) {
            return;
        }
        selectedPaperSize = nextSize;
        syncPaperSize();
    }

    public static void setEraserFaceMode(boolean faceMode) {
        eraserFaceMode = faceMode;
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
        if (client.world == null || CHUNK_MESHES.isEmpty()) {
            return;
        }

        Vec3d camera = context.camera().getPos();
        Frustum frustum = context.frustum();
        if (context.consumers() == null || context.matrixStack() == null) {
            return;
        }
        Matrix4f matrix = context.matrixStack().peek().getPositionMatrix();
        VertexConsumer vertices = context.consumers().getBuffer(PaintRenderLayers.paintOverlay());
        List<VisibleChunk> visibleChunks = new ArrayList<>();

        for (ChunkMesh mesh : CHUNK_MESHES.values()) {
            Box bounds = mesh.bounds();
            double distanceSquared = squaredDistanceToBox(camera, bounds);
            if (distanceSquared > MAX_RENDER_DISTANCE_SQUARED) {
                continue;
            }
            if (frustum != null && !frustum.isVisible(bounds)) {
                continue;
            }
            visibleChunks.add(new VisibleChunk(mesh, distanceSquared));
        }

        visibleChunks.sort(Comparator.comparingDouble(VisibleChunk::distanceSquared));
        int remainingVertices = MAX_FRAME_PAINT_VERTICES;
        for (VisibleChunk visible : visibleChunks) {
            ChunkMesh mesh = visible.mesh();
            MeshData meshData = selectRenderMesh(mesh, visible.distanceSquared(), remainingVertices);
            if (meshData.isEmpty()) {
                continue;
            }
            drawMesh(vertices, matrix, mesh.originX() - camera.x, -camera.y, mesh.originZ() - camera.z, meshData.positions(), meshData.colors(), meshData.normals());
            remainingVertices -= meshData.vertexCount();
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
        PaintModelOverlayClient.ModelHit modelHit = PaintModelOverlayClient.raycastCombinedBody(client);
        if (eraser && useStarted && shouldToggleEraserMode(client, modelHit)) {
            toggleEraserMode(client);
            lastStrokeKey = null;
            repeatedStrokeTicks = 0;
            return;
        }
        if (modelHit != null) {
            boolean clear = eraser && eraserFaceMode;
            ModelStrokeKey key = new ModelStrokeKey(modelHit.entityId(), modelHit.surface(), modelHit.face(), modelHit.x(), modelHit.y(), clear);
            repeatedStrokeTicks++;
            if (key.equals(lastStrokeKey) && repeatedStrokeTicks < REPEATED_STROKE_SKIP_CALLS) {
                return;
            }
            lastStrokeKey = key;
            repeatedStrokeTicks = 0;
            SafeClientNetworking.send(new PaintOverlayPackets.ModelPaintStrokeC2S(
                    modelHit.entityId(), modelHit.surface(), modelHit.face(), modelHit.x(), modelHit.y(), clear));
            client.player.sendMessage(Text.literal("model paint " + modelHit.surface() + " " + modelHit.face().asString()
                    + " (" + modelHit.x() + "," + modelHit.y() + ")"), true);
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
        if (key.equals(lastStrokeKey) && repeatedStrokeTicks < REPEATED_STROKE_SKIP_CALLS) {
            return;
        }
        lastStrokeKey = key;
        repeatedStrokeTicks = 0;
        SafeClientNetworking.send(new PaintOverlayPackets.PaintStrokeC2S(pos, face, pixel[0], pixel[1], clear));
    }

    public static boolean isBrushInputActive(MinecraftClient client) {
        return isPaintEditorActive(client);
    }

    public static void handleBrushPickInput(MinecraftClient client) {
        if (client == null || client.player == null) {
            return;
        }

        int color = pickModelColor(client);
        if (color == 0) {
            color = pickBlockColor(client);
        }
        if (color == 0) {
            color = pickBlockTextureColor(client);
        }
        if (color == 0) {
            return;
        }

        setSelectedColor(color);
        recordPickedColor(color);
        client.player.sendMessage(Text.literal("取色 " + toHex(color)), true);
    }

    public static void handleBrushPickInputAtScreenPoint(MinecraftClient client, double mouseX, double mouseY, int width, int height) {
        if (client == null || client.player == null) {
            return;
        }
        Ray ray = screenRay(client, mouseX, mouseY, width, height);
        if (ray == null) {
            return;
        }
        if (!client.player.isCreative()) {
            BlockHitResult hit = raycastBlock(client, ray);
            if (hit == null || hit.getType() != HitResult.Type.BLOCK || client.world == null
                    || client.world.getBlockState(hit.getBlockPos()).getBlock() != PaintItems.PAINT_BUCKET_BLOCK
                    || !(client.world.getBlockEntity(hit.getBlockPos()) instanceof PaintBucketBlockEntity bucket)
                    || !bucket.isFilled()) {
                client.player.sendMessage(Text.literal("Aim at a filled paint bucket"), true);
                return;
            }
            int color = 0xFF000000 | bucket.getColor();
            setSelectedColor(color);
            recordPickedColor(color);
            SafeClientNetworking.send(new PaintOverlayPackets.LoadBrushFromBucketC2S(hit.getBlockPos()));
            client.player.sendMessage(Text.literal("Load brush " + toHex(color)), true);
            return;
        }

        int color = pickModelColor(client, ray);
        BlockHitResult hit = null;
        if (color == 0) {
            hit = raycastBlock(client, ray);
            color = pickBlockColor(hit);
        }
        if (color == 0) {
            if (hit == null) {
                hit = raycastBlock(client, ray);
            }
            color = pickBlockTextureColor(client, hit);
        }
        if (color == 0) {
            return;
        }
        setSelectedColor(color);
        recordPickedColor(color);
        client.player.sendMessage(Text.literal("Pick " + toHex(color)), true);
    }

    public static void performEditorUse(MinecraftClient client, EditorTool tool) {
        performEditorUseAtScreenPoint(client, tool, -1.0D, -1.0D, 0, 0);
    }

    public static void performEditorUseAtScreenPoint(MinecraftClient client, EditorTool tool, double mouseX, double mouseY, int width, int height) {
        if (client == null || client.player == null || tool == null || tool == EditorTool.NONE) {
            lastStrokeKey = null;
            repeatedStrokeTicks = 0;
            return;
        }
        Ray ray = screenRay(client, mouseX, mouseY, width, height);
        PaintModelOverlayClient.ModelHit modelHit = ray == null
                ? PaintModelOverlayClient.raycastCombinedBody(client)
                : PaintModelOverlayClient.raycastCombinedBody(client, ray.start(), ray.end());
        if (modelHit != null && (tool == EditorTool.BRUSH || tool == EditorTool.ERASER)) {
            boolean clear = tool == EditorTool.ERASER && eraserFaceMode;
            ModelStrokeKey key = new ModelStrokeKey(modelHit.entityId(), modelHit.surface(), modelHit.face(), modelHit.x(), modelHit.y(), clear);
            repeatedStrokeTicks++;
            if (key.equals(lastStrokeKey) && repeatedStrokeTicks < REPEATED_STROKE_SKIP_CALLS) {
                return;
            }
            lastStrokeKey = key;
            repeatedStrokeTicks = 0;
            SafeClientNetworking.send(new PaintOverlayPackets.EditorModelPaintStrokeC2S(
                    modelHit.entityId(), modelHit.surface(), modelHit.face(), modelHit.x(), modelHit.y(), tool.networkId(), clear));
            return;
        }
        BlockHitResult hit = ray == null ? crosshairBlockHit(client) : raycastBlock(client, ray);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            lastStrokeKey = null;
            repeatedStrokeTicks = 0;
            return;
        }

        BlockPos pos = hit.getBlockPos();
        Direction face = hit.getSide();
        if (tool == EditorTool.PAPER) {
            SafeClientNetworking.send(new PaintOverlayPackets.EditorPaperUseC2S(pos, face, client.player.isSneaking()));
            lastStrokeKey = null;
            repeatedStrokeTicks = 0;
            return;
        }

        int[] pixel = PaintBrushItem.getPixel(hit.getPos(), pos, face);
        boolean clear = tool == EditorTool.ERASER && eraserFaceMode;
        StrokeKey key = new StrokeKey(pos, face, pixel[0], pixel[1], clear);
        repeatedStrokeTicks++;
        if (key.equals(lastStrokeKey) && repeatedStrokeTicks < REPEATED_STROKE_SKIP_CALLS) {
            return;
        }
        lastStrokeKey = key;
        repeatedStrokeTicks = 0;
        pushUndo(pos, face);
        SafeClientNetworking.send(new PaintOverlayPackets.EditorPaintStrokeC2S(pos, face, pixel[0], pixel[1], tool.networkId(), clear));
    }

    private static BlockHitResult crosshairBlockHit(MinecraftClient client) {
        return client.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    private static BlockHitResult raycastBlock(MinecraftClient client, Ray ray) {
        if (client.world == null || client.player == null || ray == null) {
            return null;
        }
        HitResult hit = client.world.raycast(new RaycastContext(
                ray.start(),
                ray.end(),
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                client.player
        ));
        return hit instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK ? blockHit : null;
    }

    public static void stopEditorUse() {
        lastStrokeKey = null;
        repeatedStrokeTicks = 0;
    }

    public static void undoEditorStroke(MinecraftClient client) {
        if (client == null || client.player == null || EDITOR_UNDO.isEmpty()) {
            return;
        }
        SafeClientNetworking.send(new PaintOverlayPackets.RestoreFaceC2S(EDITOR_UNDO.pop()));
    }

    public static void moveEditorView(MinecraftClient client, double amount) {
        if (client == null || client.player == null) {
            return;
        }
        setEditorViewVelocity(client, amount, 0.0D);
    }

    public static void setEditorViewVelocity(MinecraftClient client, double forwardAmount, double strafeAmount) {
        setEditorViewVelocity(client, forwardAmount, strafeAmount, 0.0D);
    }

    public static void setEditorViewVelocity(MinecraftClient client, double forwardAmount, double strafeAmount, double verticalAmount) {
        if (client == null || client.player == null) {
            return;
        }
        Vec3d look = client.player.getRotationVec(1.0F).normalize();
        Vec3d up = new Vec3d(0.0D, 1.0D, 0.0D);
        Vec3d right = look.crossProduct(up);
        if (right.lengthSquared() < 1.0E-4D) {
            right = new Vec3d(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        Vec3d velocity = look.multiply(forwardAmount)
                .add(right.multiply(strafeAmount))
                .add(0.0D, verticalAmount, 0.0D);
        client.player.setVelocity(velocity);
        client.player.fallDistance = 0.0F;
    }

    public static void moveEditorViewTowardScreenPoint(MinecraftClient client, double mouseX, double mouseY, int width, int height, double amount) {
        if (client == null || client.player == null || width <= 0 || height <= 0) {
            return;
        }
        Vec3d direction = screenDirection(client, mouseX, mouseY, width, height);
        if (direction == null) {
            return;
        }
        client.player.updatePosition(
                client.player.getX() + direction.x * amount,
                client.player.getY() + direction.y * amount,
                client.player.getZ() + direction.z * amount
        );
        client.player.setVelocity(Vec3d.ZERO);
        client.player.fallDistance = 0.0F;
    }

    private static Ray screenRay(MinecraftClient client, double mouseX, double mouseY, int width, int height) {
        if (client == null || client.player == null || width <= 0 || height <= 0 || mouseX < 0.0D || mouseY < 0.0D) {
            return null;
        }
        Vec3d direction = screenDirection(client, mouseX, mouseY, width, height);
        if (direction == null) {
            return null;
        }
        Vec3d start = client.gameRenderer.getCamera().getPos();
        return new Ray(start, start.add(direction.multiply(EDITOR_RAY_DISTANCE)));
    }

    private static Vec3d screenDirection(MinecraftClient client, double mouseX, double mouseY, int width, int height) {
        if (client == null || client.player == null || width <= 0 || height <= 0) {
            return null;
        }
        Camera camera = client.gameRenderer.getCamera();
        float factorX = (float) MathHelper.clamp((mouseX / width - 0.5D) * 2.0D, -1.0D, 1.0D);
        float factorY = (float) MathHelper.clamp((0.5D - mouseY / height) * 2.0D, -1.0D, 1.0D);
        return camera.getProjection().getPosition(factorX, factorY).normalize();
    }

    public static void panEditorView(MinecraftClient client, double deltaX, double deltaY) {
        if (client == null || client.player == null) {
            return;
        }
        Vec3d look = client.player.getRotationVec(1.0F).normalize();
        Vec3d up = new Vec3d(0.0D, 1.0D, 0.0D);
        Vec3d right = look.crossProduct(up);
        if (right.lengthSquared() < 1.0E-4D) {
            right = new Vec3d(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        double scale = 0.018D;
        Vec3d offset = right.multiply(deltaX * scale).add(up.multiply(-deltaY * scale));
        client.player.updatePosition(client.player.getX() + offset.x, client.player.getY() + offset.y, client.player.getZ() + offset.z);
        client.player.setVelocity(Vec3d.ZERO);
        client.player.fallDistance = 0.0F;
    }

    public static ScreenPanAnchor createEditorPanAnchor(MinecraftClient client, double mouseX, double mouseY, int width, int height) {
        Ray ray = screenRay(client, mouseX, mouseY, width, height);
        if (ray == null) {
            return null;
        }
        BlockHitResult hit = raycastBlock(client, ray);
        Vec3d point = hit != null && hit.getType() == HitResult.Type.BLOCK
                ? hit.getPos()
                : ray.start().add(ray.direction().multiply(8.0D));
        return new ScreenPanAnchor(point, Math.max(0.25D, point.distanceTo(ray.start())));
    }

    public static void panEditorViewToScreenPoint(MinecraftClient client, ScreenPanAnchor anchor, double mouseX, double mouseY, int width, int height) {
        if (client == null || client.player == null || anchor == null) {
            return;
        }
        Vec3d direction = screenDirection(client, mouseX, mouseY, width, height);
        if (direction == null) {
            return;
        }
        Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
        Vec3d targetCameraPos = anchor.point().subtract(direction.multiply(anchor.distance()));
        Vec3d delta = targetCameraPos.subtract(cameraPos);
        client.player.updatePosition(client.player.getX() + delta.x, client.player.getY() + delta.y, client.player.getZ() + delta.z);
        client.player.setVelocity(Vec3d.ZERO);
        client.player.fallDistance = 0.0F;
    }

    public static void rotateEditorView(MinecraftClient client, double deltaX, double deltaY) {
        rotateEditorView(client, deltaX, deltaY, 0.18F);
    }

    public static void rotateEditorView(MinecraftClient client, double deltaX, double deltaY, float sensitivity) {
        if (client == null || client.player == null) {
            return;
        }
        client.player.setYaw(client.player.getYaw() + (float) deltaX * sensitivity);
        client.player.setPitch(MathHelper.clamp(client.player.getPitch() + (float) deltaY * sensitivity, -89.9F, 89.9F));
    }

    private static void pushUndo(BlockPos pos, Direction face) {
        int[] pixels = FACE_PIXELS.get(new PaintOverlayStore.FaceKey(pos, face));
        int[] snapshot = new int[PaintOverlayStore.FACE_PIXELS];
        if (pixels != null) {
            System.arraycopy(pixels, 0, snapshot, 0, Math.min(pixels.length, snapshot.length));
        }
        EDITOR_UNDO.push(new PaintOverlayPackets.FaceData(pos, face, snapshot));
        while (EDITOR_UNDO.size() > 32) {
            EDITOR_UNDO.removeLast();
        }
    }

    private static int pickBlockTextureColor(MinecraftClient client) {
        if (client.world == null || !(client.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return 0;
        }
        return pickBlockTextureColor(client, hit);
    }

    private static int pickBlockTextureColor(MinecraftClient client, BlockHitResult hit) {
        if (client.world == null || hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return 0;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = client.world.getBlockState(pos);
        Vec3d localHit = hit.getPos().subtract(pos.getX(), pos.getY(), pos.getZ());
        Random random = Random.create(state.getRenderingSeed(pos));
        TextureSample best = null;

        for (BlockModelPart part : client.getBlockRenderManager().getModel(state).getParts(random)) {
            best = betterSample(best, findTextureSample(part.getQuads(hit.getSide()), localHit));
            best = betterSample(best, findTextureSample(part.getQuads(null), localHit));
        }

        if (best == null) {
            Sprite sprite = client.getBlockRenderManager().getModel(state).particleSprite();
            return sampleSpriteColor(client, state, pos, sprite, 0.5F, 0.5F, -1);
        }
        return sampleSpriteColor(client, state, pos, best.quad().sprite(), best.u(), best.v(), best.quad().tintIndex());
    }

    private static TextureSample betterSample(TextureSample current, TextureSample candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null || candidate.distanceSquared() < current.distanceSquared()) {
            return candidate;
        }
        return current;
    }

    private static TextureSample findTextureSample(List<BakedQuad> quads, Vec3d localHit) {
        TextureSample best = null;
        for (BakedQuad quad : quads) {
            best = betterSample(best, sampleQuad(quad, localHit));
        }
        return best;
    }

    private static TextureSample sampleQuad(BakedQuad quad, Vec3d point) {
        int vertices = quad.vertexData().length / 8;
        if (vertices < 4) {
            return null;
        }
        QuadVertex v0 = quadVertex(quad, 0);
        QuadVertex v1 = quadVertex(quad, 1);
        QuadVertex v2 = quadVertex(quad, 2);
        QuadVertex v3 = quadVertex(quad, 3);
        TextureSample first = sampleTriangle(quad, point, v0, v1, v2);
        TextureSample second = sampleTriangle(quad, point, v0, v2, v3);
        return betterSample(first, second);
    }

    private static TextureSample sampleTriangle(BakedQuad quad, Vec3d point, QuadVertex a, QuadVertex b, QuadVertex c) {
        Vector3d ab = new Vector3d(b.position()).sub(a.position());
        Vector3d ac = new Vector3d(c.position()).sub(a.position());
        Vector3d ap = new Vector3d(point.x, point.y, point.z).sub(a.position());
        double d00 = ab.dot(ab);
        double d01 = ab.dot(ac);
        double d11 = ac.dot(ac);
        double d20 = ap.dot(ab);
        double d21 = ap.dot(ac);
        double denominator = d00 * d11 - d01 * d01;
        if (Math.abs(denominator) < 1.0E-8D) {
            return null;
        }

        double bWeight = (d11 * d20 - d01 * d21) / denominator;
        double cWeight = (d00 * d21 - d01 * d20) / denominator;
        double aWeight = 1.0D - bWeight - cWeight;
        double tolerance = 1.0E-4D;
        if (aWeight < -tolerance || bWeight < -tolerance || cWeight < -tolerance) {
            return null;
        }

        float u = (float) (a.u() * aWeight + b.u() * bWeight + c.u() * cWeight);
        float v = (float) (a.v() * aWeight + b.v() * bWeight + c.v() * cWeight);
        Vector3d closest = new Vector3d(a.position()).mul(aWeight)
                .add(new Vector3d(b.position()).mul(bWeight))
                .add(new Vector3d(c.position()).mul(cWeight));
        double distanceSquared = closest.distanceSquared(point.x, point.y, point.z);
        return new TextureSample(quad, u, v, distanceSquared);
    }

    private static QuadVertex quadVertex(BakedQuad quad, int vertex) {
        int base = vertex * 8;
        int[] data = quad.vertexData();
        Vector3d position = new Vector3d(
                Float.intBitsToFloat(data[base]),
                Float.intBitsToFloat(data[base + 1]),
                Float.intBitsToFloat(data[base + 2])
        );
        float u = Float.intBitsToFloat(data[base + 4]);
        float v = Float.intBitsToFloat(data[base + 5]);
        return new QuadVertex(position, u, v);
    }

    private static int sampleSpriteColor(MinecraftClient client, BlockState state, BlockPos pos, Sprite sprite, float u, float v, int tintIndex) {
        if (sprite == null) {
            return 0;
        }
        NativeImage image = ((SpriteContentsAccessor) sprite.getContents()).monvhua$getImage();
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            return 0;
        }

        int x = MathHelper.clamp((int) sprite.getFrameFromU(u), 0, image.getWidth() - 1);
        int y = MathHelper.clamp((int) sprite.getFrameFromV(v), 0, image.getHeight() - 1);
        int color = image.getColorArgb(x, y);
        if (((color >>> 24) & 0xFF) == 0) {
            return 0;
        }
        if (tintIndex >= 0 && client.world != null) {
            color = multiplyRgb(color, client.getBlockColors().getColor(state, client.world, pos, tintIndex));
        }
        return ensureAlpha(color);
    }

    private static int multiplyRgb(int color, int tint) {
        int alpha = color & 0xFF000000;
        int red = ((color >>> 16) & 0xFF) * ((tint >>> 16) & 0xFF) / 255;
        int green = ((color >>> 8) & 0xFF) * ((tint >>> 8) & 0xFF) / 255;
        int blue = (color & 0xFF) * (tint & 0xFF) / 255;
        return alpha | (red << 16) | (green << 8) | blue;
    }

    public static void consumeBrushColorSlotKeys(MinecraftClient client) {
        if (!isBrushInputActive(client)) {
            return;
        }
        for (int slot = 0; slot < 3 && slot < client.options.hotbarKeys.length; slot++) {
            while (client.options.hotbarKeys[slot].wasPressed()) {
            }
        }
    }

    private static void selectRecentColorSlot(MinecraftClient client, int slot) {
        if (slot < 0 || slot >= RECENT_COLORS.size()) {
            if (client.player != null) {
                client.player.sendMessage(Text.literal("颜色槽 " + (slot + 1) + " 为空"), true);
            }
            return;
        }
        int color = RECENT_COLORS.get(slot);
        setSelectedColor(color);
        if (client.player != null) {
            client.player.sendMessage(Text.literal("颜色槽 " + (slot + 1) + " " + toHex(color)), true);
        }
    }

    private static int pickModelColor(MinecraftClient client) {
        PaintModelOverlayClient.ModelHit modelHit = PaintModelOverlayClient.raycastCombinedBody(client);
        return pickModelColor(client, modelHit);
    }

    private static int pickModelColor(MinecraftClient client, Ray ray) {
        PaintModelOverlayClient.ModelHit modelHit = ray == null ? null : PaintModelOverlayClient.raycastCombinedBody(client, ray.start(), ray.end());
        return pickModelColor(client, modelHit);
    }

    private static int pickModelColor(MinecraftClient client, PaintModelOverlayClient.ModelHit modelHit) {
        if (modelHit == null) {
            return 0;
        }
        int color = PaintModelOverlayClient.colorAt(client, modelHit);
        return color == 0 ? 0 : ensureAlpha(color);
    }

    private static int pickBlockColor(MinecraftClient client) {
        if (!(client.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return 0;
        }
        return pickBlockColor(hit);
    }

    private static int pickBlockColor(BlockHitResult hit) {
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return 0;
        }
        int[] pixel = PaintBrushItem.getPixel(hit.getPos(), hit.getBlockPos(), hit.getSide());
        int[] pixels = FACE_PIXELS.get(new PaintOverlayStore.FaceKey(hit.getBlockPos(), hit.getSide()));
        if (pixels == null) {
            return 0;
        }
        int x = pixel[0];
        int y = pixel[1];
        if (x < 0 || y < 0 || x >= PaintOverlayStore.SIZE || y >= PaintOverlayStore.SIZE) {
            return 0;
        }
        int color = pixels[y * PaintOverlayStore.SIZE + x];
        return color == 0 ? 0 : ensureAlpha(color);
    }

    private static boolean shouldToggleEraserMode(MinecraftClient client, PaintModelOverlayClient.ModelHit modelHit) {
        if (client.player == null) {
            return false;
        }
        if (client.player.isSneaking()) {
            return true;
        }
        if (modelHit != null) {
            return false;
        }
        return !(client.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK;
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
        List<Integer> recent = recentColors();
        for (int i = 0; i < 3; i++) {
            int slotX = x + i * 20;
            context.fill(slotX, slotY, slotX + 18, slotY + 18, 0x66101015);
            if (!paper && !eraser && i < recent.size()) {
                context.fill(slotX + 2, slotY + 2, slotX + 16, slotY + 16, recent.get(i));
            }
            context.fill(slotX, slotY, slotX + 18, slotY + 1, 0x99FFFFFF);
            context.fill(slotX, slotY + 17, slotX + 18, slotY + 18, 0x99000000);
            context.fill(slotX, slotY, slotX + 1, slotY + 18, 0x99FFFFFF);
            context.fill(slotX + 17, slotY, slotX + 18, slotY + 18, 0x99000000);
        }
        renderBrushCharge(context, client);
    }

    private static void renderBrushCharge(DrawContext context, MinecraftClient client) {
        if (client.player == null || client.player.isCreative()) {
            return;
        }
        ItemStack brush = paintBrushStack(client);
        if (brush.isEmpty()) {
            return;
        }
        int remaining = PaintBrushItem.getRemainingPaint(brush);
        int hotbarLeft = context.getScaledWindowWidth() / 2 - 91;
        int slot;
        int x;
        if (isPaintBrush(client.player.getMainHandStack())) {
            slot = client.player.getInventory().getSelectedSlot();
            x = hotbarLeft + slot * 20 + 1;
        } else {
            x = hotbarLeft - 28;
        }
        int y = context.getScaledWindowHeight() - 34;
        int width = 18;
        int filled = MathHelper.clamp((int) Math.round(width * (remaining / (double) PaintBrushItem.MAX_PAINT_PIXELS)), 0, width);
        int color = remaining > 0 ? 0xFF58D66D : 0xFF7A3030;
        context.fill(x - 1, y - 1, x + width + 1, y + 5, 0xAA101015);
        context.fill(x, y, x + width, y + 4, 0xFF2A2A30);
        context.fill(x, y, x + filled, y + 4, color);
        context.drawText(client.textRenderer, Text.literal(String.valueOf(remaining)), x + 2, y - 10, 0xFFE6E6E6, false);
    }

    private static boolean isHoldingPaintBrush(MinecraftClient client) {
        if (client.player == null) {
            return false;
        }
        return isPaintBrush(client.player.getMainHandStack()) || isPaintBrush(client.player.getOffHandStack());
    }

    private static ItemStack paintBrushStack(MinecraftClient client) {
        if (client.player == null) {
            return ItemStack.EMPTY;
        }
        if (isPaintBrush(client.player.getMainHandStack())) {
            return client.player.getMainHandStack();
        }
        if (isPaintBrush(client.player.getOffHandStack())) {
            return client.player.getOffHandStack();
        }
        return ItemStack.EMPTY;
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
        return (color >>> 24) == 0 ? color | 0xFF000000 : color;
    }

    private static String toHex(int color) {
        return String.format("#%06X", color & 0xFFFFFF);
    }

    private static void applyFullSync(List<PaintOverlayPackets.FaceData> faces) {
        FACE_PIXELS.clear();
        FACE_MESHES.clear();
        CHUNK_MESHES.clear();
        CHUNK_FACE_KEYS.clear();
        Set<ChunkPos> dirtyChunks = new HashSet<>();
        for (PaintOverlayPackets.FaceData face : faces) {
            if (!hasPixels(face.pixels())) {
                continue;
            }
            PaintOverlayStore.FaceKey key = new PaintOverlayStore.FaceKey(face.pos(), face.face());
            ChunkPos chunkPos = chunkPos(key.pos());
            FACE_PIXELS.put(key, copyPixels(face.pixels()));
            putFaceMesh(key, buildMesh(key, face.pixels()), chunkPos);
            dirtyChunks.add(chunkPos);
        }
        for (ChunkPos chunkPos : dirtyChunks) {
            rebuildChunkMesh(chunkPos);
        }
    }

    private static void applyFace(PaintOverlayPackets.FaceData face) {
        PaintOverlayStore.FaceKey key = new PaintOverlayStore.FaceKey(face.pos(), face.face());
        ChunkPos chunkPos = chunkPos(key.pos());
        if (!hasPixels(face.pixels())) {
            FACE_PIXELS.remove(key);
            removeFaceMesh(key, chunkPos);
            rebuildChunkMesh(chunkPos);
            return;
        }
        FACE_PIXELS.put(key, copyPixels(face.pixels()));
        putFaceMesh(key, buildMesh(key, face.pixels()), chunkPos);
        rebuildChunkMesh(chunkPos);
    }

    private static int[] copyPixels(int[] source) {
        int[] pixels = new int[PaintOverlayStore.FACE_PIXELS];
        System.arraycopy(source, 0, pixels, 0, Math.min(source.length, pixels.length));
        return pixels;
    }

    private static boolean hasPixels(int[] pixels) {
        for (int pixel : pixels) {
            if (pixel != 0) {
                return true;
            }
        }
        return false;
    }

    private static FaceMesh buildMesh(PaintOverlayStore.FaceKey key, int[] pixels) {
        return new FaceMesh(
                key.pos(),
                new Box(key.pos()).expand(0.01D),
                buildFaceMesh(key.face(), pixels, 1),
                buildFaceMesh(key.face(), pixels, 2),
                buildFaceMesh(key.face(), pixels, 4)
        );
    }

    private static MeshData buildFaceMesh(Direction face, int[] pixels, int cellSize) {
        int gridSize = PaintOverlayStore.SIZE / cellSize;
        int[] colors = new int[gridSize * gridSize];
        for (int gridY = 0; gridY < gridSize; gridY++) {
            for (int gridX = 0; gridX < gridSize; gridX++) {
                colors[gridY * gridSize + gridX] = cellColor(pixels, gridX * cellSize, gridY * cellSize, cellSize);
            }
        }

        MeshBuilder builder = new MeshBuilder();
        boolean[] used = new boolean[gridSize * gridSize];
        for (int y = 0; y < gridSize; y++) {
            for (int x = 0; x < gridSize; x++) {
                int index = y * gridSize + x;
                int color = colors[index];
                if (color == 0 || used[index]) {
                    continue;
                }
                int width = 1;
                while (x + width < gridSize) {
                    int nextIndex = y * gridSize + x + width;
                    if (used[nextIndex] || colors[nextIndex] != color) {
                        break;
                    }
                    width++;
                }

                int height = 1;
                boolean canGrow = true;
                while (y + height < gridSize && canGrow) {
                    for (int rx = 0; rx < width; rx++) {
                        int nextIndex = (y + height) * gridSize + x + rx;
                        if (used[nextIndex] || colors[nextIndex] != color) {
                            canGrow = false;
                            break;
                        }
                    }
                    if (canGrow) {
                        height++;
                    }
                }

                for (int ry = 0; ry < height; ry++) {
                    for (int rx = 0; rx < width; rx++) {
                        used[(y + ry) * gridSize + x + rx] = true;
                    }
                }
                appendRect(builder, face, x * cellSize, y * cellSize, (x + width) * cellSize, (y + height) * cellSize, color);
            }
        }
        return new MeshData(builder.positions(), builder.colors(), builder.normals());
    }

    private static int cellColor(int[] pixels, int startX, int startY, int cellSize) {
        int alphaTotal = 0;
        int redTotal = 0;
        int greenTotal = 0;
        int blueTotal = 0;
        int filled = 0;
        int total = cellSize * cellSize;
        for (int y = 0; y < cellSize; y++) {
            for (int x = 0; x < cellSize; x++) {
                int color = pixels[(startY + y) * PaintOverlayStore.SIZE + startX + x];
                int alpha = (color >>> 24) & 0xFF;
                if (alpha == 0) {
                    continue;
                }
                alphaTotal += alpha;
                redTotal += (color >>> 16) & 0xFF;
                greenTotal += (color >>> 8) & 0xFF;
                blueTotal += color & 0xFF;
                filled++;
            }
        }
        if (filled == 0) {
            return 0;
        }
        if (cellSize > 1 && filled * 4 < total) {
            return 0;
        }
        int alpha = MathHelper.clamp((alphaTotal / filled) * filled / total, 64, 255);
        int red = quantizeLodColor(redTotal / filled, cellSize);
        int green = quantizeLodColor(greenTotal / filled, cellSize);
        int blue = quantizeLodColor(blueTotal / filled, cellSize);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int quantizeLodColor(int channel, int cellSize) {
        int step = cellSize <= 1 ? 1 : cellSize == 2 ? 16 : 32;
        return Math.min(255, ((channel + step / 2) / step) * step);
    }

    private static void appendRect(MeshBuilder builder, Direction face, int x0, int y0, int x1, int y1, int color) {
        float minU = x0 * STEP;
        float maxU = x1 * STEP;
        float minV = y0 * STEP;
        float maxV = y1 * STEP;
        float normalX = face.getOffsetX();
        float normalY = face.getOffsetY();
        float normalZ = face.getOffsetZ();
        switch (face) {
            case UP -> {
                builder.vertex(minU, 1.0F + OFFSET, minV, color, normalX, normalY, normalZ);
                builder.vertex(maxU, 1.0F + OFFSET, minV, color, normalX, normalY, normalZ);
                builder.vertex(maxU, 1.0F + OFFSET, maxV, color, normalX, normalY, normalZ);
                builder.vertex(minU, 1.0F + OFFSET, maxV, color, normalX, normalY, normalZ);
            }
            case DOWN -> {
                builder.vertex(minU, -OFFSET, 1.0F - minV, color, normalX, normalY, normalZ);
                builder.vertex(maxU, -OFFSET, 1.0F - minV, color, normalX, normalY, normalZ);
                builder.vertex(maxU, -OFFSET, 1.0F - maxV, color, normalX, normalY, normalZ);
                builder.vertex(minU, -OFFSET, 1.0F - maxV, color, normalX, normalY, normalZ);
            }
            case NORTH -> {
                builder.vertex(1.0F - minU, 1.0F - minV, -OFFSET, color, normalX, normalY, normalZ);
                builder.vertex(1.0F - maxU, 1.0F - minV, -OFFSET, color, normalX, normalY, normalZ);
                builder.vertex(1.0F - maxU, 1.0F - maxV, -OFFSET, color, normalX, normalY, normalZ);
                builder.vertex(1.0F - minU, 1.0F - maxV, -OFFSET, color, normalX, normalY, normalZ);
            }
            case SOUTH -> {
                builder.vertex(minU, 1.0F - minV, 1.0F + OFFSET, color, normalX, normalY, normalZ);
                builder.vertex(maxU, 1.0F - minV, 1.0F + OFFSET, color, normalX, normalY, normalZ);
                builder.vertex(maxU, 1.0F - maxV, 1.0F + OFFSET, color, normalX, normalY, normalZ);
                builder.vertex(minU, 1.0F - maxV, 1.0F + OFFSET, color, normalX, normalY, normalZ);
            }
            case WEST -> {
                builder.vertex(-OFFSET, 1.0F - minV, minU, color, normalX, normalY, normalZ);
                builder.vertex(-OFFSET, 1.0F - minV, maxU, color, normalX, normalY, normalZ);
                builder.vertex(-OFFSET, 1.0F - maxV, maxU, color, normalX, normalY, normalZ);
                builder.vertex(-OFFSET, 1.0F - maxV, minU, color, normalX, normalY, normalZ);
            }
            case EAST -> {
                builder.vertex(1.0F + OFFSET, 1.0F - minV, 1.0F - minU, color, normalX, normalY, normalZ);
                builder.vertex(1.0F + OFFSET, 1.0F - minV, 1.0F - maxU, color, normalX, normalY, normalZ);
                builder.vertex(1.0F + OFFSET, 1.0F - maxV, 1.0F - maxU, color, normalX, normalY, normalZ);
                builder.vertex(1.0F + OFFSET, 1.0F - maxV, 1.0F - minU, color, normalX, normalY, normalZ);
            }
            default -> {
            }
        }
    }

    private static ChunkPos chunkPos(BlockPos pos) {
        return new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static void putFaceMesh(PaintOverlayStore.FaceKey key, FaceMesh mesh, ChunkPos chunkPos) {
        FACE_MESHES.put(key, mesh);
        CHUNK_FACE_KEYS.computeIfAbsent(chunkPos, ignored -> new HashSet<>()).add(key);
    }

    private static void removeFaceMesh(PaintOverlayStore.FaceKey key, ChunkPos chunkPos) {
        FACE_MESHES.remove(key);
        Set<PaintOverlayStore.FaceKey> keys = CHUNK_FACE_KEYS.get(chunkPos);
        if (keys == null) {
            return;
        }
        keys.remove(key);
        if (keys.isEmpty()) {
            CHUNK_FACE_KEYS.remove(chunkPos);
        }
    }

    private static void rebuildChunkMesh(ChunkPos chunkPos) {
        Set<PaintOverlayStore.FaceKey> keys = CHUNK_FACE_KEYS.get(chunkPos);
        if (keys == null || keys.isEmpty()) {
            CHUNK_MESHES.remove(chunkPos);
            return;
        }

        Box bounds = null;
        int fullVertexCount = 0;
        int mediumVertexCount = 0;
        int farVertexCount = 0;
        for (PaintOverlayStore.FaceKey key : keys) {
            FaceMesh mesh = FACE_MESHES.get(key);
            if (mesh == null) {
                continue;
            }
            bounds = bounds == null ? mesh.bounds() : union(bounds, mesh.bounds());
            fullVertexCount += mesh.full().vertexCount();
            mediumVertexCount += mesh.medium().vertexCount();
            farVertexCount += mesh.far().vertexCount();
        }
        if (fullVertexCount == 0 || bounds == null) {
            CHUNK_MESHES.remove(chunkPos);
            return;
        }

        int originX = chunkPos.x << 4;
        int originZ = chunkPos.z << 4;
        CHUNK_MESHES.put(chunkPos, new ChunkMesh(
                originX,
                originZ,
                bounds,
                buildChunkMesh(keys, originX, originZ, fullVertexCount, FaceMesh::full),
                buildChunkMesh(keys, originX, originZ, mediumVertexCount, FaceMesh::medium),
                buildChunkMesh(keys, originX, originZ, farVertexCount, FaceMesh::far)
        ));
    }

    private static MeshData buildChunkMesh(Set<PaintOverlayStore.FaceKey> keys, int originX, int originZ, int vertexCount,
                                           java.util.function.Function<FaceMesh, MeshData> meshSelector) {
        if (vertexCount == 0) {
            return EMPTY_MESH;
        }
        float[] positions = new float[vertexCount * 3];
        int[] colors = new int[vertexCount];
        float[] normals = new float[vertexCount * 3];
        int vertexIndex = 0;
        for (PaintOverlayStore.FaceKey key : keys) {
            FaceMesh mesh = FACE_MESHES.get(key);
            if (mesh == null) {
                continue;
            }
            MeshData faceMesh = meshSelector.apply(mesh);
            float[] facePositions = faceMesh.positions();
            int[] faceColors = faceMesh.colors();
            float[] faceNormals = faceMesh.normals();
            float offsetX = mesh.pos().getX() - originX;
            float offsetY = mesh.pos().getY();
            float offsetZ = mesh.pos().getZ() - originZ;
            for (int i = 0; i < faceColors.length; i++) {
                int sourcePositionIndex = i * 3;
                int targetPositionIndex = vertexIndex * 3;
                positions[targetPositionIndex] = offsetX + facePositions[sourcePositionIndex];
                positions[targetPositionIndex + 1] = offsetY + facePositions[sourcePositionIndex + 1];
                positions[targetPositionIndex + 2] = offsetZ + facePositions[sourcePositionIndex + 2];
                normals[targetPositionIndex] = faceNormals[sourcePositionIndex];
                normals[targetPositionIndex + 1] = faceNormals[sourcePositionIndex + 1];
                normals[targetPositionIndex + 2] = faceNormals[sourcePositionIndex + 2];
                colors[vertexIndex] = faceColors[i];
                vertexIndex++;
            }
        }
        return new MeshData(positions, colors, normals);
    }

    private static MeshData selectRenderMesh(ChunkMesh mesh, double distanceSquared, int remainingVertices) {
        boolean dense = mesh.full().vertexCount() > DENSE_CHUNK_VERTEX_THRESHOLD;
        if (!dense && distanceSquared <= MEDIUM_DETAIL_DISTANCE_SQUARED && mesh.full().vertexCount() <= remainingVertices) {
            return mesh.full();
        }
        if (!dense && mesh.medium().vertexCount() <= remainingVertices) {
            return mesh.medium().isEmpty() ? mesh.full() : mesh.medium();
        }
        if (distanceSquared <= FULL_DETAIL_DISTANCE_SQUARED && mesh.full().vertexCount() <= remainingVertices) {
            return mesh.full();
        }
        if (distanceSquared <= MEDIUM_DETAIL_DISTANCE_SQUARED && !mesh.medium().isEmpty()
                && mesh.medium().vertexCount() <= remainingVertices) {
            return mesh.medium();
        }
        if (!mesh.far().isEmpty() && mesh.far().vertexCount() <= remainingVertices) {
            return mesh.far();
        }
        if (remainingVertices > 0 && !mesh.far().isEmpty() && mesh.far().vertexCount() <= DENSE_CHUNK_VERTEX_THRESHOLD) {
            return mesh.far();
        }
        return EMPTY_MESH;
    }

    private static double squaredDistanceToBox(Vec3d point, Box box) {
        double dx = point.x < box.minX ? box.minX - point.x : Math.max(point.x - box.maxX, 0.0D);
        double dy = point.y < box.minY ? box.minY - point.y : Math.max(point.y - box.maxY, 0.0D);
        double dz = point.z < box.minZ ? box.minZ - point.z : Math.max(point.z - box.maxZ, 0.0D);
        return dx * dx + dy * dy + dz * dz;
    }

    private static Box union(Box a, Box b) {
        return new Box(
                Math.min(a.minX, b.minX),
                Math.min(a.minY, b.minY),
                Math.min(a.minZ, b.minZ),
                Math.max(a.maxX, b.maxX),
                Math.max(a.maxY, b.maxY),
                Math.max(a.maxZ, b.maxZ)
        );
    }

    private static void drawMesh(VertexConsumer vertices, Matrix4f matrix, double originX, double originY, double originZ,
                                 float[] positions, int[] colors, float[] normals) {
        for (int i = 0; i < colors.length; i++) {
            int color = colors[i];
            int positionIndex = i * 3;
            vertices.vertex(matrix,
                            (float) (originX + positions[positionIndex]),
                            (float) (originY + positions[positionIndex + 1]),
                            (float) (originZ + positions[positionIndex + 2]))
                    .color((color >>> 16) & 0xFF, (color >>> 8) & 0xFF, color & 0xFF, renderAlpha(color))
                    .texture(0.0F, 0.0F)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                    .normal(normals[positionIndex], normals[positionIndex + 1], normals[positionIndex + 2]);
        }
    }

    private static int renderAlpha(int color) {
        int alpha = (color >>> 24) & 0xFF;
        return alpha == 0 ? 0 : Math.max(alpha, 240);
    }

    private record VisibleChunk(ChunkMesh mesh, double distanceSquared) {
    }

    private record MeshData(float[] positions, int[] colors, float[] normals) {
        private int vertexCount() {
            return colors.length;
        }

        private boolean isEmpty() {
            return colors.length == 0;
        }
    }

    private record FaceMesh(BlockPos pos, Box bounds, MeshData full, MeshData medium, MeshData far) {
    }

    private record ChunkMesh(int originX, int originZ, Box bounds, MeshData full, MeshData medium, MeshData far) {
    }

    private record QuadVertex(Vector3d position, float u, float v) {
    }

    private record TextureSample(BakedQuad quad, float u, float v, double distanceSquared) {
    }

    private record Ray(Vec3d start, Vec3d end) {
        private Vec3d direction() {
            return end.subtract(start).normalize();
        }
    }

    public record ScreenPanAnchor(Vec3d point, double distance) {
    }

    private static class MeshBuilder {
        private float[] positions = new float[64 * 3];
        private int[] colors = new int[64];
        private float[] normals = new float[64 * 3];
        private int vertexIndex;

        private void vertex(float x, float y, float z, int color, float normalX, float normalY, float normalZ) {
            ensureCapacity(vertexIndex + 1);
            int positionIndex = vertexIndex * 3;
            positions[positionIndex] = x;
            positions[positionIndex + 1] = y;
            positions[positionIndex + 2] = z;
            normals[positionIndex] = normalX;
            normals[positionIndex + 1] = normalY;
            normals[positionIndex + 2] = normalZ;
            colors[vertexIndex] = color;
            vertexIndex++;
        }

        private void ensureCapacity(int requiredVertices) {
            if (requiredVertices <= colors.length) {
                return;
            }
            int nextVertices = Math.max(requiredVertices, colors.length * 2);
            float[] nextPositions = new float[nextVertices * 3];
            int[] nextColors = new int[nextVertices];
            float[] nextNormals = new float[nextVertices * 3];
            System.arraycopy(positions, 0, nextPositions, 0, vertexIndex * 3);
            System.arraycopy(colors, 0, nextColors, 0, vertexIndex);
            System.arraycopy(normals, 0, nextNormals, 0, vertexIndex * 3);
            positions = nextPositions;
            colors = nextColors;
            normals = nextNormals;
        }

        private float[] positions() {
            float[] result = new float[vertexIndex * 3];
            System.arraycopy(positions, 0, result, 0, result.length);
            return result;
        }

        private int[] colors() {
            int[] result = new int[vertexIndex];
            System.arraycopy(colors, 0, result, 0, result.length);
            return result;
        }

        private float[] normals() {
            float[] result = new float[vertexIndex * 3];
            System.arraycopy(normals, 0, result, 0, result.length);
            return result;
        }
    }

    private record StrokeKey(BlockPos pos, Direction face, int x, int y, boolean clear) {
        private StrokeKey {
            pos = pos.toImmutable();
        }
    }

    private record ModelStrokeKey(int entityId, String surface, Direction face, int x, int y, boolean clear) {
    }

    public enum EditorTool {
        NONE(0),
        BRUSH(1),
        ERASER(2),
        PAPER(3);

        private final int networkId;

        EditorTool(int networkId) {
            this.networkId = networkId;
        }

        public int networkId() {
            return networkId;
        }
    }
}
