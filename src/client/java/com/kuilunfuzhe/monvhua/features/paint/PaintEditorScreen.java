package com.kuilunfuzhe.monvhua.features.paint;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.item.paint.PaintItems;
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
    private boolean rightUsing;
    private boolean leftLooking;
    private boolean rightPanning;
    private PaintOverlayClient.ScreenPanAnchor rightPanAnchor;
    private double lastMouseX;
    private double lastMouseY;

    public PaintEditorScreen() {
        super(Text.literal("Paint Editor"));
        loadSelectedColor();
    }

    @Override
    protected void init() {
        PaintOverlayClient.enterPaintEditor(client);
        int rightX = rightX();
        hexField = addDrawableChild(new TextFieldWidget(textRenderer, rightX + 16, 138, 86, 18, Text.literal("HEX")));
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
        if (client.options.jumpKey.isPressed()) {
            vertical += 0.36D;
        }
        if (client.options.sneakKey.isPressed()) {
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
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    protected void applyBlur(DrawContext context) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            PaintOverlayClient.handleBrushPickInputAtScreenPoint(client, mouseX, mouseY, width, height);
            loadSelectedColor();
            updateHexField();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
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
        if (pickingColor && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            pickColor(mouseX, mouseY, true);
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
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
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
        PaintOverlayClient.moveEditorViewTowardScreenPoint(client, mouseX, mouseY, width, height, Math.signum(verticalAmount) * 0.45D);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (hexField != null && hexField.visible && hexField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (keyCode == GLFW.GLFW_KEY_B || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            PaintOverlayClient.markPaintEditorKeyConsumed();
            close();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_Z) {
            PaintOverlayClient.undoEditorStroke(client);
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
        context.fill(0, 0, LEFT_WIDTH, height, 0xC8101015);
        drawToolButton(context, 10, 20, PaintOverlayClient.EditorTool.BRUSH, new ItemStack(PaintItems.PAINT_BRUSH), mouseX, mouseY);
        drawToolButton(context, 10, 62, PaintOverlayClient.EditorTool.ERASER, new ItemStack(PaintItems.ERASER), mouseX, mouseY);
        drawToolButton(context, 10, 104, PaintOverlayClient.EditorTool.PAPER, new ItemStack(PaintItems.PAINT_PAPER), mouseX, mouseY);
        drawToolButton(context, 10, 146, PaintOverlayClient.EditorTool.NONE, ItemStack.EMPTY, mouseX, mouseY);
    }

    private void drawToolButton(DrawContext context, int x, int y, PaintOverlayClient.EditorTool tool, ItemStack stack, int mouseX, int mouseY) {
        boolean selected = selectedTool == tool;
        boolean hover = isInside(x, y, 34, 34, mouseX, mouseY);
        context.fill(x, y, x + 34, y + 34, selected ? 0xFF2E6A84 : (hover ? 0xAA303038 : 0xAA1B1B22));
        drawBorder(context, x, y, 34, 34);
        if (stack.isEmpty()) {
            context.fill(x + 11, y + 16, x + 23, y + 18, 0xFFE6E6E6);
        } else {
            context.drawItem(stack, x + 9, y + 9);
        }
    }

    private void drawRightPanel(DrawContext context, int mouseX, int mouseY) {
        int x = rightX();
        context.fill(x, 0, width, height, 0xC8101015);
        context.drawText(textRenderer, Text.literal("Paint"), x + 16, 12, 0xFFFFFFFF, false);
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
        drawColorMap(context, x, 28);
        drawHueBar(context, x + MAP_SIZE + 8, 28);
        drawColorCursor(context, x, 28);

        int color = PaintOverlayClient.selectedColor();
        drawSwatch(context, x, 102, 28, color, true);
        drawStar(context, x, 102, 28, PaintOverlayClient.isFavoriteColor(color));
        context.drawText(textRenderer, Text.literal(toHex(color)), x + 52, 112, 0xFFE6E6E6, false);

        context.drawText(textRenderer, Text.literal("HEX"), x, 128, 0xFFB8B8C2, false);
        drawRecent(context, x, 166);
        drawFavorites(context, x, 204);
        drawRadiusControls(context, x, 292);
    }

    private void drawEraserPanel(DrawContext context, int x) {
        context.drawText(textRenderer, Text.literal("Eraser"), x, 36, 0xFFFFFFFF, false);
        drawRadiusControls(context, x, 64);
        int modeY = 96;
        boolean faceMode = PaintOverlayClient.eraserFaceMode();
        drawToggle(context, x, modeY, 52, 18, "Pixel", !faceMode);
        drawToggle(context, x + 58, modeY, 52, 18, "Face", faceMode);
    }

    private void drawPaperPanel(DrawContext context, int x) {
        context.drawText(textRenderer, Text.literal("Paper"), x, 36, 0xFFFFFFFF, false);
        int paperY = 64;
        drawSmallButton(context, x, paperY, 22, 18, "-");
        context.drawText(textRenderer, Text.literal("P " + PaintOverlayClient.selectedPaperSize()), x + 30, paperY + 5, 0xFFFFFFFF, false);
        drawSmallButton(context, x + 66, paperY, 22, 18, "+");
        drawSmallButton(context, x, paperY + 34, 88, 18, "Import");
    }

    private void drawViewPanel(DrawContext context, int x) {
        context.drawText(textRenderer, Text.literal("View"), x, 36, 0xFFFFFFFF, false);
    }

    private void drawColorMap(DrawContext context, int x, int y) {
        context.drawTexture(RenderPipelines.GUI_TEXTURED, COLOR_MAP_TEXTURE_ID, x, y, 0.0F, 0.0F, MAP_SIZE, MAP_SIZE, MAP_SIZE, MAP_SIZE);
        drawBorder(context, x, y, MAP_SIZE, MAP_SIZE);
    }

    private void drawHueBar(DrawContext context, int x, int y) {
        for (int row = 0; row < MAP_SIZE; row++) {
            int color = 0xFF000000 | hsvToRgb((float) row / (MAP_SIZE - 1), 1.0F, 1.0F);
            context.fill(x, y + row, x + HUE_WIDTH, y + row + 1, color);
        }
        drawBorder(context, x, y, HUE_WIDTH, MAP_SIZE);
    }

    private void drawColorCursor(DrawContext context, int x, int y) {
        int sx = x + Math.round(saturation * (MAP_SIZE - 1));
        int sy = y + Math.round((1.0F - value) * (MAP_SIZE - 1));
        context.fill(sx - 4, sy, sx + 5, sy + 1, 0xFFFFFFFF);
        context.fill(sx, sy - 4, sx + 1, sy + 5, 0xFFFFFFFF);

        int hueX = x + MAP_SIZE + 8;
        int hy = y + Math.round(hue * (MAP_SIZE - 1));
        context.fill(hueX - 2, hy - 1, hueX + HUE_WIDTH + 2, hy + 2, 0xFFFFFFFF);
    }

    private void drawRecent(DrawContext context, int x, int y) {
        context.drawText(textRenderer, Text.literal("Recent"), x, y - 12, 0xFFFFFFFF, false);
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
        context.drawText(textRenderer, Text.literal("Fav"), x, y - 12, 0xFFFFFFFF, false);
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

    private void drawRadiusControls(DrawContext context, int x, int y) {
        int radius = PaintOverlayClient.selectedRadius();
        drawSmallButton(context, x, y, 22, 18, "-");
        context.drawText(textRenderer, Text.literal("R " + radius), x + 30, y + 5, 0xFFFFFFFF, false);
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
        if (isInside(10, 20, 34, 34, mouseX, mouseY)) return PaintOverlayClient.EditorTool.BRUSH;
        if (isInside(10, 62, 34, 34, mouseX, mouseY)) return PaintOverlayClient.EditorTool.ERASER;
        if (isInside(10, 104, 34, 34, mouseX, mouseY)) return PaintOverlayClient.EditorTool.PAPER;
        if (isInside(10, 146, 34, 34, mouseX, mouseY)) return PaintOverlayClient.EditorTool.NONE;
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
                if (handleSwatches(mouseX, mouseY, x)) {
                    return true;
                }
                return handleRadiusClick(mouseX, mouseY, x, 292) || true;
            }
            case ERASER -> {
                if (handleRadiusClick(mouseX, mouseY, x, 64)) {
                    return true;
                }
                if (isInside(x, 96, 52, 18, mouseX, mouseY)) {
                    PaintOverlayClient.setEraserFaceMode(false);
                    return true;
                }
                if (isInside(x + 58, 96, 52, 18, mouseX, mouseY)) {
                    PaintOverlayClient.setEraserFaceMode(true);
                    return true;
                }
                return true;
            }
            case PAPER -> {
                if (isInside(x, 64, 22, 18, mouseX, mouseY)) {
                    PaintOverlayClient.setSelectedPaperSize(PaintOverlayClient.selectedPaperSize() - 1);
                    return true;
                }
                if (isInside(x + 66, 64, 22, 18, mouseX, mouseY)) {
                    PaintOverlayClient.setSelectedPaperSize(PaintOverlayClient.selectedPaperSize() + 1);
                    return true;
                }
                if (isInside(x, 98, 88, 18, mouseX, mouseY)) {
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

    private boolean handleSwatches(double mouseX, double mouseY, int x) {
        int color = PaintOverlayClient.selectedColor();
        if (isInsideStar(x, 102, 28, mouseX, mouseY)) {
            PaintOverlayClient.toggleFavoriteColor(color);
            return true;
        }
        List<Integer> recent = PaintOverlayClient.recentColors();
        for (int i = 0; i < recent.size(); i++) {
            int slotX = x + i * 24;
            if (isInsideStar(slotX, 166, SWATCH, mouseX, mouseY)) {
                PaintOverlayClient.toggleFavoriteColor(recent.get(i));
                return true;
            }
            if (isInside(slotX, 166, SWATCH, SWATCH, mouseX, mouseY)) {
                selectColor(recent.get(i), false);
                return true;
            }
        }
        List<Integer> favorites = PaintOverlayClient.favoriteColors();
        for (int i = 0; i < Math.min(9, favorites.size()); i++) {
            int slotX = x + (i % 3) * 24;
            int slotY = 204 + (i / 3) * 24;
            if (isInsideStar(slotX, slotY, SWATCH, mouseX, mouseY)) {
                PaintOverlayClient.toggleFavoriteColor(favorites.get(i));
                return true;
            }
            if (isInside(slotX, slotY, SWATCH, SWATCH, mouseX, mouseY)) {
                selectColor(favorites.get(i), false);
                return true;
            }
        }
        return false;
    }

    private boolean pickColor(double mouseX, double mouseY, boolean dragging) {
        int x = rightX() + 16;
        int y = 28;
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
        int rgb = color & 0xFFFFFF;
        loadColor(rgb);
        PaintOverlayClient.setSelectedColor(0xFF000000 | rgb);
        updateHexField();
        uploadedHue = -1.0F;
        if (record) {
            PaintOverlayClient.recordPickedColor(color);
        }
    }

    private void syncSelectedColor() {
        PaintOverlayClient.setSelectedColor(0xFF000000 | hsvToRgb(hue, saturation, value));
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
        context.fill(x, y, x + size, y + size, filled ? color : 0x55202028);
        drawBorder(context, x, y, size, size);
    }

    private void drawBorder(DrawContext context, int x, int y, int w, int h) {
        context.fill(x, y, x + w, y + 1, 0x99FFFFFF);
        context.fill(x, y + h - 1, x + w, y + h, 0x99000000);
        context.fill(x, y, x + 1, y + h, 0x99FFFFFF);
        context.fill(x + w - 1, y, x + w, y + h, 0x99000000);
    }

    private void drawStar(DrawContext context, int x, int y, int size, boolean favorite) {
        int sx = x + size - 8;
        int sy = y + 1;
        context.drawText(textRenderer, Text.literal("*"), sx, sy, favorite ? 0xFFFFD75E : 0xFFFFFFFF, true);
    }

    private void drawSmallButton(DrawContext context, int x, int y, int w, int h, String text) {
        context.fill(x, y, x + w, y + h, 0xAA303038);
        drawBorder(context, x, y, w, h);
        context.drawText(textRenderer, Text.literal(text), x + 6, y + 5, 0xFFFFFFFF, false);
    }

    private void drawToggle(DrawContext context, int x, int y, int w, int h, String text, boolean active) {
        context.fill(x, y, x + w, y + h, active ? 0xFF2E6A84 : 0xAA303038);
        drawBorder(context, x, y, w, h);
        context.drawText(textRenderer, Text.literal(text), x + 6, y + 5, 0xFFFFFFFF, false);
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
