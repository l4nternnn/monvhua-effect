package com.kuilunfuzhe.monvhua.features.paint;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.network.SafeClientNetworking;
import com.kuilunfuzhe.monvhua.network.paint.PaintOverlayPackets;
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

import java.util.List;

public class PaintBrushColorScreen extends Screen {
    private static final int MAP_SIZE = 144;
    private static final int HUE_WIDTH = 14;
    private static final int GAP = 10;
    private static final int PREVIEW_SIZE = 28;
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_GAP = 4;
    private static final int CONTROL_WIDTH = 112;
    private static final int FAVORITE_WIDTH = 46;
    private static final int PANEL_PADDING = 14;
    private static final Identifier COLOR_MAP_TEXTURE_ID = Identifier.of(MonvhuaMod.MOD_ID, "dynamic/paint_color_map");

    private int mapX;
    private int mapY;
    private int hueX;
    private int hueY;
    private int controlX;
    private int favoriteX;
    private float hue = 0.97F;
    private float saturation = 0.84F;
    private float value = 1.0F;
    private NativeImageBackedTexture colorMapTexture;
    private TextFieldWidget hexField;
    private final BlockPos paintBucketPos;
    private float uploadedHue = -1.0F;
    private boolean updatingHexField;
    private boolean picking;

    public PaintBrushColorScreen() {
        this(null);
    }

    public PaintBrushColorScreen(BlockPos paintBucketPos) {
        super(Text.literal("Paint Brush Color"));
        this.paintBucketPos = paintBucketPos == null ? null : paintBucketPos.toImmutable();
        loadSelectedColor();
    }

    @Override
    protected void init() {
        int contentWidth = MAP_SIZE + GAP + HUE_WIDTH + GAP + CONTROL_WIDTH + GAP + FAVORITE_WIDTH;
        mapX = (width - contentWidth) / 2;
        mapY = (height - MAP_SIZE) / 2;
        hueX = mapX + MAP_SIZE + GAP;
        hueY = mapY;
        controlX = hueX + HUE_WIDTH + GAP;
        favoriteX = controlX + CONTROL_WIDTH + GAP;

        hexField = addDrawableChild(new TextFieldWidget(textRenderer, controlX, mapY + 55, CONTROL_WIDTH, 18, Text.literal("HEX")));
        hexField.setMaxLength(7);
        hexField.setTextPredicate(PaintBrushColorScreen::isValidHexInput);
        hexField.setPlaceholder(Text.literal("#RRGGBB"));
        hexField.setChangedListener(this::onHexFieldChanged);
        updateHexField();
        uploadColorMapIfNeeded();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        uploadColorMapIfNeeded();
        int panelX = mapX - PANEL_PADDING;
        int panelY = mapY - 34;
        int panelWidth = MAP_SIZE + GAP + HUE_WIDTH + GAP + CONTROL_WIDTH + GAP + FAVORITE_WIDTH + PANEL_PADDING * 2;
        int panelHeight = MAP_SIZE + 82;
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xE0101015);
        context.drawText(textRenderer, title, mapX, panelY + 10, 0xFFFFFFFF, false);

        drawColorMap(context);
        drawHueBar(context);
        drawControlColumn(context);
        drawFavoriteColumn(context);
        drawCursors(context);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (handleFillButtonClick(mouseX, mouseY)) {
                return true;
            }
            if (handleStarClick(mouseX, mouseY) || handleColorSlotClick(mouseX, mouseY)) {
                return true;
            }
            if (pick(mouseX, mouseY)) {
                picking = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && pick(mouseX, mouseY)) {
            picking = true;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && picking) {
            PaintOverlayClient.recordPickedColor(PaintOverlayClient.selectedColor());
            picking = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean pick(double mouseX, double mouseY) {
        if (mouseX >= mapX && mouseX < mapX + MAP_SIZE && mouseY >= mapY && mouseY < mapY + MAP_SIZE) {
            saturation = MathHelper.clamp((float) ((mouseX - mapX) / (MAP_SIZE - 1)), 0.0F, 1.0F);
            value = MathHelper.clamp(1.0F - (float) ((mouseY - mapY) / (MAP_SIZE - 1)), 0.0F, 1.0F);
            syncSelectedColor();
            return true;
        }
        if (mouseX >= hueX && mouseX < hueX + HUE_WIDTH && mouseY >= hueY && mouseY < hueY + MAP_SIZE) {
            hue = MathHelper.clamp((float) ((mouseY - hueY) / (MAP_SIZE - 1)), 0.0F, 1.0F);
            uploadedHue = -1.0F;
            syncSelectedColor();
            return true;
        }
        return false;
    }

    private boolean handleFillButtonClick(double mouseX, double mouseY) {
        if (paintBucketPos == null || !isInside(fillButtonX(), fillButtonY(), 64, 18, mouseX, mouseY)) {
            return false;
        }
        int color = PaintOverlayClient.selectedColor() & 0xFFFFFF;
        PaintOverlayClient.recordPickedColor(color);
        SafeClientNetworking.send(new PaintOverlayPackets.FillPaintBucketC2S(paintBucketPos, color));
        close();
        return true;
    }

    private boolean handleStarClick(double mouseX, double mouseY) {
        int previewX = controlX;
        int previewY = mapY + 12;
        if (isInsideStar(previewX, previewY, PREVIEW_SIZE, mouseX, mouseY)) {
            PaintOverlayClient.toggleFavoriteColor(PaintOverlayClient.selectedColor());
            return true;
        }

        List<Integer> recent = PaintOverlayClient.recentColors();
        int recentY = mapY + 93;
        for (int i = 0; i < recent.size(); i++) {
            int slotX = controlX + i * (SLOT_SIZE + SLOT_GAP);
            if (isInsideStar(slotX, recentY, SLOT_SIZE, mouseX, mouseY)) {
                PaintOverlayClient.toggleFavoriteColor(recent.get(i));
                return true;
            }
        }

        List<Integer> favorites = PaintOverlayClient.favoriteColors();
        for (int i = 0; i < Math.min(favorites.size(), maxFavoriteSlots()); i++) {
            int slotY = mapY + 16 + i * (SLOT_SIZE + SLOT_GAP);
            if (isInsideStar(favoriteX, slotY, SLOT_SIZE, mouseX, mouseY)) {
                PaintOverlayClient.toggleFavoriteColor(favorites.get(i));
                return true;
            }
        }
        return false;
    }

    private boolean handleColorSlotClick(double mouseX, double mouseY) {
        List<Integer> recent = PaintOverlayClient.recentColors();
        int recentY = mapY + 93;
        for (int i = 0; i < recent.size(); i++) {
            int slotX = controlX + i * (SLOT_SIZE + SLOT_GAP);
            if (isInside(slotX, recentY, SLOT_SIZE, SLOT_SIZE, mouseX, mouseY)) {
                selectColor(recent.get(i), true);
                return true;
            }
        }

        List<Integer> favorites = PaintOverlayClient.favoriteColors();
        for (int i = 0; i < Math.min(favorites.size(), maxFavoriteSlots()); i++) {
            int slotY = mapY + 16 + i * (SLOT_SIZE + SLOT_GAP);
            if (isInside(favoriteX, slotY, SLOT_SIZE, SLOT_SIZE, mouseX, mouseY)) {
                selectColor(favorites.get(i), true);
                return true;
            }
        }
        return false;
    }

    private void drawColorMap(DrawContext context) {
        context.drawTexture(RenderPipelines.GUI_TEXTURED, COLOR_MAP_TEXTURE_ID,
                mapX, mapY, 0.0F, 0.0F, MAP_SIZE, MAP_SIZE, MAP_SIZE, MAP_SIZE);
        drawBorder(context, mapX, mapY, MAP_SIZE, MAP_SIZE);
    }

    private void drawHueBar(DrawContext context) {
        for (int y = 0; y < MAP_SIZE; y++) {
            int color = 0xFF000000 | hsvToRgb((float) y / (MAP_SIZE - 1), 1.0F, 1.0F);
            context.fill(hueX, hueY + y, hueX + HUE_WIDTH, hueY + y + 1, color);
        }
        drawBorder(context, hueX, hueY, HUE_WIDTH, MAP_SIZE);
    }

    private void drawControlColumn(DrawContext context) {
        int previewX = controlX;
        int previewY = mapY + 12;
        int color = PaintOverlayClient.selectedColor();
        context.drawText(textRenderer, Text.literal("Current"), controlX, mapY, 0xFFFFFFFF, false);
        drawSwatch(context, previewX, previewY, PREVIEW_SIZE, color, true);
        drawStar(context, previewX, previewY, PREVIEW_SIZE, PaintOverlayClient.isFavoriteColor(color));
        context.drawText(textRenderer, Text.literal(toHex(color)), previewX + PREVIEW_SIZE + 8, previewY + 10, 0xFFE6E6E6, false);

        context.drawText(textRenderer, Text.literal("HEX Search"), controlX, mapY + 44, 0xFFB8B8C2, false);
        context.drawText(textRenderer, Text.literal("最近取色"), controlX, mapY + 80, 0xFFFFFFFF, false);

        List<Integer> recent = PaintOverlayClient.recentColors();
        int recentY = mapY + 93;
        for (int i = 0; i < 3; i++) {
            int slotX = controlX + i * (SLOT_SIZE + SLOT_GAP);
            int slotColor = i < recent.size() ? recent.get(i) : 0;
            drawSwatch(context, slotX, recentY, SLOT_SIZE, slotColor, i < recent.size());
            if (i < recent.size()) {
                drawStar(context, slotX, recentY, SLOT_SIZE, PaintOverlayClient.isFavoriteColor(slotColor));
            }
        }

        if (paintBucketPos != null) {
            drawFillButton(context);
        }
    }

    private void drawFavoriteColumn(DrawContext context) {
        context.drawText(textRenderer, Text.literal("Fav-收藏"), favoriteX, mapY, 0xFFFFFFFF, false);
        List<Integer> favorites = PaintOverlayClient.favoriteColors();
        int slots = maxFavoriteSlots();
        for (int i = 0; i < slots; i++) {
            int slotY = mapY + 16 + i * (SLOT_SIZE + SLOT_GAP);
            int slotColor = i < favorites.size() ? favorites.get(i) : 0;
            drawSwatch(context, favoriteX, slotY, SLOT_SIZE, slotColor, i < favorites.size());
            if (i < favorites.size()) {
                drawStar(context, favoriteX, slotY, SLOT_SIZE, true);
            }
        }
    }

    private void drawCursors(DrawContext context) {
        int sx = mapX + Math.round(saturation * (MAP_SIZE - 1));
        int sy = mapY + Math.round((1.0F - value) * (MAP_SIZE - 1));
        context.fill(sx - 4, sy - 1, sx + 5, sy + 1, 0xFFFFFFFF);
        context.fill(sx - 1, sy - 4, sx + 1, sy + 5, 0xFFFFFFFF);
        context.fill(sx - 3, sy, sx + 4, sy + 1, 0xFF000000);
        context.fill(sx, sy - 3, sx + 1, sy + 4, 0xFF000000);

        int hy = hueY + Math.round(hue * (MAP_SIZE - 1));
        context.fill(hueX - 3, hy - 2, hueX + HUE_WIDTH + 3, hy + 2, 0xFFFFFFFF);
        context.fill(hueX - 2, hy - 1, hueX + HUE_WIDTH + 2, hy + 1, 0xFF000000);
    }

    private void drawSwatch(DrawContext context, int x, int y, int size, int color, boolean filled) {
        context.fill(x, y, x + size, y + size, filled ? color : 0x55202028);
        drawBorder(context, x, y, size, size);
    }

    private void drawBorder(DrawContext context, int x, int y, int width, int height) {
        context.fill(x - 1, y - 1, x + width + 1, y, 0xFFB8B8C2);
        context.fill(x - 1, y + height, x + width + 1, y + height + 1, 0xFF111116);
        context.fill(x - 1, y - 1, x, y + height + 1, 0xFFB8B8C2);
        context.fill(x + width, y - 1, x + width + 1, y + height + 1, 0xFF111116);
    }

    private void drawStar(DrawContext context, int x, int y, int size, boolean favorite) {
        int starX = x + size - 9;
        int starY = y + 1;
        context.fill(starX - 1, starY, starX + 9, starY + 9, 0x66000000);
        context.drawText(textRenderer, Text.literal(favorite ? "★" : "☆"), starX, starY, favorite ? 0xFFFFD75E : 0xFFFFFFFF, true);
    }

    private void drawFillButton(DrawContext context) {
        int x = fillButtonX();
        int y = fillButtonY();
        context.fill(x, y, x + 64, y + 18, 0xFF2F613A);
        context.fill(x, y, x + 64, y + 1, 0xFF8FD99C);
        context.fill(x, y + 17, x + 64, y + 18, 0xFF102414);
        context.fill(x, y, x + 1, y + 18, 0xFF8FD99C);
        context.fill(x + 63, y, x + 64, y + 18, 0xFF102414);
        context.drawText(textRenderer, Text.literal("Fill"), x + 20, y + 5, 0xFFFFFFFF, false);
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

    private void onHexFieldChanged(String text) {
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
            colorMapTexture = new NativeImageBackedTexture(() -> "monvhua paint color map", image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(COLOR_MAP_TEXTURE_ID, colorMapTexture);
        } else {
            colorMapTexture.setImage(image);
            colorMapTexture.upload();
        }
        uploadedHue = hue;
    }

    @Override
    public void removed() {
        if (colorMapTexture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(COLOR_MAP_TEXTURE_ID);
            colorMapTexture = null;
        }
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

    private int maxFavoriteSlots() {
        return Math.max(1, (MAP_SIZE - 16) / (SLOT_SIZE + SLOT_GAP));
    }

    private int fillButtonX() {
        return controlX;
    }

    private int fillButtonY() {
        return mapY + 124;
    }

    private static boolean isInsideStar(int x, int y, int size, double mouseX, double mouseY) {
        return isInside(x + size - 10, y, 10, 10, mouseX, mouseY);
    }

    private static boolean isInside(int x, int y, int width, int height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
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
}
