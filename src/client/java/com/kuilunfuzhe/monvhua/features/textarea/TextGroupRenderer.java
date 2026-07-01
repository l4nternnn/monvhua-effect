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
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Set;
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

    public static void renderGroup(DrawContext context, AreaTipConfig.GroupConfig group, boolean editing, boolean selected,
                                   long elapsedTicks, Set<String> entryIds) {
        renderGroup(context, group, editing, selected ? 0 : -1, elapsedTicks, entryIds);
    }

    public static void renderGroup(DrawContext context, AreaTipConfig.GroupConfig group, boolean editing, int selectedEntryIndex, long elapsedTicks) {
        renderGroup(context, group, editing, selectedEntryIndex, elapsedTicks, null);
    }

    public static void renderGroup(DrawContext context, AreaTipConfig.GroupConfig group, boolean editing, int selectedEntryIndex,
                                   long elapsedTicks, Set<String> entryIds) {
        List<AreaTipConfig.HudTextEntry> entries = group.hudTexts.stream()
                .sorted(Comparator.comparingInt(entry -> entry.priority))
                .toList();
        for (AreaTipConfig.HudTextEntry entry : entries) {
            if (entryIds != null && !entryIds.contains(entry.id)) {
                continue;
            }
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
                cached.close();
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
        Text text = styledText(entry, alpha);
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

    public static long playbackTicks(AreaTipConfig.HudTextEntry entry) {
        if (entry == null) {
            return 0L;
        }
        return Math.max(1L, (long) entry.delayTicks + entry.displayTicks + entry.fadeTicks);
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

    private static Text styledText(AreaTipConfig.HudTextEntry entry, float alpha) {
        String text = entry.text == null ? "" : entry.text;
        if (text.isEmpty() || entry.fontSpans == null || entry.fontSpans.isEmpty()) {
            return Text.literal(text).setStyle(Style.EMPTY.withFont(fontId(entry.font)));
        }
        MutableText root = Text.empty();
        int index = 0;
        int length = text.length();
        for (AreaTipConfig.FontSpan span : entry.fontSpans) {
            if (span == null) {
                continue;
            }
            int start = Math.clamp(Math.min(span.start, span.end), 0, length);
            int end = Math.clamp(Math.max(span.start, span.end), 0, length);
            if (end <= index) {
                continue;
            }
            if (start > index) {
                appendRun(root, text, index, start, entry.font, null, alpha);
            }
            int runStart = Math.max(start, index);
            appendRun(root, text, runStart, end, span.font == null ? entry.font : span.font, span.color, alpha);
            index = end;
        }
        if (index < length) {
            appendRun(root, text, index, length, entry.font, null, alpha);
        }
        return root;
    }

    private static void appendRun(MutableText root, String text, int start, int end, String font, Integer color, float alpha) {
        if (end <= start) {
            return;
        }
        Style style = Style.EMPTY.withFont(fontId(font));
        if (color != null) {
            style = style.withColor(TextColor.fromRgb(color & 0xFFFFFF));
        }
        root.append(Text.literal(text.substring(start, end)).setStyle(style));
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
            cached.update();
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
            Identifier id = Identifier.of(MonvhuaMod.MOD_ID, "dynamic/text_area/" + safeId(relativePath));
            CachedTexture texture = createTexture(id, "monvhua text area " + relativePath, path);
            TEXTURES.put(relativePath, texture);
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
            cached.update();
            return cached.id();
        }
        try {
            Identifier id = Identifier.of(MonvhuaMod.MOD_ID, "dynamic/text_area_builtin/" + safeId(relativePath));
            CachedTexture texture = createTexture(id, "monvhua text area builtin " + relativePath, path);
            TEXTURES.put(cacheKey, texture);
            return id;
        } catch (IOException ignored) {
            return null;
        }
    }

    private static CachedTexture createTexture(Identifier id, String name, Path path) throws IOException {
        if (isGif(path)) {
            GifImage gif = readGif(path);
            AnimatedCachedTexture cached = new AnimatedCachedTexture(id, gif.frames());
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, cached.texture());
            return cached;
        }
        NativeImage image = readNativeImage(path);
        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> name, image);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
        return new StaticCachedTexture(id, texture);
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
            BufferedImage buffered;
            try (InputStream stream = Files.newInputStream(path)) {
                buffered = ImageIO.read(stream);
            }
            if (buffered == null) {
                throw nativeError;
            }
            return nativeImageFromBuffered(buffered);
        }
    }

    private static GifImage readGif(Path path) throws IOException {
        try (InputStream stream = Files.newInputStream(path);
             ImageInputStream input = ImageIO.createImageInputStream(stream)) {
            ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
            try {
                reader.setInput(input, false);
                int frameCount = reader.getNumImages(true);
                if (frameCount <= 0) {
                    throw new IOException("GIF contains no frames");
                }
                int[] canvasSize = gifCanvasSize(reader, reader.read(0));
                BufferedImage canvas = new BufferedImage(canvasSize[0], canvasSize[1], BufferedImage.TYPE_INT_ARGB);
                List<GifFrame> frames = new ArrayList<>();
                for (int i = 0; i < frameCount; i++) {
                    BufferedImage frame = reader.read(i);
                    IIOMetadata metadata = reader.getImageMetadata(i);
                    GifFrameMeta meta = gifFrameMeta(metadata);
                    Graphics2D graphics = canvas.createGraphics();
                    graphics.setComposite(AlphaComposite.SrcOver);
                    graphics.drawImage(frame, meta.left(), meta.top(), null);
                    graphics.dispose();

                    frames.add(new GifFrame(nativeImageFromBuffered(canvas), Math.max(20, meta.delayMillis())));
                    if ("restoreToBackgroundColor".equals(meta.disposalMethod())) {
                        Graphics2D clear = canvas.createGraphics();
                        clear.setComposite(AlphaComposite.Clear);
                        clear.fillRect(meta.left(), meta.top(), frame.getWidth(), frame.getHeight());
                        clear.dispose();
                    }
                }
                return new GifImage(frames);
            } finally {
                reader.dispose();
            }
        }
    }

    private static int[] gifCanvasSize(ImageReader reader, BufferedImage firstFrame) {
        try {
            Node root = reader.getStreamMetadata().getAsTree("javax_imageio_gif_stream_1.0");
            Node screen = child(root, "LogicalScreenDescriptor");
            int width = intAttribute(screen, "logicalScreenWidth", firstFrame.getWidth());
            int height = intAttribute(screen, "logicalScreenHeight", firstFrame.getHeight());
            return new int[]{Math.max(1, width), Math.max(1, height)};
        } catch (Exception ignored) {
            return new int[]{firstFrame.getWidth(), firstFrame.getHeight()};
        }
    }

    private static GifFrameMeta gifFrameMeta(IIOMetadata metadata) {
        try {
            Node root = metadata.getAsTree("javax_imageio_gif_image_1.0");
            Node image = child(root, "ImageDescriptor");
            Node control = child(root, "GraphicControlExtension");
            int left = intAttribute(image, "imageLeftPosition", 0);
            int top = intAttribute(image, "imageTopPosition", 0);
            int delay = intAttribute(control, "delayTime", 10) * 10;
            String disposal = stringAttribute(control, "disposalMethod", "none");
            return new GifFrameMeta(left, top, delay, disposal);
        } catch (Exception ignored) {
            return new GifFrameMeta(0, 0, 100, "none");
        }
    }

    private static Node child(Node root, String name) {
        if (root == null) {
            return null;
        }
        for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (name.equals(child.getNodeName())) {
                return child;
            }
        }
        return null;
    }

    private static int intAttribute(Node node, String name, int fallback) {
        String value = stringAttribute(node, name, null);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String stringAttribute(Node node, String name, String fallback) {
        if (node == null || node.getAttributes() == null || node.getAttributes().getNamedItem(name) == null) {
            return fallback;
        }
        return node.getAttributes().getNamedItem(name).getNodeValue();
    }

    private static NativeImage nativeImageFromBuffered(BufferedImage buffered) {
        NativeImage image = new NativeImage(buffered.getWidth(), buffered.getHeight(), false);
        for (int y = 0; y < buffered.getHeight(); y++) {
            for (int x = 0; x < buffered.getWidth(); x++) {
                image.setColorArgb(x, y, buffered.getRGB(x, y));
            }
        }
        return image;
    }

    private static NativeImage copyNativeImage(NativeImage source) {
        NativeImage copy = new NativeImage(source.getWidth(), source.getHeight(), false);
        copy.copyFrom(source);
        return copy;
    }

    private static boolean isGif(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".gif");
    }

    private interface CachedTexture {
        Identifier id();

        default void update() {
        }

        void close();
    }

    private record StaticCachedTexture(Identifier id, NativeImageBackedTexture texture) implements CachedTexture {
        @Override
        public void close() {
        }
    }

    private static final class AnimatedCachedTexture implements CachedTexture {
        private final Identifier id;
        private final NativeImageBackedTexture texture;
        private final List<GifFrame> frames;
        private final int totalDuration;
        private int lastFrame = -1;

        AnimatedCachedTexture(Identifier id, List<GifFrame> frames) {
            this.id = id;
            this.frames = frames;
            this.texture = new NativeImageBackedTexture(() -> "monvhua text area gif " + id, copyNativeImage(frames.get(0).image()));
            int duration = 0;
            for (GifFrame frame : frames) {
                duration += frame.delayMillis();
            }
            this.totalDuration = Math.max(20, duration);
        }

        @Override
        public Identifier id() {
            return id;
        }

        @Override
        public void update() {
            updateFrame();
        }

        NativeImageBackedTexture texture() {
            return texture;
        }

        private void updateFrame() {
            int tick = Math.floorMod((int) Util.getMeasuringTimeMs(), totalDuration);
            int accumulated = 0;
            int frameIndex = 0;
            for (int i = 0; i < frames.size(); i++) {
                accumulated += frames.get(i).delayMillis();
                if (tick < accumulated) {
                    frameIndex = i;
                    break;
                }
            }
            if (frameIndex == lastFrame) {
                return;
            }
            texture.setImage(copyNativeImage(frames.get(frameIndex).image()));
            texture.upload();
            lastFrame = frameIndex;
        }

        @Override
        public void close() {
            for (GifFrame frame : frames) {
                frame.image().close();
            }
        }
    }

    private record GifImage(List<GifFrame> frames) {
    }

    private record GifFrame(NativeImage image, int delayMillis) {
    }

    private record GifFrameMeta(int left, int top, int delayMillis, String disposalMethod) {
    }

    public record ImageSize(int width, int height) {
    }
}
