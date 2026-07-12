package com.kuilunfuzhe.monvhua.features.paint;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.item.paint.PaintItems;
import com.kuilunfuzhe.monvhua.item.paint.PaintBrushItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PaintEditorScreen extends Screen {
    private static final int LEFT_WIDTH = 54;
    private static final int RIGHT_WIDTH = 190;
    private static final int MAP_SIZE = 104;
    private static final int HUE_WIDTH = 12;
    private static final int SWATCH = 18;
    private static final int BRUSH_COLOR_Y = 74;
    private static final int BRUSH_SWATCH_Y = 183;
    private static final int BRUSH_HEX_Y = 228;
    private static final int BRUSH_ALPHA_Y = 254;
    private static final int BRUSH_RECENT_Y = 304;
    private static final int BRUSH_FAVORITE_X_OFFSET = 86;
    private static final int BRUSH_RADIUS_Y = 408;
    private static final int ERASER_RADIUS_Y = 82;
    private static final int ERASER_MODE_Y = 118;
    private static final int ERASER_HISTORY_Y = 176;
    private static final int RADIUS_TRACK_X_OFFSET = 34;
    private static final int RADIUS_TRACK_WIDTH = 88;
    private static final int SHAPE_MODE_Y = 76;
    private static final int SHAPE_COLOR_Y = 330;
    private static final int SHAPE_ALPHA_Y = 442;
    private static final int SHAPE_STROKE_Y = 482;
    private static final int MIN_SHAPE_STROKE = 1;
    private static final int MAX_SHAPE_STROKE = 32;
    private static final int HISTORY_ROW_HEIGHT = 17;
    private static final int HISTORY_MENU_WIDTH = 86;
    private static final int HISTORY_MENU_HEIGHT = 24;
    private static final int PRESET_SLOT_SIZE = 42;
    private static final int PRESET_SLOT_GAP = 8;
    private static final int PRESET_MENU_WIDTH = 92;
    private static final int PRESET_MENU_ROW_HEIGHT = 18;
    private static final int PAPER_SIZE_Y = 82;
    private static final int TOOL_X = 10;
    private static final int TOOL_BRUSH_Y = 36;
    private static final int TOOL_ERASER_Y = 78;
    private static final int TOOL_PAPER_Y = 120;
    private static final int TOOL_PRESET_Y = 162;
    private static final int TOOL_SELECT_Y = 204;
    private static final int TOOL_SHAPE_Y = 246;
    private static final int TOOL_NONE_Y = 288;
    private static final int SELECT_MENU_WIDTH = 76;
    private static final int SELECT_MENU_ROW_HEIGHT = 18;
    private static final int HANDLE_SIZE = 5;
    private static final double WORLD_PREVIEW_LINE_WIDTH = 0.35D;
    private static final int PRESET_EDITOR_GRID_Y = 78;
    private static final int PRESET_EDITOR_PICKER_Y = 258;
    private static final int PRESET_SAVE_Y = 408;
    private static final int SURFACE = 0xD812141A;
    private static final int SURFACE_SOFT = 0xA6181B23;
    private static final int SURFACE_HOVER = 0xC8242832;
    private static final int OUTLINE = 0xAA8DDAFF;
    private static final int OUTLINE_SOFT = 0x668DDAFF;
    private static final int TEXT_PRIMARY = 0xFFF3F5F7;
    private static final int TEXT_SECONDARY = 0xFFAEB6C2;
    private static final int ACCENT = 0xFF5FB7FF;
    private static final int ACCENT_DARK = 0xFF1E5D7A;
    private static final int CHAMFER = 5;
    private static final int MAX_EDITOR_BLOCK_SPAN = 16 * 4;
    private static final int MAX_EDITOR_PIXEL_SPAN = MAX_EDITOR_BLOCK_SPAN * PaintOverlayStore.SIZE;
    private static final int MAX_EDITOR_AREA_PIXELS = MAX_EDITOR_PIXEL_SPAN * MAX_EDITOR_PIXEL_SPAN;
    private static final Identifier COLOR_MAP_TEXTURE_ID = Identifier.of(MonvhuaMod.MOD_ID, "dynamic/paint_editor_color_map");

    private PaintOverlayClient.EditorTool selectedTool = PaintOverlayClient.EditorTool.NONE;
    private NativeImageBackedTexture colorMapTexture;
    private TextFieldWidget hexField;
    private float hue = 0.97F;
    private float saturation = 0.84F;
    private float value = 1.0F;
    private float uploadedHue = -1.0F;
    private boolean updatingHexField;
    private boolean pickingColor;
    private boolean draggingAlpha;
    private boolean draggingRadius;
    private PaintOverlayClient.EditorTool draggingRadiusTool = PaintOverlayClient.EditorTool.NONE;
    private boolean draggingShapeStroke;
    private int shapeStrokeWidth = 1;
    private boolean rightUsing;
    private boolean leftLooking;
    private boolean rightPanning;
    private PaintOverlayClient.ScreenPanAnchor rightPanAnchor;
    private double lastMouseX;
    private double lastMouseY;
    private int historyMenuIndex = -1;
    private int historyMenuX;
    private int historyMenuY;
    private boolean presetStoreMenuOpen;
    private int presetStoreMenuX;
    private int presetStoreMenuY;
    private final int[] presetDraftColors = new int[PaintBrushItem.COLOR_SLOTS];
    private final boolean[] presetDraftFilled = new boolean[PaintBrushItem.COLOR_SLOTS];
    private boolean presetDraftsLoaded;
    private int selectedPresetSlot;
    private ShapeMode shapeMode = ShapeMode.LINE;
    private PaintSelection selection;
    private PaintSelection clipboardSelection;
    private DragMode dragMode = DragMode.NONE;
    private int dragStartX;
    private int dragStartY;
    private int dragCurrentX;
    private int dragCurrentY;
    private int dragAnchorMinX;
    private int dragAnchorMinY;
    private int dragAnchorMaxX;
    private int dragAnchorMaxY;
    private int[] dragAnchorPixels;
    private PaintOverlayStore.FaceKey dragKey;
    private PaintSelection selectionBeforePreview;
    private boolean selectionPreviewActive;
    private boolean selectMenuOpen;
    private int selectMenuX;
    private int selectMenuY;
    private long horizontalResizeCursor;
    private long verticalResizeCursor;
    private long nwseResizeCursor;
    private long neswResizeCursor;
    private int activeCursorShape;

    private PlanePoint planePoint(PaintOverlayClient.EditorHit hit) {
        return hit == null ? null : new PlanePoint(hit.key().face(), hit.planeU(), hit.planeV());
    }

    private PlanePoint planePoint(PaintOverlayStore.FaceKey key, int x, int y) {
        if (key == null) {
            return null;
        }
        BlockPos pos = key.pos();
        int size = PaintOverlayStore.SIZE;
        int u;
        int v;
        switch (key.face()) {
            case UP -> {
                u = pos.getX() * size + x;
                v = pos.getZ() * size + y;
            }
            case DOWN -> {
                u = pos.getX() * size + x;
                v = pos.getZ() * size + (size - 1 - y);
            }
            case NORTH -> {
                u = pos.getX() * size + (size - 1 - x);
                v = pos.getY() * size + (size - 1 - y);
            }
            case SOUTH -> {
                u = pos.getX() * size + x;
                v = pos.getY() * size + (size - 1 - y);
            }
            case WEST -> {
                u = pos.getZ() * size + x;
                v = pos.getY() * size + (size - 1 - y);
            }
            case EAST -> {
                u = pos.getZ() * size + (size - 1 - x);
                v = pos.getY() * size + (size - 1 - y);
            }
            default -> {
                u = x;
                v = y;
            }
        }
        return new PlanePoint(key.face(), u, v);
    }

    private PaintOverlayStore.FaceKey keyAtPlane(PaintOverlayStore.FaceKey anchor, Direction face, int u, int v) {
        if (anchor == null) {
            return null;
        }
        int size = PaintOverlayStore.SIZE;
        int blockU = Math.floorDiv(u, size);
        int blockV = Math.floorDiv(v, size);
        BlockPos pos = switch (face) {
            case UP, DOWN -> new BlockPos(blockU, anchor.pos().getY(), blockV);
            case NORTH, SOUTH -> new BlockPos(blockU, blockV, anchor.pos().getZ());
            case WEST, EAST -> new BlockPos(anchor.pos().getX(), blockV, blockU);
        };
        return new PaintOverlayStore.FaceKey(pos, face);
    }

    private int localX(Direction face, int u) {
        int local = Math.floorMod(u, PaintOverlayStore.SIZE);
        return switch (face) {
            case NORTH, EAST -> PaintOverlayStore.SIZE - 1 - local;
            default -> local;
        };
    }

    private int localY(Direction face, int v) {
        int local = Math.floorMod(v, PaintOverlayStore.SIZE);
        return switch (face) {
            case UP -> local;
            default -> PaintOverlayStore.SIZE - 1 - local;
        };
    }

    private int clampSpanEnd(int start, int current) {
        return MathHelper.clamp(current, start - MAX_EDITOR_PIXEL_SPAN + 1, start + MAX_EDITOR_PIXEL_SPAN - 1);
    }

    private boolean samePlane(PaintOverlayStore.FaceKey a, PaintOverlayStore.FaceKey b) {
        if (a == null || b == null || a.face() != b.face()) {
            return false;
        }
        return switch (a.face()) {
            case UP, DOWN -> a.pos().getY() == b.pos().getY();
            case NORTH, SOUTH -> a.pos().getZ() == b.pos().getZ();
            case WEST, EAST -> a.pos().getX() == b.pos().getX();
        };
    }

    public PaintEditorScreen() {
        super(Text.literal("画板"));
        loadSelectedColor();
    }

    @Override
    protected void init() {
        PaintOverlayClient.enterPaintEditor(client);
        int rightX = rightX();
        hexField = addDrawableChild(new TextFieldWidget(textRenderer, rightX + 16, BRUSH_HEX_Y, 86, 18, Text.literal("十六进制")));
        hexField.setMaxLength(7);
        hexField.setTextPredicate(PaintEditorScreen::isValidHexInput);
        hexField.setChangedListener(this::onHexChanged);
        updateHexField();
        loadPresetDraftsFromBrush();
        uploadColorMapIfNeeded();
    }

    @Override
    public void tick() {
        if (client == null || client.player == null) {
            close();
            return;
        }
        PaintOverlayClient.enterPaintEditor(client);
        long handle = client.getWindow().getHandle();
        double forward = 0.0D;
        double strafe = 0.0D;
        double vertical = 0.0D;
        if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS) {
            forward += 0.36D;
        }
        if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS) {
            forward -= 0.36D;
        }
        if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS) {
            strafe -= 0.36D;
        }
        if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS) {
            strafe += 0.36D;
        }
        if (client.options.jumpKey.isPressed() || isKeyDown(handle, GLFW.GLFW_KEY_SPACE)) {
            vertical += 0.36D;
        }
        if (client.options.sneakKey.isPressed()
                || isKeyDown(handle, GLFW.GLFW_KEY_LEFT_SHIFT)
                || isKeyDown(handle, GLFW.GLFW_KEY_RIGHT_SHIFT)) {
            vertical -= 0.36D;
        }
        if (forward != 0.0D || strafe != 0.0D || vertical != 0.0D) {
            PaintOverlayClient.setEditorViewVelocity(client, forward, strafe, vertical);
        } else {
            client.player.setVelocity(0.0D, 0.0D, 0.0D);
        }
        if (rightUsing) {
            PaintOverlayClient.performEditorUseAtScreenPoint(client, selectedTool, lastMouseX, lastMouseY, width, height);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        updateWorldPreview(mouseX, mouseY);
        updateResizeCursor(mouseX, mouseY);
        uploadColorMapIfNeeded();
        drawLeftPanel(context, mouseX, mouseY);
        drawRightPanel(context, mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
        drawPaintToolOverlay(context, mouseX, mouseY);
        drawSelectionMenu(context, mouseX, mouseY);
        drawPresetStoreContextMenu(context, mouseX, mouseY);
        drawHistoryContextMenu(context, mouseX, mouseY);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    protected void applyBlur(DrawContext context) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handlePresetStoreContextMenuClick(mouseX, mouseY, button)) {
            return true;
        }
        if (presetStoreMenuOpen) {
            closePresetStoreContextMenu();
            return true;
        }
        if (handleHistoryContextMenuClick(mouseX, mouseY, button)) {
            return true;
        }
        if (historyMenuIndex >= 0) {
            closeHistoryContextMenu();
            return true;
        }
        boolean viewModifier = isShiftDown()
                && (selectedTool == PaintOverlayClient.EditorTool.SELECT || selectedTool == PaintOverlayClient.EditorTool.SHAPE);
        if (!viewModifier && handleSelectionMenuClick(mouseX, mouseY, button)) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            closeHistoryContextMenu();
            PaintOverlayClient.handleBrushPickInputAtScreenPoint(client, mouseX, mouseY, width, height);
            loadSelectedColor();
            updateHexField();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (handleRecentPresetStoreRightClick(mouseX, mouseY)) {
                return true;
            }
            if (handleHistoryRightClick(mouseX, mouseY)) {
                return true;
            }
            closeHistoryContextMenu();
            if (!viewModifier && selectedTool == PaintOverlayClient.EditorTool.SELECT && (selection != null || clipboardSelection != null)) {
                if (selectionPreviewActive) {
                    cancelSelectionPreview();
                    return true;
                }
                selectMenuOpen = true;
                selectMenuX = (int) mouseX;
                selectMenuY = (int) mouseY;
                return true;
            }
            if (selectedTool != PaintOverlayClient.EditorTool.NONE
                    && selectedTool != PaintOverlayClient.EditorTool.PRESET
                    && selectedTool != PaintOverlayClient.EditorTool.SELECT
                    && selectedTool != PaintOverlayClient.EditorTool.SHAPE
                    && !isShiftDown()) {
                rightUsing = true;
                PaintOverlayClient.performEditorUseAtScreenPoint(client, selectedTool, mouseX, mouseY, width, height);
                return true;
            }
            if (selectedTool == PaintOverlayClient.EditorTool.NONE
                    || selectedTool == PaintOverlayClient.EditorTool.PRESET
                    || isShiftDown()) {
                rightPanning = true;
                rightPanAnchor = PaintOverlayClient.createEditorPanAnchor(client, mouseX, mouseY, width, height);
                return true;
            }
            return false;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            closeHistoryContextMenu();
            return super.mouseClicked(mouseX, mouseY, button);
        }
        closeHistoryContextMenu();
        if (hexField != null && hexField.visible && hexField.isMouseOver(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (handleLeftPanelClick(mouseX, mouseY) || handleRightPanelClick(mouseX, mouseY)) {
            return true;
        }
        if (viewModifier) {
            leftLooking = true;
            return true;
        }
        if (selectedTool == PaintOverlayClient.EditorTool.SELECT && handleSelectionLeftClick(mouseX, mouseY)) {
            return true;
        }
        if (selectedTool == PaintOverlayClient.EditorTool.SHAPE && handleShapeLeftClick(mouseX, mouseY)) {
            return true;
        }
        leftLooking = true;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (hexField != null && hexField.visible && hexField.isFocused()) {
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        if (pickingColor && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (selectedTool == PaintOverlayClient.EditorTool.PRESET) {
                pickPresetColor(mouseX, mouseY, true);
            } else if (selectedTool == PaintOverlayClient.EditorTool.SHAPE) {
                pickColorAt(mouseX, mouseY, rightX() + 16, SHAPE_COLOR_Y, true);
            } else {
                pickColor(mouseX, mouseY, true);
            }
            return true;
        }
        if (draggingAlpha && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            updateAlphaFromMouse(mouseX);
            return true;
        }
        if (draggingRadius && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            updateRadiusFromMouse(mouseX, draggingRadiusTool);
            return true;
        }
        if (draggingShapeStroke && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            updateShapeStrokeFromMouse(mouseX);
            return true;
        }
        if ((selectedTool == PaintOverlayClient.EditorTool.SELECT || selectedTool == PaintOverlayClient.EditorTool.SHAPE)
                && dragMode != DragMode.NONE
                && button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && !isShiftDown()) {
            updatePaintToolDrag(mouseX, mouseY);
            return true;
        }
        if (leftLooking && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            PaintOverlayClient.rotateEditorView(client, deltaX, deltaY,
                    selectedTool == PaintOverlayClient.EditorTool.NONE || isShiftDown() ? 0.18F : 0.34F);
            return true;
        }
        if (rightUsing && button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            performEditorUseInterpolated(mouseX, mouseY, deltaX, deltaY);
            return true;
        }
        if (rightPanning && button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (rightPanAnchor == null) {
                rightPanAnchor = PaintOverlayClient.createEditorPanAnchor(client, mouseX - deltaX, mouseY - deltaY, width, height);
            }
            PaintOverlayClient.panEditorViewToScreenPoint(client, rightPanAnchor, mouseX, mouseY, width, height);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (hexField != null && hexField.visible && hexField.isFocused() && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseReleased(mouseX, mouseY, button);
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if ((selectedTool == PaintOverlayClient.EditorTool.SELECT || selectedTool == PaintOverlayClient.EditorTool.SHAPE)
                    && dragMode != DragMode.NONE) {
                finishPaintToolDrag(mouseX, mouseY);
                return true;
            }
            if (draggingAlpha) {
                updateAlphaFromMouse(mouseX);
                draggingAlpha = false;
                return true;
            }
            if (draggingRadius) {
                updateRadiusFromMouse(mouseX, draggingRadiusTool);
                draggingRadius = false;
                draggingRadiusTool = PaintOverlayClient.EditorTool.NONE;
                return true;
            }
            if (draggingShapeStroke) {
                updateShapeStrokeFromMouse(mouseX);
                draggingShapeStroke = false;
                return true;
            }
            if (pickingColor) {
                if (selectedTool == PaintOverlayClient.EditorTool.PRESET) {
                    stageSelectedPresetColor();
                } else {
                    PaintOverlayClient.recordPickedColor(PaintOverlayClient.selectedColor());
                }
            }
            pickingColor = false;
            leftLooking = false;
            PaintOverlayClient.stopEditorUse();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            rightUsing = false;
            rightPanning = false;
            rightPanAnchor = null;
            PaintOverlayClient.stopEditorUse();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void performEditorUseInterpolated(double mouseX, double mouseY, double deltaX, double deltaY) {
        double startX = mouseX - deltaX;
        double startY = mouseY - deltaY;
        int steps = MathHelper.clamp((int) Math.ceil(Math.max(Math.abs(deltaX), Math.abs(deltaY)) / 2.0D), 1, 12);
        for (int step = 1; step <= steps; step++) {
            double t = (double) step / (double) steps;
            PaintOverlayClient.performEditorUseAtScreenPoint(
                    client,
                    selectedTool,
                    MathHelper.lerp(t, startX, mouseX),
                    MathHelper.lerp(t, startY, mouseY),
                    width,
                    height
            );
        }
    }

    private boolean isCtrlDown() {
        if (client == null || client.getWindow() == null) {
            return false;
        }
        long handle = client.getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private boolean isShiftDown() {
        if (client == null || client.getWindow() == null) {
            return false;
        }
        long handle = client.getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    private PaintOverlayClient.EditorHit editorHitForTool(double mouseX, double mouseY) {
        PaintOverlayStore.FaceKey anchor = dragKey != null ? dragKey
                : selectedTool == PaintOverlayClient.EditorTool.SELECT && selection != null ? selection.key : null;
        PaintOverlayClient.EditorHit hit = PaintOverlayClient.editorPlaneHitAtScreenPoint(client, mouseX, mouseY, width, height, anchor);
        return hit != null ? hit : PaintOverlayClient.editorHitAtScreenPoint(client, mouseX, mouseY, width, height);
    }

    private DragMode hoveredSelectionHandle(double mouseX, double mouseY) {
        if (selectedTool != PaintOverlayClient.EditorTool.SELECT || selection == null || !selection.showHandles || isShiftDown()) {
            return DragMode.NONE;
        }
        return selectionHandleAtScreen(mouseX, mouseY);
    }

    private void updateResizeCursor(double mouseX, double mouseY) {
        DragMode mode = hoveredSelectionHandle(mouseX, mouseY);
        int shape = resizeCursorShape(mode);
        if (shape == activeCursorShape || client == null || client.getWindow() == null) {
            return;
        }
        activeCursorShape = shape;
        long window = client.getWindow().getHandle();
        GLFW.glfwSetCursor(window, cursorForShape(shape));
    }

    private int resizeCursorShape(DragMode mode) {
        return switch (mode) {
            case SELECT_RESIZE_N, SELECT_RESIZE_S -> GLFW.GLFW_VRESIZE_CURSOR;
            case SELECT_RESIZE_E, SELECT_RESIZE_W -> GLFW.GLFW_HRESIZE_CURSOR;
            case SELECT_RESIZE_NW, SELECT_RESIZE_SE -> glfwCursorShape("GLFW_RESIZE_NWSE_CURSOR", GLFW.GLFW_HRESIZE_CURSOR);
            case SELECT_RESIZE_NE, SELECT_RESIZE_SW -> glfwCursorShape("GLFW_RESIZE_NESW_CURSOR", GLFW.GLFW_HRESIZE_CURSOR);
            default -> 0;
        };
    }

    private static int glfwCursorShape(String field, int fallback) {
        try {
            return GLFW.class.getField(field).getInt(null);
        } catch (ReflectiveOperationException ignored) {
            return fallback;
        }
    }

    private long cursorForShape(int shape) {
        if (shape == 0) {
            return 0L;
        }
        if (shape == GLFW.GLFW_HRESIZE_CURSOR) {
            if (horizontalResizeCursor == 0L) horizontalResizeCursor = GLFW.glfwCreateStandardCursor(shape);
            return horizontalResizeCursor;
        }
        if (shape == GLFW.GLFW_VRESIZE_CURSOR) {
            if (verticalResizeCursor == 0L) verticalResizeCursor = GLFW.glfwCreateStandardCursor(shape);
            return verticalResizeCursor;
        }
        if (shape == glfwCursorShape("GLFW_RESIZE_NWSE_CURSOR", -1)) {
            if (nwseResizeCursor == 0L) nwseResizeCursor = GLFW.glfwCreateStandardCursor(shape);
            return nwseResizeCursor;
        }
        if (shape == glfwCursorShape("GLFW_RESIZE_NESW_CURSOR", -1)) {
            if (neswResizeCursor == 0L) neswResizeCursor = GLFW.glfwCreateStandardCursor(shape);
            return neswResizeCursor;
        }
        return 0L;
    }

    private void resetCursor() {
        if (activeCursorShape != 0 && client != null && client.getWindow() != null) {
            GLFW.glfwSetCursor(client.getWindow().getHandle(), 0L);
        }
        activeCursorShape = 0;
    }

    private void destroyResizeCursors() {
        if (horizontalResizeCursor != 0L) GLFW.glfwDestroyCursor(horizontalResizeCursor);
        if (verticalResizeCursor != 0L) GLFW.glfwDestroyCursor(verticalResizeCursor);
        if (nwseResizeCursor != 0L) GLFW.glfwDestroyCursor(nwseResizeCursor);
        if (neswResizeCursor != 0L) GLFW.glfwDestroyCursor(neswResizeCursor);
        horizontalResizeCursor = 0L;
        verticalResizeCursor = 0L;
        nwseResizeCursor = 0L;
        neswResizeCursor = 0L;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (handleValueScroll(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        PaintOverlayClient.moveEditorViewTowardScreenPoint(client, mouseX, mouseY, width, height,
//                Math.signum(verticalAmount) * 0.45D
        verticalAmount * 0.45D
        );
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && keyCode == GLFW.GLFW_KEY_Z) {
            PaintOverlayClient.undoEditorStroke(client);
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_Y) {
            PaintOverlayClient.redoEditorStroke(client);
            return true;
        }
        if (hexField != null && hexField.visible && hexField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_B || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            PaintOverlayClient.markPaintEditorKeyConsumed();
            close();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_R && selectedTool == PaintOverlayClient.EditorTool.SELECT) {
            rotateSelectionClockwise();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_C) {
            copySelection();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
            pasteSelection();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE && selectedTool == PaintOverlayClient.EditorTool.SELECT) {
            deleteSelection();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_1) {
            toggleTool(PaintOverlayClient.EditorTool.BRUSH);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_2) {
            toggleTool(PaintOverlayClient.EditorTool.ERASER);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_3) {
            toggleTool(PaintOverlayClient.EditorTool.PAPER);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_4) {
            toggleTool(PaintOverlayClient.EditorTool.PRESET);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_5) {
            toggleTool(PaintOverlayClient.EditorTool.NONE);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_6) {
            toggleTool(PaintOverlayClient.EditorTool.SELECT);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_7) {
            toggleTool(PaintOverlayClient.EditorTool.SHAPE);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_W || keyCode == GLFW.GLFW_KEY_S) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static boolean isKeyDown(long handle, int keyCode) {
        return GLFW.glfwGetKey(handle, keyCode) == GLFW.GLFW_PRESS;
    }

    private void toggleTool(PaintOverlayClient.EditorTool tool) {
        selectedTool = selectedTool == tool ? PaintOverlayClient.EditorTool.NONE : tool;
        selectMenuOpen = false;
        dragMode = DragMode.NONE;
        PaintOverlayClient.syncSelectedRadius(selectedTool);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return hexField != null && hexField.visible && hexField.charTyped(chr, modifiers) || super.charTyped(chr, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        resetCursor();
        if (PaintOverlayClient.isSuspendedPaintEditor(this)) {
            return;
        }
        if (colorMapTexture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(COLOR_MAP_TEXTURE_ID);
            colorMapTexture = null;
        }
        destroyResizeCursors();
        PaintOverlayClient.exitPaintEditor(MinecraftClient.getInstance());
        PaintOverlayClient.clearEditorPreview();
    }

    private void drawLeftPanel(DrawContext context, int mouseX, int mouseY) {
        context.fill(0, 0, LEFT_WIDTH, height, SURFACE);
        context.fill(LEFT_WIDTH - 1, 0, LEFT_WIDTH, height, OUTLINE);
        context.fill(12, 8, 42, 9, ACCENT);
        context.drawText(textRenderer, Text.literal("工具"), 10, 14, TEXT_SECONDARY, false);
        drawToolButton(context, TOOL_X, TOOL_BRUSH_Y, PaintOverlayClient.EditorTool.BRUSH, new ItemStack(PaintItems.PAINT_BRUSH), mouseX, mouseY);
        drawToolButton(context, TOOL_X, TOOL_ERASER_Y, PaintOverlayClient.EditorTool.ERASER, new ItemStack(PaintItems.ERASER), mouseX, mouseY);
        drawToolButton(context, TOOL_X, TOOL_PAPER_Y, PaintOverlayClient.EditorTool.PAPER, new ItemStack(PaintItems.PAINT_PAPER), mouseX, mouseY);
        drawToolButton(context, TOOL_X, TOOL_PRESET_Y, PaintOverlayClient.EditorTool.PRESET, ItemStack.EMPTY, mouseX, mouseY);
        drawToolButton(context, TOOL_X, TOOL_SELECT_Y, PaintOverlayClient.EditorTool.SELECT, ItemStack.EMPTY, mouseX, mouseY);
        drawToolButton(context, TOOL_X, TOOL_SHAPE_Y, PaintOverlayClient.EditorTool.SHAPE, ItemStack.EMPTY, mouseX, mouseY);
        drawToolButton(context, TOOL_X, TOOL_NONE_Y, PaintOverlayClient.EditorTool.NONE, ItemStack.EMPTY, mouseX, mouseY);
    }

    private void drawToolButton(DrawContext context, int x, int y, PaintOverlayClient.EditorTool tool, ItemStack stack, int mouseX, int mouseY) {
        boolean selected = selectedTool == tool;
        boolean hover = isInside(x, y, 34, 34, mouseX, mouseY);
        drawControlSurface(context, x, y, 34, 34, selected, hover);
        if (selected) {
            context.fill(x + 1, y + CHAMFER, x + 4, y + 34 - CHAMFER, ACCENT);
        }
        if (tool == PaintOverlayClient.EditorTool.PRESET) {
            int size = 5;
            int startX = x + 9;
            int startY = y + 9;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    int color = presetDraftFilled[row * 3 + col] ? presetDraftColors[row * 3 + col] : 0xFF3A424C;
                    context.fill(startX + col * 6, startY + row * 6, startX + col * 6 + size, startY + row * 6 + size, color);
                }
            }
        } else if (tool == PaintOverlayClient.EditorTool.SELECT) {
            drawDashedBorder(context, x + 8, y + 8, 18, 18, TEXT_PRIMARY);
        } else if (tool == PaintOverlayClient.EditorTool.SHAPE) {
            context.fill(x + 8, y + 10, x + 26, y + 12, TEXT_PRIMARY);
            context.fill(x + 8, y + 24, x + 26, y + 26, TEXT_PRIMARY);
            context.fill(x + 8, y + 10, x + 10, y + 26, TEXT_PRIMARY);
            context.fill(x + 24, y + 10, x + 26, y + 26, TEXT_PRIMARY);
        } else if (stack.isEmpty()) {
            context.fill(x + 11, y + 16, x + 23, y + 18, TEXT_PRIMARY);
        } else {
            context.drawItem(stack, x + 9, y + 9);
        }
    }

    private void drawRightPanel(DrawContext context, int mouseX, int mouseY) {
        int x = rightX();
        context.fill(x, 0, width, height, SURFACE);
        context.fill(x, 0, x + 1, height, OUTLINE);
        context.fill(x + 14, 9, x + 72, 10, ACCENT);
        context.drawText(textRenderer, Text.literal("画板"), x + 16, 16, TEXT_PRIMARY, false);
        context.drawText(textRenderer, Text.literal(selectedToolName()), x + 16, 28, TEXT_SECONDARY, false);
        if (hexField != null) {
            hexField.visible = selectedTool == PaintOverlayClient.EditorTool.BRUSH;
        }
        switch (selectedTool) {
            case BRUSH -> drawBrushPanel(context, x + 16);
            case ERASER -> drawEraserPanel(context, x + 16);
            case PAPER -> drawPaperPanel(context, x + 16);
            case PRESET -> drawPresetEditorPanel(context, x + 16, mouseX, mouseY);
            case SELECT -> drawSelectPanel(context, x + 16);
            case SHAPE -> drawShapePanel(context, x + 16, mouseX, mouseY);
            case NONE -> drawViewPanel(context, x + 16);
        }
    }

    private void drawBrushPanel(DrawContext context, int x) {
        drawSection(context, x - 8, 48, 166, 228);
        drawSectionTitle(context, x, 56, "颜色");
        drawColorMap(context, x, BRUSH_COLOR_Y);
        drawHueBar(context, x + MAP_SIZE + 8, BRUSH_COLOR_Y);
        drawColorCursor(context, x, BRUSH_COLOR_Y);

        int color = PaintOverlayClient.selectedColor();
        drawSwatch(context, x, BRUSH_SWATCH_Y, 28, color, true);
        drawStar(context, x, BRUSH_SWATCH_Y, 28, PaintOverlayClient.isFavoriteColor(color));
        context.drawText(textRenderer, Text.literal(toHex(color)), x + 40, 192, TEXT_PRIMARY, false);

        context.drawText(textRenderer, Text.literal("色值"), x, 216, TEXT_SECONDARY, false);
        drawAlphaSlider(context, x, BRUSH_ALPHA_Y);
        drawSection(context, x - 8, 284, 166, 94);
        drawRecent(context, x, BRUSH_RECENT_Y);
        drawFavorites(context, x + BRUSH_FAVORITE_X_OFFSET, BRUSH_RECENT_Y);
        drawSection(context, x - 8, 390, 166, 46);
        drawRadiusControls(context, x, BRUSH_RADIUS_Y, PaintOverlayClient.EditorTool.BRUSH);
    }

    private void drawEraserPanel(DrawContext context, int x) {
        drawSection(context, x - 8, 48, 166, 112);
        drawSectionTitle(context, x, 56, "橡皮");
        drawRadiusControls(context, x, ERASER_RADIUS_Y, PaintOverlayClient.EditorTool.ERASER);
        drawHistoryPanel(context, x, lastMouseX, lastMouseY);
        int modeY = ERASER_MODE_Y;
        boolean faceMode = PaintOverlayClient.eraserFaceMode();
        drawToggle(context, x, modeY, 52, 18, "像素", !faceMode);
        drawToggle(context, x + 58, modeY, 52, 18, "整面", faceMode);
    }

    private void drawHistoryPanel(DrawContext context, int x, double mouseX, double mouseY) {
        int sectionY = ERASER_HISTORY_Y - 18;
        int sectionH = Math.max(72, height - sectionY - 16);
        drawSection(context, x - 8, sectionY, 166, sectionH);
        drawSectionTitle(context, x, sectionY + 8, "历史");

        List<PaintOverlayClient.EditorHistoryEntry> entries = PaintOverlayClient.editorHistory();
        int currentIndex = PaintOverlayClient.editorHistoryCurrentIndex();
        int listY = ERASER_HISTORY_Y + 12;
        int maxRows = Math.max(1, (height - listY - 24) / HISTORY_ROW_HEIGHT);
        if (entries.isEmpty()) {
            context.drawText(textRenderer, Text.literal("暂无历史"), x, listY + 4, TEXT_SECONDARY, false);
            return;
        }
        int start = historyStartIndex(entries.size(), currentIndex, maxRows);
        int end = Math.min(entries.size(), start + maxRows);
        for (int i = start; i < end; i++) {
            PaintOverlayClient.EditorHistoryEntry entry = entries.get(i);
            int rowY = historyRowY(i, start);
            boolean current = i == currentIndex;
            boolean menuSelected = i == historyMenuIndex;
            boolean hover = isInside(x - 2, rowY, 148, HISTORY_ROW_HEIGHT - 1, mouseX, mouseY);
            int fill = current ? ACCENT_DARK : (menuSelected ? 0x993F5F72 : (hover ? SURFACE_HOVER : 0));
            if (fill != 0) {
                fillChamfered(context, x - 2, rowY, 148, HISTORY_ROW_HEIGHT - 1, 3, fill);
            }
            if (current) {
                context.fill(x - 5, rowY + 3, x - 3, rowY + HISTORY_ROW_HEIGHT - 4, ACCENT);
            }
            String text = "#" + entry.sequence() + " " + entry.label();
            int textColor = current || menuSelected ? TEXT_PRIMARY : TEXT_SECONDARY;
            context.drawText(textRenderer, Text.literal(trimToWidth(text, 114)), x + 3, rowY + 4, textColor, false);
            String faces = String.valueOf(entry.changedFaces());
            context.drawText(textRenderer, Text.literal(faces), x + 138 - textRenderer.getWidth(faces), rowY + 4, textColor, false);
        }
    }

    private void drawHistoryContextMenu(DrawContext context, int mouseX, int mouseY) {
        if (historyMenuIndex < 0 || selectedTool != PaintOverlayClient.EditorTool.ERASER) {
            return;
        }
        int x = MathHelper.clamp(historyMenuX, 2, Math.max(2, width - HISTORY_MENU_WIDTH - 2));
        int y = MathHelper.clamp(historyMenuY, 2, Math.max(2, height - HISTORY_MENU_HEIGHT - 2));
        boolean hover = isInside(x, y, HISTORY_MENU_WIDTH, HISTORY_MENU_HEIGHT, mouseX, mouseY);
        drawControlSurface(context, x, y, HISTORY_MENU_WIDTH, HISTORY_MENU_HEIGHT, false, hover);
        context.drawText(textRenderer, Text.literal("跳转"), x + 8, y + 8, TEXT_PRIMARY, false);
    }

    private void drawSelectPanel(DrawContext context, int x) {
        drawSection(context, x - 8, 48, 166, 116);
        drawSectionTitle(context, x, 56, "选区");
        context.drawText(textRenderer, Text.literal("拖动：框选"), x, 82, TEXT_SECONDARY, false);
        context.drawText(textRenderer, Text.literal("Ctrl+R：旋转"), x, 100, TEXT_SECONDARY, false);
        context.drawText(textRenderer, Text.literal("右键：菜单"), x, 118, TEXT_SECONDARY, false);
        String size = selection == null ? "未选择" : selection.width() + " x " + selection.height();
        context.drawText(textRenderer, Text.literal(size), x, 140, selection == null ? TEXT_SECONDARY : TEXT_PRIMARY, false);
    }

    private void drawShapePanel(DrawContext context, int x, int mouseX, int mouseY) {
        drawSection(context, x - 8, 48, 166, 468);
        drawSectionTitle(context, x, 56, "形状");
        int y = SHAPE_MODE_Y;
        for (ShapeMode mode : ShapeMode.values()) {
            boolean active = shapeMode == mode;
            boolean hover = isInside(x, y, 118, 18, mouseX, mouseY);
            drawControlSurface(context, x, y, 118, 18, active, hover);
            context.drawText(textRenderer, Text.literal(mode.label), x + 8, y + 5, active ? TEXT_PRIMARY : TEXT_SECONDARY, false);
            y += 22;
        }
        context.drawText(textRenderer, Text.literal("颜色"), x, SHAPE_COLOR_Y - 14, TEXT_PRIMARY, false);
        drawColorMap(context, x, SHAPE_COLOR_Y);
        drawHueBar(context, x + MAP_SIZE + 8, SHAPE_COLOR_Y);
        drawColorCursor(context, x, SHAPE_COLOR_Y);
        int color = PaintOverlayClient.selectedColor();
        drawSwatch(context, x + 134, SHAPE_COLOR_Y, 24, color, true);
        context.drawText(textRenderer, Text.literal(toHex(color)), x + 122, SHAPE_COLOR_Y + 32, TEXT_PRIMARY, false);
        drawAlphaSlider(context, x, SHAPE_ALPHA_Y);
        drawShapeStrokeControls(context, x, SHAPE_STROKE_Y);
    }

    private boolean handleHistoryContextMenuClick(double mouseX, double mouseY, int button) {
        if (historyMenuIndex < 0) {
            return false;
        }
        int x = MathHelper.clamp(historyMenuX, 2, Math.max(2, width - HISTORY_MENU_WIDTH - 2));
        int y = MathHelper.clamp(historyMenuY, 2, Math.max(2, height - HISTORY_MENU_HEIGHT - 2));
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isInside(x, y, HISTORY_MENU_WIDTH, HISTORY_MENU_HEIGHT, mouseX, mouseY)) {
            PaintOverlayClient.jumpEditorHistory(client, historyMenuIndex);
            closeHistoryContextMenu();
            return true;
        }
        if (!isInside(x, y, HISTORY_MENU_WIDTH, HISTORY_MENU_HEIGHT, mouseX, mouseY)) {
            closeHistoryContextMenu();
        }
        return false;
    }

    private boolean handleHistoryRightClick(double mouseX, double mouseY) {
        if (selectedTool != PaintOverlayClient.EditorTool.ERASER) {
            return false;
        }
        int index = historyIndexAt(mouseX, mouseY);
        if (index < 0) {
            return false;
        }
        historyMenuIndex = index;
        historyMenuX = MathHelper.clamp((int) mouseX + 8, 2, Math.max(2, width - HISTORY_MENU_WIDTH - 2));
        historyMenuY = MathHelper.clamp((int) mouseY - 2, 2, Math.max(2, height - HISTORY_MENU_HEIGHT - 2));
        return true;
    }

    private void closeHistoryContextMenu() {
        historyMenuIndex = -1;
    }

    private int historyIndexAt(double mouseX, double mouseY) {
        int x = rightX() + 16;
        List<PaintOverlayClient.EditorHistoryEntry> entries = PaintOverlayClient.editorHistory();
        if (entries.isEmpty()) {
            return -1;
        }
        int listY = ERASER_HISTORY_Y + 12;
        int maxRows = Math.max(1, (height - listY - 24) / HISTORY_ROW_HEIGHT);
        int start = historyStartIndex(entries.size(), PaintOverlayClient.editorHistoryCurrentIndex(), maxRows);
        int end = Math.min(entries.size(), start + maxRows);
        if (!isInside(x - 2, listY, 148, (end - start) * HISTORY_ROW_HEIGHT, mouseX, mouseY)) {
            return -1;
        }
        int index = start + (int) ((mouseY - listY) / HISTORY_ROW_HEIGHT);
        return index >= start && index < end ? index : -1;
    }

    private int historyStartIndex(int size, int currentIndex, int maxRows) {
        if (size <= maxRows) {
            return 0;
        }
        int focus = currentIndex >= 0 ? currentIndex : size - 1;
        return MathHelper.clamp(focus - maxRows / 2, 0, size - maxRows);
    }

    private int historyRowY(int index, int start) {
        return ERASER_HISTORY_Y + 12 + (index - start) * HISTORY_ROW_HEIGHT;
    }

    private String trimToWidth(String text, int maxWidth) {
        if (textRenderer.getWidth(text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        int suffixWidth = textRenderer.getWidth(suffix);
        StringBuilder builder = new StringBuilder(text);
        while (!builder.isEmpty() && textRenderer.getWidth(builder.toString()) + suffixWidth > maxWidth) {
            builder.deleteCharAt(builder.length() - 1);
        }
        return builder + suffix;
    }

    private void drawPaperPanel(DrawContext context, int x) {
        drawSection(context, x - 8, 48, 166, 124);
        drawSectionTitle(context, x, 56, "纸张");
        int paperY = PAPER_SIZE_Y;
        drawSmallButton(context, x, paperY, 22, 18, "-");
        context.drawText(textRenderer, Text.literal("纸 " + PaintOverlayClient.selectedPaperSize()), x + 30, paperY + 5, TEXT_PRIMARY, false);
        drawSmallButton(context, x + 66, paperY, 22, 18, "+");
        drawSmallButton(context, x, paperY + 34, 88, 18, "导入");
    }

    private void drawViewPanel(DrawContext context, int x) {
        drawSection(context, x - 8, 48, 166, 80);
        drawSectionTitle(context, x, 56, "视角");
        context.drawText(textRenderer, Text.literal("自由视角"), x, 82, TEXT_SECONDARY, false);
    }

    private void drawPresetEditorPanel(DrawContext context, int x, int mouseX, int mouseY) {
        drawSection(context, x - 8, 48, 166, 184);
        drawSectionTitle(context, x, 56, "预设");
        int hoverSlot = presetEditorSlotAt(mouseX, mouseY, x);
        for (int slot = 0; slot < PaintBrushItem.COLOR_SLOTS; slot++) {
            drawPresetDraftSlot(context, presetEditorSlotX(x, slot), presetEditorSlotY(slot), slot, slot == hoverSlot);
        }

        drawSection(context, x - 8, 240, 166, 154);
        drawSectionTitle(context, x, 248, "取色");
        drawColorMap(context, x, PRESET_EDITOR_PICKER_Y);
        drawHueBar(context, x + MAP_SIZE + 8, PRESET_EDITOR_PICKER_Y);
        drawColorCursor(context, x, PRESET_EDITOR_PICKER_Y);
        int color = currentPresetDraftColor();
        drawSwatch(context, x + 134, PRESET_EDITOR_PICKER_Y, 24, color, true);
        context.drawText(textRenderer, Text.literal("色槽 " + (selectedPresetSlot + 1)), x + 134, PRESET_EDITOR_PICKER_Y + 30, TEXT_SECONDARY, false);
        context.drawText(textRenderer, Text.literal(toHex(color)), x, PRESET_EDITOR_PICKER_Y + MAP_SIZE + 10, TEXT_PRIMARY, false);
        drawSmallButton(context, x, PRESET_SAVE_Y, 118, 20, "保存");
    }

    private void drawColorMap(DrawContext context, int x, int y) {
        context.fill(x - 2, y - 2, x + MAP_SIZE + 2, y + MAP_SIZE + 2, 0x66000000);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, COLOR_MAP_TEXTURE_ID, x, y, 0.0F, 0.0F, MAP_SIZE, MAP_SIZE, MAP_SIZE, MAP_SIZE);
        drawBorder(context, x, y, MAP_SIZE, MAP_SIZE);
    }

    private void drawHueBar(DrawContext context, int x, int y) {
        context.fill(x - 2, y - 2, x + HUE_WIDTH + 2, y + MAP_SIZE + 2, 0x66000000);
        for (int row = 0; row < MAP_SIZE; row++) {
            int color = 0xFF000000 | hsvToRgb((float) row / (MAP_SIZE - 1), 1.0F, 1.0F);
            context.fill(x, y + row, x + HUE_WIDTH, y + row + 1, color);
        }
        drawBorder(context, x, y, HUE_WIDTH, MAP_SIZE);
    }

    private void drawColorCursor(DrawContext context, int x, int y) {
        int sx = x + Math.round(saturation * (MAP_SIZE - 1));
        int sy = y + Math.round((1.0F - value) * (MAP_SIZE - 1));
        context.fill(sx - 5, sy, sx + 6, sy + 1, 0xFF000000);
        context.fill(sx, sy - 5, sx + 1, sy + 6, 0xFF000000);
        context.fill(sx - 4, sy, sx + 5, sy + 1, 0xFFFFFFFF);
        context.fill(sx, sy - 4, sx + 1, sy + 5, 0xFFFFFFFF);

        int hueX = x + MAP_SIZE + 8;
        int hy = y + Math.round(hue * (MAP_SIZE - 1));
        context.fill(hueX - 3, hy - 2, hueX + HUE_WIDTH + 3, hy + 3, 0xFF000000);
        context.fill(hueX - 2, hy - 1, hueX + HUE_WIDTH + 2, hy + 2, 0xFFFFFFFF);
    }

    private void drawRecent(DrawContext context, int x, int y) {
        context.drawText(textRenderer, Text.literal("最近"), x, y - 12, TEXT_PRIMARY, false);
        List<Integer> recent = PaintOverlayClient.recentColors();
        for (int i = 0; i < 3; i++) {
            int slotX = x + i * 24;
            int color = i < recent.size() ? recent.get(i) : 0;
            drawSwatch(context, slotX, y, SWATCH, color, i < recent.size());
            if (i < recent.size()) {
                drawStar(context, slotX, y, SWATCH, PaintOverlayClient.isFavoriteColor(color));
            }
        }
    }

    private void drawFavorites(DrawContext context, int x, int y) {
        context.drawText(textRenderer, Text.literal("收藏"), x, y - 12, TEXT_PRIMARY, false);
        List<Integer> favorites = PaintOverlayClient.favoriteColors();
        int max = Math.min(9, favorites.size());
        for (int i = 0; i < 9; i++) {
            int slotX = x + (i % 3) * 24;
            int slotY = y + (i / 3) * 24;
            int color = i < max ? favorites.get(i) : 0;
            drawSwatch(context, slotX, slotY, SWATCH, color, i < max);
            if (i < max) {
                drawStar(context, slotX, slotY, SWATCH, true);
            }
        }
    }

    private void drawAlphaSlider(DrawContext context, int x, int y) {
        int alpha = PaintOverlayClient.selectedAlpha();
        context.drawText(textRenderer, Text.literal("不透明度 " + Math.round(alpha * 100.0F / 255.0F) + "%"), x, y - 10, TEXT_SECONDARY, false);
        drawChecker(context, x, y, 118, 10, 5);
        context.fill(x, y, x + 118, y + 10, PaintOverlayClient.selectedColor());
        drawBorder(context, x, y, 118, 10);
        int knobX = x + Math.round(alpha * 118.0F / 255.0F);
        context.fill(knobX - 2, y - 3, knobX + 3, y + 13, 0xFF0B1016);
        context.fill(knobX - 1, y - 2, knobX + 2, y + 12, 0xFFFFFFFF);
    }

    private void drawPresetStoreContextMenu(DrawContext context, int mouseX, int mouseY) {
        if (!presetStoreMenuOpen) {
            return;
        }
        int x = clampedPresetMenuX();
        int y = clampedPresetMenuY();
        int height = PaintBrushItem.COLOR_SLOTS * PRESET_MENU_ROW_HEIGHT;
        fillChamfered(context, x, y, PRESET_MENU_WIDTH, height, CHAMFER, 0xEE111820);
        drawBorder(context, x, y, PRESET_MENU_WIDTH, height);
        for (int slot = 0; slot < PaintBrushItem.COLOR_SLOTS; slot++) {
            int rowY = y + slot * PRESET_MENU_ROW_HEIGHT;
            boolean hover = isInside(x, rowY, PRESET_MENU_WIDTH, PRESET_MENU_ROW_HEIGHT, mouseX, mouseY);
            if (hover) {
                context.fill(x + 1, rowY + 1, x + PRESET_MENU_WIDTH - 1, rowY + PRESET_MENU_ROW_HEIGHT - 1, SURFACE_HOVER);
            }
            context.drawText(textRenderer, Text.literal("\u5B58\u5165\u7B2C" + (slot + 1) + "\u8272\u69FD"), x + 8, rowY + 5, TEXT_PRIMARY, false);
        }
    }

    private void drawPresetDraftSlot(DrawContext context, int x, int y, int slot, boolean hover) {
        boolean filled = presetDraftFilled[slot];
        int color = filled ? presetDraftColors[slot] : 0;
        int border = slot == selectedPresetSlot ? 0xFFFFFFFF : (hover ? 0xAA8DDAFF : 0x66000000);
        context.fill(x - 2, y - 2, x + PRESET_SLOT_SIZE + 2, y + PRESET_SLOT_SIZE + 2, border);
        drawChecker(context, x, y, PRESET_SLOT_SIZE, PRESET_SLOT_SIZE, 6);
        if (filled) {
            fillChamfered(context, x, y, PRESET_SLOT_SIZE, PRESET_SLOT_SIZE, Math.min(4, CHAMFER), color);
            String hex = toHex(color);
            context.fill(x + 2, y + PRESET_SLOT_SIZE - 12, x + PRESET_SLOT_SIZE - 2, y + PRESET_SLOT_SIZE - 2, 0x99000000);
            context.drawText(textRenderer, Text.literal(hex), x + Math.max(2, (PRESET_SLOT_SIZE - textRenderer.getWidth(hex)) / 2), y + PRESET_SLOT_SIZE - 10, 0xFFFFFFFF, false);
        } else {
            context.fill(x, y, x + PRESET_SLOT_SIZE, y + PRESET_SLOT_SIZE, 0x665A6068);
            drawDashedBorder(context, x, y, PRESET_SLOT_SIZE, PRESET_SLOT_SIZE, 0xFFD4D9DF);
        }
        drawBorder(context, x, y, PRESET_SLOT_SIZE, PRESET_SLOT_SIZE);
        context.drawText(textRenderer, Text.literal(String.valueOf(slot + 1)), x + 3, y + 3, filled ? 0xDDFFFFFF : 0xFF30343A, false);
    }

    private void drawRadiusControls(DrawContext context, int x, int y, PaintOverlayClient.EditorTool tool) {
        int radius = PaintOverlayClient.selectedRadius(tool);
        int trackX = x + RADIUS_TRACK_X_OFFSET;
        int trackY = y + 8;
        float t = (radius - PaintOverlayFeature.MIN_RADIUS)
                / (float) (PaintOverlayFeature.MAX_RADIUS - PaintOverlayFeature.MIN_RADIUS);
        int knobX = trackX + Math.round(t * RADIUS_TRACK_WIDTH);
        boolean hoverTrack = isInside(trackX - 6, y - 4, RADIUS_TRACK_WIDTH + 12, 26, lastMouseX, lastMouseY);
        drawSmallButton(context, x, y, 22, 18, "-");
        context.fill(trackX, trackY, trackX + RADIUS_TRACK_WIDTH, trackY + 2, 0x664C5968);
        context.fill(trackX, trackY, knobX, trackY + 2, ACCENT);
        context.drawText(textRenderer, Text.literal("尺寸 " + radius), trackX, y - 8, TEXT_SECONDARY, false);
        if (hoverTrack || draggingRadius && draggingRadiusTool == tool) {
            drawFilledCircle(context, knobX, trackY + 1, 5, 0xFFFFFFFF);
            drawFilledCircle(context, knobX, trackY + 1, 3, ACCENT);
        } else {
            drawFilledCircle(context, knobX, trackY + 1, 3, 0xFFE8F6FF);
        }
        drawSmallButton(context, x + 130, y, 22, 18, "+");
    }

    private void drawShapeStrokeControls(DrawContext context, int x, int y) {
        int trackX = x + RADIUS_TRACK_X_OFFSET;
        int trackY = y + 8;
        float t = (shapeStrokeWidth - MIN_SHAPE_STROKE) / (float) (MAX_SHAPE_STROKE - MIN_SHAPE_STROKE);
        int knobX = trackX + Math.round(t * RADIUS_TRACK_WIDTH);
        boolean hoverTrack = isInside(trackX - 6, y - 4, RADIUS_TRACK_WIDTH + 12, 26, lastMouseX, lastMouseY);
        drawSmallButton(context, x, y, 22, 18, "-");
        context.fill(trackX, trackY, trackX + RADIUS_TRACK_WIDTH, trackY + 2, 0x664C5968);
        context.fill(trackX, trackY, knobX, trackY + 2, ACCENT);
        context.drawText(textRenderer, Text.literal("粗细 " + shapeStrokeWidth), trackX, y - 8, TEXT_SECONDARY, false);
        if (hoverTrack || draggingShapeStroke) {
            drawFilledCircle(context, knobX, trackY + 1, 5, 0xFFFFFFFF);
            drawFilledCircle(context, knobX, trackY + 1, 3, ACCENT);
        } else {
            drawFilledCircle(context, knobX, trackY + 1, 3, 0xFFE8F6FF);
        }
        drawSmallButton(context, x + 130, y, 22, 18, "+");
    }

    private boolean handleLeftPanelClick(double mouseX, double mouseY) {
        if (!isInside(0, 0, LEFT_WIDTH, height, mouseX, mouseY)) {
            return false;
        }
        PaintOverlayClient.EditorTool clicked = toolAt(mouseX, mouseY);
        if (clicked != null) {
            toggleTool(clicked);
        }
        return true;
    }

    private PaintOverlayClient.EditorTool toolAt(double mouseX, double mouseY) {
        if (isInside(TOOL_X, TOOL_BRUSH_Y, 34, 34, mouseX, mouseY)) return PaintOverlayClient.EditorTool.BRUSH;
        if (isInside(TOOL_X, TOOL_ERASER_Y, 34, 34, mouseX, mouseY)) return PaintOverlayClient.EditorTool.ERASER;
        if (isInside(TOOL_X, TOOL_PAPER_Y, 34, 34, mouseX, mouseY)) return PaintOverlayClient.EditorTool.PAPER;
        if (isInside(TOOL_X, TOOL_PRESET_Y, 34, 34, mouseX, mouseY)) return PaintOverlayClient.EditorTool.PRESET;
        if (isInside(TOOL_X, TOOL_SELECT_Y, 34, 34, mouseX, mouseY)) return PaintOverlayClient.EditorTool.SELECT;
        if (isInside(TOOL_X, TOOL_SHAPE_Y, 34, 34, mouseX, mouseY)) return PaintOverlayClient.EditorTool.SHAPE;
        if (isInside(TOOL_X, TOOL_NONE_Y, 34, 34, mouseX, mouseY)) return PaintOverlayClient.EditorTool.NONE;
        return null;
    }

    private boolean handleRightPanelClick(double mouseX, double mouseY) {
        int x = rightX() + 16;
        if (!isInside(rightX(), 0, RIGHT_WIDTH, height, mouseX, mouseY)) {
            return false;
        }
        switch (selectedTool) {
            case BRUSH -> {
                if (pickColor(mouseX, mouseY, false)) {
                    pickingColor = true;
                    return true;
                }
                if (handleAlphaSliderClick(mouseX, mouseY, x)) {
                    draggingAlpha = true;
                    return true;
                }
                if (handleSwatches(mouseX, mouseY, x)) {
                    return true;
                }
                if (handleRadiusClick(mouseX, mouseY, x, BRUSH_RADIUS_Y, PaintOverlayClient.EditorTool.BRUSH)) {
                    return true;
                }
                return true;
            }
            case ERASER -> {
                if (handleRadiusClick(mouseX, mouseY, x, ERASER_RADIUS_Y, PaintOverlayClient.EditorTool.ERASER)) {
                    return true;
                }
                if (isInside(x, ERASER_MODE_Y, 52, 18, mouseX, mouseY)) {
                    PaintOverlayClient.setEraserFaceMode(false);
                    return true;
                }
                if (isInside(x + 58, ERASER_MODE_Y, 52, 18, mouseX, mouseY)) {
                    PaintOverlayClient.setEraserFaceMode(true);
                    return true;
                }
                return true;
            }
            case PAPER -> {
                if (isInside(x, PAPER_SIZE_Y, 22, 18, mouseX, mouseY)) {
                    PaintOverlayClient.setSelectedPaperSize(PaintOverlayClient.selectedPaperSize() - 1);
                    return true;
                }
                if (isInside(x + 66, PAPER_SIZE_Y, 22, 18, mouseX, mouseY)) {
                    PaintOverlayClient.setSelectedPaperSize(PaintOverlayClient.selectedPaperSize() + 1);
                    return true;
                }
                if (isInside(x, PAPER_SIZE_Y + 34, 88, 18, mouseX, mouseY)) {
                    client.setScreen(new PaintPaperImportScreen());
                    return true;
                }
                return true;
            }
            case PRESET -> {
                if (handlePresetEditorClick(mouseX, mouseY, x)) {
                    return true;
                }
                return true;
            }
            case SHAPE -> {
                if (pickColorAt(mouseX, mouseY, x, SHAPE_COLOR_Y, false)) {
                    pickingColor = true;
                    return true;
                }
                if (handleAlphaSliderClick(mouseX, mouseY, x, SHAPE_ALPHA_Y)) {
                    draggingAlpha = true;
                    return true;
                }
                if (handleShapeStrokeClick(mouseX, mouseY, x, SHAPE_STROKE_Y)) {
                    return true;
                }
                int y = SHAPE_MODE_Y;
                for (ShapeMode mode : ShapeMode.values()) {
                    if (isInside(x, y, 118, 18, mouseX, mouseY)) {
                        shapeMode = mode;
                        return true;
                    }
                    y += 22;
                }
                return true;
            }
            case SELECT -> {
                return true;
            }
            case NONE -> {
                return true;
            }
        }
        return true;
    }

    private boolean handleRadiusClick(double mouseX, double mouseY, int x, int y, PaintOverlayClient.EditorTool tool) {
        if (isInside(x, y, 22, 18, mouseX, mouseY)) {
            PaintOverlayClient.setSelectedRadius(tool, PaintOverlayClient.selectedRadius(tool) - 1);
            return true;
        }
        if (isInside(x + 130, y, 22, 18, mouseX, mouseY)) {
            PaintOverlayClient.setSelectedRadius(tool, PaintOverlayClient.selectedRadius(tool) + 1);
            return true;
        }
        if (isInside(x + RADIUS_TRACK_X_OFFSET - 6, y - 4, RADIUS_TRACK_WIDTH + 12, 26, mouseX, mouseY)) {
            draggingRadius = true;
            draggingRadiusTool = tool;
            updateRadiusFromMouse(mouseX, tool);
            return true;
        }
        return false;
    }

    private boolean handleShapeStrokeClick(double mouseX, double mouseY, int x, int y) {
        if (isInside(x, y, 22, 18, mouseX, mouseY)) {
            setShapeStrokeWidth(shapeStrokeWidth - 1);
            return true;
        }
        if (isInside(x + 130, y, 22, 18, mouseX, mouseY)) {
            setShapeStrokeWidth(shapeStrokeWidth + 1);
            return true;
        }
        if (isInside(x + RADIUS_TRACK_X_OFFSET - 6, y - 4, RADIUS_TRACK_WIDTH + 12, 26, mouseX, mouseY)) {
            draggingShapeStroke = true;
            updateShapeStrokeFromMouse(mouseX);
            return true;
        }
        return false;
    }

    private boolean handleAlphaSliderClick(double mouseX, double mouseY, int x) {
        return handleAlphaSliderClick(mouseX, mouseY, x, BRUSH_ALPHA_Y);
    }

    private boolean handleAlphaSliderClick(double mouseX, double mouseY, int x, int y) {
        if (!isInside(x, y - 5, 118, 20, mouseX, mouseY)) {
            return false;
        }
        updateAlphaFromMouse(mouseX);
        return true;
    }

    private void updateRadiusFromMouse(double mouseX, PaintOverlayClient.EditorTool tool) {
        int x = rightX() + 16 + RADIUS_TRACK_X_OFFSET;
        float t = MathHelper.clamp((float) ((mouseX - x) / RADIUS_TRACK_WIDTH), 0.0F, 1.0F);
        int radius = Math.round(MathHelper.lerp(t, PaintOverlayFeature.MIN_RADIUS, PaintOverlayFeature.MAX_RADIUS));
        PaintOverlayClient.setSelectedRadius(tool, radius);
    }

    private void updateShapeStrokeFromMouse(double mouseX) {
        int x = rightX() + 16 + RADIUS_TRACK_X_OFFSET;
        float t = MathHelper.clamp((float) ((mouseX - x) / RADIUS_TRACK_WIDTH), 0.0F, 1.0F);
        setShapeStrokeWidth(Math.round(MathHelper.lerp(t, MIN_SHAPE_STROKE, MAX_SHAPE_STROKE)));
    }

    private void setShapeStrokeWidth(int width) {
        shapeStrokeWidth = MathHelper.clamp(width, MIN_SHAPE_STROKE, MAX_SHAPE_STROKE);
    }

    private void updateAlphaFromMouse(double mouseX) {
        int x = rightX() + 16;
        int alpha = Math.round(MathHelper.clamp((float) ((mouseX - x) / 118.0F), 0.0F, 1.0F) * 255.0F);
        PaintOverlayClient.setSelectedAlpha(alpha);
    }

    private boolean handlePresetStoreContextMenuClick(double mouseX, double mouseY, int button) {
        if (!presetStoreMenuOpen) {
            return false;
        }
        int x = clampedPresetMenuX();
        int y = clampedPresetMenuY();
        int h = PaintBrushItem.COLOR_SLOTS * PRESET_MENU_ROW_HEIGHT;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isInside(x, y, PRESET_MENU_WIDTH, h, mouseX, mouseY)) {
            int slot = MathHelper.clamp((int) ((mouseY - y) / PRESET_MENU_ROW_HEIGHT), 0, PaintBrushItem.COLOR_SLOTS - 1);
            PaintOverlayClient.storeSelectedColorInPresetSlot(slot);
            closePresetStoreContextMenu();
            return true;
        }
        return false;
    }

    private void closePresetStoreContextMenu() {
        presetStoreMenuOpen = false;
    }

    private boolean handleRecentPresetStoreRightClick(double mouseX, double mouseY) {
        if (selectedTool != PaintOverlayClient.EditorTool.BRUSH) {
            return false;
        }
        int x = rightX() + 16;
        List<Integer> recent = PaintOverlayClient.recentColors();
        for (int i = 0; i < 3 && i < recent.size(); i++) {
            if (isInside(x + i * 24, BRUSH_RECENT_Y, SWATCH, SWATCH, mouseX, mouseY)) {
                presetStoreMenuOpen = true;
                presetStoreMenuX = (int) mouseX + 8;
                presetStoreMenuY = (int) mouseY - 2;
                return true;
            }
        }
        return false;
    }

    private int clampedPresetMenuX() {
        return MathHelper.clamp(presetStoreMenuX, 2, Math.max(2, width - PRESET_MENU_WIDTH - 2));
    }

    private int clampedPresetMenuY() {
        int h = PaintBrushItem.COLOR_SLOTS * PRESET_MENU_ROW_HEIGHT;
        return MathHelper.clamp(presetStoreMenuY, 2, Math.max(2, height - h - 2));
    }

    private boolean handleValueScroll(double mouseX, double mouseY, double verticalAmount) {
        int delta = verticalAmount > 0.0D ? 1 : -1;
        int x = rightX() + 16;
        return switch (selectedTool) {
            case BRUSH -> {
                if (isInside(x, BRUSH_ALPHA_Y - 6, 118, 22, mouseX, mouseY)) {
                    PaintOverlayClient.setSelectedAlpha(PaintOverlayClient.selectedAlpha() + delta * 8);
                    yield true;
                }
                if (isInside(x - 8, 390, 166, 46, mouseX, mouseY)
                        || isInside(x, BRUSH_RADIUS_Y - 12, 90, 34, mouseX, mouseY)) {
                    PaintOverlayClient.setSelectedRadius(PaintOverlayClient.EditorTool.BRUSH,
                            PaintOverlayClient.selectedRadius(PaintOverlayClient.EditorTool.BRUSH) + delta);
                    yield true;
                }
                yield false;
            }
            case ERASER -> {
                if (isInside(x - 8, 48, 166, 112, mouseX, mouseY)
                        || isInside(x, ERASER_RADIUS_Y - 12, 90, 34, mouseX, mouseY)) {
                    PaintOverlayClient.setSelectedRadius(PaintOverlayClient.EditorTool.ERASER,
                            PaintOverlayClient.selectedRadius(PaintOverlayClient.EditorTool.ERASER) + delta);
                    yield true;
                }
                yield false;
            }
            case PAPER -> {
                if (isInside(x - 8, 48, 166, 124, mouseX, mouseY)
                        || isInside(x, PAPER_SIZE_Y - 8, 90, 30, mouseX, mouseY)) {
                    PaintOverlayClient.setSelectedPaperSize(PaintOverlayClient.selectedPaperSize() + delta);
                    yield true;
                }
                yield false;
            }
            case SHAPE -> {
                if (isInside(x, SHAPE_ALPHA_Y - 6, 118, 22, mouseX, mouseY)) {
                    PaintOverlayClient.setSelectedAlpha(PaintOverlayClient.selectedAlpha() + delta * 8);
                    yield true;
                }
                if (isInside(x + RADIUS_TRACK_X_OFFSET - 6, SHAPE_STROKE_Y - 4, RADIUS_TRACK_WIDTH + 12, 26, mouseX, mouseY)) {
                    setShapeStrokeWidth(shapeStrokeWidth + delta);
                    yield true;
                }
                ShapeMode[] modes = ShapeMode.values();
                int index = Math.floorMod(shapeMode.ordinal() + delta, modes.length);
                shapeMode = modes[index];
                yield true;
            }
            case SELECT -> false;
            case PRESET -> false;
            case NONE -> false;
        };
    }

    private boolean handleSwatches(double mouseX, double mouseY, int x) {
        int color = PaintOverlayClient.selectedColor();
        if (isInsideStar(x, BRUSH_SWATCH_Y, 28, mouseX, mouseY)) {
            PaintOverlayClient.toggleFavoriteColor(color);
            return true;
        }
        List<Integer> recent = PaintOverlayClient.recentColors();
        for (int i = 0; i < recent.size(); i++) {
            int slotX = x + i * 24;
            if (isInsideStar(slotX, BRUSH_RECENT_Y, SWATCH, mouseX, mouseY)) {
                PaintOverlayClient.toggleFavoriteColor(recent.get(i));
                return true;
            }
            if (isInside(slotX, BRUSH_RECENT_Y, SWATCH, SWATCH, mouseX, mouseY)) {
                selectColor(recent.get(i), false, true);
                return true;
            }
        }
        List<Integer> favorites = PaintOverlayClient.favoriteColors();
        for (int i = 0; i < Math.min(9, favorites.size()); i++) {
            int slotX = x + BRUSH_FAVORITE_X_OFFSET + (i % 3) * 24;
            int slotY = BRUSH_RECENT_Y + (i / 3) * 24;
            if (isInsideStar(slotX, slotY, SWATCH, mouseX, mouseY)) {
                PaintOverlayClient.toggleFavoriteColor(favorites.get(i));
                return true;
            }
            if (isInside(slotX, slotY, SWATCH, SWATCH, mouseX, mouseY)) {
                selectColor(favorites.get(i), false, true);
                return true;
            }
        }
        return false;
    }

    private boolean pickColor(double mouseX, double mouseY, boolean dragging) {
        return pickColorAt(mouseX, mouseY, rightX() + 16, BRUSH_COLOR_Y, dragging);
    }

    private boolean pickPresetColor(double mouseX, double mouseY, boolean dragging) {
        boolean handled = pickColorAt(mouseX, mouseY, rightX() + 16, PRESET_EDITOR_PICKER_Y, dragging);
        if (handled) {
            stageSelectedPresetColor();
        }
        return handled;
    }

    private boolean pickColorAt(double mouseX, double mouseY, int x, int y, boolean dragging) {
        if (mouseX >= x && mouseX < x + MAP_SIZE && mouseY >= y && mouseY < y + MAP_SIZE) {
            saturation = MathHelper.clamp((float) ((mouseX - x) / (MAP_SIZE - 1)), 0.0F, 1.0F);
            value = MathHelper.clamp(1.0F - (float) ((mouseY - y) / (MAP_SIZE - 1)), 0.0F, 1.0F);
            syncSelectedColor();
            return true;
        }
        int hueX = x + MAP_SIZE + 8;
        if (mouseX >= hueX && mouseX < hueX + HUE_WIDTH && mouseY >= y && mouseY < y + MAP_SIZE) {
            hue = MathHelper.clamp((float) ((mouseY - y) / (MAP_SIZE - 1)), 0.0F, 1.0F);
            uploadedHue = -1.0F;
            syncSelectedColor();
            return true;
        }
        return dragging && pickingColor;
    }

    private void loadPresetDraftsFromBrush() {
        if (presetDraftsLoaded) {
            return;
        }
        for (int slot = 0; slot < PaintBrushItem.COLOR_SLOTS; slot++) {
            if (PaintOverlayClient.presetSlotFilled(slot)) {
                presetDraftColors[slot] = PaintOverlayClient.presetSlotColor(slot);
                presetDraftFilled[slot] = true;
            } else {
                presetDraftColors[slot] = PaintOverlayClient.selectedColor();
                presetDraftFilled[slot] = false;
            }
        }
        selectedPresetSlot = MathHelper.clamp(PaintOverlayClient.selectedBrushSlot(), 0, PaintBrushItem.COLOR_SLOTS - 1);
        if (presetDraftFilled[selectedPresetSlot]) {
            selectColor(presetDraftColors[selectedPresetSlot], false, true);
        }
        presetDraftsLoaded = true;
    }

    private boolean handlePresetEditorClick(double mouseX, double mouseY, int x) {
        int slot = presetEditorSlotAt(mouseX, mouseY, x);
        if (slot >= 0) {
            selectPresetDraftSlot(slot);
            return true;
        }
        if (pickPresetColor(mouseX, mouseY, false)) {
            pickingColor = true;
            return true;
        }
        if (isInside(x, PRESET_SAVE_Y, 118, 20, mouseX, mouseY)) {
            savePresetDrafts();
            return true;
        }
        return false;
    }

    private void selectPresetDraftSlot(int slot) {
        int seedColor = PaintOverlayClient.selectedColor();
        selectedPresetSlot = MathHelper.clamp(slot, 0, PaintBrushItem.COLOR_SLOTS - 1);
        PaintOverlayClient.selectBrushSlot(selectedPresetSlot);
        if (!presetDraftFilled[selectedPresetSlot]) {
            presetDraftColors[selectedPresetSlot] = seedColor;
        }
        selectColor(presetDraftColors[selectedPresetSlot], false, true);
    }

    private void stageSelectedPresetColor() {
        presetDraftColors[selectedPresetSlot] = PaintOverlayClient.selectedColor();
        presetDraftFilled[selectedPresetSlot] = true;
    }

    private int currentPresetDraftColor() {
        if (!presetDraftFilled[selectedPresetSlot]) {
            return PaintOverlayClient.selectedColor();
        }
        return presetDraftColors[selectedPresetSlot];
    }

    private void savePresetDrafts() {
        for (int slot = 0; slot < PaintBrushItem.COLOR_SLOTS; slot++) {
            if (presetDraftFilled[slot]) {
                PaintOverlayClient.storeColorInPresetSlot(slot, presetDraftColors[slot]);
            }
        }
        PaintOverlayClient.selectBrushSlot(selectedPresetSlot);
        selectColor(presetDraftColors[selectedPresetSlot], false, true);
    }

    private int presetEditorSlotX(int x, int slot) {
        return x + 8 + (slot % 3) * (PRESET_SLOT_SIZE + PRESET_SLOT_GAP);
    }

    private int presetEditorSlotY(int slot) {
        return PRESET_EDITOR_GRID_Y + (slot / 3) * (PRESET_SLOT_SIZE + PRESET_SLOT_GAP);
    }

    private int presetEditorSlotAt(double mouseX, double mouseY, int x) {
        for (int slot = 0; slot < PaintBrushItem.COLOR_SLOTS; slot++) {
            if (isInside(presetEditorSlotX(x, slot), presetEditorSlotY(slot), PRESET_SLOT_SIZE, PRESET_SLOT_SIZE, mouseX, mouseY)) {
                return slot;
            }
        }
        return -1;
    }

    private void selectColor(int color, boolean record) {
        selectColor(color, record, (color & 0xFF000000) != 0);
    }

    private void selectColor(int color, boolean record, boolean useColorAlpha) {
        int rgb = color & 0xFFFFFF;
        int alpha = useColorAlpha ? (color >>> 24) & 0xFF : PaintOverlayClient.selectedAlpha();
        loadColor(rgb);
        PaintOverlayClient.setSelectedColor((alpha << 24) | rgb);
        updateHexField();
        uploadedHue = -1.0F;
        if (record) {
            PaintOverlayClient.recordPickedColor(PaintOverlayClient.selectedColor());
        }
    }

    private void syncSelectedColor() {
        PaintOverlayClient.setSelectedColor((PaintOverlayClient.selectedAlpha() << 24) | hsvToRgb(hue, saturation, value));
        updateHexField();
    }

    private void onHexChanged(String text) {
        if (updatingHexField) {
            return;
        }
        String digits = stripHexPrefix(text);
        if (digits.length() != 6) {
            return;
        }
        try {
            selectColor(Integer.parseInt(digits, 16), true);
        } catch (NumberFormatException ignored) {
        }
    }

    private void updateHexField() {
        if (hexField == null) {
            return;
        }
        updatingHexField = true;
        hexField.setText(toHex(PaintOverlayClient.selectedColor()));
        updatingHexField = false;
    }

    private void uploadColorMapIfNeeded() {
        if (colorMapTexture != null && Math.abs(uploadedHue - hue) < 0.0001F) {
            return;
        }
        NativeImage image = new NativeImage(MAP_SIZE, MAP_SIZE, false);
        for (int y = 0; y < MAP_SIZE; y++) {
            float v = 1.0F - (float) y / (MAP_SIZE - 1);
            for (int x = 0; x < MAP_SIZE; x++) {
                float s = (float) x / (MAP_SIZE - 1);
                image.setColorArgb(x, y, 0xFF000000 | hsvToRgb(hue, s, v));
            }
        }
        if (colorMapTexture == null) {
            colorMapTexture = new NativeImageBackedTexture(() -> "monvhua paint editor color map", image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(COLOR_MAP_TEXTURE_ID, colorMapTexture);
        } else {
            colorMapTexture.setImage(image);
            colorMapTexture.upload();
        }
        uploadedHue = hue;
    }

    private void loadSelectedColor() {
        loadColor(PaintOverlayClient.selectedColor() & 0xFFFFFF);
    }

    private void loadColor(int rgb) {
        float r = ((rgb >>> 16) & 0xFF) / 255.0F;
        float g = ((rgb >>> 8) & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        value = max;
        saturation = max == 0.0F ? 0.0F : delta / max;
        if (delta == 0.0F) {
            hue = 0.0F;
        } else if (max == r) {
            hue = ((g - b) / delta) / 6.0F;
        } else if (max == g) {
            hue = (2.0F + (b - r) / delta) / 6.0F;
        } else {
            hue = (4.0F + (r - g) / delta) / 6.0F;
        }
        if (hue < 0.0F) {
            hue += 1.0F;
        }
    }

    private void drawSwatch(DrawContext context, int x, int y, int size, int color, boolean filled) {
        context.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0x66000000);
        if (filled) {
            drawChecker(context, x, y, size, size, Math.max(4, size / 3));
        }
        fillChamfered(context, x, y, size, size, Math.min(3, CHAMFER), filled ? color : 0x55202028);
        drawBorder(context, x, y, size, size);
    }

    private void drawFilledCircle(DrawContext context, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int span = (int) Math.floor(Math.sqrt(radius * radius - dy * dy));
            context.fill(cx - span, cy + dy, cx + span + 1, cy + dy + 1, color);
        }
    }

    private void drawChecker(DrawContext context, int x, int y, int w, int h, int cell) {
        int step = Math.max(2, cell);
        for (int yy = 0; yy < h; yy += step) {
            for (int xx = 0; xx < w; xx += step) {
                int color = ((xx / step + yy / step) & 1) == 0 ? 0xFFE3E7EC : 0xFFC4CAD2;
                context.fill(x + xx, y + yy, x + Math.min(w, xx + step), y + Math.min(h, yy + step), color);
            }
        }
    }

    private void drawDashedBorder(DrawContext context, int x, int y, int w, int h, int color) {
        for (int xx = x; xx < x + w; xx += 6) {
            context.fill(xx, y, Math.min(xx + 4, x + w), y + 1, color);
            context.fill(xx, y + h - 1, Math.min(xx + 4, x + w), y + h, color);
        }
        for (int yy = y; yy < y + h; yy += 6) {
            context.fill(x, yy, x + 1, Math.min(yy + 4, y + h), color);
            context.fill(x + w - 1, yy, x + w, Math.min(yy + 4, y + h), color);
        }
    }

    private void drawPaintToolOverlay(DrawContext context, int mouseX, int mouseY) {
        int x = LEFT_WIDTH + 8;
        int y = height - 70;
        if (selection != null) {
            int color = selection.solid ? 0xFFE9F4FF : 0xFF79C8FF;
        context.drawText(textRenderer, Text.literal("选区 " + selection.width() + "x" + selection.height()), x, y, color, false);
        }
        if (dragMode == DragMode.SELECT_CREATE || dragMode == DragMode.SHAPE_CREATE) {
            int minX = Math.min(dragStartX, dragCurrentX);
            int minY = Math.min(dragStartY, dragCurrentY);
            int maxX = Math.max(dragStartX, dragCurrentX);
            int maxY = Math.max(dragStartY, dragCurrentY);
            context.drawText(textRenderer, Text.literal((dragMode == DragMode.SHAPE_CREATE ? shapeMode.label : "选区")
                    + " " + (maxX - minX + 1) + "x" + (maxY - minY + 1)), x, y + 16, TEXT_PRIMARY, false);
        }
    }

    private void updateWorldPreview(double mouseX, double mouseY) {
        if (selectedTool == PaintOverlayClient.EditorTool.SELECT && selection != null) {
            List<PaintOverlayClient.EditorGeometryPreview> frames = buildPlaneGeometryPreviews(selection.key,
                    selection.minX, selection.minY, selection.maxX, selection.maxY,
                    selection.solid ? 0xFFE9F4FF : 0xFF79C8FF, selection.solid, selection.showHandles);
            if (selectionPreviewActive) {
                PaintOverlayClient.setEditorPreviewLayers(selectionBeforePreview == null ? List.of() : buildPlaneHidePreviews(selection.key,
                                selectionBeforePreview.sourceMinX, selectionBeforePreview.sourceMinY,
                                selectionBeforePreview.sourceMaxX, selectionBeforePreview.sourceMaxY),
                        buildPlanePixelPreviews(selection.key, selection.minX, selection.minY, selection.maxX, selection.maxY, selection.pixels),
                        List.of(), frames);
            } else {
                PaintOverlayClient.setEditorGeometryPreviews(frames);
            }
            return;
        }
        if ((dragMode == DragMode.SELECT_CREATE || dragMode == DragMode.SHAPE_CREATE) && dragKey != null) {
            int minX = Math.min(dragStartX, dragCurrentX);
            int minY = Math.min(dragStartY, dragCurrentY);
            int maxX = Math.max(dragStartX, dragCurrentX);
            int maxY = Math.max(dragStartY, dragCurrentY);
            int color = dragMode == DragMode.SHAPE_CREATE ? PaintOverlayClient.selectedColor() : 0xFF79C8FF;
            List<PaintOverlayClient.EditorGeometryPreview> frames = buildPlaneGeometryPreviews(dragKey, minX, minY, maxX, maxY, color, false, false);
            if (dragMode == DragMode.SHAPE_CREATE) {
                PaintOverlayClient.setEditorPreviewLayers(List.of(),
                        buildPlanePixelPreviews(dragKey, minX, minY, maxX, maxY,
                                buildShapePixels(minX, minY, maxX, maxY)),
                        List.of(), frames);
            } else {
                PaintOverlayClient.setEditorGeometryPreviews(frames);
            }
            return;
        }
        if (selectedTool == PaintOverlayClient.EditorTool.SELECT || selectedTool == PaintOverlayClient.EditorTool.SHAPE) {
            PaintOverlayClient.EditorHit hit = editorHitForTool(mouseX, mouseY);
            if (hit != null) {
                int color = selectedTool == PaintOverlayClient.EditorTool.SHAPE ? PaintOverlayClient.selectedColor() : 0xFF79C8FF;
                PlanePoint point = planePoint(hit);
                if (point != null) {
                    PaintOverlayClient.setEditorGeometryPreviews(buildPlaneGeometryPreviews(hit.key(),
                            point.u(), point.v(), point.u(), point.v(), color, false, false));
                }
                return;
            }
        }
        PaintOverlayClient.clearEditorPreview();
    }

    private void drawMiniSelection(DrawContext context, int x, int y, int minX, int minY, int maxX, int maxY, boolean solid) {
        int scale = 4;
        int w = Math.max(1, maxX - minX + 1) * scale;
        int h = Math.max(1, maxY - minY + 1) * scale;
        if (solid) {
            context.fill(x, y, x + w, y + 1, ACCENT);
            context.fill(x, y + h - 1, x + w, y + h, ACCENT);
            context.fill(x, y, x + 1, y + h, ACCENT);
            context.fill(x + w - 1, y, x + w, y + h, ACCENT);
        } else {
            drawDashedBorder(context, x, y, w, h, ACCENT);
        }
    }

    private void drawSelectionHandles(DrawContext context, int x, int y) {
        if (selection == null || !selection.showHandles) {
            return;
        }
        int w = selection.width() * 4;
        int h = selection.height() * 4;
        drawHandle(context, x, y);
        drawHandle(context, x + w, y);
        drawHandle(context, x, y + h);
        drawHandle(context, x + w, y + h);
    }

    private void drawHandle(DrawContext context, int x, int y) {
        context.fill(x - HANDLE_SIZE / 2, y - HANDLE_SIZE / 2, x + HANDLE_SIZE / 2 + 1, y + HANDLE_SIZE / 2 + 1, 0xFFFFFFFF);
    }

    private void drawSelectionContextMenu(DrawContext context, int mouseX, int mouseY) {
        if (!selectMenuOpen || selection == null && clipboardSelection == null) {
            return;
        }
        int x = MathHelper.clamp(selectMenuX, 2, Math.max(2, width - SELECT_MENU_WIDTH - 88));
        int y = MathHelper.clamp(selectMenuY, 2, Math.max(2, height - SELECT_MENU_ROW_HEIGHT * 6 - 2));
        String[] items = {"复制", "粘贴", "左旋转", "镜像", "对称"};
        fillChamfered(context, x, y, SELECT_MENU_WIDTH, items.length * SELECT_MENU_ROW_HEIGHT, CHAMFER, 0xEE111820);
        drawBorder(context, x, y, SELECT_MENU_WIDTH, items.length * SELECT_MENU_ROW_HEIGHT);
        for (int i = 0; i < items.length; i++) {
            int rowY = y + i * SELECT_MENU_ROW_HEIGHT;
            boolean hover = isInside(x, rowY, SELECT_MENU_WIDTH, SELECT_MENU_ROW_HEIGHT, mouseX, mouseY);
            if (hover) {
                context.fill(x + 1, rowY + 1, x + SELECT_MENU_WIDTH - 1, rowY + SELECT_MENU_ROW_HEIGHT - 1, SURFACE_HOVER);
            }
            context.drawText(textRenderer, Text.literal(items[i]), x + 7, rowY + 5, hover ? TEXT_PRIMARY : TEXT_SECONDARY, false);
        }
        if (isInside(x, y + 4 * SELECT_MENU_ROW_HEIGHT, SELECT_MENU_WIDTH, SELECT_MENU_ROW_HEIGHT, mouseX, mouseY)) {
            String[] dirs = {"上", "下", "左", "右"};
            int sx = x + SELECT_MENU_WIDTH + 3;
            int sy = y + 4 * SELECT_MENU_ROW_HEIGHT;
            fillChamfered(context, sx, sy, 52, dirs.length * SELECT_MENU_ROW_HEIGHT, CHAMFER, 0xEE111820);
            drawBorder(context, sx, sy, 52, dirs.length * SELECT_MENU_ROW_HEIGHT);
            for (int i = 0; i < dirs.length; i++) {
                int rowY = sy + i * SELECT_MENU_ROW_HEIGHT;
                boolean hover = isInside(sx, rowY, 52, SELECT_MENU_ROW_HEIGHT, mouseX, mouseY);
                if (hover) {
                    context.fill(sx + 1, rowY + 1, sx + 51, rowY + SELECT_MENU_ROW_HEIGHT - 1, SURFACE_HOVER);
                }
                context.drawText(textRenderer, Text.literal(dirs[i]), sx + 7, rowY + 5, hover ? TEXT_PRIMARY : TEXT_SECONDARY, false);
            }
        }
    }

    private boolean handleSelectionContextMenuClick(double mouseX, double mouseY, int button) {
        if (!selectMenuOpen || selection == null && clipboardSelection == null) {
            return false;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            selectMenuOpen = false;
            return true;
        }
        int x = MathHelper.clamp(selectMenuX, 2, Math.max(2, width - SELECT_MENU_WIDTH - 88));
        int y = MathHelper.clamp(selectMenuY, 2, Math.max(2, height - SELECT_MENU_ROW_HEIGHT * 5 - 2));
        if (selection != null && isInside(x + SELECT_MENU_WIDTH + 3, y + 4 * SELECT_MENU_ROW_HEIGHT, 52, 4 * SELECT_MENU_ROW_HEIGHT, mouseX, mouseY)) {
            int dir = MathHelper.clamp((int) ((mouseY - (y + 4 * SELECT_MENU_ROW_HEIGHT)) / SELECT_MENU_ROW_HEIGHT), 0, 3);
            symmetrySelection(dir);
            selectMenuOpen = false;
            return true;
        }
        if (!isInside(x, y, SELECT_MENU_WIDTH, 6 * SELECT_MENU_ROW_HEIGHT, mouseX, mouseY)
                && !selectionSymmetrySubmenuOpen(x, y, mouseX, mouseY)) {
            selectMenuOpen = false;
            return true;
        }
        int row = MathHelper.clamp((int) ((mouseY - y) / SELECT_MENU_ROW_HEIGHT), 0, 5);
        if (row == 0) copySelection();
        if (row == 1) pasteSelection();
        if (row == 2) rotateSelectionLeft();
        if (row == 3) mirrorSelection();
        if (row == 5) deleteSelection();
        selectMenuOpen = false;
        return true;
    }

    private boolean selectionSymmetrySubmenuOpen(int menuX, int menuY, double mouseX, double mouseY) {
        int rowY = menuY + 4 * SELECT_MENU_ROW_HEIGHT;
        return isInside(menuX, rowY, SELECT_MENU_WIDTH, SELECT_MENU_ROW_HEIGHT, mouseX, mouseY)
                || isInside(menuX + SELECT_MENU_WIDTH + 3, rowY, 52, 4 * SELECT_MENU_ROW_HEIGHT, mouseX, mouseY);
    }

    private void drawSelectionMenu(DrawContext context, int mouseX, int mouseY) {
        if (!selectMenuOpen || selection == null && clipboardSelection == null) {
            return;
        }
        int x = selectionMenuX();
        int y = selectionMenuY();
        String[] items = {"复制", "粘贴", "左旋转", "镜像", "对称", "删除"};
        fillChamfered(context, x, y, SELECT_MENU_WIDTH, items.length * SELECT_MENU_ROW_HEIGHT, CHAMFER, 0xEE111820);
        drawBorder(context, x, y, SELECT_MENU_WIDTH, items.length * SELECT_MENU_ROW_HEIGHT);
        for (int i = 0; i < items.length; i++) {
            int rowY = y + i * SELECT_MENU_ROW_HEIGHT;
            boolean hover = isInside(x, rowY, SELECT_MENU_WIDTH, SELECT_MENU_ROW_HEIGHT, mouseX, mouseY)
                    || i == 4 && selectionSymmetrySubmenuOpen(x, y, mouseX, mouseY);
            if (hover) {
                context.fill(x + 1, rowY + 1, x + SELECT_MENU_WIDTH - 1, rowY + SELECT_MENU_ROW_HEIGHT - 1, SURFACE_HOVER);
            }
            context.drawText(textRenderer, Text.literal(items[i]), x + 7, rowY + 5,
                    hover ? TEXT_PRIMARY : TEXT_SECONDARY, false);
        }
        if (selectionSymmetrySubmenuOpen(x, y, mouseX, mouseY)) {
            String[] dirs = {"上", "下", "左", "右"};
            int sx = x + SELECT_MENU_WIDTH + 3;
            int sy = y + 4 * SELECT_MENU_ROW_HEIGHT;
            fillChamfered(context, sx, sy, 52, dirs.length * SELECT_MENU_ROW_HEIGHT, CHAMFER, 0xEE111820);
            drawBorder(context, sx, sy, 52, dirs.length * SELECT_MENU_ROW_HEIGHT);
            for (int i = 0; i < dirs.length; i++) {
                int rowY = sy + i * SELECT_MENU_ROW_HEIGHT;
                boolean hover = isInside(sx, rowY, 52, SELECT_MENU_ROW_HEIGHT, mouseX, mouseY);
                if (hover) {
                    context.fill(sx + 1, rowY + 1, sx + 51, rowY + SELECT_MENU_ROW_HEIGHT - 1, SURFACE_HOVER);
                }
                context.drawText(textRenderer, Text.literal(dirs[i]), sx + 7, rowY + 5,
                        hover ? TEXT_PRIMARY : TEXT_SECONDARY, false);
            }
        }
    }

    private boolean handleSelectionMenuClick(double mouseX, double mouseY, int button) {
        if (!selectMenuOpen || selection == null && clipboardSelection == null) {
            return false;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            selectMenuOpen = false;
            return true;
        }
        int x = selectionMenuX();
        int y = selectionMenuY();
        int subX = x + SELECT_MENU_WIDTH + 3;
        int subY = y + 4 * SELECT_MENU_ROW_HEIGHT;
        if (selection != null && isInside(subX, subY, 52, 4 * SELECT_MENU_ROW_HEIGHT, mouseX, mouseY)) {
            int dir = MathHelper.clamp((int) ((mouseY - subY) / SELECT_MENU_ROW_HEIGHT), 0, 3);
            symmetrySelection(dir);
            selectMenuOpen = false;
            return true;
        }
        if (!isInside(x, y, SELECT_MENU_WIDTH, 6 * SELECT_MENU_ROW_HEIGHT, mouseX, mouseY)
                && !selectionSymmetrySubmenuOpen(x, y, mouseX, mouseY)) {
            selectMenuOpen = false;
            return true;
        }
        int row = MathHelper.clamp((int) ((mouseY - y) / SELECT_MENU_ROW_HEIGHT), 0, 5);
        if (row == 0) copySelection();
        if (row == 1) pasteSelection();
        if (row == 2) rotateSelectionLeft();
        if (row == 3) mirrorSelection();
        if (row == 5) deleteSelection();
        selectMenuOpen = false;
        return true;
    }

    private int selectionMenuX() {
        return MathHelper.clamp(selectMenuX, 2, Math.max(2, width - SELECT_MENU_WIDTH - 88));
    }

    private int selectionMenuY() {
        return MathHelper.clamp(selectMenuY, 2, Math.max(2, height - SELECT_MENU_ROW_HEIGHT * 6 - 2));
    }

    private boolean handleSelectionLeftClick(double mouseX, double mouseY) {
        selectMenuOpen = false;
        DragMode screenHandleMode = hoveredSelectionHandle(mouseX, mouseY);
        if (screenHandleMode != DragMode.NONE) {
            startSelectionDragAtHandle(screenHandleMode);
            return true;
        }
        PaintOverlayClient.EditorHit hit = editorHitForTool(mouseX, mouseY);
        PlanePoint point = planePoint(hit);
        if (hit == null) {
            if (selection != null) {
                applySelection("编辑器应用选区");
                selection = null;
                return true;
            }
            return false;
        }
        if (selection != null && point != null && samePlane(selection.key, hit.key())) {
            DragMode handleMode = selectionHandleAt(point.u(), point.v());
            if (handleMode != DragMode.NONE) {
                startSelectionDrag(handleMode, hit);
                return true;
            }
        }
        if (selection != null && point != null && samePlane(selection.key, hit.key()) && insideSelection(point.u(), point.v())) {
            startSelectionDrag(DragMode.SELECT_MOVE, hit);
            return true;
        }
        if (selection != null) {
            applySelection("编辑器应用选区");
            selection = null;
        }
        dragMode = DragMode.SELECT_CREATE;
        dragKey = hit.key();
        dragStartX = point.u();
        dragStartY = point.v();
        dragCurrentX = point.u();
        dragCurrentY = point.v();
        return true;
    }

    private void startSelectionDrag(DragMode mode, PaintOverlayClient.EditorHit hit) {
        PlanePoint point = isResizeDrag(mode) ? selectionHandlePoint(mode) : planePoint(hit);
        if (point == null) {
            return;
        }
        beginSelectionPreview();
        dragMode = mode;
        dragStartX = point.u();
        dragStartY = point.v();
        dragCurrentX = point.u();
        dragCurrentY = point.v();
        dragAnchorMinX = selection.minX;
        dragAnchorMinY = selection.minY;
        dragAnchorMaxX = selection.maxX;
        dragAnchorMaxY = selection.maxY;
        dragAnchorPixels = Arrays.copyOf(selection.pixels, selection.pixels.length);
        dragKey = selection.key;
    }

    private void startSelectionDragAtHandle(DragMode mode) {
        if (selection == null || mode == DragMode.NONE) {
            return;
        }
        beginSelectionPreview();
        dragMode = mode;
        PlanePoint point = selectionHandlePoint(mode);
        dragStartX = point.u();
        dragStartY = point.v();
        dragCurrentX = point.u();
        dragCurrentY = point.v();
        dragAnchorMinX = selection.minX;
        dragAnchorMinY = selection.minY;
        dragAnchorMaxX = selection.maxX;
        dragAnchorMaxY = selection.maxY;
        dragAnchorPixels = Arrays.copyOf(selection.pixels, selection.pixels.length);
        dragKey = selection.key;
    }

    private PlanePoint selectionHandlePoint(DragMode mode) {
        int right = selection.maxX + 1;
        int bottom = selection.maxY + 1;
        int midX = (selection.minX + right) / 2;
        int midY = (selection.minY + bottom) / 2;
        return switch (mode) {
            case SELECT_RESIZE_NW -> new PlanePoint(selection.key.face(), selection.minX, selection.minY);
            case SELECT_RESIZE_N -> new PlanePoint(selection.key.face(), midX, selection.minY);
            case SELECT_RESIZE_NE -> new PlanePoint(selection.key.face(), right, selection.minY);
            case SELECT_RESIZE_E -> new PlanePoint(selection.key.face(), right, midY);
            case SELECT_RESIZE_SE -> new PlanePoint(selection.key.face(), right, bottom);
            case SELECT_RESIZE_S -> new PlanePoint(selection.key.face(), midX, bottom);
            case SELECT_RESIZE_SW -> new PlanePoint(selection.key.face(), selection.minX, bottom);
            case SELECT_RESIZE_W -> new PlanePoint(selection.key.face(), selection.minX, midY);
            default -> new PlanePoint(selection.key.face(), selection.minX, selection.minY);
        };
    }

    private void beginSelectionPreview() {
        if (!selectionPreviewActive) {
            selectionBeforePreview = selection == null ? null : selection.copy();
            selectionPreviewActive = true;
        }
    }

    private void cancelSelectionPreview() {
        if (selectionPreviewActive) {
            selection = selectionBeforePreview == null ? null : selectionBeforePreview.copy();
            selectionBeforePreview = null;
            selectionPreviewActive = false;
            dragMode = DragMode.NONE;
            dragKey = null;
            dragAnchorPixels = null;
            PaintOverlayClient.clearEditorPreview();
        }
    }

    private boolean handleShapeLeftClick(double mouseX, double mouseY) {
        PaintOverlayClient.EditorHit hit = editorHitForTool(mouseX, mouseY);
        if (hit == null) {
            return false;
        }
        PlanePoint point = planePoint(hit);
        if (point == null) {
            return false;
        }
        dragMode = DragMode.SHAPE_CREATE;
        dragKey = hit.key();
        dragStartX = point.u();
        dragStartY = point.v();
        dragCurrentX = point.u();
        dragCurrentY = point.v();
        return true;
    }

    private void updatePaintToolDrag(double mouseX, double mouseY) {
        PaintOverlayClient.EditorHit hit = editorHitForTool(mouseX, mouseY);
        PlanePoint point = planePoint(hit);
        if (hit == null || point == null || !samePlane(dragKey, hit.key())) {
            return;
        }
        int currentX = point.u();
        int currentY = point.v();
        if (isResizeDrag(dragMode)) {
            if (dragMode == DragMode.SELECT_RESIZE_NE || dragMode == DragMode.SELECT_RESIZE_E
                    || dragMode == DragMode.SELECT_RESIZE_SE) {
                currentX++;
            }
            if (dragMode == DragMode.SELECT_RESIZE_SW || dragMode == DragMode.SELECT_RESIZE_S
                    || dragMode == DragMode.SELECT_RESIZE_SE) {
                currentY++;
            }
        }
        dragCurrentX = clampSpanEnd(dragStartX, currentX);
        dragCurrentY = clampSpanEnd(dragStartY, currentY);
        if (dragMode == DragMode.SELECT_MOVE && selection != null) {
            int dx = dragCurrentX - dragStartX;
            int dy = dragCurrentY - dragStartY;
            int w = selection.width();
            int h = selection.height();
            selection.minX = dragAnchorMinX + dx;
            selection.minY = dragAnchorMinY + dy;
            selection.maxX = selection.minX + w - 1;
            selection.maxY = selection.minY + h - 1;
            selection.solid = true;
            selection.showHandles = true;
        } else if (isResizeDrag(dragMode) && selection != null && dragAnchorPixels != null) {
            resizeSelectionDrag();
        }
    }

    private void finishPaintToolDrag(double mouseX, double mouseY) {
        updatePaintToolDrag(mouseX, mouseY);
        if (dragMode == DragMode.SELECT_CREATE && dragKey != null) {
            int minX = Math.min(dragStartX, dragCurrentX);
            int minY = Math.min(dragStartY, dragCurrentY);
            int maxX = Math.max(dragStartX, dragCurrentX);
            int maxY = Math.max(dragStartY, dragCurrentY);
            int[] pixels = copyPlaneRect(dragKey, minX, minY, maxX, maxY);
            if (hasPaintPixels(pixels)) {
                PaintSelection next = new PaintSelection(dragKey, minX, minY, maxX, maxY, pixels, false, true);
                if (isCtrlDown() && selection != null && samePlane(selection.key, next.key)) {
                    selection = mergeSelections(selection, next);
                } else {
                    selection = next;
                }
            }
        } else if (dragMode == DragMode.SHAPE_CREATE && dragKey != null) {
            submitShape();
        }
        dragMode = DragMode.NONE;
        dragKey = null;
        dragAnchorPixels = null;
    }

    private void submitShape() {
        int minX = Math.min(dragStartX, dragCurrentX);
        int minY = Math.min(dragStartY, dragCurrentY);
        int maxX = Math.max(dragStartX, dragCurrentX);
        int maxY = Math.max(dragStartY, dragCurrentY);
        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        if (w <= 0 || h <= 0 || (long) w * h > MAX_EDITOR_AREA_PIXELS) {
            return;
        }
        int[] pixels = buildShapePixels(minX, minY, maxX, maxY);
        PaintOverlayClient.submitEditorRegionPatches(buildPlanePatches(dragKey, minX, minY, maxX, maxY, pixels, false),
                "编辑器形状 " + shapeMode.label);
    }

    private int[] buildShapePixels(int minX, int minY, int maxX, int maxY) {
        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        if (w <= 0 || h <= 0 || (long) w * h > MAX_EDITOR_AREA_PIXELS) {
            return new int[0];
        }
        int[] pixels = new int[w * h];
        rasterShape(pixels, w, h, PaintOverlayClient.selectedColor());
        return pixels;
    }

    private void rasterShape(int[] pixels, int w, int h, int color) {
        int stroke = MathHelper.clamp(shapeStrokeWidth, MIN_SHAPE_STROKE, Math.max(w, h));
        switch (shapeMode) {
            case LINE -> {
                int x0 = dragStartX < dragCurrentX ? 0 : w - 1;
                int y0 = dragStartY < dragCurrentY ? 0 : h - 1;
                int x1 = dragStartX < dragCurrentX ? w - 1 : 0;
                int y1 = dragStartY < dragCurrentY ? h - 1 : 0;
                drawThickLine(pixels, w, h, x0, y0, x1, y1, color, stroke);
            }
            case RECTANGLE, RECTANGLE_FILLED -> {
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        if (shapeMode == ShapeMode.RECTANGLE_FILLED || x < stroke || y < stroke || x >= w - stroke || y >= h - stroke) {
                            pixels[y * w + x] = color;
                        }
                    }
                }
            }
            case ELLIPSE, ELLIPSE_FILLED -> {
                rasterEllipse(pixels, w, h, color, shapeMode == ShapeMode.ELLIPSE_FILLED, false, stroke);
            }
            case CIRCLE, CIRCLE_FILLED -> {
                rasterEllipse(pixels, w, h, color, shapeMode == ShapeMode.CIRCLE_FILLED, true, stroke);
            }
            case DIAMOND, DIAMOND_FILLED -> {
                rasterDiamond(pixels, w, h, color, shapeMode == ShapeMode.DIAMOND_FILLED, stroke);
            }
            case TRIANGLE, TRIANGLE_FILLED -> {
                rasterTriangle(pixels, w, h, color, shapeMode == ShapeMode.TRIANGLE_FILLED, stroke);
            }
        }
    }

    private void rasterEllipse(int[] pixels, int w, int h, int color, boolean filled, boolean circle, int stroke) {
        int shapeW = w;
        int shapeH = h;
        int offsetX = 0;
        int offsetY = 0;
        if (circle) {
            int size = Math.min(w, h);
            shapeW = size;
            shapeH = size;
            offsetX = (w - size) / 2;
            offsetY = (h - size) / 2;
        }
        double rx = Math.max(0.5D, (shapeW - 1) / 2.0D);
        double ry = Math.max(0.5D, (shapeH - 1) / 2.0D);
        double cx = offsetX + (shapeW - 1) / 2.0D;
        double cy = offsetY + (shapeH - 1) / 2.0D;
        double minR = Math.min(rx, ry);
        double inner = Math.max(0.0D, (minR - stroke) / minR);
        double innerSq = inner * inner;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double d = Math.pow((x - cx) / rx, 2.0D) + Math.pow((y - cy) / ry, 2.0D);
                if (filled ? d <= 1.0D : d <= 1.0D && d >= innerSq) {
                    pixels[y * w + x] = color;
                }
            }
        }
    }

    private void rasterDiamond(int[] pixels, int w, int h, int color, boolean filled, int stroke) {
        double cx = (w - 1) / 2.0D;
        double cy = (h - 1) / 2.0D;
        double rx = Math.max(0.5D, (w - 1) / 2.0D);
        double ry = Math.max(0.5D, (h - 1) / 2.0D);
        double minR = Math.min(rx, ry);
        double inner = Math.max(0.0D, 1.0D - stroke / minR);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double d = Math.abs((x - cx) / rx) + Math.abs((y - cy) / ry);
                if (filled ? d <= 1.0D : d <= 1.0D && d >= inner) {
                    pixels[y * w + x] = color;
                }
            }
        }
    }

    private void rasterTriangle(int[] pixels, int w, int h, int color, boolean filled, int stroke) {
        double ax = (w - 1) / 2.0D;
        double ay = 0.0D;
        double bx = 0.0D;
        double by = h - 1.0D;
        double cx = w - 1.0D;
        double cy = h - 1.0D;
        double area = edge(ax, ay, bx, by, cx, cy);
        if (Math.abs(area) < 0.0001D) {
            drawThickLine(pixels, w, h, 0, h - 1, w - 1, h - 1, color, stroke);
            return;
        }
        double minEdge = Math.max(1.0D, stroke - 0.5D);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double px = x + 0.5D;
                double py = y + 0.5D;
                double e0 = edge(bx, by, cx, cy, px, py);
                double e1 = edge(cx, cy, ax, ay, px, py);
                double e2 = edge(ax, ay, bx, by, px, py);
                boolean inside = area > 0.0D ? e0 >= 0.0D && e1 >= 0.0D && e2 >= 0.0D : e0 <= 0.0D && e1 <= 0.0D && e2 <= 0.0D;
                if (!inside) {
                    continue;
                }
                if (filled || distanceToSegment(px, py, ax, ay, bx, by) <= minEdge
                        || distanceToSegment(px, py, bx, by, cx, cy) <= minEdge
                        || distanceToSegment(px, py, cx, cy, ax, ay) <= minEdge) {
                    pixels[y * w + x] = color;
                }
            }
        }
    }

    private double edge(double ax, double ay, double bx, double by, double px, double py) {
        return (px - ax) * (by - ay) - (py - ay) * (bx - ax);
    }

    private double distanceToSegment(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double lenSq = dx * dx + dy * dy;
        if (lenSq <= 0.0001D) {
            return Math.hypot(px - ax, py - ay);
        }
        double t = MathHelper.clamp((float) (((px - ax) * dx + (py - ay) * dy) / lenSq), 0.0F, 1.0F);
        double sx = ax + t * dx;
        double sy = ay + t * dy;
        return Math.hypot(px - sx, py - sy);
    }

    private void drawThickLine(int[] pixels, int w, int h, int x0, int y0, int x1, int y1, int color, int stroke) {
        int radius = Math.max(0, (stroke - 1) / 2);
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            drawPixelBrush(pixels, w, h, x0, y0, radius, color);
            if (x0 == x1 && y0 == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    private void drawPixelBrush(int[] pixels, int w, int h, int cx, int cy, int radius, int color) {
        for (int y = cy - radius; y <= cy + radius; y++) {
            for (int x = cx - radius; x <= cx + radius; x++) {
                if (x >= 0 && x < w && y >= 0 && y < h) {
                    pixels[y * w + x] = color;
                }
            }
        }
    }

    private void drawLine(int[] pixels, int w, int h, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            if (x0 >= 0 && x0 < w && y0 >= 0 && y0 < h) {
                pixels[y0 * w + x0] = color;
            }
            if (x0 == x1 && y0 == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    private boolean insideSelection(int x, int y) {
        return selection != null && x >= selection.minX && x <= selection.maxX && y >= selection.minY && y <= selection.maxY;
    }

    private DragMode selectionHandleAtScreen(double mouseX, double mouseY) {
        if (selection == null || !selection.showHandles) {
            return DragMode.NONE;
        }
        double left = selection.minX;
        double top = selection.minY;
        double right = selection.maxX + 1.0D;
        double bottom = selection.maxY + 1.0D;
        double midX = (left + right) * 0.5D;
        double midY = (top + bottom) * 0.5D;
        DragMode[] modes = {
                DragMode.SELECT_RESIZE_NW,
                DragMode.SELECT_RESIZE_N,
                DragMode.SELECT_RESIZE_NE,
                DragMode.SELECT_RESIZE_E,
                DragMode.SELECT_RESIZE_SE,
                DragMode.SELECT_RESIZE_S,
                DragMode.SELECT_RESIZE_SW,
                DragMode.SELECT_RESIZE_W
        };
        double[] xs = {left, midX, right, right, right, midX, left, left};
        double[] ys = {top, top, top, midY, bottom, bottom, bottom, midY};
        double bestDistance = Double.MAX_VALUE;
        DragMode bestMode = DragMode.NONE;
        for (int i = 0; i < modes.length; i++) {
            PaintOverlayClient.ScreenPoint screenPoint = PaintOverlayClient.projectEditorPlanePoint(client, selection.key, xs[i], ys[i], width, height);
            if (screenPoint == null) {
                continue;
            }
            double dx = mouseX - screenPoint.x();
            double dy = mouseY - screenPoint.y();
            double distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestMode = modes[i];
            }
        }
        double threshold = 12.0D;
        return bestDistance <= threshold * threshold ? bestMode : DragMode.NONE;
    }

    private void copySelection() {
        if (selection != null) {
            clipboardSelection = selection.copy();
        }
    }

    private void pasteSelection() {
        if (clipboardSelection == null) {
            return;
        }
        beginSelectionPreview();
        selection = clipboardSelection.copyDetached();
        selection.solid = true;
        selection.showHandles = true;
    }

    private void rotateSelectionClockwise() {
        if (selection != null) {
            beginSelectionPreview();
            selection.rotateClockwise();
            selection.solid = true;
            selection.showHandles = true;
        }
    }

    private void rotateSelectionLeft() {
        if (selection != null) {
            beginSelectionPreview();
            selection.rotateCounterClockwise();
            selection.solid = true;
            selection.showHandles = true;
        }
    }

    private void mirrorSelection() {
        if (selection != null) {
            beginSelectionPreview();
            selection.mirrorHorizontal();
            selection.solid = true;
            selection.showHandles = true;
        }
    }

    private void deleteSelection() {
        if (selection == null) {
            return;
        }
        Map<PaintOverlayStore.FaceKey, FacePatchDraft> drafts = new HashMap<>();
        clearSelectionPixels(drafts, selection);
        if (PaintOverlayClient.submitEditorRegionPatches(collectPatches(drafts), "编辑器删除选区")) {
            selection = null;
            selectionBeforePreview = null;
            selectionPreviewActive = false;
            PaintOverlayClient.clearEditorPreview();
        }
    }

    private void symmetrySelection(int direction) {
        if (selection == null) {
            return;
        }
        beginSelectionPreview();
        PaintSelection mirrored = selection.copyDetached();
        int w = mirrored.width();
        int h = mirrored.height();
        if (direction == 0 || direction == 1) {
            mirrored.mirrorVertical();
            mirrored.minY = direction == 0 ? selection.minY - h : selection.maxY + 1;
        } else {
            mirrored.mirrorHorizontal();
            mirrored.minX = direction == 2 ? selection.minX - w : selection.maxX + 1;
        }
        mirrored.maxX = mirrored.minX + w - 1;
        mirrored.maxY = mirrored.minY + h - 1;
        selection = mirrored;
        selection.solid = true;
        selection.showHandles = true;
    }

    private void applySelection(String label) {
        if (selection == null) {
            return;
        }
        int unionMinX = Math.min(selection.clearSource ? selection.sourceMinX : selection.minX, selection.minX);
        int unionMinY = Math.min(selection.clearSource ? selection.sourceMinY : selection.minY, selection.minY);
        int unionMaxX = Math.max(selection.clearSource ? selection.sourceMaxX : selection.maxX, selection.maxX);
        int unionMaxY = Math.max(selection.clearSource ? selection.sourceMaxY : selection.maxY, selection.maxY);
        Map<PaintOverlayStore.FaceKey, FacePatchDraft> drafts = new HashMap<>();
        if (selection.clearSource) {
            clearSelectionSourcePixels(drafts, selection);
        }
        writeSelectionToDrafts(drafts, selection);
        if (PaintOverlayClient.submitEditorRegionPatches(collectPatches(drafts), label)) {
            selection.solid = true;
            selection.showHandles = true;
            selection.clearSource = false;
            selection.sourceMinX = selection.minX;
            selection.sourceMinY = selection.minY;
            selection.sourceMaxX = selection.maxX;
            selection.sourceMaxY = selection.maxY;
        }
        selectionBeforePreview = null;
        selectionPreviewActive = false;
    }

    private boolean hasPaintPixels(int[] pixels) {
        if (pixels == null) {
            return false;
        }
        for (int color : pixels) {
            if (color != 0) {
                return true;
            }
        }
        return false;
    }

    private PaintSelection mergeSelections(PaintSelection base, PaintSelection next) {
        int minX = Math.min(base.minX, next.minX);
        int minY = Math.min(base.minY, next.minY);
        int maxX = Math.max(base.maxX, next.maxX);
        int maxY = Math.max(base.maxY, next.maxY);
        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        if (w <= 0 || h <= 0 || (long) w * h > MAX_EDITOR_AREA_PIXELS) {
            return next;
        }
        int[] merged = new int[w * h];
        copyPaintPixels(base, merged, minX, minY, w);
        copyPaintPixels(next, merged, minX, minY, w);
        int sourceMinX = Math.min(base.sourceMinX, next.sourceMinX);
        int sourceMinY = Math.min(base.sourceMinY, next.sourceMinY);
        int sourceMaxX = Math.max(base.sourceMaxX, next.sourceMaxX);
        int sourceMaxY = Math.max(base.sourceMaxY, next.sourceMaxY);
        int sourceW = sourceMaxX - sourceMinX + 1;
        int sourceH = sourceMaxY - sourceMinY + 1;
        if (sourceW <= 0 || sourceH <= 0 || (long) sourceW * sourceH > MAX_EDITOR_AREA_PIXELS) {
            return next;
        }
        int[] mergedSource = new int[sourceW * sourceH];
        copySourcePaintPixels(base, mergedSource, sourceMinX, sourceMinY, sourceW);
        copySourcePaintPixels(next, mergedSource, sourceMinX, sourceMinY, sourceW);
        PaintSelection result = new PaintSelection(base.key, minX, minY, maxX, maxY, merged, false, true,
                sourceMinX,
                sourceMinY,
                sourceMaxX,
                sourceMaxY,
                base.clearSource || next.clearSource,
                mergedSource);
        result.solid = base.solid && next.solid;
        return result;
    }

    private void copyPaintPixels(PaintSelection source, int[] target, int targetMinX, int targetMinY, int targetWidth) {
        int w = source.width();
        int h = source.height();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int sourceIndex = y * w + x;
                if (sourceIndex < 0 || sourceIndex >= source.pixels.length) {
                    continue;
                }
                int color = source.pixels[sourceIndex];
                if (color == 0) {
                    continue;
                }
                int tx = source.minX + x - targetMinX;
                int ty = source.minY + y - targetMinY;
                int index = ty * targetWidth + tx;
                if (index >= 0 && index < target.length) {
                    target[index] = color;
                }
            }
        }
    }

    private void copySourcePaintPixels(PaintSelection source, int[] target, int targetMinX, int targetMinY, int targetWidth) {
        int sourceW = source.sourceWidth();
        int sourceH = source.sourceHeight();
        for (int y = 0; y < sourceH; y++) {
            for (int x = 0; x < sourceW; x++) {
                int sourceIndex = y * sourceW + x;
                if (sourceIndex < 0 || sourceIndex >= source.sourcePixels.length) {
                    continue;
                }
                int color = source.sourcePixels[sourceIndex];
                if (color == 0) {
                    continue;
                }
                int tx = source.sourceMinX + x - targetMinX;
                int ty = source.sourceMinY + y - targetMinY;
                int index = ty * targetWidth + tx;
                if (index >= 0 && index < target.length) {
                    target[index] = color;
                }
            }
        }
    }

    private void clearPatchRect(int[] patch, int patchWidth, int patchMinX, int patchMinY,
                                int minX, int minY, int maxX, int maxY) {
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                int px = x - patchMinX;
                int py = y - patchMinY;
                if (px >= 0 && px < patchWidth && py >= 0 && py * patchWidth + px < patch.length) {
                    patch[py * patchWidth + px] = 0;
                }
            }
        }
    }

    private void writeSelectionToPatch(int[] patch, int patchWidth, int patchMinX, int patchMinY, PaintSelection source) {
        int w = source.width();
        int h = source.height();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int px = source.minX + x - patchMinX;
                int py = source.minY + y - patchMinY;
                int sourceIndex = y * w + x;
                if (sourceIndex < 0 || sourceIndex >= source.pixels.length) {
                    continue;
                }
                int color = source.pixels[sourceIndex];
                if (color != 0 && px >= 0 && px < patchWidth && py >= 0 && py * patchWidth + px < patch.length) {
                    patch[py * patchWidth + px] = color;
                }
            }
        }
    }

    private int[] copyRect(int[] source, int minX, int minY, int maxX, int maxY) {
        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        int[] result = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int sx = minX + x;
                int sy = minY + y;
                if (sx >= 0 && sx < PaintOverlayStore.SIZE && sy >= 0 && sy < PaintOverlayStore.SIZE) {
                    result[y * w + x] = source[sy * PaintOverlayStore.SIZE + sx];
                }
            }
        }
        return result;
    }

    private int[] copyPlaneRect(PaintOverlayStore.FaceKey anchor, int minU, int minV, int maxU, int maxV) {
        int w = maxU - minU + 1;
        int h = maxV - minV + 1;
        if (anchor == null || w <= 0 || h <= 0 || (long) w * h > MAX_EDITOR_AREA_PIXELS) {
            return new int[0];
        }
        int[] result = new int[w * h];
        Direction face = anchor.face();
        Map<PaintOverlayStore.FaceKey, int[]> faces = new HashMap<>();
        for (int v = minV; v <= maxV; v++) {
            for (int u = minU; u <= maxU; u++) {
                PaintOverlayStore.FaceKey key = keyAtPlane(anchor, face, u, v);
                if (key == null) {
                    continue;
                }
                int[] pixels = faces.computeIfAbsent(key, PaintOverlayClient::copyEditorFacePixels);
                int lx = localX(face, u);
                int ly = localY(face, v);
                result[(v - minV) * w + (u - minU)] = pixels[ly * PaintOverlayStore.SIZE + lx];
            }
        }
        return result;
    }

    private List<PaintOverlayClient.EditorPreview> buildPlanePreviews(PaintOverlayStore.FaceKey anchor,
                                                                      int minU, int minV, int maxU, int maxV,
                                                                      int color, boolean solid, boolean handles) {
        if (anchor == null || minU > maxU || minV > maxV) {
            return List.of();
        }
        List<PaintOverlayClient.EditorPreview> previews = new ArrayList<>();
        Direction face = anchor.face();
        addHorizontalPlaneEdge(previews, anchor, face, minU - 1, maxU + 1, minV - 1, color, solid);
        addHorizontalPlaneEdge(previews, anchor, face, minU - 1, maxU + 1, maxV + 1, color, solid);
        addVerticalPlaneEdge(previews, anchor, face, minV, maxV, minU - 1, color, solid);
        addVerticalPlaneEdge(previews, anchor, face, minV, maxV, maxU + 1, color, solid);
        if (handles) {
            int midU = (minU + maxU) / 2;
            int midV = (minV + maxV) / 2;
            addPlaneHandle(previews, anchor, face, minU, minV, color);
            if (maxU != minU) {
                addPlaneHandle(previews, anchor, face, midU, minV, color);
                addPlaneHandle(previews, anchor, face, maxU, minV, color);
            }
            if (maxV != minV) {
                addPlaneHandle(previews, anchor, face, maxU, midV, color);
            }
            if (maxU != minU && maxV != minV) {
                addPlaneHandle(previews, anchor, face, maxU, maxV, color);
                addPlaneHandle(previews, anchor, face, midU, maxV, color);
            }
            if (maxV != minV) {
                addPlaneHandle(previews, anchor, face, minU, maxV, color);
            }
            if (maxV != minV) {
                addPlaneHandle(previews, anchor, face, minU, midV, color);
            }
        }
        return previews;
    }

    private List<PaintOverlayClient.EditorGeometryPreview> buildPlaneGeometryPreviews(PaintOverlayStore.FaceKey anchor,
                                                                                      int minU, int minV, int maxU, int maxV,
                                                                                      int color, boolean solid, boolean handles) {
        if (anchor == null || minU > maxU || minV > maxV) {
            return List.of();
        }
        List<PaintOverlayClient.EditorGeometryPreview> previews = new ArrayList<>();
        double line = WORLD_PREVIEW_LINE_WIDTH;
        double left = minU;
        double top = minV;
        double right = maxU + 1.0D;
        double bottom = maxV + 1.0D;
        addGeometryRect(previews, anchor, left, top - line, right, top, color, solid);
        addGeometryRect(previews, anchor, left, bottom, right, bottom + line, color, solid);
        addGeometryRect(previews, anchor, left - line, top, left, bottom, color, solid);
        addGeometryRect(previews, anchor, right, top, right + line, bottom, color, solid);
        if (handles) {
            double midU = (left + right) * 0.5D;
            double midV = (top + bottom) * 0.5D;
            double half = HANDLE_SIZE * 0.5D;
            addGeometryHandle(previews, anchor, left, top, half, color);
            addGeometryHandle(previews, anchor, midU, top, half, color);
            addGeometryHandle(previews, anchor, right, top, half, color);
            addGeometryHandle(previews, anchor, right, midV, half, color);
            addGeometryHandle(previews, anchor, right, bottom, half, color);
            addGeometryHandle(previews, anchor, midU, bottom, half, color);
            addGeometryHandle(previews, anchor, left, bottom, half, color);
            addGeometryHandle(previews, anchor, left, midV, half, color);
        }
        return previews;
    }

    private void addGeometryRect(List<PaintOverlayClient.EditorGeometryPreview> previews,
                                 PaintOverlayStore.FaceKey anchor,
                                 double minU, double minV, double maxU, double maxV,
                                 int color, boolean solid) {
        if (maxU <= minU || maxV <= minV) {
            return;
        }
        previews.add(new PaintOverlayClient.EditorGeometryPreview(anchor, minU, minV, maxU, maxV, color, solid));
    }

    private void addGeometryHandle(List<PaintOverlayClient.EditorGeometryPreview> previews,
                                   PaintOverlayStore.FaceKey anchor,
                                   double u, double v, double halfSize, int color) {
        addGeometryRect(previews, anchor, u - halfSize, v - halfSize, u + halfSize, v + halfSize, 0xFFFFFFFF, true);
    }

    private void addHorizontalPlaneEdge(List<PaintOverlayClient.EditorPreview> previews, PaintOverlayStore.FaceKey anchor,
                                        Direction face, int minU, int maxU, int v, int color, boolean solid) {
        int size = PaintOverlayStore.SIZE;
        for (int blockU = Math.floorDiv(minU, size); blockU <= Math.floorDiv(maxU, size); blockU++) {
            int pieceMinU = Math.max(minU, blockU * size);
            int pieceMaxU = Math.min(maxU, blockU * size + size - 1);
            PaintOverlayStore.FaceKey key = keyAtPlane(anchor, face, pieceMinU, v);
            int x0 = localX(face, pieceMinU);
            int x1 = localX(face, pieceMaxU);
            int y = localY(face, v);
            previews.add(PaintOverlayClient.blockEditorPreview(key,
                    Math.min(x0, x1), y, Math.max(x0, x1), y, color, solid, false));
        }
    }

    private void addVerticalPlaneEdge(List<PaintOverlayClient.EditorPreview> previews, PaintOverlayStore.FaceKey anchor,
                                      Direction face, int minV, int maxV, int u, int color, boolean solid) {
        int size = PaintOverlayStore.SIZE;
        for (int blockV = Math.floorDiv(minV, size); blockV <= Math.floorDiv(maxV, size); blockV++) {
            int pieceMinV = Math.max(minV, blockV * size);
            int pieceMaxV = Math.min(maxV, blockV * size + size - 1);
            PaintOverlayStore.FaceKey key = keyAtPlane(anchor, face, u, pieceMinV);
            int x = localX(face, u);
            int y0 = localY(face, pieceMinV);
            int y1 = localY(face, pieceMaxV);
            previews.add(PaintOverlayClient.blockEditorPreview(key,
                    x, Math.min(y0, y1), x, Math.max(y0, y1), color, solid, false));
        }
    }

    private void addPlaneHandle(List<PaintOverlayClient.EditorPreview> previews, PaintOverlayStore.FaceKey anchor,
                                Direction face, int u, int v, int color) {
        int size = HANDLE_SIZE / 2;
        int minU = u - size;
        int maxU = u + size;
        int minV = v - size;
        int maxV = v + size;
        int blockSize = PaintOverlayStore.SIZE;
        for (int blockU = Math.floorDiv(minU, blockSize); blockU <= Math.floorDiv(maxU, blockSize); blockU++) {
            for (int blockV = Math.floorDiv(minV, blockSize); blockV <= Math.floorDiv(maxV, blockSize); blockV++) {
                int pieceMinU = Math.max(minU, blockU * blockSize);
                int pieceMaxU = Math.min(maxU, blockU * blockSize + blockSize - 1);
                int pieceMinV = Math.max(minV, blockV * blockSize);
                int pieceMaxV = Math.min(maxV, blockV * blockSize + blockSize - 1);
                PaintOverlayStore.FaceKey key = keyAtPlane(anchor, face, pieceMinU, pieceMinV);
                int x0 = localX(face, pieceMinU);
                int x1 = localX(face, pieceMaxU);
                int y0 = localY(face, pieceMinV);
                int y1 = localY(face, pieceMaxV);
                previews.add(PaintOverlayClient.blockEditorPreview(key,
                        Math.min(x0, x1), Math.min(y0, y1),
                        Math.max(x0, x1), Math.max(y0, y1), color, true, false));
            }
        }
    }

    private List<PaintOverlayClient.EditorRegionPatch> buildPlanePatches(PaintOverlayStore.FaceKey anchor,
                                                                         int minU, int minV, int maxU, int maxV,
                                                                         int[] source, boolean writeTransparent) {
        int w = maxU - minU + 1;
        int h = maxV - minV + 1;
        if (anchor == null || source == null || w <= 0 || h <= 0 || source.length < w * h
                || (long) w * h > MAX_EDITOR_AREA_PIXELS) {
            return List.of();
        }
        Map<PaintOverlayStore.FaceKey, FacePatchDraft> drafts = new HashMap<>();
        Direction face = anchor.face();
        for (int y = 0; y < h; y++) {
            int v = minV + y;
            for (int x = 0; x < w; x++) {
                int color = source[y * w + x];
                if (!writeTransparent && color == 0) {
                    continue;
                }
                writePlanePixel(drafts, anchor, face, minU + x, v, color);
            }
        }
        return collectPatches(drafts);
    }

    private List<PaintOverlayClient.EditorPixelPreview> buildPlanePixelPreviews(PaintOverlayStore.FaceKey anchor,
                                                                                int minU, int minV, int maxU, int maxV,
                                                                                int[] source) {
        int w = maxU - minU + 1;
        int h = maxV - minV + 1;
        if (anchor == null || source == null || w <= 0 || h <= 0 || source.length < w * h
                || (long) w * h > MAX_EDITOR_AREA_PIXELS) {
            return List.of();
        }
        List<PaintOverlayClient.EditorPixelPreview> previews = new ArrayList<>();
        Direction face = anchor.face();
        int size = PaintOverlayStore.SIZE;
        for (int blockU = Math.floorDiv(minU, size); blockU <= Math.floorDiv(maxU, size); blockU++) {
            for (int blockV = Math.floorDiv(minV, size); blockV <= Math.floorDiv(maxV, size); blockV++) {
                int pieceMinU = Math.max(minU, blockU * size);
                int pieceMaxU = Math.min(maxU, blockU * size + size - 1);
                int pieceMinV = Math.max(minV, blockV * size);
                int pieceMaxV = Math.min(maxV, blockV * size + size - 1);
                PaintOverlayStore.FaceKey key = keyAtPlane(anchor, face, pieceMinU, pieceMinV);
                int localMinX = Math.min(localX(face, pieceMinU), localX(face, pieceMaxU));
                int localMaxX = Math.max(localX(face, pieceMinU), localX(face, pieceMaxU));
                int localMinY = Math.min(localY(face, pieceMinV), localY(face, pieceMaxV));
                int localMaxY = Math.max(localY(face, pieceMinV), localY(face, pieceMaxV));
                int pieceW = localMaxX - localMinX + 1;
                int pieceH = localMaxY - localMinY + 1;
                int[] pixels = new int[pieceW * pieceH];
                boolean hasPixels = false;
                for (int v = pieceMinV; v <= pieceMaxV; v++) {
                    for (int u = pieceMinU; u <= pieceMaxU; u++) {
                        int color = source[(v - minV) * w + (u - minU)];
                        if (color == 0) {
                            continue;
                        }
                        int px = localX(face, u) - localMinX;
                        int py = localY(face, v) - localMinY;
                        pixels[py * pieceW + px] = color;
                        hasPixels = true;
                    }
                }
                if (hasPixels) {
                    previews.add(new PaintOverlayClient.EditorPixelPreview(key, localMinX, localMinY, pieceW, pieceH, pixels));
                }
            }
        }
        return previews;
    }

    private List<PaintOverlayClient.EditorHidePreview> buildPlaneHidePreviews(PaintOverlayStore.FaceKey anchor,
                                                                              int minU, int minV, int maxU, int maxV) {
        if (anchor == null || minU > maxU || minV > maxV) {
            return List.of();
        }
        List<PaintOverlayClient.EditorHidePreview> previews = new ArrayList<>();
        Direction face = anchor.face();
        int size = PaintOverlayStore.SIZE;
        for (int blockU = Math.floorDiv(minU, size); blockU <= Math.floorDiv(maxU, size); blockU++) {
            for (int blockV = Math.floorDiv(minV, size); blockV <= Math.floorDiv(maxV, size); blockV++) {
                int pieceMinU = Math.max(minU, blockU * size);
                int pieceMaxU = Math.min(maxU, blockU * size + size - 1);
                int pieceMinV = Math.max(minV, blockV * size);
                int pieceMaxV = Math.min(maxV, blockV * size + size - 1);
                PaintOverlayStore.FaceKey key = keyAtPlane(anchor, face, pieceMinU, pieceMinV);
                int x0 = localX(face, pieceMinU);
                int x1 = localX(face, pieceMaxU);
                int y0 = localY(face, pieceMinV);
                int y1 = localY(face, pieceMaxV);
                previews.add(new PaintOverlayClient.EditorHidePreview(key,
                        Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1), Math.max(y0, y1)));
            }
        }
        return previews;
    }

    private void clearPlaneRect(Map<PaintOverlayStore.FaceKey, FacePatchDraft> drafts, PaintOverlayStore.FaceKey anchor,
                                int minU, int minV, int maxU, int maxV) {
        if (anchor == null || minU > maxU || minV > maxV) {
            return;
        }
        Direction face = anchor.face();
        for (int v = minV; v <= maxV; v++) {
            for (int u = minU; u <= maxU; u++) {
                writePlanePixel(drafts, anchor, face, u, v, 0);
            }
        }
    }

    private void clearSelectionPixels(Map<PaintOverlayStore.FaceKey, FacePatchDraft> drafts, PaintSelection source) {
        int w = source.width();
        int h = source.height();
        Direction face = source.key.face();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int index = y * w + x;
                if (index >= 0 && index < source.pixels.length && source.pixels[index] != 0) {
                    writePlanePixel(drafts, source.key, face, source.minX + x, source.minY + y, 0);
                }
            }
        }
    }

    private void clearSelectionSourcePixels(Map<PaintOverlayStore.FaceKey, FacePatchDraft> drafts, PaintSelection source) {
        int w = source.sourceWidth();
        int h = source.sourceHeight();
        Direction face = source.key.face();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int index = y * w + x;
                if (index >= 0 && index < source.sourcePixels.length && source.sourcePixels[index] != 0) {
                    writePlanePixel(drafts, source.key, face, source.sourceMinX + x, source.sourceMinY + y, 0);
                }
            }
        }
    }

    private void writeSelectionToDrafts(Map<PaintOverlayStore.FaceKey, FacePatchDraft> drafts, PaintSelection source) {
        int w = source.width();
        int h = source.height();
        if (w <= 0 || h <= 0 || source.pixels.length == 0) {
            return;
        }
        Direction face = source.key.face();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int index = y * w + x;
                if (index < 0 || index >= source.pixels.length) {
                    continue;
                }
                int color = source.pixels[index];
                if (color != 0) {
                    writePlanePixel(drafts, source.key, face, source.minX + x, source.minY + y, color);
                }
            }
        }
    }

    private void writePlanePixel(Map<PaintOverlayStore.FaceKey, FacePatchDraft> drafts, PaintOverlayStore.FaceKey anchor,
                                 Direction face, int u, int v, int color) {
        PaintOverlayStore.FaceKey key = keyAtPlane(anchor, face, u, v);
        if (key == null) {
            return;
        }
        FacePatchDraft draft = drafts.computeIfAbsent(key, k -> new FacePatchDraft(k, PaintOverlayClient.copyEditorFacePixels(k)));
        draft.set(localX(face, u), localY(face, v), color);
    }

    private List<PaintOverlayClient.EditorRegionPatch> collectPatches(Map<PaintOverlayStore.FaceKey, FacePatchDraft> drafts) {
        List<PaintOverlayClient.EditorRegionPatch> patches = new ArrayList<>();
        for (FacePatchDraft draft : drafts.values()) {
            PaintOverlayClient.EditorRegionPatch patch = draft.toPatch();
            if (patch != null) {
                patches.add(patch);
            }
        }
        return patches;
    }

    private boolean sameKey(PaintOverlayStore.FaceKey a, PaintOverlayStore.FaceKey b) {
        return a != null && b != null && a.equals(b);
    }

    private DragMode selectionHandleAt(int x, int y) {
        if (selection == null || !selection.showHandles) {
            return DragMode.NONE;
        }
        int threshold = Math.max(5, HANDLE_SIZE);
        int right = selection.maxX + 1;
        int bottom = selection.maxY + 1;
        int midX = (selection.minX + right) / 2;
        int midY = (selection.minY + bottom) / 2;
        if (nearHandle(x, y, selection.minX, selection.minY, threshold)) return DragMode.SELECT_RESIZE_NW;
        if (nearHandle(x, y, midX, selection.minY, threshold)) return DragMode.SELECT_RESIZE_N;
        if (nearHandle(x, y, right, selection.minY, threshold)) return DragMode.SELECT_RESIZE_NE;
        if (nearHandle(x, y, right, midY, threshold)) return DragMode.SELECT_RESIZE_E;
        if (nearHandle(x, y, right, bottom, threshold)) return DragMode.SELECT_RESIZE_SE;
        if (nearHandle(x, y, midX, bottom, threshold)) return DragMode.SELECT_RESIZE_S;
        if (nearHandle(x, y, selection.minX, bottom, threshold)) return DragMode.SELECT_RESIZE_SW;
        if (nearHandle(x, y, selection.minX, midY, threshold)) return DragMode.SELECT_RESIZE_W;
        return DragMode.NONE;
    }

    private boolean nearHandle(int x, int y, int handleX, int handleY, int threshold) {
        return Math.abs(x - handleX) <= threshold && Math.abs(y - handleY) <= threshold;
    }

    private boolean isResizeDrag(DragMode mode) {
        return mode == DragMode.SELECT_RESIZE_N
                || mode == DragMode.SELECT_RESIZE_NE
                || mode == DragMode.SELECT_RESIZE_E
                || mode == DragMode.SELECT_RESIZE_SE
                || mode == DragMode.SELECT_RESIZE_S
                || mode == DragMode.SELECT_RESIZE_SW
                || mode == DragMode.SELECT_RESIZE_W
                || mode == DragMode.SELECT_RESIZE_NW
                || mode == DragMode.SELECT_RESIZE_NE
                || mode == DragMode.SELECT_RESIZE_SW
                || mode == DragMode.SELECT_RESIZE_SE;
    }

    private void resizeSelectionDrag() {
        int minX = dragAnchorMinX;
        int minY = dragAnchorMinY;
        int maxX = dragAnchorMaxX;
        int maxY = dragAnchorMaxY;
        if (dragMode == DragMode.SELECT_RESIZE_NW || dragMode == DragMode.SELECT_RESIZE_SW || dragMode == DragMode.SELECT_RESIZE_W) {
            minX = dragCurrentX;
        }
        if (dragMode == DragMode.SELECT_RESIZE_NE || dragMode == DragMode.SELECT_RESIZE_SE || dragMode == DragMode.SELECT_RESIZE_E) {
            maxX = dragCurrentX - 1;
        }
        if (dragMode == DragMode.SELECT_RESIZE_NW || dragMode == DragMode.SELECT_RESIZE_NE || dragMode == DragMode.SELECT_RESIZE_N) {
            minY = dragCurrentY;
        }
        if (dragMode == DragMode.SELECT_RESIZE_SW || dragMode == DragMode.SELECT_RESIZE_SE || dragMode == DragMode.SELECT_RESIZE_S) {
            maxY = dragCurrentY - 1;
        }
        if (minX > maxX) {
            int swap = minX;
            minX = maxX;
            maxX = swap;
        }
        if (minY > maxY) {
            int swap = minY;
            minY = maxY;
            maxY = swap;
        }
        int newW = maxX - minX + 1;
        int newH = maxY - minY + 1;
        if (newW <= 0 || newH <= 0 || newW > MAX_EDITOR_PIXEL_SPAN || newH > MAX_EDITOR_PIXEL_SPAN
                || (long) newW * newH > MAX_EDITOR_AREA_PIXELS) {
            return;
        }
        maxX = minX + newW - 1;
        maxY = minY + newH - 1;
        int oldW = dragAnchorMaxX - dragAnchorMinX + 1;
        int oldH = dragAnchorMaxY - dragAnchorMinY + 1;
        selection.minX = minX;
        selection.minY = minY;
        selection.maxX = maxX;
        selection.maxY = maxY;
        selection.pixels = scalePixels(dragAnchorPixels, oldW, oldH, newW, newH);
        selection.solid = true;
        selection.showHandles = true;
    }

    private int[] scalePixels(int[] source, int oldW, int oldH, int newW, int newH) {
        int[] scaled = new int[newW * newH];
        if (source == null || oldW <= 0 || oldH <= 0 || source.length == 0) {
            return scaled;
        }
        for (int y = 0; y < newH; y++) {
            int sy = MathHelper.clamp(y * oldH / newH, 0, oldH - 1);
            for (int x = 0; x < newW; x++) {
                int sx = MathHelper.clamp(x * oldW / newW, 0, oldW - 1);
                int sourceIndex = sy * oldW + sx;
                if (sourceIndex >= 0 && sourceIndex < source.length) {
                    scaled[y * newW + x] = source[sourceIndex];
                }
            }
        }
        return scaled;
    }

    private void drawBorder(DrawContext context, int x, int y, int w, int h) {
        int c = Math.min(CHAMFER, Math.max(1, Math.min(w, h) / 3));
        context.fill(x + c, y, x + w - c, y + 1, OUTLINE);
        context.fill(x + c, y + h - 1, x + w - c, y + h, OUTLINE_SOFT);
        context.fill(x, y + c, x + 1, y + h - c, OUTLINE);
        context.fill(x + w - 1, y + c, x + w, y + h - c, OUTLINE_SOFT);
        for (int i = 0; i < c; i++) {
            context.fill(x + i, y + c - i - 1, x + i + 1, y + c - i, OUTLINE);
            context.fill(x + w - c + i, y + i, x + w - c + i + 1, y + i + 1, OUTLINE);
            context.fill(x + i, y + h - c + i, x + i + 1, y + h - c + i + 1, OUTLINE_SOFT);
            context.fill(x + w - i - 1, y + h - c + i, x + w - i, y + h - c + i + 1, OUTLINE_SOFT);
        }
    }

    private void drawStar(DrawContext context, int x, int y, int size, boolean favorite) {
        int sx = x + size - 8;
        int sy = y + 1;
        context.drawText(textRenderer, Text.literal("*"), sx, sy, favorite ? 0xFFFFD75E : TEXT_SECONDARY, true);
    }

    private void drawSmallButton(DrawContext context, int x, int y, int w, int h, String text) {
        drawControlSurface(context, x, y, w, h, false, false);
        int textX = x + Math.max(4, (w - textRenderer.getWidth(text)) / 2);
        context.drawText(textRenderer, Text.literal(text), textX, y + 5, TEXT_PRIMARY, false);
    }

    private void drawToggle(DrawContext context, int x, int y, int w, int h, String text, boolean active) {
        drawControlSurface(context, x, y, w, h, active, false);
        context.drawText(textRenderer, Text.literal(text), x + 6, y + 5, active ? TEXT_PRIMARY : TEXT_SECONDARY, false);
    }

    private void drawSection(DrawContext context, int x, int y, int w, int h) {
        fillChamfered(context, x, y, w, h, CHAMFER, SURFACE_SOFT);
        drawBorder(context, x, y, w, h);
    }

    private void drawSectionTitle(DrawContext context, int x, int y, String text) {
        context.fill(x, y + 10, x + 12, y + 11, ACCENT);
        context.drawText(textRenderer, Text.literal(text), x + 16, y + 5, TEXT_PRIMARY, false);
    }

    private void drawControlSurface(DrawContext context, int x, int y, int w, int h, boolean active, boolean hover) {
        int fill = active ? ACCENT_DARK : (hover ? SURFACE_HOVER : SURFACE_SOFT);
        fillChamfered(context, x, y, w, h, CHAMFER, fill);
        drawBorder(context, x, y, w, h);
    }

    private static void fillChamfered(DrawContext context, int x, int y, int w, int h, int cut, int color) {
        int c = Math.min(cut, Math.max(0, Math.min(w, h) / 3));
        context.fill(x + c, y, x + w - c, y + h, color);
        context.fill(x, y + c, x + w, y + h - c, color);
        for (int i = 0; i < c; i++) {
            context.fill(x + c - i - 1, y + i, x + w - c + i + 1, y + i + 1, color);
            context.fill(x + c - i - 1, y + h - i - 1, x + w - c + i + 1, y + h - i, color);
        }
    }

    private String selectedToolName() {
        return switch (selectedTool) {
            case BRUSH -> "画笔";
            case ERASER -> "橡皮";
            case PAPER -> "纸张";
            case PRESET -> "预设";
            case SELECT -> "选区";
            case SHAPE -> "形状";
            case NONE -> "视角";
        };
    }

    private int rightX() {
        return Math.max(LEFT_WIDTH + 20, width - RIGHT_WIDTH);
    }

    private static boolean isInsideStar(int x, int y, int size, double mouseX, double mouseY) {
        return isInside(x + size - 9, y, 9, 10, mouseX, mouseY);
    }

    private static boolean isInside(int x, int y, int w, int h, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static boolean isValidHexInput(String text) {
        String digits = stripHexPrefix(text);
        if (digits.length() > 6 || text.indexOf('#') > 0) {
            return false;
        }
        for (int i = 0; i < digits.length(); i++) {
            if (Character.digit(digits.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static String stripHexPrefix(String text) {
        return text.startsWith("#") ? text.substring(1) : text;
    }

    private static String toHex(int color) {
        return String.format("#%06X", color & 0xFFFFFF);
    }

    private static int hsvToRgb(float h, float s, float v) {
        float hue = (h - (float) Math.floor(h)) * 6.0F;
        int sector = (int) Math.floor(hue);
        float f = hue - sector;
        float p = v * (1.0F - s);
        float q = v * (1.0F - f * s);
        float t = v * s * f + v * (1.0F - s);
        float r;
        float g;
        float b;
        switch (sector) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return ((int) (r * 255.0F) << 16) | ((int) (g * 255.0F) << 8) | (int) (b * 255.0F);
    }

    private enum DragMode {
        NONE,
        SELECT_CREATE,
        SELECT_MOVE,
        SELECT_RESIZE_NW,
        SELECT_RESIZE_N,
        SELECT_RESIZE_NE,
        SELECT_RESIZE_E,
        SELECT_RESIZE_S,
        SELECT_RESIZE_SW,
        SELECT_RESIZE_W,
        SELECT_RESIZE_SE,
        SHAPE_CREATE
    }

    private enum ShapeMode {
        LINE("直线"),
        RECTANGLE("矩形"),
        RECTANGLE_FILLED("填充矩形"),
        ELLIPSE("椭圆"),
        ELLIPSE_FILLED("填充椭圆"),
        CIRCLE("圆形"),
        CIRCLE_FILLED("填充圆形"),
        DIAMOND("菱形"),
        DIAMOND_FILLED("填充菱形"),
        TRIANGLE("三角形"),
        TRIANGLE_FILLED("填充三角形");

        private final String label;

        ShapeMode(String label) {
            this.label = label;
        }
    }

    private record PlanePoint(Direction face, int u, int v) {
    }

    private static final class FacePatchDraft {
        private final PaintOverlayStore.FaceKey key;
        private final int[] base;
        private final int[] pixels;
        private int minX = PaintOverlayStore.SIZE;
        private int minY = PaintOverlayStore.SIZE;
        private int maxX = -1;
        private int maxY = -1;

        private FacePatchDraft(PaintOverlayStore.FaceKey key, int[] basePixels) {
            this.key = key;
            this.base = Arrays.copyOf(basePixels, PaintOverlayStore.FACE_PIXELS);
            this.pixels = Arrays.copyOf(basePixels, PaintOverlayStore.FACE_PIXELS);
        }

        private void set(int x, int y, int color) {
            if (x < 0 || x >= PaintOverlayStore.SIZE || y < 0 || y >= PaintOverlayStore.SIZE) {
                return;
            }
            int index = y * PaintOverlayStore.SIZE + x;
            if (pixels[index] == color) {
                return;
            }
            pixels[index] = color;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }

        private PaintOverlayClient.EditorRegionPatch toPatch() {
            if (maxX < minX || maxY < minY) {
                return null;
            }
            int w = maxX - minX + 1;
            int h = maxY - minY + 1;
            int[] patch = new int[w * h];
            boolean changed = false;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int sourceIndex = (minY + y) * PaintOverlayStore.SIZE + minX + x;
                    int color = pixels[sourceIndex];
                    patch[y * w + x] = color;
                    if (base[sourceIndex] != color) {
                        changed = true;
                    }
                }
            }
            return changed ? new PaintOverlayClient.EditorRegionPatch(key, minX, minY, w, h, patch) : null;
        }
    }

    private static final class PaintSelection {
        private final PaintOverlayStore.FaceKey key;
        private int minX;
        private int minY;
        private int maxX;
        private int maxY;
        private int[] pixels;
        private boolean solid;
        private boolean showHandles;
        private int sourceMinX;
        private int sourceMinY;
        private int sourceMaxX;
        private int sourceMaxY;
        private int[] sourcePixels;
        private boolean clearSource;

        private PaintSelection(PaintOverlayStore.FaceKey key, int minX, int minY, int maxX, int maxY,
                               int[] pixels, boolean solid, boolean showHandles) {
            this(key, minX, minY, maxX, maxY, pixels, solid, showHandles, minX, minY, maxX, maxY, true);
        }

        private PaintSelection(PaintOverlayStore.FaceKey key, int minX, int minY, int maxX, int maxY,
                               int[] pixels, boolean solid, boolean showHandles,
                               int sourceMinX, int sourceMinY, int sourceMaxX, int sourceMaxY, boolean clearSource) {
            this(key, minX, minY, maxX, maxY, pixels, solid, showHandles,
                    sourceMinX, sourceMinY, sourceMaxX, sourceMaxY, clearSource, pixels);
        }

        private PaintSelection(PaintOverlayStore.FaceKey key, int minX, int minY, int maxX, int maxY,
                               int[] pixels, boolean solid, boolean showHandles,
                               int sourceMinX, int sourceMinY, int sourceMaxX, int sourceMaxY, boolean clearSource,
                               int[] sourcePixels) {
            this.key = key;
            this.minX = minX;
            this.minY = minY;
            this.maxX = Math.max(maxX, this.minX);
            this.maxY = Math.max(maxY, this.minY);
            this.pixels = Arrays.copyOf(pixels, width() * height());
            this.solid = solid;
            this.showHandles = showHandles;
            this.sourceMinX = sourceMinX;
            this.sourceMinY = sourceMinY;
            this.sourceMaxX = Math.max(sourceMaxX, this.sourceMinX);
            this.sourceMaxY = Math.max(sourceMaxY, this.sourceMinY);
            this.sourcePixels = Arrays.copyOf(sourcePixels, sourceWidth() * sourceHeight());
            this.clearSource = clearSource;
        }

        private int width() {
            return maxX - minX + 1;
        }

        private int height() {
            return maxY - minY + 1;
        }

        private int sourceWidth() {
            return sourceMaxX - sourceMinX + 1;
        }

        private int sourceHeight() {
            return sourceMaxY - sourceMinY + 1;
        }

        private PaintSelection copy() {
            return new PaintSelection(key, minX, minY, maxX, maxY, pixels, solid, showHandles,
                    sourceMinX, sourceMinY, sourceMaxX, sourceMaxY, clearSource, sourcePixels);
        }

        private PaintSelection copyDetached() {
            return new PaintSelection(key, minX, minY, maxX, maxY, pixels, solid, showHandles,
                    minX, minY, maxX, maxY, false);
        }

        private void rotateClockwise() {
            int oldW = width();
            int oldH = height();
            int[] rotated = new int[oldW * oldH];
            for (int y = 0; y < oldH; y++) {
                for (int x = 0; x < oldW; x++) {
                    int nx = oldH - 1 - y;
                    int ny = x;
                    rotated[ny * oldH + nx] = pixels[y * oldW + x];
                }
            }
            pixels = rotated;
            resizeBounds(oldH, oldW);
        }

        private void rotateCounterClockwise() {
            int oldW = width();
            int oldH = height();
            int[] rotated = new int[oldW * oldH];
            for (int y = 0; y < oldH; y++) {
                for (int x = 0; x < oldW; x++) {
                    int nx = y;
                    int ny = oldW - 1 - x;
                    rotated[ny * oldH + nx] = pixels[y * oldW + x];
                }
            }
            pixels = rotated;
            resizeBounds(oldH, oldW);
        }

        private void mirrorHorizontal() {
            int w = width();
            int h = height();
            int[] mirrored = new int[pixels.length];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    mirrored[y * w + (w - 1 - x)] = pixels[y * w + x];
                }
            }
            pixels = mirrored;
        }

        private void mirrorVertical() {
            int w = width();
            int h = height();
            int[] mirrored = new int[pixels.length];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    mirrored[(h - 1 - y) * w + x] = pixels[y * w + x];
                }
            }
            pixels = mirrored;
        }

        private void resizeBounds(int newWidth, int newHeight) {
            newWidth = MathHelper.clamp(newWidth, 1, MAX_EDITOR_PIXEL_SPAN);
            newHeight = MathHelper.clamp(newHeight, 1, MAX_EDITOR_PIXEL_SPAN);
            maxX = minX + newWidth - 1;
            maxY = minY + newHeight - 1;
        }
    }
}
