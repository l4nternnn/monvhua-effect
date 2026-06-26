package com.kuilunfuzhe.monvhua.features.textarea;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.item.config.AreaTipConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TextGroupRenderer {
    private static final Map<String, CachedTexture> TEXTURES = new HashMap<>();

    private TextGroupRenderer() {
    }

    public static void renderGroups(DrawContext context, List<AreaTipConfig.GroupConfig> groups, boolean editing, String selectedId) {
        for (AreaTipConfig.GroupConfig group : groups) {
            if (group.hudVisible || editing) {
                renderGroup(context, group, editing, group.id.equals(selectedId));
            }
        }
    }

    public static void renderGroup(DrawContext context, AreaTipConfig.GroupConfig group, boolean editing, boolean selected) {
        renderGroup(context, group, editing, selected, -1L);
    }

    public static void renderGroup(DrawContext context, AreaTipConfig.GroupConfig group, boolean editing, boolean selected, long elapsedTicks) {
        renderGroup(context, group, editing, selected ? 0 : -1, elapsedTicks);
    }

    public static void renderGroup(DrawContext context, AreaTipConfig.GroupConfig group, boolean editing, int selectedEntryIndex, long elapsedTicks) {
        List<AreaTipConfig.HudTextEntry> entries = group.hudTexts.stream()
                .sorted(Comparator.comparingInt(entry -> entry.priority))
                .toList();
        for (AreaTipConfig.HudTextEntry entry : entries) {
            renderEntry(context, entry, editing, group.hudTexts.indexOf(entry) == selectedEntryIndex, elapsedTicks);
        }
    }

    public static void renderEntry(DrawContext context, AreaTipConfig.HudTextEntry entry, boolean editing, boolean selected) {
        renderEntry(context, entry, editing, selected, -1L);
    }

    public static void renderEntry(DrawContext context, AreaTipConfig.HudTextEntry entry, boolean editing, boolean selected, long elapsedTicks) {
        float alpha = editing ? 1.0F : alphaFor(entry, elapsedTicks);
        if (!editing && alpha <= 0.0F) {
            return;
        }
        Matrix3x2fStack matrices = context.getMatrices();
        float pivotX = entry.width * 0.5F;
        float pivotY = entry.height * 0.5F;
        matrices.pushMatrix();
        matrices.translate(entry.x, entry.y);
        matrices.translate(pivotX * entry.scale, pivotY * entry.scale);
        matrices.rotate((float) Math.toRadians(entry.rotation));
        matrices.scale(entry.scale, entry.scale);
        matrices.translate(-pivotX, -pivotY);

        if (!entry.background.isBlank()) {
            Identifier texture = textureFor(entry.background);
            if (texture != null) {
                context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0.0F, 0.0F,
                        entry.width, entry.height, entry.width, entry.height, withAlpha(0xFFFFFFFF, alpha));
            } else if (editing) {
                context.fill(0, 0, entry.width, entry.height, withAlpha(0x44202028, alpha));
            }
        }

        if (editing) {
            int color = selected ? 0xCC5AB7FF : 0x66FFFFFF;
            context.drawBorder(0, 0, entry.width, entry.height, color);
            context.fill(entry.width - 10, entry.height - 10, entry.width, entry.height, 0xDD3B82F6);
            context.fill(entry.width - 10, 0, entry.width, 10, 0xCCA855F7);
        }

        drawText(context, MinecraftClient.getInstance().textRenderer, entry, alpha);
        matrices.popMatrix();
    }

    public static void cleanupTextures() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            for (CachedTexture cached : TEXTURES.values()) {
                client.getTextureManager().destroyTexture(cached.id());
            }
            TEXTURES.clear();
        });
    }

    private static void drawText(DrawContext context, TextRenderer textRenderer, AreaTipConfig.HudTextEntry entry, float alpha) {
        if (alpha <= 0.0F) {
            return;
        }
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(entry.offsetX, entry.offsetY);
        matrices.scale(entry.fontSize, entry.fontSize);
        int wrapWidth = Math.max(8, Math.round(entry.wrapWidth / entry.fontSize));
        Text text = Text.literal(entry.text).setStyle(Style.EMPTY.withFont(fontId(entry.font)));
        List<OrderedText> lines = textRenderer.wrapLines(text, wrapWidth);
        int color = withAlpha(entry.color, alpha);
        int y = 0;
        for (OrderedText line : lines) {
            int x = alignedX(textRenderer, line, wrapWidth, entry.align);
            context.drawText(textRenderer, line, x, y, color, true);
            y += textRenderer.fontHeight + 2;
        }
        matrices.popMatrix();
    }

    private static float alphaFor(AreaTipConfig.HudTextEntry entry, long elapsedTicks) {
        if (elapsedTicks < 0L) {
            return 1.0F;
        }
        if (entry.delayTicks > 0 && elapsedTicks < entry.delayTicks) {
            return Math.clamp(elapsedTicks / (float) entry.delayTicks, 0.0F, 1.0F);
        }
        long local = elapsedTicks - entry.delayTicks;
        if (local < entry.displayTicks) {
            return 1.0F;
        }
        long fadeOutTick = local - entry.displayTicks;
        if (entry.fadeTicks <= 0 || fadeOutTick >= entry.fadeTicks) {
            return 0.0F;
        }
        return Math.clamp(1.0F - fadeOutTick / (float) entry.fadeTicks, 0.0F, 1.0F);
    }

    private static int withAlpha(int argb, float alpha) {
        int baseAlpha = (argb >>> 24) & 0xFF;
        int nextAlpha = Math.clamp(Math.round(baseAlpha * alpha), 0, 255);
        return (argb & 0x00FFFFFF) | (nextAlpha << 24);
    }

    private static int alignedX(TextRenderer textRenderer, OrderedText line, int wrapWidth, String align) {
        int width = textRenderer.getWidth(line);
        if ("center".equals(align)) {
            return Math.max(0, (wrapWidth - width) / 2);
        }
        if ("right".equals(align)) {
            return Math.max(0, wrapWidth - width);
        }
        return 0;
    }

    private static Identifier fontId(String value) {
        Identifier parsed = Identifier.tryParse(normalizeFontId(value));
        return parsed == null ? Identifier.of("minecraft", "default") : parsed;
    }

    private static String normalizeFontId(String value) {
        if (value == null || value.isBlank()) {
            return "minecraft:default";
        }
        if (value.indexOf(':') >= 0) {
            return value;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "source han sans cn medium", "思源黑体 cn medium" -> "modernui:source-han-sans-cn-medium";
            case "inter frozen medium" -> "modernui:inter-frozen-medium";
            case "jetbrains mono medium" -> "modernui:jetbrains-mono-medium";
            case "mui-i18n-compat" -> "modernui:mui-i18n-compat";
            default -> "modernui:" + value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-").replaceAll("(^-+|-+$)", "");
        };
    }

    public static Path textureDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("monvhua/textures");
    }

    public static Optional<ImageSize> imageSize(String relativePath) {
        Path bundled = bundledTextUiPath(relativePath);
        if (bundled != null && Files.isRegularFile(bundled)) {
            try {
                NativeImage image = readNativeImage(bundled);
                ImageSize size = new ImageSize(image.getWidth(), image.getHeight());
                image.close();
                return Optional.of(size);
            } catch (IOException ignored) {
            }
        }
        Path root = textureDir().normalize();
        Path path = root.resolve(relativePath).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            NativeImage image = readNativeImage(path);
            ImageSize size = new ImageSize(image.getWidth(), image.getHeight());
            image.close();
            return Optional.of(size);
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    private static Identifier textureFor(String relativePath) {
        CachedTexture cached = TEXTURES.get(relativePath);
        if (cached != null) {
            return cached.id();
        }
        Identifier bundled = bundledTextureFor(relativePath);
        if (bundled != null) {
            return bundled;
        }
        Path root = textureDir().normalize();
        Path path = root.resolve(relativePath).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            return null;
        }
        try {
            NativeImage image = readNativeImage(path);
            Identifier id = Identifier.of(MonvhuaMod.MOD_ID, "dynamic/text_area/" + safeId(relativePath));
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "monvhua text area " + relativePath, image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
            TEXTURES.put(relativePath, new CachedTexture(id, texture));
            return id;
        } catch (IOException ignored) {
            return null;
        }
    }

    private static String safeId(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
    }

    private static Identifier bundledTextureFor(String relativePath) {
        Path path = bundledTextUiPath(relativePath);
        if (path == null || !Files.isRegularFile(path)) {
            return null;
        }
        String cacheKey = "builtin:" + relativePath;
        CachedTexture cached = TEXTURES.get(cacheKey);
        if (cached != null) {
            return cached.id();
        }
        try {
            NativeImage image = readNativeImage(path);
            Identifier id = Identifier.of(MonvhuaMod.MOD_ID, "dynamic/text_area_builtin/" + safeId(relativePath));
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "monvhua text area builtin " + relativePath, image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
            TEXTURES.put(cacheKey, new CachedTexture(id, texture));
            return id;
        } catch (IOException ignored) {
            return null;
        }
    }

    public static Path bundledTextUiDir() {
        return FabricLoader.getInstance().getModContainer(MonvhuaMod.MOD_ID)
                .flatMap(container -> container.findPath("assets/monvhua/textures/gui/text_ui"))
                .orElse(null);
    }

    private static Path bundledTextUiPath(String relativePath) {
        if (!relativePath.startsWith("gui/text_ui/")) {
            return null;
        }
        Path root = bundledTextUiDir();
        if (root == null) {
            return null;
        }
        Path path = root.resolve(relativePath.substring("gui/text_ui/".length())).normalize();
        return path.startsWith(root.normalize()) ? path : null;
    }

    public static NativeImage readNativeImage(Path path) throws IOException {
        try (InputStream stream = Files.newInputStream(path)) {
            return NativeImage.read(stream);
        } catch (IOException nativeError) {
            BufferedImage buffered = ImageIO.read(path.toFile());
            if (buffered == null) {
                throw nativeError;
            }
            NativeImage image = new NativeImage(buffered.getWidth(), buffered.getHeight(), false);
            for (int y = 0; y < buffered.getHeight(); y++) {
                for (int x = 0; x < buffered.getWidth(); x++) {
                    image.setColorArgb(x, y, buffered.getRGB(x, y));
                }
            }
            return image;
        }
    }

    private record CachedTexture(Identifier id, NativeImageBackedTexture texture) {
    }

    public record ImageSize(int width, int height) {
    }
}
