package com.kuilunfuzhe.monvhua.features.paint.drawingboard.client;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.features.paint.PaintOverlayClient;
import com.kuilunfuzhe.monvhua.features.paint.drawingboard.DrawingBoardBlockEntity;
import com.kuilunfuzhe.monvhua.features.textarea.TextGroupRenderer;
import com.kuilunfuzhe.monvhua.gui.layout.DraggableResizableLayout;
import com.kuilunfuzhe.monvhua.item.paint.PaintBrushItem;
import com.kuilunfuzhe.monvhua.network.SafeClientNetworking;
import com.kuilunfuzhe.monvhua.network.drawingboard.DrawingBoardPackets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.joml.Matrix3x2fStack;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public class DrawingBoardScreen extends Screen {
    private static final Identifier BACKGROUND = Identifier.of(MonvhuaMod.MOD_ID, "textures/gui/draw_background.png");
    private static final Identifier CANVAS_TEXTURE = Identifier.of(MonvhuaMod.MOD_ID, "dynamic/drawing_board_screen");
    private static final Identifier COLOR_MAP_TEXTURE = Identifier.of(MonvhuaMod.MOD_ID, "dynamic/drawing_board_color_map");
    private static final Identifier IMPORT_TEXTURE = Identifier.of(MonvhuaMod.MOD_ID, "dynamic/drawing_board_import");
    private static final int BACKGROUND_W = 463;
    private static final int BACKGROUND_H = 454;
    private static final int DEFAULT_CANVAS_W = DrawingBoardBlockEntity.WIDTH;
    private static final int DEFAULT_CANVAS_H = DrawingBoardBlockEntity.HEIGHT;
    private static final int PANEL_PAD = 10;
    private static final int CONTROL_W = 64;
    private static final int GAP = 10;
    private static final int PALETTE_W = 72;
    private static final int COLOR_MAP_SIZE = 56;
    private static final int HUE_BAR_W = 8;
    private static final int ALPHA_SLIDER_H = 10;
    private static final int HISTORY_W = 152;
    private static final int HISTORY_ROW_H = 17;
    private static final int HISTORY_MENU_W = 86;
    private static final int HISTORY_MENU_H = 24;
    private static final int MAX_HISTORY = 80;
    private static final int SCROLL_PAN_STEP = 24;

    private final BlockPos pos;
    private int canvasW = DEFAULT_CANVAS_W;
    private int canvasH = DEFAULT_CANVAS_H;
    private int[] pixels = whitePixels(DEFAULT_CANVAS_W, DEFAULT_CANVAS_H);
    private NativeImageBackedTexture canvasTexture;
    private NativeImage canvasImage;
    private int canvasTextureW = -1;
    private int canvasTextureH = -1;
    private NativeImageBackedTexture colorMapTexture;
    private NativeImageBackedTexture importTexture;
    private NativeImage importImage;
    private int[] importPixels;
    private boolean canvasDirty = true;
    private boolean canvasFullyDirty = true;
    private final IntList canvasDirtyIndices = new IntList();
    private float hue = 0.0F;
    private float saturation = 0.0F;
    private float value = 0.0F;
    private int alpha = 255;
    private float uploadedHue = -1.0F;
    private boolean pickingColor;
    private int canvasX;
    private int canvasY;
    private int drawW;
    private int drawH;
    private int drawScale = 1;
    private int paperX;
    private int paperY;
    private int paperW;
    private int paperH;
    private boolean paperTransformInitialized;
    private boolean paperTransformCustomized;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int baseCanvasX;
    private int baseCanvasY;
    private int baseDrawW;
    private int baseDrawH;
    private DraggableResizableLayout layout;
    private DraggableResizableLayout.Bounds canvasBounds = DraggableResizableLayout.Bounds.EMPTY;
    private boolean drawing;
    private int drawButton = -1;
    private int brushRadius = 1;
    private int lastX = -1;
    private int lastY = -1;
    private boolean middleDraggingCanvas;
    private double middleDragLastX;
    private double middleDragLastY;
    private IntList strokeIndices;
    private IntList strokeBeforeColors;
    private IntList strokeAfterColors;
    private final List<HistoryStep> history = new ArrayList<>();
    private int historyCursor;
    private int historySequence = 1;
    private int historyMenuIndex = -1;
    private int historyMenuX;
    private int historyMenuY;
    private TextFieldWidget widthField;
    private double importScale = 1.0D;
    private int importRotation;
    private int importCanvasX;
    private int importCanvasY;
    private boolean draggingImport;

    public DrawingBoardScreen(BlockPos pos) {
        super(Text.literal("Drawing Board"));
        this.pos = pos.toImmutable();
        loadColor(PaintOverlayClient.selectedColor() & 0xFFFFFF);
        alpha = PaintOverlayClient.selectedAlpha();
    }

    public static void receiveSync(BlockPos pos, int width, int height, int[] pixels) {
        if (net.minecraft.client.MinecraftClient.getInstance().currentScreen instanceof DrawingBoardScreen screen
                && screen.pos.equals(pos)) {
            screen.setPixels(width, height, pixels);
        }
    }

    public static void receivePatch(BlockPos pos, int width, int height, int[] indices, int[] colors) {
        if (net.minecraft.client.MinecraftClient.getInstance().currentScreen instanceof DrawingBoardScreen screen
                && screen.pos.equals(pos)) {
            screen.applyRemotePatch(width, height, indices, colors);
        }
    }

    @Override
    protected void init() {
        panelW = Math.max(260, Math.min(Math.max(260, width - 24), Math.max(560, (int) (width * 0.72F))));
        panelH = Math.max(250, height / 2);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        rebuildCanvasLayout();
        initWidthField();
        uploadCanvasIfNeeded();
        SafeClientNetworking.send(new DrawingBoardPackets.RequestSyncC2S(pos));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x99000000);
        uploadCanvasIfNeeded();
        uploadColorMapIfNeeded();
        context.drawTexture(RenderPipelines.GUI_TEXTURED, BACKGROUND, panelX, panelY, 0.0F, 0.0F,
                panelW, panelH, BACKGROUND_W, BACKGROUND_H, BACKGROUND_W, BACKGROUND_H);
        context.fill(canvasX - 2, canvasY - 2, canvasX + drawW + 2, canvasY + drawH + 2, 0xFF777777);
        drawCanvas(context);
        if (layout != null) {
            layout.drawEditHandles(context);
        }
        drawControls(context, mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
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
        applyWidthIfFocusLeaving(mouseX, mouseY);
        if (handleHistoryContextMenuClick(mouseX, mouseY, button)) {
            return true;
        }
        if (historyMenuIndex >= 0) {
            closeHistoryContextMenu();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && handleHistoryRightClick(mouseX, mouseY)) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && handleControlClick(mouseX, mouseY)) {
            return true;
        }
        if (importImage != null && insidePaper(mouseX, mouseY)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                draggingImport = true;
                moveImportTo(mouseX, mouseY);
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                placeImport();
                return true;
            }
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && insideCanvas(mouseX, mouseY)) {
            middleDraggingCanvas = true;
            middleDragLastX = mouseX;
            middleDragLastY = mouseY;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && layout != null && layout.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if ((button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
                && insidePaper(mouseX, mouseY)) {
            drawing = true;
            drawButton = button;
            beginDeltaHistory();
            lastX = toCanvasX(mouseX);
            lastY = toCanvasY(mouseY);
            paintAt(lastX, lastY, button == GLFW.GLFW_MOUSE_BUTTON_RIGHT, true);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggingImport) {
            moveImportTo(mouseX, mouseY);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && pickingColor) {
            pickColor(mouseX, mouseY);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && middleDraggingCanvas) {
            movePaperBy((int) Math.round(mouseX - middleDragLastX), (int) Math.round(mouseY - middleDragLastY));
            middleDragLastX = mouseX;
            middleDragLastY = mouseY;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && layout != null && layout.mouseDragged(mouseX, mouseY, button)) {
            canvasBounds = layout.bounds("canvas");
            updateCanvasFromBounds();
            return true;
        }
        if (drawing && button == drawButton) {
            if (!insidePaper(mouseX, mouseY)) {
                lastX = -1;
                lastY = -1;
                return true;
            }
            int x = toCanvasX(mouseX);
            int y = toCanvasY(mouseY);
            if (lastX < 0 || lastY < 0) {
                paintAt(x, y, button == GLFW.GLFW_MOUSE_BUTTON_RIGHT, true);
                lastX = x;
                lastY = y;
                return true;
            }
            interpolatePaint(lastX, lastY, x, y, button == GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            lastX = x;
            lastY = y;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggingImport) {
            draggingImport = false;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && pickingColor) {
            pickingColor = false;
            PaintOverlayClient.recordPickedColor(PaintOverlayClient.selectedColor());
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && middleDraggingCanvas) {
            middleDraggingCanvas = false;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && layout != null && layout.isEditing()) {
            DraggableResizableLayout.DragResult result = layout.mouseReleased(mouseX, mouseY, button);
            canvasBounds = layout.bounds("canvas");
            updateCanvasFromBounds();
            return result.moved();
        }
        if (button == drawButton) {
            drawing = false;
            drawButton = -1;
            lastX = -1;
            lastY = -1;
            finishStrokeHistory(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? "擦除" : "绘制");
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (insideCanvas(mouseX, mouseY)) {
            if (isCtrlKeyDown()) {
                if (importImage != null) {
                    importScale = MathHelper.clamp(importScale * Math.pow(1.12D, verticalAmount), 0.05D, 16.0D);
                } else {
                    zoomPaperAt(mouseX, mouseY, verticalAmount);
                }
                return true;
            }
            if (importImage == null) {
                int offset = scrollPanOffset(verticalAmount);
                if (isAltKeyDown()) {
                    movePaperBy(0, offset);
                } else {
                    movePaperBy(offset, 0);
                }
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (widthField != null && widthField.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                applyWidthField();
                widthField.setFocused(false);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && keyCode == GLFW.GLFW_KEY_Z) {
            undoHistory();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_Y) {
            redoHistory();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_R && importImage != null) {
            importRotation = (importRotation + 1) & 3;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (importImage != null) {
                clearImport();
                return true;
            }
            close();
            return true;
        }
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            PaintOverlayClient.selectBrushSlot(keyCode - GLFW.GLFW_KEY_1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void drawCanvas(DrawContext context) {
        context.fill(canvasX, canvasY, canvasX + drawW, canvasY + drawH, 0xFF4B4F55);
        context.enableScissor(canvasX, canvasY, canvasX + drawW, canvasY + drawH);
        try {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, CANVAS_TEXTURE, paperX, paperY, 0.0F, 0.0F,
                    paperW, paperH, canvasW, canvasH, canvasW, canvasH);
            drawImportPreview(context);
        } finally {
            context.disableScissor();
        }
    }

    private void drawImportPreview(DrawContext context) {
        if (importImage == null || importTexture == null) {
            return;
        }
        int[] size = importCanvasSize();
        int previewW = Math.max(1, Math.round(size[0] * paperW / (float) Math.max(1, canvasW)));
        int previewH = Math.max(1, Math.round(size[1] * paperH / (float) Math.max(1, canvasH)));
        int previewX = paperX + Math.round(importCanvasX * paperW / (float) Math.max(1, canvasW));
        int previewY = paperY + Math.round(importCanvasY * paperH / (float) Math.max(1, canvasH));
        int flash = ((int) (System.currentTimeMillis() / 240L) & 1) == 0 ? 0x66FFFFFF : 0x22FFFFFF;
        context.fill(previewX - 2, previewY - 2, previewX + previewW + 2, previewY + previewH + 2, flash);
        withImportRotation(context, previewX, previewY, previewW, previewH, () ->
                context.drawTexture(RenderPipelines.GUI_TEXTURED, IMPORT_TEXTURE, previewX, previewY, 0.0F, 0.0F,
                        previewW, previewH, importImage.getWidth(), importImage.getHeight(), importImage.getWidth(), importImage.getHeight()));
        context.drawBorder(previewX - 2, previewY - 2, previewW + 4, previewH + 4, 0xCCFFFFFF);
    }

    private void withImportRotation(DrawContext context, int x, int y, int w, int h, Runnable drawAction) {
        int rotation = importRotation & 3;
        if (rotation == 0) {
            drawAction.run();
            return;
        }
        Matrix3x2fStack matrices = context.getMatrices();
        float centerX = x + w * 0.5F;
        float centerY = y + h * 0.5F;
        matrices.pushMatrix();
        try {
            matrices.translate(centerX, centerY);
            matrices.rotate((float) Math.toRadians(rotation * 90.0F));
            matrices.translate(-centerX, -centerY);
            drawAction.run();
        } finally {
            matrices.popMatrix();
        }
    }

    private void drawControls(DrawContext context, int mouseX, int mouseY) {
        int x = panelX + panelW - PANEL_PAD - CONTROL_W;
        int y = controlsY();
        drawPalette(context, panelX + PANEL_PAD, y, mouseX, mouseY);
        drawButton(context, x, y, CONTROL_W, 18, "Clear", isInside(x, y, CONTROL_W, 18, mouseX, mouseY));
        drawButton(context, x, y + 24, CONTROL_W, 18, "Paper", isInside(x, y + 24, CONTROL_W, 18, mouseX, mouseY));
        drawButton(context, x, y + 48, CONTROL_W, 18, "Reset", isInside(x, y + 48, CONTROL_W, 18, mouseX, mouseY));
        drawButton(context, x, y + 72, CONTROL_W, 18, "Import", isInside(x, y + 72, CONTROL_W, 18, mouseX, mouseY));
        drawButton(context, x, y + 96, CONTROL_W, 18, "Fit", isInside(x, y + 96, CONTROL_W, 18, mouseX, mouseY));
        context.drawText(textRenderer, Text.literal("Brush"), x, y + 124, 0xFFFFFFFF, false);
        drawButton(context, x, y + 144, 18, 16, "-", isInside(x, y + 144, 18, 16, mouseX, mouseY));
        context.drawText(textRenderer, Text.literal(String.valueOf(brushRadius)), x + 27, y + 149, 0xFFFFFFFF, false);
        drawButton(context, x + 46, y + 144, 18, 16, "+", isInside(x + 46, y + 144, 18, 16, mouseX, mouseY));
        context.drawText(textRenderer, Text.literal("L draw"), x, y + 178, 0xFFE8E8E8, false);
        context.drawText(textRenderer, Text.literal("R erase"), x, y + 190, 0xFFE8E8E8, false);
        drawWidthControl(context, x, y);
        drawHistoryPanel(context, historyX(), y, mouseX, mouseY);
    }

    private void drawWidthControl(DrawContext context, int x, int y) {
        int fieldY = y + Math.max(162, panelH - PANEL_PAD - 20 - (y - panelY));
        context.drawText(textRenderer, Text.literal("Width"), x, fieldY - 11, 0xFFE8E8E8, false);
        if (widthField != null) {
            widthField.setPosition(x, fieldY);
            widthField.setWidth(CONTROL_W);
        }
    }

    private void drawButton(DrawContext context, int x, int y, int w, int h, String text, boolean hover) {
        context.fill(x, y, x + w, y + h, hover ? 0xFF3E5268 : 0xFF28313C);
        context.fill(x, y, x + w, y + 1, 0xFF8FA6BE);
        context.fill(x, y + h - 1, x + w, y + h, 0xFF111820);
        context.fill(x, y, x + 1, y + h, 0xFF8FA6BE);
        context.fill(x + w - 1, y, x + w, y + h, 0xFF111820);
        context.drawText(textRenderer, Text.literal(text), x + Math.max(4, (w - textRenderer.getWidth(text)) / 2), y + 6, 0xFFFFFFFF, false);
    }

    private boolean handleControlClick(double mouseX, double mouseY) {
        if (handlePaletteClick(mouseX, mouseY)) {
            return true;
        }
        int x = panelX + panelW - PANEL_PAD - CONTROL_W;
        int y = controlsY();
        if (isInside(x, y, CONTROL_W, 18, mouseX, mouseY)) {
            int[] before = copyPixels();
            pixels = whitePixels(canvasW, canvasH);
            markCanvasFullyDirty();
            pushFullHistory("清空", before, copyPixels());
            SafeClientNetworking.send(new DrawingBoardPackets.StrokeC2S(pos, 0, 0, brushRadius, 0xFFFFFFFF, true));
            return true;
        }
        if (isInside(x, y + 24, CONTROL_W, 18, mouseX, mouseY)) {
            SafeClientNetworking.send(new DrawingBoardPackets.SaveToPaperC2S(pos));
            return true;
        }
        if (isInside(x, y + 48, CONTROL_W, 18, mouseX, mouseY)) {
            resetCanvasLayout();
            return true;
        }
        if (isInside(x, y + 72, CONTROL_W, 18, mouseX, mouseY)) {
            startImport();
            return true;
        }
        if (isInside(x, y + 96, CONTROL_W, 18, mouseX, mouseY)) {
            resetPaperToCanvas();
            return true;
        }
        if (isInside(x, y + 144, 18, 16, mouseX, mouseY)) {
            brushRadius = Math.max(1, brushRadius - 1);
            return true;
        }
        if (isInside(x + 46, y + 144, 18, 16, mouseX, mouseY)) {
            brushRadius = Math.min(12, brushRadius + 1);
            return true;
        }
        return false;
    }

    private void drawPalette(DrawContext context, int x, int y, int mouseX, int mouseY) {
        int mapX = colorMapX();
        int mapY = colorMapY();
        int hueX = hueBarX();
        int alphaY = alphaSliderY();
        int currentColor = currentPaintColor();
        context.drawText(textRenderer, Text.literal("Color"), x, y - 12, 0xFFFFFFFF, false);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, COLOR_MAP_TEXTURE, mapX, mapY, 0.0F, 0.0F,
                COLOR_MAP_SIZE, COLOR_MAP_SIZE, COLOR_MAP_SIZE, COLOR_MAP_SIZE);
        drawPanelBorder(context, mapX, mapY, COLOR_MAP_SIZE, COLOR_MAP_SIZE);
        for (int yy = 0; yy < COLOR_MAP_SIZE; yy++) {
            int color = 0xFF000000 | hsvToRgb((float) yy / Math.max(1, COLOR_MAP_SIZE - 1), 1.0F, 1.0F);
            context.fill(hueX, mapY + yy, hueX + HUE_BAR_W, mapY + yy + 1, color);
        }
        drawPanelBorder(context, hueX, mapY, HUE_BAR_W, COLOR_MAP_SIZE);
        drawAlphaSlider(context, x, alphaY);
        context.fill(x, alphaY + 18, x + 18, alphaY + 36, currentColor);
        drawPanelBorder(context, x, alphaY + 18, 18, 18);
        context.drawText(textRenderer, Text.literal(String.format(java.util.Locale.ROOT, "#%06X", currentColor & 0xFFFFFF)),
                x + 22, alphaY + 24, 0xFFE6E6E6, false);
        drawColorCursors(context);
    }

    private static String formatPaintPercent(double remaining) {
        double percent = Math.clamp(remaining * 100.0D / PaintBrushItem.MAX_PAINT_PIXELS, 0.0D, 100.0D);
        if (Math.abs(percent - Math.rint(percent)) < 0.05D) {
            return String.format(java.util.Locale.ROOT, "%.0f%%", percent);
        }
        return String.format(java.util.Locale.ROOT, "%.1f%%", percent);
    }

    private void drawAlphaSlider(DrawContext context, int x, int y) {
        int w = PALETTE_W;
        int rgb = hsvToRgb(hue, saturation, value);
        for (int xx = 0; xx < w; xx++) {
            int a = MathHelper.clamp(Math.round(xx * 255.0F / Math.max(1, w - 1)), 0, 255);
            context.fill(x + xx, y, x + xx + 1, y + ALPHA_SLIDER_H, (a << 24) | rgb);
        }
        drawPanelBorder(context, x, y, w, ALPHA_SLIDER_H);
        int markerX = x + Math.round(alpha * (w - 1) / 255.0F);
        context.fill(markerX - 1, y - 2, markerX + 2, y + ALPHA_SLIDER_H + 2, 0xFFFFFFFF);
        context.fill(markerX, y - 1, markerX + 1, y + ALPHA_SLIDER_H + 1, 0xFF000000);
    }

    private void drawColorCursors(DrawContext context) {
        int sx = colorMapX() + Math.round(saturation * (COLOR_MAP_SIZE - 1));
        int sy = colorMapY() + Math.round((1.0F - value) * (COLOR_MAP_SIZE - 1));
        context.fill(sx - 4, sy - 1, sx + 5, sy + 1, 0xFFFFFFFF);
        context.fill(sx - 1, sy - 4, sx + 1, sy + 5, 0xFFFFFFFF);
        context.fill(sx - 3, sy, sx + 4, sy + 1, 0xFF000000);
        context.fill(sx, sy - 3, sx + 1, sy + 4, 0xFF000000);

        int hy = colorMapY() + Math.round(hue * (COLOR_MAP_SIZE - 1));
        context.fill(hueBarX() - 2, hy - 2, hueBarX() + HUE_BAR_W + 2, hy + 2, 0xFFFFFFFF);
        context.fill(hueBarX() - 1, hy - 1, hueBarX() + HUE_BAR_W + 1, hy + 1, 0xFF000000);
    }

    private void drawPanelBorder(DrawContext context, int x, int y, int w, int h) {
        context.fill(x - 1, y - 1, x + w + 1, y, 0xFF8FA6BE);
        context.fill(x - 1, y + h, x + w + 1, y + h + 1, 0xFF111820);
        context.fill(x - 1, y - 1, x, y + h + 1, 0xFF8FA6BE);
        context.fill(x + w, y - 1, x + w + 1, y + h + 1, 0xFF111820);
    }

    private boolean handlePaletteClick(double mouseX, double mouseY) {
        if (pickColor(mouseX, mouseY)) {
            pickingColor = true;
            return true;
        }
        return false;
    }

    private boolean pickColor(double mouseX, double mouseY) {
        int mapX = colorMapX();
        int mapY = colorMapY();
        if (isInside(mapX, mapY, COLOR_MAP_SIZE, COLOR_MAP_SIZE, mouseX, mouseY)) {
            saturation = MathHelper.clamp((float) ((mouseX - mapX) / Math.max(1, COLOR_MAP_SIZE - 1)), 0.0F, 1.0F);
            value = MathHelper.clamp(1.0F - (float) ((mouseY - mapY) / Math.max(1, COLOR_MAP_SIZE - 1)), 0.0F, 1.0F);
            syncPaintColor();
            return true;
        }
        int hueX = hueBarX();
        if (isInside(hueX, mapY, HUE_BAR_W, COLOR_MAP_SIZE, mouseX, mouseY)) {
            hue = MathHelper.clamp((float) ((mouseY - mapY) / Math.max(1, COLOR_MAP_SIZE - 1)), 0.0F, 1.0F);
            uploadedHue = -1.0F;
            syncPaintColor();
            return true;
        }
        int alphaY = alphaSliderY();
        int alphaX = panelX + PANEL_PAD;
        if (isInside(alphaX, alphaY, PALETTE_W, ALPHA_SLIDER_H, mouseX, mouseY)) {
            alpha = MathHelper.clamp((int) Math.round((mouseX - alphaX) * 255.0D / Math.max(1, PALETTE_W - 1)), 0, 255);
            syncPaintColor();
            return true;
        }
        return false;
    }

    private void syncPaintColor() {
        PaintOverlayClient.setSelectedColor(currentPaintColor());
    }

    private int currentPaintColor() {
        return (alpha << 24) | hsvToRgb(hue, saturation, value);
    }

    private int colorMapX() {
        return panelX + PANEL_PAD;
    }

    private int colorMapY() {
        return controlsY();
    }

    private int hueBarX() {
        return colorMapX() + COLOR_MAP_SIZE + 5;
    }

    private int alphaSliderY() {
        return colorMapY() + COLOR_MAP_SIZE + 12;
    }

    private void drawHistoryPanel(DrawContext context, int x, int y, double mouseX, double mouseY) {
        int h = Math.max(72, Math.min(panelY + panelH - y - PANEL_PAD, baseDrawH));
        context.fill(x, y, x + HISTORY_W, y + h, 0xAA151A22);
        context.drawBorder(x, y, HISTORY_W, h, 0xAA8FA6BE);
        context.drawText(textRenderer, Text.literal("历史记录"), x + 6, y + 7, 0xFFFFFFFF, false);
        int listY = y + 24;
        int maxRows = Math.max(1, (h - 30) / HISTORY_ROW_H);
        if (history.isEmpty()) {
            context.drawText(textRenderer, Text.literal("暂无记录"), x + 6, listY + 4, 0xFFB8C2CC, false);
            return;
        }
        int currentIndex = historyCursor - 1;
        int start = historyStartIndex(history.size(), currentIndex, maxRows);
        int end = Math.min(history.size(), start + maxRows);
        for (int i = start; i < end; i++) {
            HistoryStep step = history.get(i);
            int rowY = listY + (i - start) * HISTORY_ROW_H;
            boolean current = i == currentIndex;
            boolean menuSelected = i == historyMenuIndex;
            boolean hover = isInside(x + 4, rowY, HISTORY_W - 8, HISTORY_ROW_H - 1, mouseX, mouseY);
            int fill = current ? 0xCC1E5D7A : (menuSelected ? 0x993F5F72 : (hover ? 0x66344554 : 0));
            if (fill != 0) {
                context.fill(x + 4, rowY, x + HISTORY_W - 4, rowY + HISTORY_ROW_H - 1, fill);
            }
            if (current) {
                context.fill(x + 6, rowY + 3, x + 8, rowY + HISTORY_ROW_H - 4, 0xFF5FB7FF);
            }
            String label = "#" + step.sequence + " " + step.label;
            context.drawText(textRenderer, Text.literal(trimToWidth(label, HISTORY_W - 34)),
                    x + 12, rowY + 4, current || menuSelected ? 0xFFFFFFFF : 0xFFB8C2CC, false);
        }
    }

    private void drawHistoryContextMenu(DrawContext context, int mouseX, int mouseY) {
        if (historyMenuIndex < 0) {
            return;
        }
        int x = MathHelper.clamp(historyMenuX, 2, Math.max(2, width - HISTORY_MENU_W - 2));
        int y = MathHelper.clamp(historyMenuY, 2, Math.max(2, height - HISTORY_MENU_H - 2));
        boolean hover = isInside(x, y, HISTORY_MENU_W, HISTORY_MENU_H, mouseX, mouseY);
        drawButton(context, x, y, HISTORY_MENU_W, HISTORY_MENU_H, "跳到此步", hover);
    }

    private boolean handleHistoryContextMenuClick(double mouseX, double mouseY, int button) {
        if (historyMenuIndex < 0) {
            return false;
        }
        int x = MathHelper.clamp(historyMenuX, 2, Math.max(2, width - HISTORY_MENU_W - 2));
        int y = MathHelper.clamp(historyMenuY, 2, Math.max(2, height - HISTORY_MENU_H - 2));
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isInside(x, y, HISTORY_MENU_W, HISTORY_MENU_H, mouseX, mouseY)) {
            jumpHistory(historyMenuIndex);
            closeHistoryContextMenu();
            return true;
        }
        if (!isInside(x, y, HISTORY_MENU_W, HISTORY_MENU_H, mouseX, mouseY)) {
            closeHistoryContextMenu();
        }
        return false;
    }

    private boolean handleHistoryRightClick(double mouseX, double mouseY) {
        int index = historyIndexAt(mouseX, mouseY);
        if (index < 0) {
            return false;
        }
        historyMenuIndex = index;
        historyMenuX = MathHelper.clamp((int) mouseX + 8, 2, Math.max(2, width - HISTORY_MENU_W - 2));
        historyMenuY = MathHelper.clamp((int) mouseY - 2, 2, Math.max(2, height - HISTORY_MENU_H - 2));
        return true;
    }

    private void closeHistoryContextMenu() {
        historyMenuIndex = -1;
    }

    private int historyIndexAt(double mouseX, double mouseY) {
        if (history.isEmpty()) {
            return -1;
        }
        int x = historyX();
        int y = controlsY();
        int h = Math.max(72, Math.min(panelY + panelH - y - PANEL_PAD, baseDrawH));
        int listY = y + 24;
        int maxRows = Math.max(1, (h - 30) / HISTORY_ROW_H);
        int start = historyStartIndex(history.size(), historyCursor - 1, maxRows);
        int end = Math.min(history.size(), start + maxRows);
        if (!isInside(x + 4, listY, HISTORY_W - 8, (end - start) * HISTORY_ROW_H, mouseX, mouseY)) {
            return -1;
        }
        int index = start + (int) ((mouseY - listY) / HISTORY_ROW_H);
        return index >= start && index < end ? index : -1;
    }

    private int historyStartIndex(int size, int currentIndex, int maxRows) {
        if (size <= maxRows) {
            return 0;
        }
        int focus = currentIndex >= 0 ? currentIndex : size - 1;
        return MathHelper.clamp(focus - maxRows / 2, 0, size - maxRows);
    }

    private void finishStrokeHistory(String label) {
        if (strokeIndices == null || strokeIndices.isEmpty()) {
            clearDeltaHistory();
            return;
        }
        pushHistory(label, strokeIndices.toArray(), strokeBeforeColors.toArray(), strokeAfterColors.toArray());
        clearDeltaHistory();
    }

    private void pushHistory(String label, int[] indices, int[] before, int[] after) {
        if (historyCursor < history.size()) {
            history.subList(historyCursor, history.size()).clear();
        }
        history.add(new HistoryStep(historySequence++, label, indices, before, after));
        while (history.size() > MAX_HISTORY) {
            history.remove(0);
            historyCursor = Math.max(0, historyCursor - 1);
        }
        historyCursor = history.size();
    }

    private void undoHistory() {
        if (historyCursor <= 0) {
            return;
        }
        HistoryStep step = history.get(historyCursor - 1);
        applyHistoryPatch(step.indices, step.before);
        historyCursor--;
    }

    private void redoHistory() {
        if (historyCursor >= history.size()) {
            return;
        }
        HistoryStep step = history.get(historyCursor);
        applyHistoryPatch(step.indices, step.after);
        historyCursor++;
    }

    private void jumpHistory(int targetIndex) {
        if (targetIndex < 0 || targetIndex >= history.size()) {
            return;
        }
        int targetCursor = targetIndex + 1;
        if (targetCursor == historyCursor) {
            return;
        }
        if (targetCursor < historyCursor) {
            for (int i = historyCursor - 1; i >= targetCursor; i--) {
                HistoryStep step = history.get(i);
                applyHistoryPatch(step.indices, step.before);
            }
        } else {
            for (int i = historyCursor; i < targetCursor; i++) {
                HistoryStep step = history.get(i);
                applyHistoryPatch(step.indices, step.after);
            }
        }
        historyCursor = targetCursor;
    }

    private void applyHistoryPatch(int[] indices, int[] colors) {
        if (applyPatchLocal(canvasW, canvasH, indices, colors, false)) {
            SafeClientNetworking.send(new DrawingBoardPackets.PatchC2S(pos, canvasW, canvasH, indices, colors));
        }
    }

    private void beginDeltaHistory() {
        strokeIndices = new IntList();
        strokeBeforeColors = new IntList();
        strokeAfterColors = new IntList();
    }

    private void clearDeltaHistory() {
        strokeIndices = null;
        strokeBeforeColors = null;
        strokeAfterColors = null;
    }

    private void initWidthField() {
        int x = panelX + panelW - PANEL_PAD - CONTROL_W;
        int y = controlsY() + Math.max(162, panelH - PANEL_PAD - 20 - (controlsY() - panelY));
        widthField = addDrawableChild(new TextFieldWidget(textRenderer, x, y, CONTROL_W, 18, Text.literal("Width")));
        widthField.setMaxLength(4);
        widthField.setTextPredicate(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
        widthField.setText(String.valueOf(canvasW));
    }

    private void applyWidthIfFocusLeaving(double mouseX, double mouseY) {
        if (widthField == null) {
            return;
        }
        boolean focused = widthField.isFocused();
        boolean inside = isInside(widthField.getX(), widthField.getY(), widthField.getWidth(), widthField.getHeight(), mouseX, mouseY);
        if (focused && !inside) {
            applyWidthField();
            widthField.setFocused(false);
        }
    }

    private void applyWidthField() {
        if (widthField == null) {
            return;
        }
        try {
            int nextWidth = DrawingBoardBlockEntity.sanitizeWidth(Integer.parseInt(widthField.getText().trim()));
            widthField.setText(String.valueOf(nextWidth));
            if (nextWidth != canvasW) {
                SafeClientNetworking.send(new DrawingBoardPackets.ResizeC2S(pos, nextWidth));
            }
        } catch (NumberFormatException ignored) {
            widthField.setText(String.valueOf(canvasW));
        }
    }

    private void startImport() {
        Path path = openImageFileDialog();
        if (path == null) {
            return;
        }
        try {
            NativeImage image = TextGroupRenderer.readNativeImage(path);
            clearImport();
            importImage = image;
            importPixels = copyImagePixels(image);
            importTexture = new NativeImageBackedTexture(() -> "monvhua drawing board import", image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(IMPORT_TEXTURE, importTexture);
            importScale = Math.max(0.05D, Math.min(1.0D, drawW / (double) Math.max(1, image.getWidth())));
            importRotation = 0;
            importCanvasX = Math.max(0, canvasW / 2 - importCanvasSize()[0] / 2);
            importCanvasY = Math.max(0, canvasH / 2 - importCanvasSize()[1] / 2);
        } catch (IOException ignored) {
            clearImport();
        }
    }

    private Path openImageFileDialog() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(5);
            filters.put(stack.UTF8("*.png"));
            filters.put(stack.UTF8("*.jpg"));
            filters.put(stack.UTF8("*.jpeg"));
            filters.put(stack.UTF8("*.bmp"));
            filters.put(stack.UTF8("*.gif"));
            filters.flip();
            String selected = TinyFileDialogs.tinyfd_openFileDialog(
                    "Select import image",
                    null,
                    filters,
                    "Image files (*.png; *.jpg; *.jpeg; *.bmp; *.gif)",
                    false
            );
            return selected == null || selected.isBlank() ? null : Path.of(selected);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private int[] copyImagePixels(NativeImage image) {
        int[] result = new int[Math.max(1, image.getWidth() * image.getHeight())];
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                result[y * image.getWidth() + x] = image.getColorArgb(x, y);
            }
        }
        return result;
    }

    private void clearImport() {
        if (importTexture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(IMPORT_TEXTURE);
            importTexture = null;
        }
        importImage = null;
        importPixels = null;
        importScale = 1.0D;
        importRotation = 0;
        importCanvasX = 0;
        importCanvasY = 0;
        draggingImport = false;
    }

    private int[] importCanvasSize() {
        if (importImage == null) {
            return new int[]{1, 1};
        }
        int imageW = importImage.getWidth();
        int imageH = importImage.getHeight();
        int baseW = (importRotation & 1) == 0 ? imageW : imageH;
        int baseH = (importRotation & 1) == 0 ? imageH : imageW;
        double displayScale = canvasW / (double) Math.max(1, drawW);
        int w = Math.max(1, (int) Math.round(baseW * importScale * displayScale));
        int h = Math.max(1, (int) Math.round(baseH * importScale * displayScale));
        return new int[]{w, h};
    }

    private void moveImportTo(double mouseX, double mouseY) {
        if (importImage == null) {
            return;
        }
        importCanvasX = toCanvasX(mouseX);
        importCanvasY = toCanvasY(mouseY);
    }

    private void placeImport() {
        if (importImage == null || importPixels == null) {
            return;
        }
        int[] size = importCanvasSize();
        int nextW = Math.max(canvasW, importCanvasX + size[0]);
        int nextH = Math.max(canvasH, DrawingBoardBlockEntity.heightForWidth(nextW));
        int[] nextPixels = whitePixels(nextW, nextH);
        for (int y = 0; y < canvasH; y++) {
            System.arraycopy(pixels, y * canvasW, nextPixels, y * nextW, Math.min(canvasW, nextW));
        }
        blitImport(nextPixels, nextW, nextH, importCanvasX, importCanvasY, size[0], size[1]);
        int[] before = copyPixels();
        canvasW = nextW;
        canvasH = nextH;
        pixels = nextPixels;
        markCanvasFullyDirty();
        pushFullHistory("瀵煎叆", before, copyPixels());
        SafeClientNetworking.send(new DrawingBoardPackets.ApplyPixelsC2S(pos, canvasW, canvasH, pixels));
        if (widthField != null) {
            widthField.setText(String.valueOf(canvasW));
        }
        rebuildCanvasLayout();
        clearImport();
    }

    private void blitImport(int[] target, int targetW, int targetH, int dstX, int dstY, int dstW, int dstH) {
        int imageW = importImage.getWidth();
        int imageH = importImage.getHeight();
        for (int y = 0; y < dstH; y++) {
            for (int x = 0; x < dstW; x++) {
                int[] src = rotatedSourcePixel(x, y, dstW, dstH, imageW, imageH);
                int color = importPixels[src[1] * imageW + src[0]];
                if ((color >>> 24) == 0) {
                    continue;
                }
                int tx = dstX + x;
                int ty = dstY + y;
                if (tx >= 0 && tx < targetW && ty >= 0 && ty < targetH) {
                    target[ty * targetW + tx] = color;
                }
            }
        }
    }

    private int[] rotatedSourcePixel(int x, int y, int dstW, int dstH, int imageW, int imageH) {
        int sx;
        int sy;
        switch (importRotation & 3) {
            case 1 -> {
                sx = MathHelper.clamp((int) ((double) y * imageW / Math.max(1, dstH)), 0, imageW - 1);
                sy = MathHelper.clamp(imageH - 1 - (int) ((double) x * imageH / Math.max(1, dstW)), 0, imageH - 1);
            }
            case 2 -> {
                sx = MathHelper.clamp(imageW - 1 - (int) ((double) x * imageW / Math.max(1, dstW)), 0, imageW - 1);
                sy = MathHelper.clamp(imageH - 1 - (int) ((double) y * imageH / Math.max(1, dstH)), 0, imageH - 1);
            }
            case 3 -> {
                sx = MathHelper.clamp(imageW - 1 - (int) ((double) y * imageW / Math.max(1, dstH)), 0, imageW - 1);
                sy = MathHelper.clamp((int) ((double) x * imageH / Math.max(1, dstW)), 0, imageH - 1);
            }
            default -> {
                sx = MathHelper.clamp((int) ((double) x * imageW / Math.max(1, dstW)), 0, imageW - 1);
                sy = MathHelper.clamp((int) ((double) y * imageH / Math.max(1, dstH)), 0, imageH - 1);
            }
        }
        return new int[]{sx, sy};
    }

    private void resetCanvasLayout() {
        if (layout == null) {
            return;
        }
        layout.resetToDefaults();
        rebuildCanvasLayout();
    }

    private void rebuildCanvasLayout() {
        int defaultDrawScale = defaultDrawScale();
        int defaultDrawW = DEFAULT_CANVAS_W * defaultDrawScale;
        int defaultDrawH = DEFAULT_CANVAS_H * defaultDrawScale;
        int defaultCanvasX = defaultCanvasX(defaultDrawW);
        int defaultCanvasY = panelY + (panelH - defaultDrawH) / 2;
        baseCanvasX = defaultCanvasX;
        baseCanvasY = defaultCanvasY;
        baseDrawW = defaultDrawW;
        baseDrawH = defaultDrawH;
        layout = new DraggableResizableLayout("drawing_board", width, height);
        canvasBounds = layout.element("canvas", defaultCanvasX, defaultCanvasY, defaultDrawW, defaultDrawH,
                DEFAULT_CANVAS_W, DEFAULT_CANVAS_H, 0.0F, false, true, true, true, aspectRatio());
        updateCanvasFromBounds();
        resetPaperToCanvas();
    }

    private int defaultCanvasX(int defaultDrawW) {
        int left = panelX + PANEL_PAD + PALETTE_W + GAP;
        int availableW = Math.max(defaultDrawW, historyX() - GAP - left);
        return left + Math.max(0, (availableW - defaultDrawW) / 2);
    }

    private void updateCanvasFromBounds() {
        int oldCanvasX = canvasX;
        int oldCanvasY = canvasY;
        int oldDrawW = drawW;
        int oldDrawH = drawH;
        canvasX = canvasBounds.x;
        canvasY = canvasBounds.y;
        drawW = Math.max(DEFAULT_CANVAS_W, canvasBounds.width);
        drawH = Math.max(DEFAULT_CANVAS_H, Math.round(drawW / aspectRatio()));
        drawScale = Math.max(1, Math.min(drawW / DEFAULT_CANVAS_W, drawH / DEFAULT_CANVAS_H));

        if (!paperTransformInitialized) {
            resetPaperToCanvas();
        } else if (!paperTransformCustomized) {
            resetPaperToCanvas();
        } else {
            paperX += canvasX - oldCanvasX;
            paperY += canvasY - oldCanvasY;
            if (oldDrawW > 0 && oldDrawH > 0 && (oldDrawW != drawW || oldDrawH != drawH)) {
                double scale = drawW / (double) oldDrawW;
                paperW = Math.max(DEFAULT_CANVAS_W, (int) Math.round(paperW * scale));
                paperH = Math.max(DEFAULT_CANVAS_H, Math.round(paperW / aspectRatio()));
            }
            constrainPaperToCanvas();
        }
    }

    private void resetPaperToCanvas() {
        paperX = canvasX;
        paperY = canvasY;
        paperW = drawW;
        paperH = drawH;
        paperTransformInitialized = true;
        paperTransformCustomized = false;
    }

    private void movePaperBy(int dx, int dy) {
        if (dx == 0 && dy == 0) {
            return;
        }
        paperTransformCustomized = true;
        paperX += dx;
        paperY += dy;
        constrainPaperToCanvas();
    }

    private int scrollPanOffset(double verticalAmount) {
        if (verticalAmount == 0.0D) {
            return 0;
        }
        return (int) Math.round(verticalAmount * SCROLL_PAN_STEP);
    }

    private void zoomPaperAt(double mouseX, double mouseY, double verticalAmount) {
        if (verticalAmount == 0.0D) {
            return;
        }
        double anchorX = MathHelper.clamp((mouseX - paperX) / Math.max(1.0D, paperW), 0.0D, 1.0D);
        double anchorY = MathHelper.clamp((mouseY - paperY) / Math.max(1.0D, paperH), 0.0D, 1.0D);
        double scale = Math.pow(1.12D, verticalAmount);
        int nextW = Math.max(DEFAULT_CANVAS_W, (int) Math.round(paperW * scale));
        int nextH = Math.max(DEFAULT_CANVAS_H, Math.round(nextW / aspectRatio()));
        int nextX = (int) Math.round(mouseX - anchorX * nextW);
        int nextY = (int) Math.round(mouseY - anchorY * nextH);
        paperTransformCustomized = true;
        paperX = nextX;
        paperY = nextY;
        paperW = nextW;
        paperH = nextH;
        constrainPaperToCanvas();
    }

    private void setCanvasBounds(DraggableResizableLayout.Bounds bounds, boolean save) {
        if (layout == null) {
            return;
        }
        canvasBounds = layout.setBounds("canvas", bounds, save);
        updateCanvasFromBounds();
    }

    private void constrainPaperToCanvas() {
        paperW = Math.max(DEFAULT_CANVAS_W, paperW);
        paperH = Math.max(DEFAULT_CANVAS_H, Math.round(paperW / aspectRatio()));
        if (paperW <= drawW) {
            paperX = canvasX + (drawW - paperW) / 2;
        } else {
            paperX = MathHelper.clamp(paperX, canvasX + drawW - paperW, canvasX);
        }
        if (paperH <= drawH) {
            paperY = canvasY + (drawH - paperH) / 2;
        } else {
            paperY = MathHelper.clamp(paperY, canvasY + drawH - paperH, canvasY);
        }
    }

    private boolean isAltKeyDown() {
        long handle = MinecraftClient.getInstance().getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }

    private boolean isCtrlKeyDown() {
        long handle = MinecraftClient.getInstance().getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private int defaultDrawScale() {
        int availableW = panelW - PANEL_PAD * 2 - PALETTE_W - CONTROL_W - HISTORY_W - GAP * 3;
        int availableH = panelH - PANEL_PAD * 2;
        return Math.max(1, Math.min(availableW / DEFAULT_CANVAS_W, availableH / DEFAULT_CANVAS_H));
    }

    private int historyX() {
        return panelX + panelW - PANEL_PAD - CONTROL_W - GAP - HISTORY_W;
    }

    private int controlsY() {
        return baseCanvasY;
    }

    private int[] copyPixels() {
        return Arrays.copyOf(pixels, pixels.length);
    }

    private net.minecraft.item.ItemStack brushStack() {
        var client = MinecraftClient.getInstance();
        if (client.player == null) {
            return net.minecraft.item.ItemStack.EMPTY;
        }
        if (client.player.getMainHandStack().getItem() instanceof PaintBrushItem) {
            return client.player.getMainHandStack();
        }
        if (client.player.getOffHandStack().getItem() instanceof PaintBrushItem) {
            return client.player.getOffHandStack();
        }
        for (int slot = 0; slot < client.player.getInventory().size(); slot++) {
            var stack = client.player.getInventory().getStack(slot);
            if (stack.getItem() instanceof PaintBrushItem) {
                return stack;
            }
        }
        return net.minecraft.item.ItemStack.EMPTY;
    }

    private void interpolatePaint(int fromX, int fromY, int toX, int toY, boolean erase) {
        int steps = Math.max(1, Math.max(Math.abs(toX - fromX), Math.abs(toY - fromY)));
        for (int i = 1; i <= steps; i++) {
            float t = (float) i / steps;
            paintAt(Math.round(MathHelper.lerp(t, fromX, toX)), Math.round(MathHelper.lerp(t, fromY, toY)), erase, true);
        }
    }

    private void paintAt(int x, int y, boolean erase, boolean send) {
        int color = erase ? 0xFFFFFFFF : currentPaintColor();
        if (!erase && !hasAnyPaint()) {
            return;
        }
        int r2 = brushRadius * brushRadius;
        boolean changed = false;
        for (int dy = -brushRadius + 1; dy <= brushRadius - 1; dy++) {
            for (int dx = -brushRadius + 1; dx <= brushRadius - 1; dx++) {
                if (dx * dx + dy * dy > r2) {
                    continue;
                }
                int px = x + dx;
                int py = y + dy;
                if (px >= 0 && px < canvasW && py >= 0 && py < canvasH) {
                    int index = py * canvasW + px;
                    if (pixels[index] != color) {
                        recordDelta(index, pixels[index], color);
                        pixels[index] = color;
                        markCanvasPixelDirty(index);
                        changed = true;
                    }
                }
            }
        }
        if (send) {
            SafeClientNetworking.send(new DrawingBoardPackets.StrokeC2S(pos, x, y, brushRadius, color, false));
        }
    }

    private void recordDelta(int index, int beforeColor, int afterColor) {
        if (strokeIndices == null) {
            return;
        }
        int existing = strokeIndices.indexOf(index);
        if (existing >= 0) {
            strokeAfterColors.set(existing, afterColor);
            return;
        }
        strokeIndices.add(index);
        strokeBeforeColors.add(beforeColor);
        strokeAfterColors.add(afterColor);
    }

    private boolean hasAnyPaint() {
        var brush = brushStack();
        if (brush.isEmpty()) {
            return false;
        }
        for (int slot = 0; slot < PaintBrushItem.COLOR_SLOTS; slot++) {
            if (PaintBrushItem.hasPaint(brush, slot)) {
                return true;
            }
        }
        return false;
    }

    private boolean insideCanvas(double mouseX, double mouseY) {
        return mouseX >= canvasX && mouseX < canvasX + drawW && mouseY >= canvasY && mouseY < canvasY + drawH;
    }

    private boolean insidePaper(double mouseX, double mouseY) {
        return insideCanvas(mouseX, mouseY)
                && mouseX >= paperX && mouseX < paperX + paperW
                && mouseY >= paperY && mouseY < paperY + paperH;
    }

    private int toCanvasX(double mouseX) {
        return MathHelper.clamp((int) ((mouseX - paperX) * canvasW / Math.max(1, paperW)), 0, canvasW - 1);
    }

    private int toCanvasY(double mouseY) {
        return MathHelper.clamp((int) ((mouseY - paperY) * canvasH / Math.max(1, paperH)), 0, canvasH - 1);
    }

    @Override
    public void removed() {
        if (canvasTexture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(CANVAS_TEXTURE);
            canvasTexture = null;
            canvasTextureW = -1;
            canvasTextureH = -1;
        }
        canvasImage = null;
        if (colorMapTexture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(COLOR_MAP_TEXTURE);
            colorMapTexture = null;
        }
        clearImport();
    }

    private void setPixels(int width, int height, int[] source) {
        int nextW = DrawingBoardBlockEntity.sanitizeWidth(width);
        int nextH = Math.max(1, height);
        boolean resized = canvasW != nextW || canvasH != nextH;
        canvasW = nextW;
        canvasH = nextH;
        pixels = sanitize(source);
        markCanvasFullyDirty();
        if (widthField != null) {
            widthField.setText(String.valueOf(canvasW));
        }
        if (resized) {
            rebuildCanvasLayout();
        }
    }

    private void applyRemotePatch(int width, int height, int[] indices, int[] colors) {
        if (applyPatchLocal(width, height, indices, colors, true) && widthField != null) {
            widthField.setText(String.valueOf(canvasW));
        }
    }

    private boolean applyPatchLocal(int width, int height, int[] indices, int[] colors, boolean allowResize) {
        int nextW = DrawingBoardBlockEntity.sanitizeWidth(width);
        int nextH = Math.max(1, height);
        if (nextW != canvasW || nextH != canvasH) {
            if (!allowResize) {
                return false;
            }
            canvasW = nextW;
            canvasH = nextH;
            pixels = sanitize(pixels);
            markCanvasFullyDirty();
            rebuildCanvasLayout();
        }
        int length = Math.min(indices == null ? 0 : indices.length, colors == null ? 0 : colors.length);
        boolean changed = false;
        for (int i = 0; i < length; i++) {
            int index = indices[i];
            if (index < 0 || index >= pixels.length) {
                continue;
            }
            int color = colors[i];
            if ((color >>> 24) == 0) {
                color |= 0xFF000000;
            }
            if (pixels[index] != color) {
                pixels[index] = color;
                markCanvasPixelDirty(index);
                changed = true;
            }
        }
        return changed;
    }

    private void pushFullHistory(String label, int[] before, int[] after) {
        int length = Math.min(before == null ? 0 : before.length, after == null ? 0 : after.length);
        IntList indices = new IntList();
        IntList beforeColors = new IntList();
        IntList afterColors = new IntList();
        for (int i = 0; i < length; i++) {
            if (before[i] != after[i]) {
                indices.add(i);
                beforeColors.add(before[i]);
                afterColors.add(after[i]);
            }
        }
        if (!indices.isEmpty()) {
            pushHistory(label, indices.toArray(), beforeColors.toArray(), afterColors.toArray());
        }
    }

    private void uploadCanvasIfNeeded() {
        if (!canvasDirty && canvasTexture != null) {
            return;
        }
        if (canvasTexture != null && (canvasTextureW != canvasW || canvasTextureH != canvasH)) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(CANVAS_TEXTURE);
            canvasTexture = null;
            canvasTextureW = -1;
            canvasTextureH = -1;
            canvasImage = null;
            canvasFullyDirty = true;
        }
        ensureCanvasImage();
        if (canvasTexture == null) {
            canvasTexture = new NativeImageBackedTexture(() -> "monvhua drawing board screen", canvasImage);
            MinecraftClient.getInstance().getTextureManager().registerTexture(CANVAS_TEXTURE, canvasTexture);
        } else {
            canvasTexture.upload();
        }
        canvasTextureW = canvasW;
        canvasTextureH = canvasH;
        canvasDirty = false;
        canvasFullyDirty = false;
        canvasDirtyIndices.clear();
    }

    private void ensureCanvasImage() {
        if (canvasImage == null || canvasImage.getWidth() != canvasW || canvasImage.getHeight() != canvasH) {
            if (canvasTexture != null) {
                MinecraftClient.getInstance().getTextureManager().destroyTexture(CANVAS_TEXTURE);
                canvasTexture = null;
                canvasTextureW = -1;
                canvasTextureH = -1;
            } else if (canvasImage != null) {
                canvasImage.close();
            }
            canvasImage = new NativeImage(canvasW, canvasH, false);
            canvasFullyDirty = true;
        }
        if (canvasFullyDirty) {
            for (int y = 0; y < canvasH; y++) {
                for (int x = 0; x < canvasW; x++) {
                    canvasImage.setColorArgb(x, y, pixels[y * canvasW + x]);
                }
            }
            return;
        }
        for (int i = 0; i < canvasDirtyIndices.size(); i++) {
            int index = canvasDirtyIndices.get(i);
            if (index < 0 || index >= pixels.length) {
                continue;
            }
            canvasImage.setColorArgb(index % canvasW, index / canvasW, pixels[index]);
        }
    }

    private void markCanvasFullyDirty() {
        canvasDirty = true;
        canvasFullyDirty = true;
        canvasDirtyIndices.clear();
    }

    private void markCanvasPixelDirty(int index) {
        canvasDirty = true;
        if (!canvasFullyDirty) {
            canvasDirtyIndices.addUnique(index);
        }
    }

    private static boolean isInside(int x, int y, int w, int h, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private int[] whitePixels() {
        return whitePixels(canvasW, canvasH);
    }

    private static int[] whitePixels(int width, int height) {
        int[] result = new int[Math.max(1, width * height)];
        Arrays.fill(result, 0xFFFFFFFF);
        return result;
    }

    private int[] sanitize(int[] source) {
        int[] result = whitePixels();
        if (source != null) {
            System.arraycopy(source, 0, result, 0, Math.min(source.length, result.length));
        }
        return result;
    }

    private float aspectRatio() {
        return canvasW / (float) Math.max(1, canvasH);
    }

    private void uploadColorMapIfNeeded() {
        if (colorMapTexture != null && Math.abs(uploadedHue - hue) < 0.0001F) {
            return;
        }
        NativeImage image = new NativeImage(COLOR_MAP_SIZE, COLOR_MAP_SIZE, false);
        for (int y = 0; y < COLOR_MAP_SIZE; y++) {
            float v = 1.0F - (float) y / Math.max(1, COLOR_MAP_SIZE - 1);
            for (int x = 0; x < COLOR_MAP_SIZE; x++) {
                float s = (float) x / Math.max(1, COLOR_MAP_SIZE - 1);
                image.setColorArgb(x, y, 0xFF000000 | hsvToRgb(hue, s, v));
            }
        }
        if (colorMapTexture == null) {
            colorMapTexture = new NativeImageBackedTexture(() -> "monvhua drawing board color map", image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(COLOR_MAP_TEXTURE, colorMapTexture);
        } else {
            colorMapTexture.setImage(image);
            colorMapTexture.upload();
        }
        uploadedHue = hue;
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

    private static int hsvToRgb(float h, float s, float v) {
        float hue = (h - (float) Math.floor(h)) * 6.0F;
        int sector = (int) Math.floor(hue);
        float f = hue - sector;
        float p = v * (1.0F - s);
        float q = v * (1.0F - f * s);
        float t = v * (1.0F - (1.0F - f) * s);
        float r;
        float g;
        float b;
        switch (sector) {
            case 0 -> {
                r = v;
                g = t;
                b = p;
            }
            case 1 -> {
                r = q;
                g = v;
                b = p;
            }
            case 2 -> {
                r = p;
                g = v;
                b = t;
            }
            case 3 -> {
                r = p;
                g = q;
                b = v;
            }
            case 4 -> {
                r = t;
                g = p;
                b = v;
            }
            default -> {
                r = v;
                g = p;
                b = q;
            }
        }
        return ((int) (r * 255.0F) << 16) | ((int) (g * 255.0F) << 8) | (int) (b * 255.0F);
    }

    private String trimToWidth(String text, int maxWidth) {
        if (textRenderer.getWidth(text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        int suffixWidth = textRenderer.getWidth(suffix);
        String result = text;
        while (!result.isEmpty() && textRenderer.getWidth(result) + suffixWidth > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + suffix;
    }

    private record HistoryStep(int sequence, String label, int[] indices, int[] before, int[] after) {
        private HistoryStep {
            int length = Math.min(indices == null ? 0 : indices.length,
                    Math.min(before == null ? 0 : before.length, after == null ? 0 : after.length));
            indices = Arrays.copyOf(indices == null ? new int[0] : indices, length);
            before = Arrays.copyOf(before == null ? new int[0] : before, length);
            after = Arrays.copyOf(after == null ? new int[0] : after, length);
        }
    }

    private static final class IntList {
        private int[] values = new int[16];
        private int size;

        private void add(int value) {
            if (size >= values.length) {
                values = Arrays.copyOf(values, values.length * 2);
            }
            values[size++] = value;
        }

        private void addUnique(int value) {
            if (indexOf(value) < 0) {
                add(value);
            }
        }

        private int get(int index) {
            return values[index];
        }

        private void set(int index, int value) {
            values[index] = value;
        }

        private int indexOf(int value) {
            for (int i = 0; i < size; i++) {
                if (values[i] == value) {
                    return i;
                }
            }
            return -1;
        }

        private boolean isEmpty() {
            return size == 0;
        }

        private int size() {
            return size;
        }

        private void clear() {
            size = 0;
        }

        private int[] toArray() {
            return Arrays.copyOf(values, size);
        }
    }
}
