package com.kuilunfuzhe.monvhua.features.activity.emotion;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.features.activity.EmotionCatalog;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class EmotionTextureManager {
    private static final int TEXTURE_WIDTH = 512;
    private static final int TEXTURE_HEIGHT = 256;
    public static final long IMAGE_EXIT_HOLD_TICKS = 40L;
    private static final long GIF_EXIT_FALLBACK_TICKS = 60L;
    private static final long IDLE_ANIMATION_RELEASE_MILLIS = 5_000L;
    private static final Map<Integer, TextureSlot> SLOTS = new HashMap<>();
    private static final ExecutorService DECODE_EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "monvhua-emotion-decode");
        thread.setDaemon(true);
        return thread;
    });
    private static int generation;
    private static boolean initialized;

    private EmotionTextureManager() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    private static final Identifier ID = Identifier.of(MonvhuaMod.MOD_ID, "emotion_textures");

                    @Override
                    public Identifier getFabricId() {
                        return ID;
                    }

                    @Override
                    public void reload(ResourceManager manager) {
                        clear();
                    }
                }
        );
    }

    public static Identifier textureFor(int contentId, boolean animate, long timeMillis) {
        EmotionCatalog.Entry entry = EmotionCatalog.byId(contentId);
        if (entry == null) {
            return null;
        }
        if (entry.type() == EmotionCatalog.Type.PROCEDURAL || entry.type() == EmotionCatalog.Type.BLOCK_DISPLAY) {
            return null;
        }
        TextureSlot slot = SLOTS.computeIfAbsent(contentId, ignored -> new TextureSlot(entry));
        slot.touch(timeMillis);
        slot.requestThumbnail();
        if (animate && entry.type() == EmotionCatalog.Type.GIF) {
            slot.requestAnimation();
        }
        if (animate && entry.type() == EmotionCatalog.Type.GIF) {
            slot.update(timeMillis);
        }
        return slot.texture == null ? null : slot.textureId;
    }

    public static Identifier textureForPreview(int contentId, boolean animate, long timeMillis) {
        EmotionCatalog.Entry entry = EmotionCatalog.byId(contentId);
        if (entry == null) {
            return null;
        }
        if (entry.type() == EmotionCatalog.Type.PROCEDURAL || entry.type() == EmotionCatalog.Type.BLOCK_DISPLAY) {
            return null;
        }
        if (animate && entry.type() == EmotionCatalog.Type.GIF) {
            return textureFor(contentId, true, timeMillis);
        }
        TextureSlot slot = SLOTS.computeIfAbsent(contentId, ignored -> new TextureSlot(entry));
        slot.touch(timeMillis);
        slot.requestThumbnail();
        return slot.previewTexture == null ? null : slot.previewTextureId;
    }

    public static PreviewRegion previewRegion(int contentId) {
        TextureSlot slot = SLOTS.get(contentId);
        if (slot == null || slot.sourceWidth <= 0 || slot.sourceHeight <= 0) {
            return null;
        }
        double scale = Math.min(
                (double) TEXTURE_WIDTH / slot.sourceWidth,
                (double) TEXTURE_HEIGHT / slot.sourceHeight
        );
        int width = Math.max(1, (int) Math.round(slot.sourceWidth * scale));
        int height = Math.max(1, (int) Math.round(slot.sourceHeight * scale));
        return new PreviewRegion(
                (TEXTURE_WIDTH - width) / 2,
                (TEXTURE_HEIGHT - height) / 2,
                width,
                height
        );
    }

    public static boolean hasPlayedLoops(int contentId, long elapsedMillis, int loops) {
        EmotionCatalog.Entry entry = EmotionCatalog.byId(contentId);
        if (entry == null || entry.type() != EmotionCatalog.Type.GIF) {
            return true;
        }
        TextureSlot slot = SLOTS.get(contentId);
        if (slot == null || slot.animation == null) {
            return elapsedMillis >= GIF_EXIT_FALLBACK_TICKS * 50L;
        }
        return elapsedMillis >= (long) slot.animation.totalDuration() * Math.max(1, loops);
    }

    public static void requestThumbnail(int contentId) {
        EmotionCatalog.Entry entry = EmotionCatalog.byId(contentId);
        if (entry != null && entry.type() != EmotionCatalog.Type.PROCEDURAL
                && entry.type() != EmotionCatalog.Type.BLOCK_DISPLAY) {
            SLOTS.computeIfAbsent(contentId, ignored -> new TextureSlot(entry)).requestThumbnail();
        }
    }

    public static void trimInactive(long nowMillis) {
        for (TextureSlot slot : SLOTS.values()) {
            if (slot.animation != null
                    && !slot.animationLoading
                    && nowMillis - slot.lastUseMillis > IDLE_ANIMATION_RELEASE_MILLIS) {
                slot.releaseAnimation();
            }
        }
    }

    public static boolean isReady(int contentId) {
        TextureSlot slot = SLOTS.get(contentId);
        return slot != null && slot.texture != null;
    }

    public static void clear() {
        generation++;
        MinecraftClient client = MinecraftClient.getInstance();
        for (TextureSlot slot : SLOTS.values()) {
            if (slot.texture != null) {
                client.getTextureManager().destroyTexture(slot.textureId);
            }
            if (slot.previewTexture != null) {
                client.getTextureManager().destroyTexture(slot.previewTextureId);
            }
            slot.closeFrames();
        }
        SLOTS.clear();
    }

    private static Path assetPath(EmotionCatalog.Entry entry) throws IOException {
        Optional<Path> root = FabricLoader.getInstance().getModContainer(MonvhuaMod.MOD_ID)
                .flatMap(container -> container.findPath("assets/monvhua/textures/emotion"));
        if (root.isEmpty()) {
            throw new IOException("Bundled emotion directory is unavailable");
        }
        Path normalizedRoot = root.get().normalize();
        Path path = normalizedRoot.resolve(entry.file()).normalize();
        if (!path.startsWith(normalizedRoot) || !Files.isRegularFile(path)) {
            throw new IOException("Missing bundled emotion: " + entry.file());
        }
        return path;
    }

    private static DecodedThumbnail decodeThumbnail(EmotionCatalog.Entry entry) throws IOException {
        Path path = assetPath(entry);
        if (entry.type() == EmotionCatalog.Type.GIF) {
            try (InputStream stream = Files.newInputStream(path);
                 ImageInputStream input = ImageIO.createImageInputStream(stream)) {
                ImageReader reader = gifReader();
                try {
                    reader.setInput(input, false);
                    BufferedImage frame = reader.read(0);
                    GifFrameMeta meta = gifFrameMeta(reader.getImageMetadata(0));
                    int[] size = gifCanvasSize(reader, frame);
                    BufferedImage canvas = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = canvas.createGraphics();
                    graphics.drawImage(frame, meta.left(), meta.top(), null);
                    graphics.dispose();
                    return new DecodedThumbnail(
                            nativeImageFromBuffered(fitToCanvas(canvas)),
                            size[0],
                            size[1]
                    );
                } finally {
                    reader.dispose();
                }
            }
        }
        try (InputStream stream = Files.newInputStream(path)) {
            BufferedImage image = ImageIO.read(stream);
            if (image == null) {
                throw new IOException("Unsupported emotion image: " + entry.file());
            }
            return new DecodedThumbnail(
                    nativeImageFromBuffered(fitToCanvas(image)),
                    image.getWidth(),
                    image.getHeight()
            );
        }
    }

    private static AnimatedData decodeAnimation(EmotionCatalog.Entry entry) throws IOException {
        Path path = assetPath(entry);
        try (InputStream stream = Files.newInputStream(path);
             ImageInputStream input = ImageIO.createImageInputStream(stream)) {
            ImageReader reader = gifReader();
            try {
                reader.setInput(input, false);
                int frameCount = reader.getNumImages(true);
                if (frameCount <= 0) {
                    throw new IOException("GIF contains no frames: " + entry.file());
                }
                BufferedImage first = reader.read(0);
                int[] size = gifCanvasSize(reader, first);
                BufferedImage canvas = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_ARGB);
                List<Frame> frames = new ArrayList<>(frameCount);
                int totalDuration = 0;
                for (int index = 0; index < frameCount; index++) {
                    BufferedImage frame = index == 0 ? first : reader.read(index);
                    GifFrameMeta meta = gifFrameMeta(reader.getImageMetadata(index));
                    BufferedImage previous = "restoreToPrevious".equals(meta.disposalMethod())
                            ? copyBuffered(canvas)
                            : null;
                    Graphics2D graphics = canvas.createGraphics();
                    graphics.setComposite(AlphaComposite.SrcOver);
                    graphics.drawImage(frame, meta.left(), meta.top(), null);
                    graphics.dispose();

                    int delay = Math.max(20, meta.delayMillis());
                    frames.add(new Frame(nativeImageFromBuffered(fitToCanvas(canvas)), delay));
                    totalDuration += delay;

                    if ("restoreToBackgroundColor".equals(meta.disposalMethod())) {
                        Graphics2D clear = canvas.createGraphics();
                        clear.setComposite(AlphaComposite.Clear);
                        clear.fillRect(meta.left(), meta.top(), frame.getWidth(), frame.getHeight());
                        clear.dispose();
                    } else if (previous != null) {
                        canvas = previous;
                    }
                }
                return new AnimatedData(
                        List.copyOf(frames),
                        Math.max(20, totalDuration),
                        size[0],
                        size[1]
                );
            } finally {
                reader.dispose();
            }
        }
    }

    private static ImageReader gifReader() throws IOException {
        var readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) {
            throw new IOException("No GIF ImageIO reader is available");
        }
        return readers.next();
    }

    private static int[] gifCanvasSize(ImageReader reader, BufferedImage firstFrame) {
        try {
            Node root = reader.getStreamMetadata().getAsTree("javax_imageio_gif_stream_1.0");
            Node screen = child(root, "LogicalScreenDescriptor");
            return new int[]{
                    Math.max(1, intAttribute(screen, "logicalScreenWidth", firstFrame.getWidth())),
                    Math.max(1, intAttribute(screen, "logicalScreenHeight", firstFrame.getHeight()))
            };
        } catch (Exception ignored) {
            return new int[]{firstFrame.getWidth(), firstFrame.getHeight()};
        }
    }

    private static GifFrameMeta gifFrameMeta(IIOMetadata metadata) {
        try {
            Node root = metadata.getAsTree("javax_imageio_gif_image_1.0");
            Node image = child(root, "ImageDescriptor");
            Node control = child(root, "GraphicControlExtension");
            return new GifFrameMeta(
                    intAttribute(image, "imageLeftPosition", 0),
                    intAttribute(image, "imageTopPosition", 0),
                    intAttribute(control, "delayTime", 10) * 10,
                    stringAttribute(control, "disposalMethod", "none")
            );
        } catch (Exception ignored) {
            return new GifFrameMeta(0, 0, 100, "none");
        }
    }

    private static Node child(Node root, String name) {
        if (root == null) {
            return null;
        }
        for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (name.equals(node.getNodeName())) {
                return node;
            }
        }
        return null;
    }

    private static int intAttribute(Node node, String name, int fallback) {
        try {
            return Integer.parseInt(stringAttribute(node, name, Integer.toString(fallback)));
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

    private static BufferedImage fitToCanvas(BufferedImage source) {
        BufferedImage target = new BufferedImage(TEXTURE_WIDTH, TEXTURE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        double scale = Math.min((double) TEXTURE_WIDTH / source.getWidth(), (double) TEXTURE_HEIGHT / source.getHeight());
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        int x = (TEXTURE_WIDTH - width) / 2;
        int y = (TEXTURE_HEIGHT - height) / 2;
        Graphics2D graphics = target.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(source, x, y, width, height, null);
        graphics.dispose();
        return target;
    }

    private static BufferedImage copyBuffered(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = copy.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return copy;
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

    private static final class TextureSlot {
        private final EmotionCatalog.Entry entry;
        private final Identifier textureId;
        private final Identifier previewTextureId;
        private NativeImageBackedTexture texture;
        private NativeImageBackedTexture previewTexture;
        private AnimatedData animation;
        private boolean thumbnailLoading;
        private boolean animationLoading;
        private int lastFrame = -1;
        private long lastUpdatedMillis = Long.MIN_VALUE;
        private long lastUseMillis;
        private int sourceWidth = TEXTURE_WIDTH;
        private int sourceHeight = TEXTURE_HEIGHT;

        private TextureSlot(EmotionCatalog.Entry entry) {
            this.entry = entry;
            this.textureId = Identifier.of(MonvhuaMod.MOD_ID, "dynamic/emotion/" + entry.id());
            this.previewTextureId = Identifier.of(MonvhuaMod.MOD_ID, "dynamic/emotion_preview/" + entry.id());
        }

        private void requestThumbnail() {
            if (texture != null || thumbnailLoading) {
                return;
            }
            thumbnailLoading = true;
            int requestedGeneration = generation;
            CompletableFuture.supplyAsync(() -> {
                try {
                    return decodeThumbnail(entry);
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            }, DECODE_EXECUTOR).whenComplete((decoded, error) -> MinecraftClient.getInstance().execute(() -> {
                thumbnailLoading = false;
                if (error != null) {
                    MonvhuaMod.LOGGER.warn("Failed to load emotion thumbnail {}", entry.file(), error);
                    return;
                }
                if (requestedGeneration != generation || SLOTS.get(entry.id()) != this || texture != null) {
                    decoded.image().close();
                    return;
                }
                sourceWidth = decoded.sourceWidth();
                sourceHeight = decoded.sourceHeight();
                NativeImage previewImage = copyNativeImage(decoded.image());
                texture = new NativeImageBackedTexture(() -> "monvhua emotion " + entry.id(), decoded.image());
                previewTexture = new NativeImageBackedTexture(
                        () -> "monvhua emotion preview " + entry.id(),
                        previewImage
                );
                MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, texture);
                MinecraftClient.getInstance().getTextureManager().registerTexture(previewTextureId, previewTexture);
            }));
        }

        private void touch(long timeMillis) {
            lastUseMillis = timeMillis;
        }

        private void requestAnimation() {
            if (animation != null || animationLoading) {
                return;
            }
            animationLoading = true;
            int requestedGeneration = generation;
            CompletableFuture.supplyAsync(() -> {
                try {
                    return decodeAnimation(entry);
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            }, DECODE_EXECUTOR).whenComplete((data, error) -> MinecraftClient.getInstance().execute(() -> {
                animationLoading = false;
                if (error != null) {
                    MonvhuaMod.LOGGER.warn("Failed to decode emotion GIF {}", entry.file(), error);
                    return;
                }
                if (requestedGeneration != generation || SLOTS.get(entry.id()) != this) {
                    data.close();
                    return;
                }
                closeFrames();
                animation = data;
                sourceWidth = data.sourceWidth();
                sourceHeight = data.sourceHeight();
                lastFrame = -1;
                if (texture == null) {
                    texture = new NativeImageBackedTexture(
                            () -> "monvhua emotion " + entry.id(),
                            copyNativeImage(data.frames().get(0).image())
                    );
                    MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, texture);
                }
                if (previewTexture == null) {
                    previewTexture = new NativeImageBackedTexture(
                            () -> "monvhua emotion preview " + entry.id(),
                            copyNativeImage(data.frames().get(0).image())
                    );
                    MinecraftClient.getInstance().getTextureManager().registerTexture(previewTextureId, previewTexture);
                }
            }));
        }

        private void update(long timeMillis) {
            if (texture == null || animation == null) {
                return;
            }
            if (lastUpdatedMillis == timeMillis) {
                return;
            }
            lastUpdatedMillis = timeMillis;
            int position = Math.floorMod(timeMillis, animation.totalDuration());
            int accumulated = 0;
            int frameIndex = 0;
            for (int index = 0; index < animation.frames().size(); index++) {
                accumulated += animation.frames().get(index).delayMillis();
                if (position < accumulated) {
                    frameIndex = index;
                    break;
                }
            }
            if (frameIndex == lastFrame) {
                return;
            }
            texture.setImage(copyNativeImage(animation.frames().get(frameIndex).image()));
            texture.upload();
            lastFrame = frameIndex;
        }

        private void releaseAnimation() {
            closeFrames();
            lastFrame = -1;
            lastUpdatedMillis = Long.MIN_VALUE;
        }

        private void closeFrames() {
            if (animation != null) {
                animation.close();
                animation = null;
            }
        }
    }

    private record DecodedThumbnail(NativeImage image, int sourceWidth, int sourceHeight) {
    }

    public record PreviewRegion(int x, int y, int width, int height) {
    }

    private record AnimatedData(List<Frame> frames, int totalDuration, int sourceWidth, int sourceHeight) {
        private void close() {
            for (Frame frame : frames) {
                frame.image().close();
            }
        }
    }

    private record Frame(NativeImage image, int delayMillis) {
    }

    private record GifFrameMeta(int left, int top, int delayMillis, String disposalMethod) {
    }
}
