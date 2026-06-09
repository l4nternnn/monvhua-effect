package com.kuilunfuzhe.monvhua.features.paint;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.fabricmc.loader.api.FabricLoader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class PaintPaperImportScreen extends Screen {
    private static final Identifier PREVIEW_TEXTURE_ID = Identifier.of(MonvhuaMod.MOD_ID, "dynamic/paint_paper_import_preview");
    private static final int PANEL_WIDTH = 620;
    private static final int PANEL_HEIGHT = 286;
    private static final int LIST_WIDTH = 210;
    private static final int ROW_HEIGHT = 18;
    private static final int PREVIEW_SIZE = 210;

    private final List<ImageEntry> images = new ArrayList<>();
    private Path folder;
    private int selectedIndex = -1;
    private int scroll;
    private int panelX;
    private int panelY;
    private double scale = 1.0D;
    private NativeImageBackedTexture previewTexture;
    private ImageEntry previewEntry;

    public PaintPaperImportScreen() {
        super(Text.literal("Paint Paper Import"));
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_WIDTH) / 2;
        panelY = (height - PANEL_HEIGHT) / 2;
        reloadImages();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int panelRight = panelX + PANEL_WIDTH;
        int panelBottom = panelY + PANEL_HEIGHT;
        context.fill(panelX, panelY, panelRight, panelBottom, 0xE0101015);
        context.drawText(textRenderer, title, panelX + 12, panelY + 10, 0xFFFFFFFF, false);

        drawList(context, mouseX, mouseY);
        drawPreview(context);
        drawControls(context);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (clickList(mouseX, mouseY)
                || clickButton(refreshX(), buttonY(), 58, 18, mouseX, mouseY, this::reloadImages)
                || clickButton(minusX(), buttonY(), 22, 18, mouseX, mouseY, () -> setScale(scale - 0.05D))
                || clickButton(plusX(), buttonY(), 22, 18, mouseX, mouseY, () -> setScale(scale + 0.05D))
                || clickButton(importX(), buttonY(), 64, 18, mouseX, mouseY, this::importSelected)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isInside(listX(), listY(), LIST_WIDTH, listHeight(), mouseX, mouseY)) {
            int maxScroll = Math.max(0, images.size() - visibleRows());
            scroll = MathHelper.clamp(scroll - (int) Math.signum(verticalAmount), 0, maxScroll);
            return true;
        }
        if (isInside(previewX(), previewY(), PREVIEW_SIZE, PREVIEW_SIZE, mouseX, mouseY)) {
            setScale(scale + Math.signum(verticalAmount) * 0.05D);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void reloadImages() {
        images.clear();
        selectedIndex = -1;
        scroll = 0;
        closePreviewTexture();

        folder = FabricLoader.getInstance().getGameDir().resolve("graffiti").normalize();
        try {
            Files.createDirectories(folder);
            try (Stream<Path> stream = Files.list(folder)) {
                stream.filter(Files::isRegularFile)
                        .filter(PaintPaperImportScreen::isImageFile)
                        .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                        .forEach(this::addImage);
            }
        } catch (IOException ignored) {
        }
        if (!images.isEmpty()) {
            selectedIndex = 0;
            loadPreview(images.get(0));
        }
    }

    private void addImage(Path path) {
        try {
            BufferedImage image = ImageIO.read(path.toFile());
            if (image != null) {
                images.add(new ImageEntry(path, path.getFileName().toString(), image.getWidth(), image.getHeight()));
            }
        } catch (IOException ignored) {
        }
    }

    private void drawList(DrawContext context, int mouseX, int mouseY) {
        int x = listX();
        int y = listY();
        context.fill(x, y, x + LIST_WIDTH, y + listHeight(), 0x66000000);
        drawBorder(context, x, y, LIST_WIDTH, listHeight());

        if (folder == null) {
            context.drawText(textRenderer, Text.literal("No local world folder"), x + 8, y + 8, 0xFFE6E6E6, false);
            return;
        }
        if (images.isEmpty()) {
            context.drawText(textRenderer, Text.literal("No images in graffiti"), x + 8, y + 8, 0xFFE6E6E6, false);
            context.drawText(textRenderer, Text.literal(folder.toString()), x + 8, y + 22, 0xFF9A9AA4, false);
            return;
        }

        int rows = visibleRows();
        for (int i = 0; i < rows; i++) {
            int index = scroll + i;
            if (index >= images.size()) {
                break;
            }
            int rowY = y + 4 + i * ROW_HEIGHT;
            ImageEntry entry = images.get(index);
            boolean selected = index == selectedIndex;
            boolean hover = isInside(x + 4, rowY, LIST_WIDTH - 8, ROW_HEIGHT - 2, mouseX, mouseY);
            context.fill(x + 4, rowY, x + LIST_WIDTH - 4, rowY + ROW_HEIGHT - 2,
                    selected ? 0xFF2E5E7D : (hover ? 0x66303038 : 0x00000000));
            String name = trimToWidth(entry.name(), LIST_WIDTH - 74);
            context.drawText(textRenderer, Text.literal(name), x + 8, rowY + 4, 0xFFFFFFFF, false);
            context.drawText(textRenderer, Text.literal(entry.width() + "x" + entry.height()), x + LIST_WIDTH - 62, rowY + 4, 0xFFB8B8C2, false);
        }
    }

    private void drawPreview(DrawContext context) {
        int x = previewX();
        int y = previewY();
        context.fill(x, y, x + PREVIEW_SIZE, y + PREVIEW_SIZE, 0x66000000);
        drawBorder(context, x, y, PREVIEW_SIZE, PREVIEW_SIZE);

        ImageEntry entry = selectedEntry();
        if (entry == null || previewTexture == null) {
            context.drawText(textRenderer, Text.literal("Select an image"), x + 58, y + 100, 0xFFE6E6E6, false);
            return;
        }

        int[] size = fitSize(entry.width(), entry.height(), PREVIEW_SIZE - 12, PREVIEW_SIZE - 12);
        int drawX = x + (PREVIEW_SIZE - size[0]) / 2;
        int drawY = y + (PREVIEW_SIZE - size[1]) / 2;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, PREVIEW_TEXTURE_ID,
                drawX, drawY, 0.0F, 0.0F, size[0], size[1], size[0], size[1]);
    }

    private void drawControls(DrawContext context) {
        int infoX = previewX() + PREVIEW_SIZE + 16;
        int y = previewY();
        ImageEntry entry = selectedEntry();
        context.drawText(textRenderer, Text.literal("Scale"), infoX, y, 0xFFFFFFFF, false);
        context.drawText(textRenderer, Text.literal(formatScale(scale) + "x"), infoX, y + 14, 0xFFE6E6E6, false);

        if (entry != null) {
            int scaledWidth = Math.max(1, (int) Math.round(entry.width() * scale));
            int scaledHeight = Math.max(1, (int) Math.round(entry.height() * scale));
            int paperColumns = Math.max(1, (scaledWidth + 399) / 400);
            int paperRows = Math.max(1, (scaledHeight + 399) / 400);
            context.drawText(textRenderer, Text.literal("Image " + entry.width() + "x" + entry.height()), infoX, y + 38, 0xFFB8B8C2, false);
            context.drawText(textRenderer, Text.literal("Scaled " + scaledWidth + "x" + scaledHeight), infoX, y + 52, 0xFFB8B8C2, false);
            context.drawText(textRenderer, Text.literal("Papers " + paperColumns + "x" + paperRows), infoX, y + 66, 0xFFB8B8C2, false);
        }

        drawButton(context, refreshX(), buttonY(), 58, 18, "Refresh", true);
        drawButton(context, minusX(), buttonY(), 22, 18, "-", true);
        drawButton(context, plusX(), buttonY(), 22, 18, "+", true);
        drawButton(context, importX(), buttonY(), 64, 18, "Import", entry != null);
    }

    private boolean clickList(double mouseX, double mouseY) {
        if (!isInside(listX(), listY(), LIST_WIDTH, listHeight(), mouseX, mouseY)) {
            return false;
        }
        int row = ((int) mouseY - listY() - 4) / ROW_HEIGHT;
        if (row < 0 || row >= visibleRows()) {
            return true;
        }
        int index = scroll + row;
        if (index >= 0 && index < images.size()) {
            selectedIndex = index;
            loadPreview(images.get(index));
        }
        return true;
    }

    private void loadPreview(ImageEntry entry) {
        closePreviewTexture();
        previewEntry = entry;
        try (InputStream stream = Files.newInputStream(entry.path())) {
            NativeImage image = NativeImage.read(stream);
            previewTexture = new NativeImageBackedTexture(() -> "monvhua paint paper import preview", image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(PREVIEW_TEXTURE_ID, previewTexture);
        } catch (IOException ignored) {
            previewTexture = null;
        }
    }

    private void importSelected() {
        ImageEntry entry = selectedEntry();
        MinecraftClient client = MinecraftClient.getInstance();
        if (entry == null || client.player == null || client.player.networkHandler == null) {
            return;
        }
        client.player.networkHandler.sendChatCommand("monvhua-graffiti import_scaled " + formatScale(scale) + " " + entry.name());
        close();
    }

    private void setScale(double nextScale) {
        scale = MathHelper.clamp(nextScale, 0.05D, 8.0D);
    }

    private ImageEntry selectedEntry() {
        return selectedIndex >= 0 && selectedIndex < images.size() ? images.get(selectedIndex) : null;
    }

    private int listX() {
        return panelX + 12;
    }

    private int listY() {
        return panelY + 34;
    }

    private int listHeight() {
        return PANEL_HEIGHT - 70;
    }

    private int visibleRows() {
        return Math.max(1, (listHeight() - 8) / ROW_HEIGHT);
    }

    private int previewX() {
        return panelX + LIST_WIDTH + 28;
    }

    private int previewY() {
        return panelY + 34;
    }

    private int buttonY() {
        return panelY + PANEL_HEIGHT - 30;
    }

    private int refreshX() {
        return panelX + 12;
    }

    private int minusX() {
        return previewX() + PREVIEW_SIZE + 16;
    }

    private int plusX() {
        return minusX() + 28;
    }

    private int importX() {
        return plusX() + 34;
    }

    private boolean clickButton(int x, int y, int width, int height, double mouseX, double mouseY, Runnable action) {
        if (!isInside(x, y, width, height, mouseX, mouseY)) {
            return false;
        }
        action.run();
        return true;
    }

    private void drawButton(DrawContext context, int x, int y, int width, int height, String label, boolean enabled) {
        context.fill(x, y, x + width, y + height, enabled ? 0xFF2F4E61 : 0xFF303038);
        context.fill(x, y, x + width, y + 1, enabled ? 0xFF88B8D8 : 0xFF666670);
        context.fill(x, y + height - 1, x + width, y + height, 0xFF101018);
        int textX = x + (width - textRenderer.getWidth(label)) / 2;
        context.drawText(textRenderer, Text.literal(label), textX, y + 5, enabled ? 0xFFFFFFFF : 0xFF999999, false);
    }

    private void drawBorder(DrawContext context, int x, int y, int width, int height) {
        context.fill(x - 1, y - 1, x + width + 1, y, 0xFFB8B8C2);
        context.fill(x - 1, y + height, x + width + 1, y + height + 1, 0xFF111116);
        context.fill(x - 1, y - 1, x, y + height + 1, 0xFFB8B8C2);
        context.fill(x + width, y - 1, x + width + 1, y + height + 1, 0xFF111116);
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

    private static int[] fitSize(int width, int height, int maxWidth, int maxHeight) {
        double ratio = Math.min(maxWidth / (double) width, maxHeight / (double) height);
        return new int[]{Math.max(1, (int) Math.round(width * ratio)), Math.max(1, (int) Math.round(height * ratio))};
    }

    private static boolean isInside(int x, int y, int width, int height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static boolean isImageFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".bmp");
    }

    private static String formatScale(double scale) {
        return String.format(Locale.ROOT, "%.2f", scale);
    }

    private void closePreviewTexture() {
        if (previewTexture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(PREVIEW_TEXTURE_ID);
            previewTexture = null;
            previewEntry = null;
        }
    }

    @Override
    public void removed() {
        closePreviewTexture();
    }

    private record ImageEntry(Path path, String name, int width, int height) {
    }
}
