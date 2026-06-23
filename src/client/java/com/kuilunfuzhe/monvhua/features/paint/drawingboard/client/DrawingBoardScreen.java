package com.kuilunfuzhe.monvhua.features.paint.drawingboard.client;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.features.paint.PaintOverlayClient;
import com.kuilunfuzhe.monvhua.features.paint.drawingboard.DrawingBoardBlockEntity;
import com.kuilunfuzhe.monvhua.gui.layout.DraggableResizableLayout;
import com.kuilunfuzhe.monvhua.item.paint.PaintBrushItem;
import com.kuilunfuzhe.monvhua.network.SafeClientNetworking;
import com.kuilunfuzhe.monvhua.network.drawingboard.DrawingBoardPackets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public class DrawingBoardScreen extends Screen {
    private static final Identifier BACKGROUND = Identifier.of(MonvhuaMod.MOD_ID, "textures/gui/draw_background.png");
    private static final Identifier CANVAS_TEXTURE = Identifier.of(MonvhuaMod.MOD_ID, "dynamic/drawing_board_screen");
    private static final int BACKGROUND_W = 463;
    private static final int BACKGROUND_H = 454;
    private static final int CANVAS_W = DrawingBoardBlockEntity.WIDTH;
    private static final int CANVAS_H = DrawingBoardBlockEntity.HEIGHT;
    private static final int PANEL_PAD = 10;
    private static final int CONTROL_W = 64;
    private static final int GAP = 10;
    private static final int PALETTE_W = 72;
    private static final int HISTORY_W = 152;
    private static final int HISTORY_ROW_H = 17;
    private static final int HISTORY_MENU_W = 86;
    private static final int HISTORY_MENU_H = 24;
    private static final int MAX_HISTORY = 80;

    private final BlockPos pos;
    private int[] pixels = whitePixels();
    private NativeImageBackedTexture canvasTexture;
    private boolean canvasDirty = true;
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
    private int[] strokeBeforePixels;
    private final List<HistoryStep> history = new ArrayList<>();
    private int historyCursor;
    private int historySequence = 1;
    private int historyMenuIndex = -1;
    private int historyMenuX;
    private int historyMenuY;

    public DrawingBoardScreen(BlockPos pos) {
        super(Text.literal("Drawing Board"));
        this.pos = pos.toImmutable();
    }

    public static void receiveSync(BlockPos pos, int[] pixels) {
        if (net.minecraft.client.MinecraftClient.getInstance().currentScreen instanceof DrawingBoardScreen screen
                && screen.pos.equals(pos)) {
            screen.setPixels(pixels);
        }
    }

    @Override
    protected void init() {
        panelW = Math.max(260, Math.min(Math.max(260, width - 24), Math.max(560, (int) (width * 0.72F))));
        panelH = Math.max(250, height / 2);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        rebuildCanvasLayout();
        uploadCanvasIfNeeded();
        SafeClientNetworking.send(new DrawingBoardPackets.RequestSyncC2S(pos));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x99000000);
        uploadCanvasIfNeeded();
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
            strokeBeforePixels = copyPixels();
            lastX = toCanvasX(mouseX);
            lastY = toCanvasY(mouseY);
            paintAt(lastX, lastY, button == GLFW.GLFW_MOUSE_BUTTON_RIGHT, true);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
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
        if (isAltKeyDown() && insideCanvas(mouseX, mouseY)) {
            zoomPaperAt(mouseX, mouseY, verticalAmount);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && keyCode == GLFW.GLFW_KEY_Z) {
            undoHistory();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_Y) {
            redoHistory();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
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
                    paperW, paperH, CANVAS_W, CANVAS_H, CANVAS_W, CANVAS_H);
        } finally {
            context.disableScissor();
        }
    }

    private void drawControls(DrawContext context, int mouseX, int mouseY) {
        int x = panelX + panelW - PANEL_PAD - CONTROL_W;
        int y = controlsY();
        drawPalette(context, panelX + PANEL_PAD, y, mouseX, mouseY);
        drawButton(context, x, y, CONTROL_W, 18, "Clear", isInside(x, y, CONTROL_W, 18, mouseX, mouseY));
        drawButton(context, x, y + 24, CONTROL_W, 18, "Paper", isInside(x, y + 24, CONTROL_W, 18, mouseX, mouseY));
        drawButton(context, x, y + 48, CONTROL_W, 18, "Reset", isInside(x, y + 48, CONTROL_W, 18, mouseX, mouseY));
        context.drawText(textRenderer, Text.literal("Brush"), x, y + 76, 0xFFFFFFFF, false);
        drawButton(context, x, y + 96, 18, 16, "-", isInside(x, y + 96, 18, 16, mouseX, mouseY));
        context.drawText(textRenderer, Text.literal(String.valueOf(brushRadius)), x + 27, y + 101, 0xFFFFFFFF, false);
        drawButton(context, x + 46, y + 96, 18, 16, "+", isInside(x + 46, y + 96, 18, 16, mouseX, mouseY));
        context.drawText(textRenderer, Text.literal("L draw"), x, y + 130, 0xFFE8E8E8, false);
        context.drawText(textRenderer, Text.literal("R erase"), x, y + 142, 0xFFE8E8E8, false);
        drawHistoryPanel(context, historyX(), y, mouseX, mouseY);
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
            pixels = whitePixels();
            canvasDirty = true;
            pushHistory("清空", before, copyPixels());
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
        if (isInside(x, y + 96, 18, 16, mouseX, mouseY)) {
            brushRadius = Math.max(1, brushRadius - 1);
            return true;
        }
        if (isInside(x + 46, y + 96, 18, 16, mouseX, mouseY)) {
            brushRadius = Math.min(12, brushRadius + 1);
            return true;
        }
        return false;
    }

    private void drawPalette(DrawContext context, int x, int y, int mouseX, int mouseY) {
        var brush = brushStack();
        context.drawText(textRenderer, Text.literal("Colors"), x, y - 12, 0xFFFFFFFF, false);
        int selected = brush.isEmpty() ? PaintOverlayClient.selectedBrushSlot() : PaintBrushItem.getSelectedSlot(brush);
        for (int i = 0; i < PaintBrushItem.COLOR_SLOTS; i++) {
            int slotY = y + i * 19;
            int color = brush.isEmpty() ? 0xFF202026 : 0xFF000000 | PaintBrushItem.getPaintColor(brush, i);
            double remaining = brush.isEmpty() ? 0.0D : PaintBrushItem.getRemainingPaintPercent(brush, i);
            boolean hover = isInside(x, slotY, 72, 16, mouseX, mouseY);
            context.fill(x, slotY, x + 72, slotY + 16, hover ? 0xFF344454 : 0xFF202832);
            context.fill(x + 2, slotY + 2, x + 14, slotY + 14, remaining > 0.0D ? color : 0xFF111111);
            if (i == selected) {
                context.drawBorder(x, slotY, 72, 16, 0xFFFFFFFF);
            }
            context.drawText(textRenderer, Text.literal((i + 1) + " " + formatPaintPercent(remaining)), x + 18, slotY + 4, 0xFFE6E6E6, false);
        }
    }

    private static String formatPaintPercent(double remaining) {
        double percent = Math.clamp(remaining * 100.0D / PaintBrushItem.MAX_PAINT_PIXELS, 0.0D, 100.0D);
        if (Math.abs(percent - Math.rint(percent)) < 0.05D) {
            return String.format(java.util.Locale.ROOT, "%.0f%%", percent);
        }
        return String.format(java.util.Locale.ROOT, "%.1f%%", percent);
    }

    private boolean handlePaletteClick(double mouseX, double mouseY) {
        int x = panelX + PANEL_PAD;
        int y = controlsY();
        for (int i = 0; i < PaintBrushItem.COLOR_SLOTS; i++) {
            if (isInside(x, y + i * 19, 72, 16, mouseX, mouseY)) {
                PaintOverlayClient.selectBrushSlot(i);
                return true;
            }
        }
        return false;
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
        if (strokeBeforePixels == null) {
            return;
        }
        int[] before = strokeBeforePixels;
        strokeBeforePixels = null;
        if (Arrays.equals(before, pixels)) {
            return;
        }
        pushHistory(label, before, copyPixels());
    }

    private void pushHistory(String label, int[] before, int[] after) {
        if (historyCursor < history.size()) {
            history.subList(historyCursor, history.size()).clear();
        }
        history.add(new HistoryStep(historySequence++, label, sanitize(before), sanitize(after)));
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
        applyPixelsFromHistory(step.before);
        historyCursor--;
    }

    private void redoHistory() {
        if (historyCursor >= history.size()) {
            return;
        }
        HistoryStep step = history.get(historyCursor);
        applyPixelsFromHistory(step.after);
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
            applyPixelsFromHistory(history.get(targetCursor).before);
        } else {
            applyPixelsFromHistory(history.get(targetIndex).after);
        }
        historyCursor = targetCursor;
    }

    private void applyPixelsFromHistory(int[] nextPixels) {
        pixels = sanitize(nextPixels);
        canvasDirty = true;
        SafeClientNetworking.send(new DrawingBoardPackets.ApplyPixelsC2S(pos, pixels));
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
        int defaultDrawW = CANVAS_W * defaultDrawScale;
        int defaultDrawH = CANVAS_H * defaultDrawScale;
        int defaultCanvasX = defaultCanvasX(defaultDrawW);
        int defaultCanvasY = panelY + (panelH - defaultDrawH) / 2;
        baseCanvasX = defaultCanvasX;
        baseCanvasY = defaultCanvasY;
        baseDrawW = defaultDrawW;
        baseDrawH = defaultDrawH;
        layout = new DraggableResizableLayout("drawing_board", width, height);
        canvasBounds = layout.element("canvas", defaultCanvasX, defaultCanvasY, defaultDrawW, defaultDrawH,
                CANVAS_W, CANVAS_H, 0.0F, false, true, true, true, CANVAS_W / (float) CANVAS_H);
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
        drawW = Math.max(CANVAS_W, canvasBounds.width);
        drawH = Math.max(CANVAS_H, Math.round(drawW * (CANVAS_H / (float) CANVAS_W)));
        drawScale = Math.max(1, Math.min(drawW / CANVAS_W, drawH / CANVAS_H));

        if (!paperTransformInitialized) {
            resetPaperToCanvas();
        } else if (!paperTransformCustomized) {
            resetPaperToCanvas();
        } else {
            paperX += canvasX - oldCanvasX;
            paperY += canvasY - oldCanvasY;
            if (oldDrawW > 0 && oldDrawH > 0 && (oldDrawW != drawW || oldDrawH != drawH)) {
                double scale = drawW / (double) oldDrawW;
                paperW = Math.max(CANVAS_W, (int) Math.round(paperW * scale));
                paperH = Math.max(CANVAS_H, Math.round(paperW * (CANVAS_H / (float) CANVAS_W)));
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

    private void zoomPaperAt(double mouseX, double mouseY, double verticalAmount) {
        if (verticalAmount == 0.0D) {
            return;
        }
        double anchorX = MathHelper.clamp((mouseX - paperX) / Math.max(1.0D, paperW), 0.0D, 1.0D);
        double anchorY = MathHelper.clamp((mouseY - paperY) / Math.max(1.0D, paperH), 0.0D, 1.0D);
        double scale = Math.pow(1.12D, verticalAmount);
        int nextW = Math.max(CANVAS_W, (int) Math.round(paperW * scale));
        int nextH = Math.max(CANVAS_H, Math.round(nextW * (CANVAS_H / (float) CANVAS_W)));
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
        paperW = Math.max(CANVAS_W, paperW);
        paperH = Math.max(CANVAS_H, Math.round(paperW * (CANVAS_H / (float) CANVAS_W)));
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

    private int defaultDrawScale() {
        int availableW = panelW - PANEL_PAD * 2 - PALETTE_W - CONTROL_W - HISTORY_W - GAP * 3;
        int availableH = panelH - PANEL_PAD * 2;
        return Math.max(1, Math.min(availableW / CANVAS_W, availableH / CANVAS_H));
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
        int color = erase ? 0xFFFFFFFF : selectedBrushColorOrZero();
        if (!erase && color == 0) {
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
                if (px >= 0 && px < CANVAS_W && py >= 0 && py < CANVAS_H) {
                    int index = py * CANVAS_W + px;
                    if (pixels[index] != color) {
                        pixels[index] = color;
                        changed = true;
                    }
                }
            }
        }
        if (changed) {
            canvasDirty = true;
        }
        if (send) {
            SafeClientNetworking.send(new DrawingBoardPackets.StrokeC2S(pos, x, y, brushRadius, color, false));
        }
    }

    private int selectedBrushColorOrZero() {
        var brush = brushStack();
        if (brush.isEmpty()) {
            return 0;
        }
        int slot = PaintBrushItem.getSelectedSlot(brush);
        if (!PaintBrushItem.hasPaint(brush, slot)) {
            return 0;
        }
        return 0xFF000000 | PaintBrushItem.getPaintColor(brush, slot);
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
        return MathHelper.clamp((int) ((mouseX - paperX) * CANVAS_W / Math.max(1, paperW)), 0, CANVAS_W - 1);
    }

    private int toCanvasY(double mouseY) {
        return MathHelper.clamp((int) ((mouseY - paperY) * CANVAS_H / Math.max(1, paperH)), 0, CANVAS_H - 1);
    }

    @Override
    public void removed() {
        if (canvasTexture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(CANVAS_TEXTURE);
            canvasTexture = null;
        }
    }

    private void setPixels(int[] source) {
        pixels = sanitize(source);
        canvasDirty = true;
    }

    private void uploadCanvasIfNeeded() {
        if (!canvasDirty && canvasTexture != null) {
            return;
        }
        NativeImage image = new NativeImage(CANVAS_W, CANVAS_H, false);
        for (int y = 0; y < CANVAS_H; y++) {
            for (int x = 0; x < CANVAS_W; x++) {
                image.setColorArgb(x, y, pixels[y * CANVAS_W + x]);
            }
        }
        if (canvasTexture == null) {
            canvasTexture = new NativeImageBackedTexture(() -> "monvhua drawing board screen", image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(CANVAS_TEXTURE, canvasTexture);
        } else {
            canvasTexture.setImage(image);
            canvasTexture.upload();
        }
        canvasDirty = false;
    }

    private static boolean isInside(int x, int y, int w, int h, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static int[] whitePixels() {
        int[] result = new int[DrawingBoardBlockEntity.PIXELS];
        Arrays.fill(result, 0xFFFFFFFF);
        return result;
    }

    private static int[] sanitize(int[] source) {
        int[] result = whitePixels();
        if (source != null) {
            System.arraycopy(source, 0, result, 0, Math.min(source.length, result.length));
        }
        return result;
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

    private record HistoryStep(int sequence, String label, int[] before, int[] after) {
        private HistoryStep {
            before = sanitize(before);
            after = sanitize(after);
        }
    }
}
