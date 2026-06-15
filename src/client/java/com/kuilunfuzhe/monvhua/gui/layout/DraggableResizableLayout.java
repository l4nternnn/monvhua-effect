package com.kuilunfuzhe.monvhua.gui.layout;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DraggableResizableLayout {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("monvhua_ui_layouts.json");
    private static final Type CONFIG_TYPE = new TypeToken<Map<String, Map<String, SavedBounds>>>() {}.getType();
    private static final int RESIZE_HANDLE = 10;
    private static final int ROTATE_HANDLE = 10;
    private static final int DRAG_HANDLE_HEIGHT = 16;
    private static Map<String, Map<String, SavedBounds>> configCache;

    private final String namespace;
    private final Map<String, Element> elements = new LinkedHashMap<>();
    private int screenWidth;
    private int screenHeight;
    private Active active;

    public DraggableResizableLayout(String namespace, int screenWidth, int screenHeight) {
        this.namespace = namespace;
        this.screenWidth = Math.max(1, screenWidth);
        this.screenHeight = Math.max(1, screenHeight);
        loadConfig();
    }

    public void setScreenSize(int screenWidth, int screenHeight) {
        this.screenWidth = Math.max(1, screenWidth);
        this.screenHeight = Math.max(1, screenHeight);
        for (Element element : elements.values()) {
            element.recalculate(this.screenWidth, this.screenHeight);
        }
    }

    public Bounds element(String id, int defaultX, int defaultY, int defaultWidth, int defaultHeight, int minWidth, int minHeight) {
        Element element = elements.get(id);
        if (element == null) {
            SavedBounds saved = namespaceConfig().get(id);
            element = new Element(id, saved != null ? saved : SavedBounds.fromPixels(defaultX, defaultY, defaultWidth, defaultHeight, 0.0F, screenWidth, screenHeight),
                    minWidth, minHeight);
            elements.put(id, element);
        }
        element.minWidth = minWidth;
        element.minHeight = minHeight;
        element.recalculate(screenWidth, screenHeight);
        return element.bounds.copy();
    }

    public Bounds bounds(String id) {
        Element element = elements.get(id);
        return element == null ? Bounds.EMPTY : element.bounds.copy();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        Element hit = null;
        Mode mode = Mode.NONE;
        for (Element element : elements.values()) {
            if (element.inRotateHandle(mouseX, mouseY)) {
                hit = element;
                mode = Mode.ROTATE_TOP_RIGHT;
            } else if (element.inResizeHandle(mouseX, mouseY)) {
                hit = element;
                mode = Mode.RESIZE_TOP_LEFT;
            } else if (element.inDragHandle(mouseX, mouseY)) {
                hit = element;
                mode = Mode.DRAG;
            }
        }
        if (hit == null) {
            return false;
        }
        active = new Active(hit, mode, mouseX, mouseY);
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (button != 0 || active == null) {
            return false;
        }
        active.update(mouseX, mouseY, screenWidth, screenHeight);
        return true;
    }

    public DragResult mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0 || active == null) {
            return DragResult.NONE;
        }
        Active finished = active;
        active = null;
        if (finished.moved) {
            persist(finished.element);
            saveConfig();
        }
        return new DragResult(finished.element.id, finished.moved);
    }

    public boolean isEditing() {
        return active != null;
    }

    public void resetToDefaults() {
        namespaceConfig().clear();
        saveConfig();
        elements.clear();
    }

    public void drawEditHandles(DrawContext context) {
        for (Element element : elements.values()) {
            Bounds b = element.bounds;
            context.fill(b.x, b.y, b.x + b.width, b.y + 1, 0x99D4A373);
            context.fill(b.x, b.y, b.x + 1, b.y + b.height, 0x99D4A373);
            context.fill(b.x, b.y, b.x + RESIZE_HANDLE, b.y + RESIZE_HANDLE, 0xCC3B82F6);
            context.fill(b.x + 2, b.y + 2, b.x + RESIZE_HANDLE - 2, b.y + RESIZE_HANDLE - 2, 0xCC110B1A);
            context.fill(b.x + b.width - ROTATE_HANDLE, b.y, b.x + b.width, b.y + ROTATE_HANDLE, 0xCCA855F7);
            context.fill(b.x + b.width - ROTATE_HANDLE + 2, b.y + 2, b.x + b.width - 2, b.y + ROTATE_HANDLE - 2, 0xCC110B1A);
        }
    }

    private void persist(Element element) {
        namespaceConfig().put(element.id, SavedBounds.fromPixels(element.bounds.x, element.bounds.y, element.bounds.width, element.bounds.height, element.bounds.rotationDegrees, screenWidth, screenHeight));
    }

    private Map<String, SavedBounds> namespaceConfig() {
        return configCache.computeIfAbsent(namespace, ignored -> new LinkedHashMap<>());
    }

    private static void loadConfig() {
        if (configCache != null) {
            return;
        }
        configCache = new LinkedHashMap<>();
        if (!Files.isRegularFile(CONFIG_PATH)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            Map<String, Map<String, SavedBounds>> loaded = GSON.fromJson(reader, CONFIG_TYPE);
            if (loaded != null) {
                configCache.putAll(loaded);
            }
        } catch (Exception ignored) {
            configCache.clear();
        }
    }

    private static void saveConfig() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(configCache, CONFIG_TYPE, writer);
            }
        } catch (IOException ignored) {
        }
    }

    public record DragResult(String id, boolean moved) {
        public static final DragResult NONE = new DragResult("", false);
    }

    public static final class Bounds {
        public static final Bounds EMPTY = new Bounds(0, 0, 0, 0);
        public final int x;
        public final int y;
        public final int width;
        public final int height;
        public final float rotationDegrees;

        public Bounds(int x, int y, int width, int height) {
            this(x, y, width, height, 0.0F);
        }

        public Bounds(int x, int y, int width, int height, float rotationDegrees) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.rotationDegrees = rotationDegrees;
        }

        public Bounds copy() {
            return new Bounds(x, y, width, height, rotationDegrees);
        }
    }

    private enum Mode {
        NONE,
        DRAG,
        RESIZE_TOP_LEFT,
        ROTATE_TOP_RIGHT
    }

    private static final class Element {
        private final String id;
        private final SavedBounds saved;
        private Bounds bounds = Bounds.EMPTY;
        private int minWidth;
        private int minHeight;

        private Element(String id, SavedBounds saved, int minWidth, int minHeight) {
            this.id = id;
            this.saved = saved;
            this.minWidth = minWidth;
            this.minHeight = minHeight;
        }

        private void recalculate(int screenWidth, int screenHeight) {
            int x = Math.round(saved.x * screenWidth);
            int y = Math.round(saved.y * screenHeight);
            int width = Math.max(minWidth, Math.round(saved.width * screenWidth));
            int height = Math.max(minHeight, Math.round(saved.height * screenHeight));
            bounds = clamp(new Bounds(x, y, width, height, saved.rotationDegrees), screenWidth, screenHeight, minWidth, minHeight);
        }

        private boolean inResizeHandle(double mouseX, double mouseY) {
            return mouseX >= bounds.x && mouseX < bounds.x + RESIZE_HANDLE
                    && mouseY >= bounds.y && mouseY < bounds.y + RESIZE_HANDLE;
        }

        private boolean inRotateHandle(double mouseX, double mouseY) {
            return mouseX >= bounds.x + bounds.width - ROTATE_HANDLE && mouseX < bounds.x + bounds.width
                    && mouseY >= bounds.y && mouseY < bounds.y + ROTATE_HANDLE;
        }

        private boolean inDragHandle(double mouseX, double mouseY) {
            return mouseX >= bounds.x && mouseX < bounds.x + bounds.width
                    && mouseY >= bounds.y && mouseY < bounds.y + Math.min(DRAG_HANDLE_HEIGHT, bounds.height);
        }

        private static Bounds clamp(Bounds bounds, int screenWidth, int screenHeight, int minWidth, int minHeight) {
            int width = Math.max(minWidth, Math.min(bounds.width, screenWidth));
            int height = Math.max(minHeight, Math.min(bounds.height, screenHeight));
            int x = MathHelper.clamp(bounds.x, 0, Math.max(0, screenWidth - width));
            int y = MathHelper.clamp(bounds.y, 0, Math.max(0, screenHeight - height));
            return new Bounds(x, y, width, height, bounds.rotationDegrees);
        }
    }

    private static final class Active {
        private final Element element;
        private final Mode mode;
        private final Bounds startBounds;
        private final double startMouseX;
        private final double startMouseY;
        private final double startAngle;
        private boolean moved;

        private Active(Element element, Mode mode, double startMouseX, double startMouseY) {
            this.element = element;
            this.mode = mode;
            this.startBounds = element.bounds.copy();
            this.startMouseX = startMouseX;
            this.startMouseY = startMouseY;
            this.startAngle = angleFromCenter(startMouseX, startMouseY, startBounds);
        }

        private void update(double mouseX, double mouseY, int screenWidth, int screenHeight) {
            int dx = (int) Math.round(mouseX - startMouseX);
            int dy = (int) Math.round(mouseY - startMouseY);
            if (Math.abs(dx) > 1 || Math.abs(dy) > 1) {
                moved = true;
            }
            if (mode == Mode.DRAG) {
                element.bounds = Element.clamp(new Bounds(startBounds.x + dx, startBounds.y + dy, startBounds.width, startBounds.height, startBounds.rotationDegrees),
                        screenWidth, screenHeight, element.minWidth, element.minHeight);
            } else if (mode == Mode.RESIZE_TOP_LEFT) {
                int right = startBounds.x + startBounds.width;
                int bottom = startBounds.y + startBounds.height;
                int nextX = MathHelper.clamp(startBounds.x + dx, 0, right - element.minWidth);
                int nextY = MathHelper.clamp(startBounds.y + dy, 0, bottom - element.minHeight);
                element.bounds = Element.clamp(new Bounds(nextX, nextY, right - nextX, bottom - nextY, startBounds.rotationDegrees),
                        screenWidth, screenHeight, element.minWidth, element.minHeight);
            } else if (mode == Mode.ROTATE_TOP_RIGHT) {
                double currentAngle = angleFromCenter(mouseX, mouseY, startBounds);
                float nextRotation = normalizeDegrees(startBounds.rotationDegrees + (float) Math.toDegrees(currentAngle - startAngle));
                element.bounds = new Bounds(startBounds.x, startBounds.y, startBounds.width, startBounds.height, nextRotation);
            }
        }

        private static double angleFromCenter(double mouseX, double mouseY, Bounds bounds) {
            double centerX = bounds.x + bounds.width * 0.5D;
            double centerY = bounds.y + bounds.height * 0.5D;
            return Math.atan2(mouseY - centerY, mouseX - centerX);
        }

        private static float normalizeDegrees(float degrees) {
            while (degrees <= -180.0F) degrees += 360.0F;
            while (degrees > 180.0F) degrees -= 360.0F;
            return degrees;
        }
    }

    private static final class SavedBounds {
        private float x;
        private float y;
        private float width;
        private float height;
        private float rotationDegrees;

        private static SavedBounds fromPixels(int x, int y, int width, int height, float rotationDegrees, int screenWidth, int screenHeight) {
            SavedBounds bounds = new SavedBounds();
            bounds.x = x / (float) Math.max(1, screenWidth);
            bounds.y = y / (float) Math.max(1, screenHeight);
            bounds.width = width / (float) Math.max(1, screenWidth);
            bounds.height = height / (float) Math.max(1, screenHeight);
            bounds.rotationDegrees = rotationDegrees;
            return bounds;
        }
    }
}
