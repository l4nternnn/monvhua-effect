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
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.List;

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
    private static final int HISTORY_ROW_HEIGHT = 17;
    private static final int HISTORY_MENU_WIDTH = 86;
    private static final int HISTORY_MENU_HEIGHT = 24;
    private static final int PRESET_PANEL_WIDTH = 190;
    private static final int PRESET_PANEL_HEIGHT = 188;
    private static final int PRESET_SLOT_SIZE = 42;
    private static final int PRESET_SLOT_GAP = 8;
    private static final int PRESET_MENU_WIDTH = 92;
    private static final int PRESET_MENU_ROW_HEIGHT = 18;
    private static final int PAPER_SIZE_Y = 82;
    private static final int TOOL_X = 10;
    private static final int TOOL_BRUSH_Y = 36;
    private static final int TOOL_ERASER_Y = 78;
    private static final int TOOL_PAPER_Y = 120;
    private static final int TOOL_NONE_Y = 162;
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
    private boolean presetPanelOpen;
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

    public PaintEditorScreen() {
        super(Text.literal("Paint Editor"));
        loadSelectedColor();
    }

    @Override
    protected void init() {
        PaintOverlayClient.enterPaintEditor(client);
        int rightX = rightX();
        hexField = addDrawableChild(new TextFieldWidget(textRenderer, rightX + 16, BRUSH_HEX_Y, 86, 18, Text.literal("HEX")));
        hexField.setMaxLength(7);
        hexField.setTextPredicate(PaintEditorScreen::isValidHexInput);
        hexField.setChangedListener(this::onHexChanged);
        updateHexField();
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
        uploadColorMapIfNeeded();
        drawLeftPanel(context, mouseX, mouseY);
        drawRightPanel(context, mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
        drawPresetPanel(context, mouseX, mouseY);
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
            if (selectedTool != PaintOverlayClient.EditorTool.NONE && !isCtrlDown()) {
                rightUsing = true;
                PaintOverlayClient.performEditorUseAtScreenPoint(client, selectedTool, mouseX, mouseY, width, height);
                return true;
            }
            if (selectedTool == PaintOverlayClient.EditorTool.NONE || isCtrlDown()) {
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
        if (handlePresetPanelMouseDown(mouseX, mouseY)) {
            return true;
        }
        if (hexField != null && hexField.visible && hexField.isMouseOver(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (handleLeftPanelClick(mouseX, mouseY) || handleRightPanelClick(mouseX, mouseY)) {
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
            pickColor(mouseX, mouseY, true);
            return true;
        }
        if (draggingAlpha && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            updateAlphaFromMouse(mouseX);
            return true;
        }
        if (leftLooking && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            PaintOverlayClient.rotateEditorView(client, deltaX, deltaY, selectedTool == PaintOverlayClient.EditorTool.NONE ? 0.18F : 0.34F);
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
            if (draggingAlpha) {
                updateAlphaFromMouse(mouseX);
                draggingAlpha = false;
                return true;
            }
            if (pickingColor) {
                PaintOverlayClient.recordPickedColor(PaintOverlayClient.selectedColor());
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
            if (keyCode == GLFW.GLFW_KEY_B) {
                presetPanelOpen = !presetPanelOpen;
                closePresetStoreContextMenu();
            } else {
                close();
            }
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_C) {
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
        if (PaintOverlayClient.isSuspendedPaintEditor(this)) {
            return;
        }
        if (colorMapTexture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(COLOR_MAP_TEXTURE_ID);
            colorMapTexture = null;
        }
        PaintOverlayClient.exitPaintEditor(MinecraftClient.getInstance());
    }

    private void drawLeftPanel(DrawContext context, int mouseX, int mouseY) {
        context.fill(0, 0, LEFT_WIDTH, height, SURFACE);
        context.fill(LEFT_WIDTH - 1, 0, LEFT_WIDTH, height, OUTLINE);
        context.fill(12, 8, 42, 9, ACCENT);
        context.drawText(textRenderer, Text.literal("Tools"), 10, 14, TEXT_SECONDARY, false);
        drawToolButton(context, TOOL_X, TOOL_BRUSH_Y, PaintOverlayClient.EditorTool.BRUSH, new ItemStack(PaintItems.PAINT_BRUSH), mouseX, mouseY);
        drawToolButton(context, TOOL_X, TOOL_ERASER_Y, PaintOverlayClient.EditorTool.ERASER, new ItemStack(PaintItems.ERASER), mouseX, mouseY);
        drawToolButton(context, TOOL_X, TOOL_PAPER_Y, PaintOverlayClient.EditorTool.PAPER, new ItemStack(PaintItems.PAINT_PAPER), mouseX, mouseY);
        drawToolButton(context, TOOL_X, TOOL_NONE_Y, PaintOverlayClient.EditorTool.NONE, ItemStack.EMPTY, mouseX, mouseY);
    }

    private void drawToolButton(DrawContext context, int x, int y, PaintOverlayClient.EditorTool tool, ItemStack stack, int mouseX, int mouseY) {
        boolean selected = selectedTool == tool;
        boolean hover = isInside(x, y, 34, 34, mouseX, mouseY);
        drawControlSurface(context, x, y, 34, 34, selected, hover);
        if (selected) {
            context.fill(x + 1, y + CHAMFER, x + 4, y + 34 - CHAMFER, ACCENT);
        }
        if (stack.isEmpty()) {
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
        context.drawText(textRenderer, Text.literal("Paint"), x + 16, 16, TEXT_PRIMARY, false);
        context.drawText(textRenderer, Text.literal(selectedToolName()), x + 16, 28, TEXT_SECONDARY, false);
        if (hexField != null) {
            hexField.visible = selectedTool == PaintOverlayClient.EditorTool.BRUSH;
        }
        switch (selectedTool) {
            case BRUSH -> drawBrushPanel(context, x + 16);
            case ERASER -> drawEraserPanel(context, x + 16);
            case PAPER -> drawPaperPanel(context, x + 16);
            case NONE -> drawViewPanel(context, x + 16);
        }
    }

    private void drawBrushPanel(DrawContext context, int x) {
        drawSection(context, x - 8, 48, 166, 228);
        drawSectionTitle(context, x, 56, "Color");
        drawColorMap(context, x, BRUSH_COLOR_Y);
        drawHueBar(context, x + MAP_SIZE + 8, BRUSH_COLOR_Y);
        drawColorCursor(context, x, BRUSH_COLOR_Y);

        int color = PaintOverlayClient.selectedColor();
        drawSwatch(context, x, BRUSH_SWATCH_Y, 28, color, true);
        drawStar(context, x, BRUSH_SWATCH_Y, 28, PaintOverlayClient.isFavoriteColor(color));
        context.drawText(textRenderer, Text.literal(toHex(color)), x + 40, 192, TEXT_PRIMARY, false);

        context.drawText(textRenderer, Text.literal("HEX"), x, 216, TEXT_SECONDARY, false);
        drawAlphaSlider(context, x, BRUSH_ALPHA_Y);
        drawSection(context, x - 8, 284, 166, 94);
        drawRecent(context, x, BRUSH_RECENT_Y);
        drawFavorites(context, x + BRUSH_FAVORITE_X_OFFSET, BRUSH_RECENT_Y);
        drawSection(context, x - 8, 390, 166, 46);
        drawRadiusControls(context, x, BRUSH_RADIUS_Y);
    }

    private void drawEraserPanel(DrawContext context, int x) {
        drawSection(context, x - 8, 48, 166, 112);
        drawSectionTitle(context, x, 56, "Eraser");
        drawRadiusControls(context, x, ERASER_RADIUS_Y);
        drawHistoryPanel(context, x, lastMouseX, lastMouseY);
        int modeY = ERASER_MODE_Y;
        boolean faceMode = PaintOverlayClient.eraserFaceMode();
        drawToggle(context, x, modeY, 52, 18, "Pixel", !faceMode);
        drawToggle(context, x + 58, modeY, 52, 18, "Face", faceMode);
    }

    private void drawHistoryPanel(DrawContext context, int x, double mouseX, double mouseY) {
        int sectionY = ERASER_HISTORY_Y - 18;
        int sectionH = Math.max(72, height - sectionY - 16);
        drawSection(context, x - 8, sectionY, 166, sectionH);
        drawSectionTitle(context, x, sectionY + 8, "History");

        List<PaintOverlayClient.EditorHistoryEntry> entries = PaintOverlayClient.editorHistory();
        int currentIndex = PaintOverlayClient.editorHistoryCurrentIndex();
        int listY = ERASER_HISTORY_Y + 12;
        int maxRows = Math.max(1, (height - listY - 24) / HISTORY_ROW_HEIGHT);
        if (entries.isEmpty()) {
            context.drawText(textRenderer, Text.literal("No history"), x, listY + 4, TEXT_SECONDARY, false);
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
        context.drawText(textRenderer, Text.literal("Jump"), x + 8, y + 8, TEXT_PRIMARY, false);
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
        drawSectionTitle(context, x, 56, "Paper");
        int paperY = PAPER_SIZE_Y;
        drawSmallButton(context, x, paperY, 22, 18, "-");
        context.drawText(textRenderer, Text.literal("P " + PaintOverlayClient.selectedPaperSize()), x + 30, paperY + 5, TEXT_PRIMARY, false);
        drawSmallButton(context, x + 66, paperY, 22, 18, "+");
        drawSmallButton(context, x, paperY + 34, 88, 18, "Import");
    }

    private void drawViewPanel(DrawContext context, int x) {
        drawSection(context, x - 8, 48, 166, 80);
        drawSectionTitle(context, x, 56, "View");
        context.drawText(textRenderer, Text.literal("Free camera"), x, 82, TEXT_SECONDARY, false);
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
        context.drawText(textRenderer, Text.literal("Recent"), x, y - 12, TEXT_PRIMARY, false);
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
        context.drawText(textRenderer, Text.literal("Fav"), x, y - 12, TEXT_PRIMARY, false);
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
        context.drawText(textRenderer, Text.literal("Opacity " + Math.round(alpha * 100.0F / 255.0F) + "%"), x, y - 10, TEXT_SECONDARY, false);
        drawChecker(context, x, y, 118, 10, 5);
        context.fill(x, y, x + 118, y + 10, PaintOverlayClient.selectedColor());
        drawBorder(context, x, y, 118, 10);
        int knobX = x + Math.round(alpha * 118.0F / 255.0F);
        context.fill(knobX - 2, y - 3, knobX + 3, y + 13, 0xFF0B1016);
        context.fill(knobX - 1, y - 2, knobX + 2, y + 12, 0xFFFFFFFF);
    }

    private void drawPresetPanel(DrawContext context, int mouseX, int mouseY) {
        if (!presetPanelOpen) {
            return;
        }
        int x = presetPanelX();
        int y = presetPanelY();
        drawSection(context, x, y, PRESET_PANEL_WIDTH, PRESET_PANEL_HEIGHT);
        drawSectionTitle(context, x + 10, y + 8, "Presets");
        context.drawText(textRenderer, Text.literal("B"), x + PRESET_PANEL_WIDTH - 20, y + 13, TEXT_SECONDARY, false);
        int hoverSlot = presetSlotAt(mouseX, mouseY);
        for (int slot = 0; slot < PaintBrushItem.COLOR_SLOTS; slot++) {
            drawPresetSlot(context, presetSlotX(slot), presetSlotY(slot), slot, slot == hoverSlot);
        }
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

    private void drawPresetSlot(DrawContext context, int x, int y, int slot, boolean hover) {
        drawPresetSlot(context, x, y, slot, hover, PRESET_SLOT_SIZE);
    }

    private void drawPresetSlot(DrawContext context, int x, int y, int slot, boolean hover, int size) {
        boolean filled = PaintOverlayClient.presetSlotFilled(slot);
        context.fill(x - 1, y - 1, x + size + 1, y + size + 1, hover ? 0xAA8DDAFF : 0x66000000);
        if (filled) {
            int color = PaintOverlayClient.presetSlotColor(slot);
            drawChecker(context, x, y, size, size, 6);
            fillChamfered(context, x, y, size, size, Math.min(4, CHAMFER), color);
            drawBorder(context, x, y, size, size);
            String hex = toHex(color);
            context.fill(x + 2, y + size - 12, x + size - 2, y + size - 2, 0x99000000);
            context.drawText(textRenderer, Text.literal(hex), x + Math.max(2, (size - textRenderer.getWidth(hex)) / 2), y + size - 10, 0xFFFFFFFF, false);
        } else {
            drawChecker(context, x, y, size, size, 6);
            context.fill(x, y, x + size, y + size, 0x665A6068);
            drawDashedBorder(context, x, y, size, size, 0xFFD4D9DF);
            String empty = "\u7A7A";
            context.drawText(textRenderer, Text.literal(empty), x + (size - textRenderer.getWidth(empty)) / 2, y + (size - 8) / 2, TEXT_SECONDARY, false);
        }
        context.drawText(textRenderer, Text.literal(String.valueOf(slot + 1)), x + 3, y + 3, filled ? 0xDDFFFFFF : 0xFF30343A, false);
    }

    private void drawRadiusControls(DrawContext context, int x, int y) {
        int radius = PaintOverlayClient.selectedRadius();
        drawSmallButton(context, x, y, 22, 18, "-");
        context.fill(x + 28, y + 8, x + 62, y + 10, 0x664C5968);
        context.fill(x + 28, y + 8, x + 28 + Math.min(34, radius), y + 10, ACCENT);
        context.drawText(textRenderer, Text.literal("R " + radius), x + 30, y - 8, TEXT_SECONDARY, false);
        drawSmallButton(context, x + 66, y, 22, 18, "+");
    }

    private boolean handleLeftPanelClick(double mouseX, double mouseY) {
        if (!isInside(0, 0, LEFT_WIDTH, height, mouseX, mouseY)) {
            return false;
        }
        PaintOverlayClient.EditorTool clicked = toolAt(mouseX, mouseY);
        if (clicked != null) {
            selectedTool = selectedTool == clicked ? PaintOverlayClient.EditorTool.NONE : clicked;
        }
        return true;
    }

    private PaintOverlayClient.EditorTool toolAt(double mouseX, double mouseY) {
        if (isInside(TOOL_X, TOOL_BRUSH_Y, 34, 34, mouseX, mouseY)) return PaintOverlayClient.EditorTool.BRUSH;
        if (isInside(TOOL_X, TOOL_ERASER_Y, 34, 34, mouseX, mouseY)) return PaintOverlayClient.EditorTool.ERASER;
        if (isInside(TOOL_X, TOOL_PAPER_Y, 34, 34, mouseX, mouseY)) return PaintOverlayClient.EditorTool.PAPER;
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
                if (handleRadiusClick(mouseX, mouseY, x, BRUSH_RADIUS_Y)) {
                    return true;
                }
                return true;
            }
            case ERASER -> {
                if (handleRadiusClick(mouseX, mouseY, x, ERASER_RADIUS_Y)) {
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
            case NONE -> {
                return true;
            }
        }
        return true;
    }

    private boolean handleRadiusClick(double mouseX, double mouseY, int x, int y) {
        if (isInside(x, y, 22, 18, mouseX, mouseY)) {
            PaintOverlayClient.setSelectedRadius(PaintOverlayClient.selectedRadius() - 1);
            return true;
        }
        if (isInside(x + 66, y, 22, 18, mouseX, mouseY)) {
            PaintOverlayClient.setSelectedRadius(PaintOverlayClient.selectedRadius() + 1);
            return true;
        }
        return false;
    }

    private boolean handleAlphaSliderClick(double mouseX, double mouseY, int x) {
        if (!isInside(x, BRUSH_ALPHA_Y - 5, 118, 20, mouseX, mouseY)) {
            return false;
        }
        updateAlphaFromMouse(mouseX);
        return true;
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

    private boolean handlePresetPanelMouseDown(double mouseX, double mouseY) {
        if (!presetPanelOpen) {
            return false;
        }
        if (!isInside(presetPanelX(), presetPanelY(), PRESET_PANEL_WIDTH, PRESET_PANEL_HEIGHT, mouseX, mouseY)) {
            return false;
        }
        int slot = presetSlotAt(mouseX, mouseY);
        if (slot >= 0) {
            PaintOverlayClient.selectBrushSlot(slot);
            if (PaintOverlayClient.presetSlotFilled(slot)) {
                selectColor(PaintOverlayClient.presetSlotColor(slot), false, true);
            }
        }
        return true;
    }

    private int presetPanelX() {
        return MathHelper.clamp(rightX() - PRESET_PANEL_WIDTH - 10, LEFT_WIDTH + 8, Math.max(LEFT_WIDTH + 8, width - PRESET_PANEL_WIDTH - 2));
    }

    private int presetPanelY() {
        return 48;
    }

    private int presetGridX() {
        return presetPanelX() + 18;
    }

    private int presetGridY() {
        return presetPanelY() + 36;
    }

    private int presetSlotX(int slot) {
        return presetGridX() + (slot % 3) * (PRESET_SLOT_SIZE + PRESET_SLOT_GAP);
    }

    private int presetSlotY(int slot) {
        return presetGridY() + (slot / 3) * (PRESET_SLOT_SIZE + PRESET_SLOT_GAP);
    }

    private int presetSlotAt(double mouseX, double mouseY) {
        for (int slot = 0; slot < PaintBrushItem.COLOR_SLOTS; slot++) {
            if (isInside(presetSlotX(slot), presetSlotY(slot), PRESET_SLOT_SIZE, PRESET_SLOT_SIZE, mouseX, mouseY)) {
                return slot;
            }
        }
        return -1;
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
                    PaintOverlayClient.setSelectedRadius(PaintOverlayClient.selectedRadius() + delta);
                    yield true;
                }
                yield false;
            }
            case ERASER -> {
                if (isInside(x - 8, 48, 166, 112, mouseX, mouseY)
                        || isInside(x, ERASER_RADIUS_Y - 12, 90, 34, mouseX, mouseY)) {
                    PaintOverlayClient.setSelectedRadius(PaintOverlayClient.selectedRadius() + delta);
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
        int x = rightX() + 16;
        int y = BRUSH_COLOR_Y;
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
            case BRUSH -> "Brush";
            case ERASER -> "Eraser";
            case PAPER -> "Paper";
            case NONE -> "View";
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
}
