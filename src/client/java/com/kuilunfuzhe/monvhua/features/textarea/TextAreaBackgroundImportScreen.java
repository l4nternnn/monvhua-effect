package com.kuilunfuzhe.monvhua.features.textarea;

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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class TextAreaBackgroundImportScreen extends Screen {
    private static final int PANEL_WIDTH = 560;
    private static final int PANEL_HEIGHT = 270;
    private static final int LIST_WIDTH = 220;
    private static final int ROW_HEIGHT = 18;
    private static final int PREVIEW_SIZE = 190;
    private static final Map<Path, CachedImage> IMAGE_CACHE = new HashMap<>();

    private final TextGroupEditScreen parent;
    private final List<ImageEntry> images = new ArrayList<>();
    private int panelX;
    private int panelY;
    private int selectedIndex = -1;
    private int scroll;
    private String status = "";

    public TextAreaBackgroundImportScreen(TextGroupEditScreen parent) {
        super(Text.literal("导入文字区域背景"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_WIDTH) / 2;
        panelY = (height - PANEL_HEIGHT) / 2;
        reloadImages();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xE0101015);
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
                || clickButton(importX(), buttonY(), 64, 18, mouseX, mouseY, this::importSelected)
                || clickButton(cancelX(), buttonY(), 54, 18, mouseX, mouseY, this::closeToParent)) {
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
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void reloadImages() {
        images.clear();
        selectedIndex = -1;
        scroll = 0;
        status = "";
        Set<Path> scannedPaths = new HashSet<>();
        Path folder = TextGroupRenderer.textureDir().normalize();
        try {
            Files.createDirectories(folder);
            try (Stream<Path> stream = Files.list(folder)) {
                stream.filter(Files::isRegularFile)
                        .filter(TextAreaBackgroundImportScreen::isImageFile)
                        .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                        .forEach(path -> addImage(path.normalize(), scannedPaths));
            }
        } catch (IOException e) {
            status = e.getMessage();
        }
        addBundledImages();
        pruneCache(scannedPaths);
        if (!images.isEmpty()) {
            selectedIndex = 0;
        }
    }

    private void addImage(Path path, Set<Path> scannedPaths) {
        scannedPaths.add(path);
        try {
            CachedImage cached = cachedImage(path);
            if (cached != null) {
                images.add(new ImageEntry(path, path.getFileName().toString(), cached.width(), cached.height(), cached.textureId(),
                        path.getFileName().toString()));
            }
        } catch (IOException ignored) {
        }
    }

    private void addBundledImages() {
        Path root = TextGroupRenderer.bundledTextUiDir();
        if (root == null || !Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(TextAreaBackgroundImportScreen::isImageFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .forEach(path -> {
                        try {
                            CachedImage cached = cachedImage(path.normalize());
                            if (cached != null) {
                                String fileName = path.getFileName().toString();
                                images.add(new ImageEntry(path.normalize(), "[内置] " + fileName,
                                        cached.width(), cached.height(), cached.textureId(), "gui/text_ui/" + fileName));
                            }
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private void drawList(DrawContext context, int mouseX, int mouseY) {
        int x = listX();
        int y = listY();
        context.fill(x, y, x + LIST_WIDTH, y + listHeight(), 0x66000000);
        drawBorder(context, x, y, LIST_WIDTH, listHeight());
        if (images.isEmpty()) {
            context.drawText(textRenderer, Text.literal("将图片放入:"), x + 8, y + 8, 0xFFE6E6E6, false);
            context.drawText(textRenderer, Text.literal(TextGroupRenderer.textureDir().toString()), x + 8, y + 22, 0xFF9A9AA4, false);
            return;
        }
        for (int i = 0; i < visibleRows(); i++) {
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
            context.drawText(textRenderer, Text.literal(trimToWidth(entry.name(), LIST_WIDTH - 74)), x + 8, rowY + 4, 0xFFFFFFFF, false);
            context.drawText(textRenderer, Text.literal(entry.width() + "x" + entry.height()), x + LIST_WIDTH - 62, rowY + 4, 0xFFB8B8C2, false);
        }
    }

    private void drawPreview(DrawContext context) {
        int x = previewX();
        int y = previewY();
        context.fill(x, y, x + PREVIEW_SIZE, y + PREVIEW_SIZE, 0x66000000);
        drawBorder(context, x, y, PREVIEW_SIZE, PREVIEW_SIZE);
        ImageEntry entry = selectedEntry();
        if (entry == null) {
            context.drawText(textRenderer, Text.literal("选择图片"), x + 70, y + 90, 0xFFE6E6E6, false);
            return;
        }
        int[] size = fitSize(entry.width(), entry.height(), PREVIEW_SIZE - 12, PREVIEW_SIZE - 12);
        int drawX = x + (PREVIEW_SIZE - size[0]) / 2;
        int drawY = y + (PREVIEW_SIZE - size[1]) / 2;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, entry.textureId(), drawX, drawY, 0.0F, 0.0F,
                size[0], size[1], size[0], size[1]);
    }

    private void drawControls(DrawContext context) {
        ImageEntry entry = selectedEntry();
        int infoX = previewX() + PREVIEW_SIZE + 16;
        if (entry != null) {
            context.drawText(textRenderer, Text.literal("图片 " + entry.width() + "x" + entry.height()), infoX, previewY(), 0xFFB8B8C2, false);
        }
        drawButton(context, refreshX(), buttonY(), 58, 18, "刷新", true);
        drawButton(context, importX(), buttonY(), 64, 18, "导入", entry != null);
        drawButton(context, cancelX(), buttonY(), 54, 18, "返回", true);
        if (!status.isEmpty()) {
            context.drawText(textRenderer, Text.literal(status), infoX, buttonY() - 18, 0xFFE6E6E6, false);
        }
    }

    private boolean clickList(double mouseX, double mouseY) {
        if (!isInside(listX(), listY(), LIST_WIDTH, listHeight(), mouseX, mouseY)) {
            return false;
        }
        int row = ((int) mouseY - listY() - 4) / ROW_HEIGHT;
        int index = scroll + row;
        if (index >= 0 && index < images.size()) {
            selectedIndex = index;
        }
        return true;
    }

    private void importSelected() {
        ImageEntry entry = selectedEntry();
        if (entry == null) {
            return;
        }
        parent.applyImportedBackground(entry.background(), entry.width(), entry.height());
        closeToParent();
    }

    private void closeToParent() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> client.setScreen(parent));
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

    private int importX() {
        return previewX() + PREVIEW_SIZE + 16;
    }

    private int cancelX() {
        return importX() + 72;
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
        context.drawText(textRenderer, Text.literal(label), x + (width - textRenderer.getWidth(label)) / 2, y + 5,
                enabled ? 0xFFFFFFFF : 0xFF999999, false);
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
        String result = text;
        while (!result.isEmpty() && textRenderer.getWidth(result + "...") > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }

    private static int[] fitSize(int width, int height, int maxWidth, int maxHeight) {
        double ratio = Math.min(maxWidth / (double) width, maxHeight / (double) height);
        return new int[]{Math.max(1, (int) Math.round(width * ratio)), Math.max(1, (int) Math.round(height * ratio))};
    }

    private static boolean isInside(int x, int y, int width, int height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static boolean isImageFile(Path path) {
        return isImageName(path.getFileName().toString());
    }

    private static boolean isImageName(String name) {
        name = name.toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".bmp") || name.endsWith(".gif");
    }

    private static CachedImage cachedImage(Path path) throws IOException {
        long lastModified = Files.getLastModifiedTime(path).toMillis();
        long size = Files.size(path);
        CachedImage cached = IMAGE_CACHE.get(path);
        if (cached != null && cached.lastModified() == lastModified && cached.size() == size) {
            return cached;
        }
        if (cached != null) {
            destroyTexture(cached.textureId());
        }
        NativeImage image = readNativeImage(path);
        Identifier textureId = Identifier.of(MonvhuaMod.MOD_ID, "dynamic/text_area_import/" + Integer.toUnsignedString(path.toString().hashCode(), 16));
        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "monvhua text area import " + path.getFileName(), image);
        MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, texture));
        cached = new CachedImage(lastModified, size, image.getWidth(), image.getHeight(), textureId, texture);
        IMAGE_CACHE.put(path, cached);
        return cached;
    }

    private static NativeImage readNativeImage(Path path) throws IOException {
        return TextGroupRenderer.readNativeImage(path);
    }

    private static void pruneCache(Set<Path> activePaths) {
        List<Path> stale = new ArrayList<>();
        for (Path path : IMAGE_CACHE.keySet()) {
            if (!activePaths.contains(path)) {
                stale.add(path);
            }
        }
        MinecraftClient client = MinecraftClient.getInstance();
        for (Path path : stale) {
            CachedImage cached = IMAGE_CACHE.remove(path);
            if (cached != null) {
                destroyTexture(cached.textureId());
            }
        }
    }

    private static void destroyTexture(Identifier textureId) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> client.getTextureManager().destroyTexture(textureId));
    }

    private record ImageEntry(Path path, String name, int width, int height, Identifier textureId, String background) {
    }

    private record CachedImage(long lastModified, long size, int width, int height,
                               Identifier textureId, NativeImageBackedTexture texture) {
    }
}
