package com.kuilunfuzhe.monvhua.features.paint;

import com.kuilunfuzhe.monvhua.mixin.SpriteContentsAccessor;
import com.kuilunfuzhe.monvhua.network.paint.PaintOverlayPackets;
import com.kuilunfuzhe.monvhua.gui.CombinedConfigScreen;
import com.kuilunfuzhe.monvhua.item.config.PaintConfig;
import com.kuilunfuzhe.monvhua.item.paint.PaintBrushItem;
import com.kuilunfuzhe.monvhua.item.paint.PaintItems;
import com.kuilunfuzhe.monvhua.item.modblock.ModBlocks;
import com.kuilunfuzhe.monvhua.network.SafeClientNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
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
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWScrollCallbackI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    private static final int MAX_EDITOR_HISTORY = 64;
    private static final long EDITOR_HISTORY_CLOSE_DELAY_MS = 160L;
    private static final double FULL_DETAIL_DISTANCE_SQUARED = 12.0D * 12.0D;
    private static final double MEDIUM_DETAIL_DISTANCE_SQUARED = 32.0D * 32.0D;
    private static final MeshData EMPTY_MESH = new MeshData(new float[0], new int[0], new float[0]);
    private static final Map<PaintOverlayStore.FaceKey, int[]> FACE_PIXELS = new HashMap<>();
    private static final Map<PaintOverlayStore.FaceKey, FaceMesh> FACE_MESHES = new HashMap<>();
    private static final Map<ChunkPos, ChunkMesh> CHUNK_MESHES = new HashMap<>();
    private static final Map<ChunkPos, Set<PaintOverlayStore.FaceKey>> CHUNK_FACE_KEYS = new HashMap<>();
    private static final List<Integer> RECENT_COLORS = new ArrayList<>();
    private static final List<Integer> FAVORITE_COLORS = new ArrayList<>();
    private static final List<EditorHistoryStep> EDITOR_HISTORY = new ArrayList<>();
    private static final Map<PaintOverlayStore.FaceKey, List<SuppressedEditorHistoryUpdate>> SUPPRESSED_EDITOR_HISTORY_UPDATES = new HashMap<>();
    private static GLFWScrollCallbackI previousScrollCallback;
    private static boolean scrollCallbackRegistered = false;
    private static int selectedBrushRadius = 1;
    private static int selectedEraserRadius = 1;
    private static int selectedPaperSize = PaintOverlayFeature.DEFAULT_PAPER_SIZE;
    private static int selectedColor = 0xFFFF2A4F;
    private static int selectedBrushSlot = 0;
    private static boolean eraserFaceMode = false;
    private static boolean wasUsePressed = false;
    private static Object lastStrokeKey;
    private static int repeatedStrokeTicks;
    private static Boolean editorPreviousNoClip;
    private static Boolean editorPreviousNoGravity;
    private static int editorKeyCooldown;
    private static boolean editorViewPassthrough;
    private static PaintEditorScreen suspendedPaintEditorScreen;
    private static int editorHistoryCursor;
    private static int editorHistorySequence = 1;
    private static PendingEditorHistoryStep pendingEditorHistory;
    private static List<EditorHidePreview> editorHidePreviews = List.of();
    private static List<EditorPixelPreview> editorPixelPreviews = List.of();
    private static List<EditorPreview> editorPreviews = List.of();
    private static List<EditorGeometryPreview> editorGeometryPreviews = List.of();

    private PaintOverlayClient() {
    }

    public static void initialize() {
        PaintBucketClientBridge.setOpener(pos -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.currentScreen != null) {
                return true;
            }
            openPaintBucketColorScreen(client, pos);
            return true;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            for (int entityId : PlayerSkinPaintManager.tickWaterClear(client)) {
                SafeClientNetworking.send(new PaintOverlayPackets.ClearPlayerPaintC2S(entityId));
            }
            if (!scrollCallbackRegistered && client.getWindow() != null) {
                registerScrollCallback(client);
            }
            tickBrushSlotKeys(client);
            tickContinuousPainting(client);
            finishPendingEditorHistoryIfIdle(false);
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
            if (player.isSneaking()) {
                return ActionResult.PASS;
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
        ClientPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.FaceRegionPatchS2C.ID, (packet, context) ->
                context.client().execute(() -> applyRegionPatch(packet.patch(), true)));
        ClientPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.PlayerPaintStrokeS2C.ID, (packet, context) ->
                context.client().execute(() -> applyPlayerPaint(packet)));
        ClientPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.PlayerPaintDataS2C.ID, (packet, context) ->
                context.client().execute(() -> applyPlayerPaintData(packet)));
        ClientPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.ClearPlayerPaintS2C.ID, (packet, context) ->
                context.client().execute(() -> clearPlayerPaint(packet.entityId())));
        ClientPlayNetworking.registerGlobalReceiver(PaintOverlayPackets.PaintConfigS2C.ID, (packet, context) ->
                context.client().execute(() -> {
                    PaintConfig config = PaintConfig.fromJson(packet.json());
                    PaintConfig.syncInstance(config);
                    CombinedConfigScreen.receivePaintConfig(config);
                }));
        PaintBucketCarryClientState.initialize();
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
        return selectedBrushRadius;
    }

    public static int selectedRadius(EditorTool tool) {
        return tool == EditorTool.ERASER ? selectedEraserRadius : selectedBrushRadius;
    }

    public static int selectedPaperSize() {
        return selectedPaperSize;
    }

    public static boolean eraserFaceMode() {
        return eraserFaceMode;
    }

    public static List<EditorHistoryEntry> editorHistory() {
        List<EditorHistoryEntry> entries = new ArrayList<>(EDITOR_HISTORY.size());
        for (int i = 0; i < EDITOR_HISTORY.size(); i++) {
            EditorHistoryStep step = EDITOR_HISTORY.get(i);
            entries.add(new EditorHistoryEntry(i, step.sequence(), step.label(), step.changes().size()));
        }
        return List.copyOf(entries);
    }

    public static int editorHistoryCurrentIndex() {
        return editorHistoryCursor - 1;
    }

    public static boolean canUndoEditorStroke() {
        return editorHistoryCursor > 0;
    }

    public static boolean canRedoEditorStroke() {
        return editorHistoryCursor < EDITOR_HISTORY.size();
    }

    public static void setSelectedRadius(int radius) {
        setSelectedRadius(EditorTool.BRUSH, radius);
    }

    public static void setSelectedRadius(EditorTool tool, int radius) {
        int nextRadius = MathHelper.clamp(radius, PaintOverlayFeature.MIN_RADIUS, PaintOverlayFeature.MAX_RADIUS);
        if (tool == EditorTool.ERASER) {
            if (nextRadius == selectedEraserRadius) {
                return;
            }
            selectedEraserRadius = nextRadius;
        } else {
            if (nextRadius == selectedBrushRadius) {
                return;
            }
            selectedBrushRadius = nextRadius;
        }
        syncSettings(tool);
    }

    public static void syncSelectedRadius(EditorTool tool) {
        if (tool == EditorTool.BRUSH || tool == EditorTool.ERASER) {
            syncSettings(tool);
        }
    }

    public static void setEditorPreview(EditorPreview preview) {
        editorHidePreviews = List.of();
        editorPixelPreviews = List.of();
        editorPreviews = preview == null ? List.of() : List.of(preview);
        editorGeometryPreviews = List.of();
    }

    public static void setEditorPreviews(List<EditorPreview> previews) {
        editorHidePreviews = List.of();
        editorPixelPreviews = List.of();
        editorPreviews = previews == null || previews.isEmpty() ? List.of() : List.copyOf(previews);
        editorGeometryPreviews = List.of();
    }

    public static void setEditorGeometryPreviews(List<EditorGeometryPreview> geometryPreviews) {
        editorHidePreviews = List.of();
        editorPixelPreviews = List.of();
        editorPreviews = List.of();
        editorGeometryPreviews = geometryPreviews == null || geometryPreviews.isEmpty() ? List.of() : List.copyOf(geometryPreviews);
    }

    public static void setEditorPreviewLayers(List<EditorPixelPreview> pixelPreviews, List<EditorPreview> previews) {
        editorHidePreviews = List.of();
        editorPixelPreviews = pixelPreviews == null || pixelPreviews.isEmpty() ? List.of() : List.copyOf(pixelPreviews);
        editorPreviews = previews == null || previews.isEmpty() ? List.of() : List.copyOf(previews);
        editorGeometryPreviews = List.of();
    }

    public static void setEditorPreviewLayers(List<EditorHidePreview> hidePreviews,
                                              List<EditorPixelPreview> pixelPreviews,
                                              List<EditorPreview> previews) {
        editorHidePreviews = hidePreviews == null || hidePreviews.isEmpty() ? List.of() : List.copyOf(hidePreviews);
        editorPixelPreviews = pixelPreviews == null || pixelPreviews.isEmpty() ? List.of() : List.copyOf(pixelPreviews);
        editorPreviews = previews == null || previews.isEmpty() ? List.of() : List.copyOf(previews);
        editorGeometryPreviews = List.of();
    }

    public static void setEditorPreviewLayers(List<EditorHidePreview> hidePreviews,
                                              List<EditorPixelPreview> pixelPreviews,
                                              List<EditorPreview> previews,
                                              List<EditorGeometryPreview> geometryPreviews) {
        editorHidePreviews = hidePreviews == null || hidePreviews.isEmpty() ? List.of() : List.copyOf(hidePreviews);
        editorPixelPreviews = pixelPreviews == null || pixelPreviews.isEmpty() ? List.of() : List.copyOf(pixelPreviews);
        editorPreviews = previews == null || previews.isEmpty() ? List.of() : List.copyOf(previews);
        editorGeometryPreviews = geometryPreviews == null || geometryPreviews.isEmpty() ? List.of() : List.copyOf(geometryPreviews);
    }

    public static void clearEditorPreview() {
        editorHidePreviews = List.of();
        editorPixelPreviews = List.of();
        editorPreviews = List.of();
        editorGeometryPreviews = List.of();
    }

    public static EditorPreview blockEditorPreview(PaintOverlayStore.FaceKey key, int minX, int minY, int maxX, int maxY,
                                                   int color, boolean solid, boolean handles) {
        if (key == null) {
            return null;
        }
        return new EditorPreview(key, minX, minY, maxX, maxY, color, solid, handles);
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
        if (color == selectedColor) {
            return;
        }
        selectedColor = color;
        syncSettings();
    }

    public static void setSelectedAlpha(int alpha) {
        alpha = MathHelper.clamp(alpha, 0, 255);
        setSelectedColor((alpha << 24) | (selectedColor() & 0xFFFFFF));
    }

    public static int selectedAlpha() {
        return (selectedColor() >>> 24) & 0xFF;
    }

    public static int selectedBrushSlot() {
        syncSelectedSlotFromBrush();
        return selectedBrushSlot;
    }

    public static void selectBrushSlot(int slot) {
        slot = MathHelper.clamp(slot, 0, PaintBrushItem.COLOR_SLOTS - 1);
        selectedBrushSlot = slot;
        MinecraftClient client = MinecraftClient.getInstance();
        ItemStack brush = paintBrushStack(client);
        if (!brush.isEmpty()) {
            PaintBrushItem.setSelectedSlot(brush, slot);
            if (PaintBrushItem.hasSlotColor(brush, slot)) {
                selectedColor = PaintBrushItem.getPaintColor(brush, slot);
                syncSettings();
            }
        }
        SafeClientNetworking.send(new PaintOverlayPackets.SelectBrushSlotC2S(slot));
    }

    public static void recordPickedColor(int color) {
        RECENT_COLORS.remove(Integer.valueOf(color));
        RECENT_COLORS.add(0, color);
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

    public static boolean presetSlotFilled(int slot) {
        ItemStack brush = paintBrushStack(MinecraftClient.getInstance());
        return !brush.isEmpty() && PaintBrushItem.hasSlotColor(brush, slot);
    }

    public static int presetSlotColor(int slot) {
        ItemStack brush = paintBrushStack(MinecraftClient.getInstance());
        return brush.isEmpty() ? 0 : PaintBrushItem.getPaintColor(brush, slot);
    }

    public static void storeSelectedColorInPresetSlot(int slot) {
        storeColorInPresetSlot(slot, selectedColor());
    }

    public static void storeColorInPresetSlot(int slot, int color) {
        slot = MathHelper.clamp(slot, 0, PaintBrushItem.COLOR_SLOTS - 1);
        ItemStack brush = paintBrushStack(MinecraftClient.getInstance());
        if (!brush.isEmpty()) {
            PaintBrushItem.storePresetColor(brush, slot, color);
        }
        SafeClientNetworking.send(new PaintOverlayPackets.StoreBrushPresetC2S(slot, color));
    }

    public static boolean isFavoriteColor(int color) {
        return FAVORITE_COLORS.contains(color);
    }

    public static void toggleFavoriteColor(int color) {
        if (FAVORITE_COLORS.remove(Integer.valueOf(color))) {
            return;
        }
        FAVORITE_COLORS.add(color);
    }

    public static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
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
            MeshData meshData = editorHidePreviews.isEmpty()
                    ? selectRenderMesh(mesh, visible.distanceSquared(), remainingVertices)
                    : selectRenderMeshWithEditorHide(mesh, remainingVertices);
            if (meshData.isEmpty()) {
                continue;
            }
            drawMesh(vertices, matrix, mesh.originX() - camera.x, -camera.y, mesh.originZ() - camera.z, meshData.positions(), meshData.colors(), meshData.normals());
            remainingVertices -= meshData.vertexCount();
        }
        renderEditorPreview(vertices, matrix, camera);
    }

    private static void renderEditorPreview(VertexConsumer vertices, Matrix4f matrix, Vec3d camera) {
        if (editorHidePreviews.isEmpty() && editorPixelPreviews.isEmpty()
                && editorPreviews.isEmpty() && editorGeometryPreviews.isEmpty()) {
            return;
        }
        for (EditorPixelPreview preview : editorPixelPreviews) {
            if (preview == null || preview.key() == null || preview.pixels() == null) {
                continue;
            }
            MeshData mesh = buildPixelPreviewMesh(preview);
            if (mesh.isEmpty()) {
                continue;
            }
            BlockPos pos = preview.key().pos();
            drawMesh(vertices, matrix, pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z,
                    mesh.positions(), mesh.colors(), mesh.normals());
        }
        for (EditorPreview preview : editorPreviews) {
            if (preview == null || preview.key() == null) {
                continue;
            }
            MeshData mesh = buildPreviewMesh(preview);
            if (mesh.isEmpty()) {
                continue;
            }
            BlockPos pos = preview.key().pos();
            drawMesh(vertices, matrix, pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z,
                    mesh.positions(), mesh.colors(), mesh.normals());
        }
        for (EditorGeometryPreview preview : editorGeometryPreviews) {
            if (preview == null || preview.key() == null) {
                continue;
            }
            MeshData mesh = buildGeometryPreviewMesh(preview);
            if (mesh.isEmpty()) {
                continue;
            }
            BlockPos pos = preview.key().pos();
            drawMesh(vertices, matrix, pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z,
                    mesh.positions(), mesh.colors(), mesh.normals());
        }
    }

    private static MeshData buildHidePreviewMesh(EditorHidePreview preview) {
        return EMPTY_MESH;
    }

    private static MeshData buildPreviewMesh(EditorPreview preview) {
        MeshBuilder builder = new MeshBuilder();
        int minX = MathHelper.clamp(Math.min(preview.minX(), preview.maxX()), 0, PaintOverlayStore.SIZE - 1);
        int minY = MathHelper.clamp(Math.min(preview.minY(), preview.maxY()), 0, PaintOverlayStore.SIZE - 1);
        int maxX = MathHelper.clamp(Math.max(preview.minX(), preview.maxX()), minX, PaintOverlayStore.SIZE - 1);
        int maxY = MathHelper.clamp(Math.max(preview.minY(), preview.maxY()), minY, PaintOverlayStore.SIZE - 1);
        if (preview.handles() && minX == maxX && minY == maxY) {
            appendHandle(builder, preview.key().face(), minX, minY);
            return new MeshData(builder.positions(), builder.colors(), builder.normals());
        }
        if (minY == maxY && minX != maxX) {
            if (preview.solid()) {
                appendRect(builder, preview.key().face(), minX, minY, maxX + 1, minY + 1, 0xEEE9F4FF);
            } else {
                appendDashedHorizontal(builder, preview.key().face(), minX, maxX, minY, 0xEE79C8FF, dashOffset(), 0);
            }
            return new MeshData(builder.positions(), builder.colors(), builder.normals());
        }
        if (minX == maxX && minY != maxY) {
            if (preview.solid()) {
                appendRect(builder, preview.key().face(), minX, minY, minX + 1, maxY + 1, 0xEEE9F4FF);
            } else {
                appendDashedVertical(builder, preview.key().face(), minY, maxY, minX, 0xEE79C8FF, dashOffset(), 0);
            }
            return new MeshData(builder.positions(), builder.colors(), builder.normals());
        }
        if (preview.solid()) {
            int line = 0xEEE9F4FF;
            appendRect(builder, preview.key().face(), minX, minY, maxX + 1, minY + 1, line);
            appendRect(builder, preview.key().face(), minX, maxY, maxX + 1, maxY + 1, line);
            appendRect(builder, preview.key().face(), minX, minY, minX + 1, maxY + 1, line);
            appendRect(builder, preview.key().face(), maxX, minY, maxX + 1, maxY + 1, line);
        } else {
            appendDashedOutline(builder, preview.key().face(), minX, minY, maxX, maxY, 0xEE79C8FF);
        }
        if (preview.handles()) {
            appendHandle(builder, preview.key().face(), minX, minY);
            appendHandle(builder, preview.key().face(), maxX, minY);
            appendHandle(builder, preview.key().face(), minX, maxY);
            appendHandle(builder, preview.key().face(), maxX, maxY);
        }
        return new MeshData(builder.positions(), builder.colors(), builder.normals());
    }

    private static MeshData buildPixelPreviewMesh(EditorPixelPreview preview) {
        MeshBuilder builder = new MeshBuilder();
        int width = preview.width();
        int height = preview.height();
        int[] pixels = preview.pixels();
        if (width <= 0 || height <= 0 || pixels.length < width * height) {
            return EMPTY_MESH;
        }
        for (int y = 0; y < height; y++) {
            int runStart = -1;
            int runColor = 0;
            for (int x = 0; x <= width; x++) {
                int color = x < width ? pixels[y * width + x] : 0;
                if (color != 0 && color == runColor) {
                    continue;
                }
                if (runStart >= 0) {
                    int x0 = preview.startX() + runStart;
                    int x1 = preview.startX() + x;
                    int y0 = preview.startY() + y;
                    appendRect(builder, preview.key().face(), x0, y0, x1, y0 + 1, runColor);
                }
                runStart = color == 0 ? -1 : x;
                runColor = color;
            }
        }
        return new MeshData(builder.positions(), builder.colors(), builder.normals());
    }

    private static MeshData buildGeometryPreviewMesh(EditorGeometryPreview preview) {
        MeshBuilder builder = new MeshBuilder();
        double minX = Math.min(preview.minX(), preview.maxX());
        double minY = Math.min(preview.minY(), preview.maxY());
        double maxX = Math.max(preview.minX(), preview.maxX());
        double maxY = Math.max(preview.minY(), preview.maxY());
        if (maxX <= minX || maxY <= minY) {
            return EMPTY_MESH;
        }
        if (preview.solid()) {
            appendPlaneRectD(builder, preview.key(), minX, minY, maxX, maxY, preview.color());
        } else {
            appendDashedPlaneRectD(builder, preview.key(), minX, minY, maxX, maxY, preview.color());
        }
        return new MeshData(builder.positions(), builder.colors(), builder.normals());
    }

    private static void appendDashedPlaneRectD(MeshBuilder builder, PaintOverlayStore.FaceKey anchor,
                                               double minX, double minY, double maxX, double maxY, int color) {
        double width = maxX - minX;
        double height = maxY - minY;
        double offset = dashOffset();
        if (width >= height) {
            for (double x = minX - offset; x < maxX; x += 8.0D) {
                double x0 = Math.max(minX, x);
                double x1 = Math.min(maxX, x + 4.0D);
                if (x1 > x0) {
                    appendPlaneRectD(builder, anchor, x0, minY, x1, maxY, color);
                }
            }
        } else {
            for (double y = minY - offset; y < maxY; y += 8.0D) {
                double y0 = Math.max(minY, y);
                double y1 = Math.min(maxY, y + 4.0D);
                if (y1 > y0) {
                    appendPlaneRectD(builder, anchor, minX, y0, maxX, y1, color);
                }
            }
        }
    }

    private static void appendPlaneRectD(MeshBuilder builder, PaintOverlayStore.FaceKey anchor,
                                         double minU, double minV, double maxU, double maxV, int color) {
        if (anchor == null) {
            return;
        }
        Vec3d origin = Vec3d.of(anchor.pos());
        Vec3d a = editorPlanePoint(anchor, minU, minV).subtract(origin);
        Vec3d b = editorPlanePoint(anchor, maxU, minV).subtract(origin);
        Vec3d c = editorPlanePoint(anchor, maxU, maxV).subtract(origin);
        Vec3d d = editorPlanePoint(anchor, minU, maxV).subtract(origin);
        float normalX = anchor.face().getOffsetX();
        float normalY = anchor.face().getOffsetY();
        float normalZ = anchor.face().getOffsetZ();
        builder.vertex((float) a.x, (float) a.y, (float) a.z, color, normalX, normalY, normalZ);
        builder.vertex((float) b.x, (float) b.y, (float) b.z, color, normalX, normalY, normalZ);
        builder.vertex((float) c.x, (float) c.y, (float) c.z, color, normalX, normalY, normalZ);
        builder.vertex((float) d.x, (float) d.y, (float) d.z, color, normalX, normalY, normalZ);
    }

    private static void appendDashedOutline(MeshBuilder builder, Direction face, int minX, int minY, int maxX, int maxY, int color) {
        int perimeter = Math.max(1, (maxX - minX + 1) * 2 + (maxY - minY + 1) * 2);
        int offset = dashOffset();
        appendDashedHorizontal(builder, face, minX, maxX, minY, color, offset, 0);
        appendDashedHorizontal(builder, face, minX, maxX, maxY, color, offset, maxX - minX + 1);
        appendDashedVertical(builder, face, minY, maxY, minX, color, offset, perimeter / 2);
        appendDashedVertical(builder, face, minY, maxY, maxX, color, offset, perimeter / 2 + maxY - minY + 1);
    }

    private static int dashOffset() {
        return (int) ((System.currentTimeMillis() / 90L) % 8L);
    }

    private static void appendDashedHorizontal(MeshBuilder builder, Direction face, int minX, int maxX, int y, int color, int offset, int phase) {
        for (int x = minX; x <= maxX; x++) {
            if (((x - minX + phase + offset) / 4) % 2 == 0) {
                appendRect(builder, face, x, y, x + 1, y + 1, color);
            }
        }
    }

    private static void appendDashedVertical(MeshBuilder builder, Direction face, int minY, int maxY, int x, int color, int offset, int phase) {
        for (int y = minY; y <= maxY; y++) {
            if (((y - minY + phase + offset) / 4) % 2 == 0) {
                appendRect(builder, face, x, y, x + 1, y + 1, color);
            }
        }
    }

    private static void appendHandle(MeshBuilder builder, Direction face, int x, int y) {
        int size = 2;
        int x0 = MathHelper.clamp(x - size, 0, PaintOverlayStore.SIZE - 1);
        int y0 = MathHelper.clamp(y - size, 0, PaintOverlayStore.SIZE - 1);
        int x1 = MathHelper.clamp(x + size + 1, x0 + 1, PaintOverlayStore.SIZE);
        int y1 = MathHelper.clamp(y + size + 1, y0 + 1, PaintOverlayStore.SIZE);
        appendRect(builder, face, x0, y0, x1, y1, 0xFFFFFFFF);
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
        if (client.player == null || client.currentScreen != null) {
            return false;
        }
        int delta = yOffset > 0 ? 1 : -1;
        if (isHoldingPaintBrush(client) && client.player.isSneaking() && !isCtrlDown(client)) {
            selectBrushSlot(selectedBrushSlot + delta);
            return true;
        }
        if (!isCtrlDown(client)) {
            return false;
        }
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
        EditorTool tool = isHoldingEraser(client) ? EditorTool.ERASER : EditorTool.BRUSH;
        setSelectedRadius(tool, selectedRadius(tool) + delta);
        return true;
    }

    private static void tickBrushSlotKeys(MinecraftClient client) {
        if (client.player == null || client.currentScreen != null || !isHoldingPaintBrush(client)) {
            return;
        }
        long handle = client.getWindow().getHandle();
        for (int i = 0; i < PaintBrushItem.COLOR_SLOTS; i++) {
            int key = i == 0 ? GLFW.GLFW_KEY_1 : GLFW.GLFW_KEY_1 + i;
            if (GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS && selectedBrushSlot != i) {
                selectBrushSlot(i);
                return;
            }
        }
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
            client.player.sendMessage(Text.literal("模型绘画 " + modelHit.surface() + " " + modelHit.face().asString()
                    + " (" + modelHit.x() + "," + modelHit.y() + ")"), true);
            return;
        }

        PlayerSkinPaintManager.PlayerPaintHit playerHit = PlayerSkinPaintManager.raycastPlayer(client);
        if (playerHit == null) {
            playerHit = fallbackCrosshairPlayerHit(client);
        }
        if (playerHit != null) {
            boolean clear = eraser && eraserFaceMode;
            int color = clear ? 0 : selectedColor();
            int radius = selectedRadius(eraser ? EditorTool.ERASER : EditorTool.BRUSH);
            PlayerStrokeKey key = new PlayerStrokeKey(playerHit.entityId(), playerHit.surface(), playerHit.face(), playerHit.x(), playerHit.y(), clear);
            repeatedStrokeTicks++;
            if (key.equals(lastStrokeKey) && repeatedStrokeTicks < REPEATED_STROKE_SKIP_CALLS) {
                return;
            }
            lastStrokeKey = key;
            repeatedStrokeTicks = 0;
            Entity target = client.world.getEntityById(playerHit.entityId());
            if (target instanceof PlayerEntity targetPlayer) {
                boolean changed = PlayerSkinPaintManager.paintAt(targetPlayer, playerHit.surface(), playerHit.face(),
                        playerHit.x(), playerHit.y(), color, radius, clear);
                if (changed) {
                    SafeClientNetworking.send(new PaintOverlayPackets.PlayerPaintStrokeC2S(
                            playerHit.entityId(), playerHit.surface(), playerHit.face(), playerHit.x(), playerHit.y(), clear));
                }
            }
            return;
        }

        if (!(client.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            lastStrokeKey = null;
            repeatedStrokeTicks = 0;
            return;
        }

        BlockPos pos = hit.getBlockPos();
        if (client.world != null && (client.world.getBlockState(pos).getBlock() == ModBlocks.DRAWING_BOARD
                || client.world.getBlockState(pos).getBlock() == PaintItems.PAINT_BUCKET_BLOCK)) {
            lastStrokeKey = null;
            repeatedStrokeTicks = 0;
            return;
        }
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
                client.player.sendMessage(Text.literal("请对准装有颜料的桶"), true);
                return;
            }
            int color = 0xFF000000 | bucket.getColor();
            setSelectedColor(color);
            recordPickedColor(color);
            SafeClientNetworking.send(new PaintOverlayPackets.LoadBrushFromBucketC2S(hit.getBlockPos()));
            client.player.sendMessage(Text.literal("载入画笔颜色 " + toHex(color)), true);
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
        client.player.sendMessage(Text.literal("取色 " + toHex(color)), true);
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
            Entity target = client.world.getEntityById(modelHit.entityId());
            if (target instanceof ItemDisplayEntity display) {
                ModelPaintFaceKey historyKey = new ModelPaintFaceKey(modelHit.entityId(), modelHit.surface(), modelHit.face());
                NbtCompound beforeRoot = modelPaintRoot(display);
                boolean slim = isSlimModel(beforeRoot);
                int[] before = ModelPaintData.copyFacePixels(beforeRoot, modelHit.surface(), modelHit.face(), slim);
                NbtCompound afterRoot = beforeRoot.copy();
                int color = clear ? 0 : selectedColor();
                int radius = selectedRadius(tool);
                boolean changed = ModelPaintData.paint(afterRoot, modelHit.surface(), modelHit.face(), modelHit.x(), modelHit.y(),
                        radius, color, clear, slim);
                if (changed) {
                    int[] after = ModelPaintData.copyFacePixels(afterRoot, modelHit.surface(), modelHit.face(), slim);
                    beginEditorModelHistory(tool, clear, historyKey);
                    recordEditorModelFaceUpdate(historyKey, before, after);
                }
            }
            SafeClientNetworking.send(new PaintOverlayPackets.EditorModelPaintStrokeC2S(
                    modelHit.entityId(), modelHit.surface(), modelHit.face(), modelHit.x(), modelHit.y(), tool.networkId(), clear));
            return;
        }

        PlayerSkinPaintManager.PlayerPaintHit playerHit = ray == null
                ? PlayerSkinPaintManager.raycastPlayer(client)
                : PlayerSkinPaintManager.raycastPlayer(client, ray.start(), ray.end());
        if (playerHit == null && ray == null) {
            playerHit = fallbackCrosshairPlayerHit(client);
        }
        if (playerHit != null && (tool == EditorTool.BRUSH || tool == EditorTool.ERASER)) {
            boolean clear = tool == EditorTool.ERASER && eraserFaceMode;
            int color = clear ? 0 : selectedColor();
            int radius = selectedRadius(tool);
            PlayerStrokeKey key = new PlayerStrokeKey(playerHit.entityId(), playerHit.surface(), playerHit.face(), playerHit.x(), playerHit.y(), clear);
            repeatedStrokeTicks++;
            if (key.equals(lastStrokeKey) && repeatedStrokeTicks < REPEATED_STROKE_SKIP_CALLS) {
                return;
            }
            lastStrokeKey = key;
            repeatedStrokeTicks = 0;
            Entity target = client.world.getEntityById(playerHit.entityId());
            if (target instanceof PlayerEntity targetPlayer) {
                PlayerPaintFaceKey historyKey = new PlayerPaintFaceKey(playerHit.entityId(), playerHit.surface(), playerHit.face());
                int[] before = PlayerSkinPaintManager.copyFacePixels(targetPlayer, playerHit.surface(), playerHit.face());
                beginEditorPlayerHistory(tool, clear, historyKey);
                boolean changed = PlayerSkinPaintManager.paintAt(targetPlayer, playerHit.surface(), playerHit.face(),
                        playerHit.x(), playerHit.y(), color, radius, clear);
                if (changed) {
                    int[] after = PlayerSkinPaintManager.copyFacePixels(targetPlayer, playerHit.surface(), playerHit.face());
                    recordEditorPlayerFaceUpdate(historyKey, before, after);
                    SafeClientNetworking.send(new PaintOverlayPackets.PlayerPaintStrokeC2S(
                            playerHit.entityId(), playerHit.surface(), playerHit.face(), playerHit.x(), playerHit.y(), clear));
                }
            }
            return;
        }

        BlockHitResult hit = ray == null ? crosshairBlockHit(client) : raycastBlock(client, ray);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            lastStrokeKey = null;
            repeatedStrokeTicks = 0;
            return;
        }

        BlockPos pos = hit.getBlockPos();
        if (client.world != null && (client.world.getBlockState(pos).getBlock() == ModBlocks.DRAWING_BOARD
                || client.world.getBlockState(pos).getBlock() == PaintItems.PAINT_BUCKET_BLOCK)) {
            lastStrokeKey = null;
            repeatedStrokeTicks = 0;
            return;
        }
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
        beginEditorHistory(tool, clear, pos, face);
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
        closePendingEditorHistory();
    }

    public static EditorHit editorHitAtScreenPoint(MinecraftClient client, double mouseX, double mouseY, int width, int height) {
        if (client == null || client.player == null) {
            return null;
        }
        Ray ray = screenRay(client, mouseX, mouseY, width, height);
        BlockHitResult hit = ray == null ? crosshairBlockHit(client) : raycastBlock(client, ray);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos pos = hit.getBlockPos();
        if (client.world != null && (client.world.getBlockState(pos).getBlock() == ModBlocks.DRAWING_BOARD
                || client.world.getBlockState(pos).getBlock() == PaintItems.PAINT_BUCKET_BLOCK)) {
            return null;
        }
        int[] pixel = PaintBrushItem.getPixel(hit.getPos(), pos, hit.getSide());
        PaintOverlayStore.FaceKey key = new PaintOverlayStore.FaceKey(pos, hit.getSide());
        return new EditorHit(key, pixel[0], pixel[1]);
    }

    public static EditorHit editorPlaneHitAtScreenPoint(MinecraftClient client, double mouseX, double mouseY,
                                                        int width, int height, PaintOverlayStore.FaceKey anchor) {
        if (client == null || client.player == null || anchor == null) {
            return null;
        }
        Ray ray = screenRay(client, mouseX, mouseY, width, height);
        if (ray == null) {
            return null;
        }
        Direction face = anchor.face();
        Vec3d normal = new Vec3d(face.getOffsetX(), face.getOffsetY(), face.getOffsetZ());
        Vec3d direction = ray.direction();
        double denominator = direction.dotProduct(normal);
        if (Math.abs(denominator) < 1.0E-6D) {
            return null;
        }
        Vec3d planePoint = faceSurfacePoint(anchor);
        double distance = planePoint.subtract(ray.start()).dotProduct(normal) / denominator;
        if (distance < 0.0D || distance > EDITOR_RAY_DISTANCE) {
            return null;
        }
        Vec3d hitPos = ray.start().add(direction.multiply(distance));
        Vec3d insidePos = hitPos.subtract(normal.multiply(OFFSET + 1.0E-5D));
        BlockPos pos = BlockPos.ofFloored(insidePos.x, insidePos.y, insidePos.z);
        int[] pixel = PaintBrushItem.getPixel(insidePos, pos, face);
        return new EditorHit(new PaintOverlayStore.FaceKey(pos, face), pixel[0], pixel[1]);
    }

    public static ScreenPoint projectEditorPlanePixel(MinecraftClient client, PaintOverlayStore.FaceKey anchor,
                                                      int u, int v, int width, int height) {
        return projectEditorPlanePoint(client, anchor, u + 0.5D, v + 0.5D, width, height);
    }

    public static ScreenPoint projectEditorPlanePoint(MinecraftClient client, PaintOverlayStore.FaceKey anchor,
                                                      double u, double v, int width, int height) {
        if (client == null || client.player == null || anchor == null || width <= 0 || height <= 0) {
            return null;
        }
        Vec3d world = editorPlanePoint(anchor, u, v);
        Vec3d projected = client.gameRenderer.project(world);
        if (projected.z < -1.0D || projected.z > 1.0D) {
            return null;
        }
        double screenX = width * (projected.x + 1.0D) * 0.5D;
        double screenY = height * (1.0D - projected.y) * 0.5D;
        return new ScreenPoint(screenX, screenY, projected.z);
    }

    private static Vec3d editorPlanePixelCenter(PaintOverlayStore.FaceKey anchor, int u, int v) {
        return editorPlanePoint(anchor, u + 0.5D, v + 0.5D);
    }

    private static Vec3d editorPlanePoint(PaintOverlayStore.FaceKey anchor, double u, double v) {
        int size = PaintOverlayStore.SIZE;
        int blockU = (int) Math.floor(u / size);
        int blockV = (int) Math.floor(v / size);
        double cellU = (u - blockU * size) * STEP;
        double cellV = (v - blockV * size) * STEP;
        BlockPos pos = switch (anchor.face()) {
            case UP, DOWN -> new BlockPos(blockU, anchor.pos().getY(), blockV);
            case NORTH, SOUTH -> new BlockPos(blockU, blockV, anchor.pos().getZ());
            case WEST, EAST -> new BlockPos(anchor.pos().getX(), blockV, blockU);
        };
        return switch (anchor.face()) {
            case UP -> new Vec3d(pos.getX() + cellU, pos.getY() + 1.0D + OFFSET, pos.getZ() + cellV);
            case DOWN -> new Vec3d(pos.getX() + cellU, pos.getY() - OFFSET, pos.getZ() + cellV);
            case NORTH -> new Vec3d(pos.getX() + cellU, pos.getY() + cellV, pos.getZ() - OFFSET);
            case SOUTH -> new Vec3d(pos.getX() + cellU, pos.getY() + cellV, pos.getZ() + 1.0D + OFFSET);
            case WEST -> new Vec3d(pos.getX() - OFFSET, pos.getY() + cellV, pos.getZ() + cellU);
            case EAST -> new Vec3d(pos.getX() + 1.0D + OFFSET, pos.getY() + cellV, pos.getZ() + cellU);
        };
    }

    private static Vec3d faceSurfacePoint(PaintOverlayStore.FaceKey key) {
        BlockPos pos = key.pos();
        return switch (key.face()) {
            case UP -> new Vec3d(pos.getX(), pos.getY() + 1.0D + OFFSET, pos.getZ());
            case DOWN -> new Vec3d(pos.getX(), pos.getY() - OFFSET, pos.getZ());
            case NORTH -> new Vec3d(pos.getX(), pos.getY(), pos.getZ() - OFFSET);
            case SOUTH -> new Vec3d(pos.getX(), pos.getY(), pos.getZ() + 1.0D + OFFSET);
            case WEST -> new Vec3d(pos.getX() - OFFSET, pos.getY(), pos.getZ());
            case EAST -> new Vec3d(pos.getX() + 1.0D + OFFSET, pos.getY(), pos.getZ());
        };
    }

    public static int[] copyEditorFacePixels(PaintOverlayStore.FaceKey key) {
        return copyFacePixels(key);
    }

    public static boolean submitEditorRegionPatch(PaintOverlayStore.FaceKey key, int startX, int startY, int patchWidth, int patchHeight,
                                                  int[] patchPixels, String label) {
        if (key == null || patchPixels == null || patchWidth <= 0 || patchHeight <= 0) {
            return false;
        }
        PaintOverlayPackets.FaceRegionPatch patch = new PaintOverlayPackets.FaceRegionPatch(
                key.pos(), key.face(), startX, startY, patchWidth, patchHeight, patchPixels);
        int[] before = copyFacePixels(key);
        int[] after = mergePatch(before, patch);
        if (Arrays.equals(before, after)) {
            return false;
        }
        beginEditorPatchHistory(label, key);
        recordEditorFaceUpdate(key, before, after);
        applyRegionPatch(patch, false);
        SafeClientNetworking.send(new PaintOverlayPackets.PaintRegionPatchC2S(patch));
        closePendingEditorHistory();
        finishPendingEditorHistoryIfIdle(true);
        return true;
    }

    public static boolean submitEditorRegionPatches(List<EditorRegionPatch> patches, String label) {
        if (patches == null || patches.isEmpty()) {
            return false;
        }
        List<PaintOverlayPackets.FaceRegionPatch> changed = new ArrayList<>();
        for (EditorRegionPatch editorPatch : patches) {
            if (editorPatch == null || editorPatch.key() == null || editorPatch.pixels() == null
                    || editorPatch.width() <= 0 || editorPatch.height() <= 0) {
                continue;
            }
            PaintOverlayPackets.FaceRegionPatch patch = new PaintOverlayPackets.FaceRegionPatch(
                    editorPatch.key().pos(), editorPatch.key().face(),
                    editorPatch.startX(), editorPatch.startY(), editorPatch.width(), editorPatch.height(), editorPatch.pixels());
            int[] before = copyFacePixels(editorPatch.key());
            int[] after = mergePatch(before, patch);
            if (!Arrays.equals(before, after)) {
                changed.add(patch);
            }
        }
        if (changed.isEmpty()) {
            return false;
        }
        beginEditorBatchPatchHistory(label, changed);
        for (PaintOverlayPackets.FaceRegionPatch patch : changed) {
            PaintOverlayStore.FaceKey key = new PaintOverlayStore.FaceKey(patch.pos(), patch.face());
            int[] before = copyFacePixels(key);
            int[] after = mergePatch(before, patch);
            recordEditorFaceUpdate(key, before, after);
            applyRegionPatch(patch, false);
            SafeClientNetworking.send(new PaintOverlayPackets.PaintRegionPatchC2S(patch));
        }
        closePendingEditorHistory();
        finishPendingEditorHistoryIfIdle(true);
        return true;
    }

    public static void undoEditorStroke(MinecraftClient client) {
        finishPendingEditorHistoryIfIdle(true);
        if (client == null || client.player == null || !canUndoEditorStroke()) {
            return;
        }
        EditorHistoryStep step = EDITOR_HISTORY.get(editorHistoryCursor - 1);
        restoreEditorHistoryStep(step, false);
        editorHistoryCursor--;
    }

    public static void redoEditorStroke(MinecraftClient client) {
        finishPendingEditorHistoryIfIdle(true);
        if (client == null || client.player == null || !canRedoEditorStroke()) {
            return;
        }
        EditorHistoryStep step = EDITOR_HISTORY.get(editorHistoryCursor);
        restoreEditorHistoryStep(step, true);
        editorHistoryCursor++;
    }

    public static void jumpEditorHistory(MinecraftClient client, int targetIndex) {
        finishPendingEditorHistoryIfIdle(true);
        if (client == null || client.player == null || targetIndex < 0 || targetIndex >= EDITOR_HISTORY.size()) {
            return;
        }
        int targetCursor = targetIndex + 1;
        if (targetCursor == editorHistoryCursor) {
            return;
        }
        if (targetCursor < editorHistoryCursor) {
            for (int i = editorHistoryCursor - 1; i >= targetCursor; i--) {
                restoreEditorHistoryStep(EDITOR_HISTORY.get(i), false);
            }
        } else {
            for (int i = editorHistoryCursor; i < targetCursor; i++) {
                restoreEditorHistoryStep(EDITOR_HISTORY.get(i), true);
            }
        }
        editorHistoryCursor = targetCursor;
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
        double ndcX = MathHelper.clamp((mouseX / width - 0.5D) * 2.0D, -1.0D, 1.0D);
        double ndcY = MathHelper.clamp((0.5D - mouseY / height) * 2.0D, -1.0D, 1.0D);
        Vec3d cameraPos = camera.getPos();
        Vec3d forward = new Vec3d(camera.getHorizontalPlane()).normalize();
        Vec3d right = new Vec3d(camera.getDiagonalPlane()).normalize();
        Vec3d up = new Vec3d(camera.getVerticalPlane()).normalize();
        double sampleDistance = 8.0D;
        Vec3d centerWorld = cameraPos.add(forward.multiply(sampleDistance));
        Vec3d center = client.gameRenderer.project(centerWorld);
        Vec3d rightPoint = client.gameRenderer.project(centerWorld.add(right.multiply(sampleDistance)));
        Vec3d upPoint = client.gameRenderer.project(centerWorld.add(up.multiply(sampleDistance)));
        double rightScale = rightPoint.x - center.x;
        double upScale = upPoint.y - center.y;
        if (!Double.isFinite(rightScale) || !Double.isFinite(upScale)
                || Math.abs(rightScale) <= 1.0E-6D || Math.abs(upScale) <= 1.0E-6D) {
            return camera.getProjection().getPosition((float) ndcX, (float) ndcY).normalize();
        }
        Vec3d target = centerWorld
                .add(right.multiply((ndcX - center.x) / rightScale * sampleDistance))
                .add(up.multiply((ndcY - center.y) / upScale * sampleDistance));
        return target.subtract(cameraPos).normalize();
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

    private static void beginEditorHistory(EditorTool tool, boolean clearFace, BlockPos pos, Direction face) {
        Set<EditorHistoryTarget> expectedKeys = expectedEditorHistoryKeys(tool, clearFace, pos, face);
        if (pendingEditorHistory == null || pendingEditorHistory.closed()) {
            finishPendingEditorHistoryIfIdle(true);
            pendingEditorHistory = new PendingEditorHistoryStep(editorHistorySequence++, editorHistoryLabel(tool, clearFace), expectedKeys);
            return;
        }
        pendingEditorHistory.touch(expectedKeys);
    }

    private static void beginEditorPlayerHistory(EditorTool tool, boolean clearFace, PlayerPaintFaceKey key) {
        Set<EditorHistoryTarget> expectedKeys = Set.of(EditorHistoryTarget.player(key));
        if (pendingEditorHistory == null || pendingEditorHistory.closed()) {
            finishPendingEditorHistoryIfIdle(true);
            pendingEditorHistory = new PendingEditorHistoryStep(editorHistorySequence++, editorHistoryLabel(tool, clearFace), expectedKeys);
            return;
        }
        pendingEditorHistory.touch(expectedKeys);
    }

    private static void beginEditorModelHistory(EditorTool tool, boolean clearFace, ModelPaintFaceKey key) {
        Set<EditorHistoryTarget> expectedKeys = Set.of(EditorHistoryTarget.model(key));
        if (pendingEditorHistory == null || pendingEditorHistory.closed()) {
            finishPendingEditorHistoryIfIdle(true);
            pendingEditorHistory = new PendingEditorHistoryStep(editorHistorySequence++, editorHistoryLabel(tool, clearFace), expectedKeys);
            return;
        }
        pendingEditorHistory.touch(expectedKeys);
    }

    private static void beginEditorPatchHistory(String label, PaintOverlayStore.FaceKey key) {
        Set<EditorHistoryTarget> expectedKeys = Set.of(EditorHistoryTarget.block(key));
        if (pendingEditorHistory == null || pendingEditorHistory.closed()) {
            finishPendingEditorHistoryIfIdle(true);
            pendingEditorHistory = new PendingEditorHistoryStep(editorHistorySequence++, label == null || label.isBlank() ? "编辑器修改" : label, expectedKeys);
            return;
        }
        pendingEditorHistory.touch(expectedKeys);
    }

    private static void beginEditorBatchPatchHistory(String label, List<PaintOverlayPackets.FaceRegionPatch> patches) {
        Set<EditorHistoryTarget> expectedKeys = new HashSet<>();
        for (PaintOverlayPackets.FaceRegionPatch patch : patches) {
            expectedKeys.add(EditorHistoryTarget.block(new PaintOverlayStore.FaceKey(patch.pos(), patch.face())));
        }
        if (pendingEditorHistory == null || pendingEditorHistory.closed()) {
            finishPendingEditorHistoryIfIdle(true);
            pendingEditorHistory = new PendingEditorHistoryStep(editorHistorySequence++, label == null || label.isBlank() ? "编辑器修改" : label, expectedKeys);
            return;
        }
        pendingEditorHistory.touch(expectedKeys);
    }

    private static Set<EditorHistoryTarget> expectedEditorHistoryKeys(EditorTool tool, boolean clearFace, BlockPos pos, Direction face) {
        Set<EditorHistoryTarget> keys = new HashSet<>();
        if (tool == EditorTool.ERASER && clearFace) {
            int blockRadius = Math.max(0, MathHelper.clamp(selectedRadius(tool), PaintOverlayFeature.MIN_RADIUS, PaintOverlayFeature.MAX_RADIUS) - 1);
            for (int a = -blockRadius; a <= blockRadius; a++) {
                for (int b = -blockRadius; b <= blockRadius; b++) {
                    if (a * a + b * b <= blockRadius * blockRadius) {
                        keys.add(EditorHistoryTarget.block(new PaintOverlayStore.FaceKey(offsetOnFacePlane(pos, face, a, b), face)));
                    }
                }
            }
        } else {
            keys.add(EditorHistoryTarget.block(new PaintOverlayStore.FaceKey(pos, face)));
        }
        return keys;
    }

    private static BlockPos offsetOnFacePlane(BlockPos pos, Direction face, int a, int b) {
        return switch (face) {
            case UP, DOWN -> pos.add(a, 0, b);
            case NORTH, SOUTH -> pos.add(a, b, 0);
            case WEST, EAST -> pos.add(0, b, a);
        };
    }

    private static String editorHistoryLabel(EditorTool tool, boolean clearFace) {
        if (tool == EditorTool.ERASER) {
            return clearFace ? "编辑器清除整面" : "编辑器擦除";
        }
        if (tool == EditorTool.BRUSH) {
            return "编辑器绘制";
        }
        return "编辑器操作";
    }

    private static void closePendingEditorHistory() {
        if (pendingEditorHistory != null) {
            pendingEditorHistory.close();
        }
    }

    private static void finishPendingEditorHistoryIfIdle(boolean force) {
        if (pendingEditorHistory == null) {
            return;
        }
        if (!force && !pendingEditorHistory.readyToFinish()) {
            return;
        }
        EditorHistoryStep step = pendingEditorHistory.toStep();
        pendingEditorHistory = null;
        if (step == null) {
            return;
        }
        if (editorHistoryCursor < EDITOR_HISTORY.size()) {
            EDITOR_HISTORY.subList(editorHistoryCursor, EDITOR_HISTORY.size()).clear();
        }
        EDITOR_HISTORY.add(step);
        editorHistoryCursor = EDITOR_HISTORY.size();
        while (EDITOR_HISTORY.size() > MAX_EDITOR_HISTORY) {
            EDITOR_HISTORY.remove(0);
            editorHistoryCursor = Math.max(0, editorHistoryCursor - 1);
        }
    }

    private static void recordEditorFaceUpdate(PaintOverlayStore.FaceKey key, int[] before, int[] after) {
        SuppressedEditorHistoryUpdate suppressed = takeSuppressedEditorHistoryUpdate(key, after);
        if (suppressed != null) {
            return;
        }
        recordEditorHistoryUpdate(EditorHistoryTarget.block(key), before, after);
    }

    private static void recordEditorPlayerFaceUpdate(PlayerPaintFaceKey key, int[] before, int[] after) {
        recordEditorHistoryUpdate(EditorHistoryTarget.player(key), before, after);
    }

    private static void recordEditorModelFaceUpdate(ModelPaintFaceKey key, int[] before, int[] after) {
        recordEditorHistoryUpdate(EditorHistoryTarget.model(key), before, after);
    }

    private static void recordEditorHistoryUpdate(EditorHistoryTarget target, int[] before, int[] after) {
        if (pendingEditorHistory == null) {
            return;
        }
        if (!pendingEditorHistory.expects(target)) {
            return;
        }
        pendingEditorHistory.record(target, before, after);
    }

    private static void restoreEditorHistoryStep(EditorHistoryStep step, boolean after) {
        for (EditorHistoryChange change : step.changes()) {
            int[] pixels = after ? change.after() : change.before();
            if (change.target().blockKey() != null) {
                PaintOverlayStore.FaceKey key = change.target().blockKey();
                PaintOverlayPackets.FaceData faceData = new PaintOverlayPackets.FaceData(key.pos(), key.face(), pixels);
                suppressEditorHistoryUpdate(faceData);
                SafeClientNetworking.send(new PaintOverlayPackets.RestoreFaceC2S(faceData));
                continue;
            }
            PlayerPaintFaceKey key = change.target().playerKey();
            if (key != null) {
                restorePlayerHistoryFace(key, pixels);
                continue;
            }
            ModelPaintFaceKey modelKey = change.target().modelKey();
            if (modelKey != null) {
                restoreModelHistoryFace(modelKey, pixels);
            }
        }
    }

    private static void restorePlayerHistoryFace(PlayerPaintFaceKey key, int[] pixels) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return;
        }
        Entity target = client.world.getEntityById(key.entityId());
        if (target instanceof PlayerEntity player) {
            PlayerSkinPaintManager.setFacePixels(player, key.surface(), key.face(), pixels);
        }
    }

    private static void restoreModelHistoryFace(ModelPaintFaceKey key, int[] pixels) {
        SafeClientNetworking.send(new PaintOverlayPackets.RestoreModelFaceC2S(
                key.entityId(), key.surface(), key.face(), pixels));
    }

    private static NbtCompound modelPaintRoot(ItemDisplayEntity display) {
        if (display == null) {
            return new NbtCompound();
        }
        ItemStack stack = display.getItemStack();
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        return component == null ? new NbtCompound() : component.copyNbt();
    }

    private static boolean isSlimModel(NbtCompound root) {
        return root != null && "slim".equals(root.getString("arm_model", ""));
    }

    private static void suppressEditorHistoryUpdate(PaintOverlayPackets.FaceData faceData) {
        PaintOverlayStore.FaceKey key = new PaintOverlayStore.FaceKey(faceData.pos(), faceData.face());
        SUPPRESSED_EDITOR_HISTORY_UPDATES
                .computeIfAbsent(key, ignored -> new ArrayList<>())
                .add(new SuppressedEditorHistoryUpdate(copyPixels(faceData.pixels()), System.currentTimeMillis() + 2_000L));
    }

    private static SuppressedEditorHistoryUpdate takeSuppressedEditorHistoryUpdate(PaintOverlayStore.FaceKey key, int[] after) {
        List<SuppressedEditorHistoryUpdate> updates = SUPPRESSED_EDITOR_HISTORY_UPDATES.get(key);
        if (updates == null || updates.isEmpty()) {
            return null;
        }
        long now = System.currentTimeMillis();
        updates.removeIf(update -> update.expiresAtMs() < now);
        for (int i = 0; i < updates.size(); i++) {
            SuppressedEditorHistoryUpdate update = updates.get(i);
            if (Arrays.equals(update.pixels(), after)) {
                updates.remove(i);
                if (updates.isEmpty()) {
                    SUPPRESSED_EDITOR_HISTORY_UPDATES.remove(key);
                }
                return update;
            }
        }
        if (updates.isEmpty()) {
            SUPPRESSED_EDITOR_HISTORY_UPDATES.remove(key);
        }
        return null;
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
        return color;
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
                client.player.sendMessage(Text.literal("色槽 " + (slot + 1) + " 为空"), true);
            }
            return;
        }
        int color = RECENT_COLORS.get(slot);
        setSelectedColor(color);
        if (client.player != null) {
            client.player.sendMessage(Text.literal("取色 " + toHex(color)), true);
        }
    }

    private static int pickModelColor(MinecraftClient client) {
        PaintModelOverlayClient.ModelHit modelHit = PaintModelOverlayClient.raycastCombinedBody(client);
        return pickModelColor(client, modelHit);
    }

    private static PlayerSkinPaintManager.PlayerPaintHit fallbackCrosshairPlayerHit(MinecraftClient client) {
        if (!(client.crosshairTarget instanceof EntityHitResult hit) || !(hit.getEntity() instanceof PlayerEntity player) || player == client.player) {
            return null;
        }
        return PlayerSkinPaintManager.approximateHit(player, hit.getPos(), client.player.getEyePos());
    }

    private static int pickModelColor(MinecraftClient client, Ray ray) {
        PaintModelOverlayClient.ModelHit modelHit = ray == null ? null : PaintModelOverlayClient.raycastCombinedBody(client, ray.start(), ray.end());
        return pickModelColor(client, modelHit);
    }

    private static int pickModelColor(MinecraftClient client, PaintModelOverlayClient.ModelHit modelHit) {
        if (modelHit == null) {
            return 0;
        }
        return PaintModelOverlayClient.colorAt(client, modelHit);
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
        return color;
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
            client.player.sendMessage(Text.literal(eraserFaceMode ? "橡皮: 整面" : "橡皮: 像素"), true);
        }
    }

    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || (!isHoldingPaintTool(client) && !isHoldingPaintPaper(client))) {
            return;
        }

        boolean paper = isHoldingPaintPaper(client);
        boolean eraser = isHoldingEraser(client);
        String radiusText = paper ? "纸 " + selectedPaperSize + "x" + selectedPaperSize
                : "R " + selectedRadius(eraser ? EditorTool.ERASER : EditorTool.BRUSH);
        String colorText = paper ? "纸张" : (eraser ? (eraserFaceMode ? "整面" : "像素") : toHex(selectedColor));
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
        int hotbarLeft = context.getScaledWindowWidth() / 2 - 91;
        int x;
        if (isPaintBrush(client.player.getMainHandStack())) {
            int hotbarSlot = client.player.getInventory().getSelectedSlot();
            x = hotbarLeft + hotbarSlot * 20 - 36;
        } else {
            x = hotbarLeft - 92;
        }
        int y = context.getScaledWindowHeight() - 42;
        int slotW = 12;
        int gap = 2;
        for (int i = 0; i < PaintBrushItem.COLOR_SLOTS; i++) {
            double remaining = PaintBrushItem.getRemainingPaintPercent(brush, i);
            int color = PaintBrushItem.getPaintColor(brush, i);
            int slotX = x + i * (slotW + gap);
            boolean selected = i == PaintBrushItem.getSelectedSlot(brush);
            context.fill(slotX - 1, y - 1, slotX + slotW + 1, y + 15, selected ? 0xFFFFFFFF : 0xAA101015);
            context.fill(slotX, y, slotX + slotW, y + 14, 0xFF202026);
            if (remaining > 0.0D) {
                context.fill(slotX + 1, y + 1, slotX + slotW - 1, y + 7, color);
            }
            int filled = MathHelper.clamp((int) Math.round((slotW - 2) * (remaining / (double) PaintBrushItem.MAX_PAINT_PIXELS)), 0, slotW - 2);
            context.fill(slotX + 1, y + 10, slotX + 1 + filled, y + 13, remaining > 0.0D ? 0xFF58D66D : 0xFF7A3030);
        }
        int selected = PaintBrushItem.getSelectedSlot(brush);
        double remaining = PaintBrushItem.getRemainingPaintPercent(brush, selected);
        context.drawText(client.textRenderer, Text.literal((selected + 1) + ":" + formatPaintPercent(remaining)), x, y - 10, 0xFFE6E6E6, false);
    }

    private static boolean isHoldingPaintBrush(MinecraftClient client) {
        if (client.player == null) {
            return false;
        }
        return isPaintBrush(client.player.getMainHandStack()) || isPaintBrush(client.player.getOffHandStack());
    }

    private static String formatPaintPercent(double remaining) {
        double percent = Math.clamp(remaining * 100.0D / PaintBrushItem.MAX_PAINT_PIXELS, 0.0D, 100.0D);
        if (Math.abs(percent - Math.rint(percent)) < 0.05D) {
            return String.format(java.util.Locale.ROOT, "%.0f%%", percent);
        }
        return String.format(java.util.Locale.ROOT, "%.1f%%", percent);
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

    private static void syncSelectedSlotFromBrush() {
        MinecraftClient client = MinecraftClient.getInstance();
        ItemStack brush = paintBrushStack(client);
        if (brush.isEmpty()) {
            return;
        }
        selectedBrushSlot = PaintBrushItem.getSelectedSlot(brush);
    }

    private static boolean isCtrlDown(MinecraftClient client) {
        long handle = client.getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private static void syncSettings() {
        syncSettings(EditorTool.BRUSH);
    }

    private static void syncSettings(EditorTool tool) {
        SafeClientNetworking.send(new PaintOverlayPackets.BrushSettingsC2S(selectedColor, selectedRadius(tool)));
    }

    private static void syncPaperSize() {
        SafeClientNetworking.send(new PaintOverlayPackets.PaperSizeC2S(selectedPaperSize));
    }

    private static int ensureAlpha(int color) {
        return color;
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

    private static void applyPlayerPaint(PaintOverlayPackets.PlayerPaintStrokeS2C packet) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return;
        }
        Entity target = client.world.getEntityById(packet.entityId());
        if (target instanceof PlayerEntity player) {
            PlayerSkinPaintManager.paintAt(player, packet.surface(), packet.face(), packet.x(), packet.y(),
                    packet.color(), packet.radius(), packet.clearFace());
        }
    }

    private static void applyPlayerPaintData(PaintOverlayPackets.PlayerPaintDataS2C packet) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return;
        }
        Entity target = client.world.getEntityById(packet.entityId());
        if (!(target instanceof PlayerEntity player)) {
            return;
        }
        PlayerSkinPaintManager.resetTexture(player);
        for (PaintOverlayPackets.PlayerPaintStrokeS2C stroke : packet.strokes()) {
            PlayerSkinPaintManager.paintAt(player, stroke.surface(), stroke.face(), stroke.x(), stroke.y(),
                    stroke.color(), stroke.radius(), stroke.clearFace());
        }
    }

    private static void clearPlayerPaint(int entityId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            return;
        }
        Entity target = client.world.getEntityById(entityId);
        if (target instanceof PlayerEntity player) {
            PlayerSkinPaintManager.resetTexture(player);
        }
    }

    private static void applyFace(PaintOverlayPackets.FaceData face) {
        applyFace(face, true);
    }

    private static void applyFace(PaintOverlayPackets.FaceData face, boolean recordHistory) {
        PaintOverlayStore.FaceKey key = new PaintOverlayStore.FaceKey(face.pos(), face.face());
        ChunkPos chunkPos = chunkPos(key.pos());
        int[] before = recordHistory ? copyFacePixels(key) : null;
        int[] after = copyPixels(face.pixels());
        if (!hasPixels(after)) {
            FACE_PIXELS.remove(key);
            removeFaceMesh(key, chunkPos);
            rebuildChunkMesh(chunkPos);
            if (recordHistory && !Arrays.equals(before, after)) {
                recordEditorFaceUpdate(key, before, after);
            }
            return;
        }
        FACE_PIXELS.put(key, after);
        putFaceMesh(key, buildMesh(key, after), chunkPos);
        rebuildChunkMesh(chunkPos);
        if (recordHistory && !Arrays.equals(before, after)) {
            recordEditorFaceUpdate(key, before, after);
        }
    }

    private static void applyRegionPatch(PaintOverlayPackets.FaceRegionPatch patch, boolean recordHistory) {
        PaintOverlayStore.FaceKey key = new PaintOverlayStore.FaceKey(patch.pos(), patch.face());
        int[] before = recordHistory ? copyFacePixels(key) : null;
        int[] after = mergePatch(copyFacePixels(key), patch);
        if (recordHistory && Arrays.equals(before, after)) {
            return;
        }
        ChunkPos chunkPos = chunkPos(key.pos());
        if (!hasPixels(after)) {
            FACE_PIXELS.remove(key);
            removeFaceMesh(key, chunkPos);
        } else {
            FACE_PIXELS.put(key, after);
            putFaceMesh(key, buildMesh(key, after), chunkPos);
        }
        rebuildChunkMesh(chunkPos);
        if (recordHistory && !Arrays.equals(before, after)) {
            recordEditorFaceUpdate(key, before, after);
        }
    }

    private static int[] mergePatch(int[] base, PaintOverlayPackets.FaceRegionPatch patch) {
        int[] after = copyPixels(base);
        int[] patchPixels = patch.pixels();
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
                after[targetY * PaintOverlayStore.SIZE + targetX] = patchPixels[sourceIndex];
            }
        }
        return after;
    }

    private static int[] copyFacePixels(PaintOverlayStore.FaceKey key) {
        int[] pixels = FACE_PIXELS.get(key);
        return pixels == null ? new int[PaintOverlayStore.FACE_PIXELS] : copyPixels(pixels);
    }

    private static int[] copyPixels(int[] source) {
        int[] pixels = new int[PaintOverlayStore.FACE_PIXELS];
        System.arraycopy(source, 0, pixels, 0, Math.min(source.length, pixels.length));
        return pixels;
    }

    private static int[] copyExactPixels(int[] source) {
        return source == null ? new int[0] : Arrays.copyOf(source, source.length);
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

    private static MeshData selectRenderMeshWithEditorHide(ChunkMesh chunkMesh, int remainingVertices) {
        ChunkPos chunkPos = new ChunkPos(chunkMesh.originX() >> 4, chunkMesh.originZ() >> 4);
        Set<PaintOverlayStore.FaceKey> keys = CHUNK_FACE_KEYS.get(chunkPos);
        if (keys == null || keys.isEmpty()) {
            return EMPTY_MESH;
        }
        List<FaceMeshPart> parts = new ArrayList<>();
        int vertexCount = 0;
        for (PaintOverlayStore.FaceKey key : keys) {
            FaceMesh faceMesh = FACE_MESHES.get(key);
            if (faceMesh == null) {
                continue;
            }
            MeshData mesh = hiddenEditorPixelsFor(key)
                    ? buildFaceMesh(key.face(), copyPixelsWithEditorHide(key), 1)
                    : faceMesh.full();
            if (mesh.isEmpty()) {
                continue;
            }
            vertexCount += mesh.vertexCount();
            if (vertexCount > remainingVertices) {
                return EMPTY_MESH;
            }
            parts.add(new FaceMeshPart(key.pos(), mesh));
        }
        if (vertexCount == 0) {
            return EMPTY_MESH;
        }
        float[] positions = new float[vertexCount * 3];
        int[] colors = new int[vertexCount];
        float[] normals = new float[vertexCount * 3];
        int vertexIndex = 0;
        for (FaceMeshPart part : parts) {
            MeshData mesh = part.mesh();
            float offsetX = part.pos().getX() - chunkMesh.originX();
            float offsetY = part.pos().getY();
            float offsetZ = part.pos().getZ() - chunkMesh.originZ();
            for (int i = 0; i < mesh.colors().length; i++) {
                int sourcePositionIndex = i * 3;
                int targetPositionIndex = vertexIndex * 3;
                positions[targetPositionIndex] = offsetX + mesh.positions()[sourcePositionIndex];
                positions[targetPositionIndex + 1] = offsetY + mesh.positions()[sourcePositionIndex + 1];
                positions[targetPositionIndex + 2] = offsetZ + mesh.positions()[sourcePositionIndex + 2];
                normals[targetPositionIndex] = mesh.normals()[sourcePositionIndex];
                normals[targetPositionIndex + 1] = mesh.normals()[sourcePositionIndex + 1];
                normals[targetPositionIndex + 2] = mesh.normals()[sourcePositionIndex + 2];
                colors[vertexIndex] = mesh.colors()[i];
                vertexIndex++;
            }
        }
        return new MeshData(positions, colors, normals);
    }

    private static boolean hiddenEditorPixelsFor(PaintOverlayStore.FaceKey key) {
        for (EditorHidePreview preview : editorHidePreviews) {
            if (preview != null && key.equals(preview.key())) {
                return true;
            }
        }
        return false;
    }

    private static int[] copyPixelsWithEditorHide(PaintOverlayStore.FaceKey key) {
        int[] pixels = copyFacePixels(key);
        for (EditorHidePreview preview : editorHidePreviews) {
            if (preview == null || !key.equals(preview.key())) {
                continue;
            }
            int minX = MathHelper.clamp(Math.min(preview.minX(), preview.maxX()), 0, PaintOverlayStore.SIZE - 1);
            int minY = MathHelper.clamp(Math.min(preview.minY(), preview.maxY()), 0, PaintOverlayStore.SIZE - 1);
            int maxX = MathHelper.clamp(Math.max(preview.minX(), preview.maxX()), minX, PaintOverlayStore.SIZE - 1);
            int maxY = MathHelper.clamp(Math.max(preview.minY(), preview.maxY()), minY, PaintOverlayStore.SIZE - 1);
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    pixels[y * PaintOverlayStore.SIZE + x] = 0;
                }
            }
        }
        return pixels;
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
        return alpha;
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

    private record FaceMeshPart(BlockPos pos, MeshData mesh) {
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

    public record EditorHistoryEntry(int index, int sequence, String label, int changedFaces) {
    }

    private record EditorHistoryStep(int sequence, String label, List<EditorHistoryChange> changes) {
        private EditorHistoryStep {
            changes = List.copyOf(changes);
        }
    }

    private record EditorHistoryChange(EditorHistoryTarget target, int[] before, int[] after) {
        private EditorHistoryChange {
            before = copyExactPixels(before);
            after = copyExactPixels(after);
        }
    }

    private record EditorHistoryTarget(PaintOverlayStore.FaceKey blockKey, PlayerPaintFaceKey playerKey, ModelPaintFaceKey modelKey) {
        private static EditorHistoryTarget block(PaintOverlayStore.FaceKey key) {
            return new EditorHistoryTarget(key, null, null);
        }

        private static EditorHistoryTarget player(PlayerPaintFaceKey key) {
            return new EditorHistoryTarget(null, key, null);
        }

        private static EditorHistoryTarget model(ModelPaintFaceKey key) {
            return new EditorHistoryTarget(null, null, key);
        }
    }

    private record PlayerPaintFaceKey(int entityId, String surface, Direction face) {
    }

    private record ModelPaintFaceKey(int entityId, String surface, Direction face) {
    }

    private record SuppressedEditorHistoryUpdate(int[] pixels, long expiresAtMs) {
        private SuppressedEditorHistoryUpdate {
            pixels = copyPixels(pixels);
        }
    }

    private static final class PendingEditorHistoryStep {
        private final int sequence;
        private final String label;
        private final Set<EditorHistoryTarget> expectedKeys = new HashSet<>();
        private final Map<EditorHistoryTarget, PendingEditorHistoryChange> changes = new LinkedHashMap<>();
        private long closeAtMs = -1L;

        private PendingEditorHistoryStep(int sequence, String label, Set<EditorHistoryTarget> expectedKeys) {
            this.sequence = sequence;
            this.label = label;
            touch(expectedKeys);
        }

        private void touch(Set<EditorHistoryTarget> expectedKeys) {
            this.expectedKeys.addAll(expectedKeys);
            closeAtMs = -1L;
        }

        private boolean expects(EditorHistoryTarget key) {
            return expectedKeys.contains(key);
        }

        private void record(EditorHistoryTarget key, int[] before, int[] after) {
            PendingEditorHistoryChange change = changes.get(key);
            if (change == null) {
                changes.put(key, new PendingEditorHistoryChange(before, after));
            } else {
                change.setAfter(after);
            }
        }

        private void close() {
            closeAtMs = System.currentTimeMillis() + EDITOR_HISTORY_CLOSE_DELAY_MS;
        }

        private boolean closed() {
            return closeAtMs >= 0L;
        }

        private boolean readyToFinish() {
            return closeAtMs >= 0L && System.currentTimeMillis() >= closeAtMs;
        }

        private EditorHistoryStep toStep() {
            List<EditorHistoryChange> finalChanges = new ArrayList<>();
            for (Map.Entry<EditorHistoryTarget, PendingEditorHistoryChange> entry : changes.entrySet()) {
                PendingEditorHistoryChange change = entry.getValue();
                if (!Arrays.equals(change.before(), change.after())) {
                    finalChanges.add(new EditorHistoryChange(entry.getKey(), change.before(), change.after()));
                }
            }
            return finalChanges.isEmpty() ? null : new EditorHistoryStep(sequence, label, finalChanges);
        }
    }

    private static final class PendingEditorHistoryChange {
        private final int[] before;
        private int[] after;

        private PendingEditorHistoryChange(int[] before, int[] after) {
            this.before = copyExactPixels(before);
            this.after = copyExactPixels(after);
        }

        private int[] before() {
            return before;
        }

        private int[] after() {
            return after;
        }

        private void setAfter(int[] after) {
            this.after = copyExactPixels(after);
        }
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

    private record PlayerStrokeKey(int entityId, String surface, Direction face, int x, int y, boolean clear) {
    }

    private static int editorPlaneU(PaintOverlayStore.FaceKey key, int x) {
        if (key == null) {
            return x;
        }
        int size = PaintOverlayStore.SIZE;
        BlockPos pos = key.pos();
        return switch (key.face()) {
            case UP, DOWN, SOUTH -> pos.getX() * size + x;
            case NORTH -> pos.getX() * size + (size - 1 - x);
            case WEST -> pos.getZ() * size + x;
            case EAST -> pos.getZ() * size + (size - 1 - x);
        };
    }

    private static int editorPlaneV(PaintOverlayStore.FaceKey key, int y) {
        if (key == null) {
            return y;
        }
        int size = PaintOverlayStore.SIZE;
        BlockPos pos = key.pos();
        return switch (key.face()) {
            case UP -> pos.getZ() * size + y;
            case DOWN -> pos.getZ() * size + (size - 1 - y);
            case NORTH, SOUTH, WEST, EAST -> pos.getY() * size + (size - 1 - y);
        };
    }

    public record EditorHit(PaintOverlayStore.FaceKey key, int x, int y, int planeU, int planeV) {
        public EditorHit(PaintOverlayStore.FaceKey key, int x, int y) {
            this(key, x, y, editorPlaneU(key, x), editorPlaneV(key, y));
        }
    }

    public record ScreenPoint(double x, double y, double depth) {
    }

    public record EditorPreview(PaintOverlayStore.FaceKey key, int minX, int minY, int maxX, int maxY,
                                int color, boolean solid, boolean handles) {
    }

    public record EditorGeometryPreview(PaintOverlayStore.FaceKey key,
                                        double minX, double minY, double maxX, double maxY,
                                        int color, boolean solid) {
    }

    public record EditorHidePreview(PaintOverlayStore.FaceKey key, int minX, int minY, int maxX, int maxY) {
    }

    public record EditorPixelPreview(PaintOverlayStore.FaceKey key, int startX, int startY, int width, int height,
                                     int[] pixels) {
    }

    public record EditorRegionPatch(PaintOverlayStore.FaceKey key, int startX, int startY, int width, int height,
                                    int[] pixels) {
    }

    public enum EditorTool {
        NONE(0),
        BRUSH(1),
        ERASER(2),
        PAPER(3),
        PRESET(0),
        SELECT(0),
        SHAPE(0);

        private final int networkId;

        EditorTool(int networkId) {
            this.networkId = networkId;
        }

        public int networkId() {
            return networkId;
        }
    }
}

