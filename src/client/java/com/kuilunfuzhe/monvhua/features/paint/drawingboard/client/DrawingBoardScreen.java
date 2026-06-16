package com.kuilunfuzhe.monvhua.features.paint.drawingboard.client;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.features.paint.PaintOverlayClient;
import com.kuilunfuzhe.monvhua.features.paint.drawingboard.DrawingBoardBlockEntity;
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

    private final BlockPos pos;
    private int[] pixels = whitePixels();
    private NativeImageBackedTexture canvasTexture;
    private boolean canvasDirty = true;
    private int canvasX;
    private int canvasY;
    private int drawW;
    private int drawH;
    private int drawScale = 1;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private boolean drawing;
    private int drawButton = -1;
    private int brushRadius = 1;
    private int lastX = -1;
    private int lastY = -1;

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
        panelW = Math.max(260, width / 2);
        panelH = Math.max(250, height / 2);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        int availableW = panelW - PANEL_PAD * 2 - CONTROL_W - GAP;
        int availableH = panelH - PANEL_PAD * 2;
        drawScale = Math.max(1, Math.min(availableW / CANVAS_W, availableH / CANVAS_H));
        drawW = CANVAS_W * drawScale;
        drawH = CANVAS_H * drawScale;
        canvasX = panelX + PANEL_PAD + drawW * 3 / 2;
        canvasY = panelY + (panelH - drawH) / 2;
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
        drawControls(context, mouseX, mouseY);
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
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && handleControlClick(mouseX, mouseY)) {
            return true;
        }
        if ((button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
                && insideCanvas(mouseX, mouseY)) {
            drawing = true;
            drawButton = button;
            lastX = toCanvasX(mouseX);
            lastY = toCanvasY(mouseY);
            paintAt(lastX, lastY, button == GLFW.GLFW_MOUSE_BUTTON_RIGHT, true);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (drawing && button == drawButton) {
            if (!insideCanvas(mouseX, mouseY)) {
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
        if (button == drawButton) {
            drawing = false;
            drawButton = -1;
            lastX = -1;
            lastY = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void drawCanvas(DrawContext context) {
        context.drawTexture(RenderPipelines.GUI_TEXTURED, CANVAS_TEXTURE, canvasX, canvasY, 0.0F, 0.0F,
                drawW, drawH, CANVAS_W, CANVAS_H);
    }

    private void drawControls(DrawContext context, int mouseX, int mouseY) {
        int x = panelX + panelW - PANEL_PAD - CONTROL_W;
        int y = canvasY;
        drawButton(context, x, y, CONTROL_W, 18, "Clear", isInside(x, y, CONTROL_W, 18, mouseX, mouseY));
        drawButton(context, x, y + 24, CONTROL_W, 18, "Paper", isInside(x, y + 24, CONTROL_W, 18, mouseX, mouseY));
        context.drawText(textRenderer, Text.literal("Brush"), x, y + 52, 0xFFFFFFFF, false);
        drawButton(context, x, y + 66, 18, 16, "-", isInside(x, y + 66, 18, 16, mouseX, mouseY));
        context.drawText(textRenderer, Text.literal(String.valueOf(brushRadius)), x + 27, y + 71, 0xFFFFFFFF, false);
        drawButton(context, x + 46, y + 66, 18, 16, "+", isInside(x + 46, y + 66, 18, 16, mouseX, mouseY));
        context.drawText(textRenderer, Text.literal("L draw"), x, y + 100, 0xFFE8E8E8, false);
        context.drawText(textRenderer, Text.literal("R erase"), x, y + 112, 0xFFE8E8E8, false);
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
        int x = panelX + panelW - PANEL_PAD - CONTROL_W;
        int y = canvasY;
        if (isInside(x, y, CONTROL_W, 18, mouseX, mouseY)) {
            pixels = whitePixels();
            canvasDirty = true;
            SafeClientNetworking.send(new DrawingBoardPackets.StrokeC2S(pos, 0, 0, brushRadius, 0xFFFFFFFF, true));
            return true;
        }
        if (isInside(x, y + 24, CONTROL_W, 18, mouseX, mouseY)) {
            SafeClientNetworking.send(new DrawingBoardPackets.SaveToPaperC2S(pos));
            return true;
        }
        if (isInside(x, y + 66, 18, 16, mouseX, mouseY)) {
            brushRadius = Math.max(1, brushRadius - 1);
            return true;
        }
        if (isInside(x + 46, y + 66, 18, 16, mouseX, mouseY)) {
            brushRadius = Math.min(12, brushRadius + 1);
            return true;
        }
        return false;
    }

    private void interpolatePaint(int fromX, int fromY, int toX, int toY, boolean erase) {
        int steps = Math.max(1, Math.max(Math.abs(toX - fromX), Math.abs(toY - fromY)));
        for (int i = 1; i <= steps; i++) {
            float t = (float) i / steps;
            paintAt(Math.round(MathHelper.lerp(t, fromX, toX)), Math.round(MathHelper.lerp(t, fromY, toY)), erase, true);
        }
    }

    private void paintAt(int x, int y, boolean erase, boolean send) {
        int color = erase ? 0xFFFFFFFF : (PaintOverlayClient.selectedColor() | 0xFF000000);
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

    private boolean insideCanvas(double mouseX, double mouseY) {
        return mouseX >= canvasX && mouseX < canvasX + drawW && mouseY >= canvasY && mouseY < canvasY + drawH;
    }

    private int toCanvasX(double mouseX) {
        return MathHelper.clamp((int) ((mouseX - canvasX) / drawScale), 0, CANVAS_W - 1);
    }

    private int toCanvasY(double mouseY) {
        return MathHelper.clamp((int) ((mouseY - canvasY) / drawScale), 0, CANVAS_H - 1);
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
}
