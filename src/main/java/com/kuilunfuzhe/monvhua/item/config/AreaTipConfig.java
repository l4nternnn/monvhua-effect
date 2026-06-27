package com.kuilunfuzhe.monvhua.item.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kuilunfuzhe.monvhua.features.gravity.GravityAreaSpec;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AreaTipConfig {
    public static final int MAX_MESSAGE_LENGTH = 9000;
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("area_tip.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static AreaTipConfig instance;

    public List<GroupConfig> groups = new ArrayList<>();
    public String selectedGroupId = "";

    public AreaTipConfig() {
    }

    public static AreaTipConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static void setInstance(AreaTipConfig config) {
        instance = sanitize(config);
        instance.save();
    }

    public static void syncInstance(AreaTipConfig config) {
        instance = sanitize(config);
    }

    public static AreaTipConfig fromJson(String json) {
        try {
            return sanitize(GSON.fromJson(json, AreaTipConfig.class));
        } catch (Exception ignored) {
            return createDefault();
        }
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Optional<GroupConfig> selectedGroup() {
        UUID selected = parseUuid(selectedGroupId);
        if (selected != null) {
            Optional<GroupConfig> group = findGroup(selected);
            if (group.isPresent()) {
                return group;
            }
        }
        return groups.isEmpty() ? Optional.empty() : Optional.of(groups.get(0));
    }

    public Optional<GroupConfig> findGroup(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        for (GroupConfig group : groups) {
            if (id.equals(group.uuid())) {
                return Optional.of(group);
            }
        }
        return Optional.empty();
    }

    private static AreaTipConfig load() {
        if (Files.isRegularFile(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                return sanitize(GSON.fromJson(reader, AreaTipConfig.class));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        AreaTipConfig config = createDefault();
        config.save();
        return config;
    }

    private static AreaTipConfig sanitize(AreaTipConfig config) {
        if (config == null) {
            return createDefault();
        }
        if (config.groups == null) {
            config.groups = new ArrayList<>();
        }
        List<GroupConfig> sanitized = new ArrayList<>();
        for (GroupConfig group : config.groups) {
            if (group == null) {
                continue;
            }
            sanitized.add(group.sanitized());
        }
        if (sanitized.isEmpty()) {
            sanitized.add(defaultGroup());
        }
        config.groups = sanitized;
        UUID selected = parseUuid(config.selectedGroupId);
        if (selected == null || config.findGroup(selected).isEmpty()) {
            config.selectedGroupId = config.groups.get(0).id;
        }
        return config;
    }

    private static AreaTipConfig createDefault() {
        AreaTipConfig config = new AreaTipConfig();
        GroupConfig group = defaultGroup();
        config.groups.add(group);
        config.selectedGroupId = group.id;
        return config;
    }

    private static GroupConfig defaultGroup() {
        GroupConfig group = new GroupConfig();
        group.id = UUID.randomUUID().toString();
        group.name = "Default";
        group.color = 0xFFFFD400;
        group.message = "\u00a7eArea tip";
        group.shape = GravityAreaSpec.Shape.BOX.ordinal();
        group.half = GravityAreaSpec.Half.FULL.ordinal();
        group.sizeX = 3;
        group.sizeY = 3;
        group.sizeZ = 3;
        return group;
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static class GroupConfig {
        public String id = UUID.randomUUID().toString();
        public String name = "Group";
        public int color = 0xFFFFD400;
        public String message = "";
        public int shape = GravityAreaSpec.Shape.BOX.ordinal();
        public int half = GravityAreaSpec.Half.FULL.ordinal();
        public int sizeX = 3;
        public int sizeY = 3;
        public int sizeZ = 3;
        public boolean hudVisible = true;
        public float hudX = 24.0F;
        public float hudY = 24.0F;
        public float hudScale = 1.0F;
        public float hudRotation = 0.0F;
        public int hudWidth = 220;
        public int hudHeight = 72;
        public String hudBackground = "";
        public List<HudTextEntry> hudTexts = new ArrayList<>();

        public UUID uuid() {
            UUID uuid = parseUuid(id);
            if (uuid == null) {
                uuid = UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8));
            }
            return uuid;
        }

        public GravityAreaSpec spec() {
            return new GravityAreaSpec(
                    GravityAreaSpec.Shape.byId(shape),
                    GravityAreaSpec.Half.byId(half),
                    sizeX,
                    sizeY,
                    sizeZ
            );
        }

        public GroupConfig copy() {
            GroupConfig copy = new GroupConfig();
            copy.id = id;
            copy.name = name;
            copy.color = color;
            copy.message = message;
            copy.shape = shape;
            copy.half = half;
            copy.sizeX = sizeX;
            copy.sizeY = sizeY;
            copy.sizeZ = sizeZ;
            copy.hudVisible = hudVisible;
            copy.hudX = hudX;
            copy.hudY = hudY;
            copy.hudScale = hudScale;
            copy.hudRotation = hudRotation;
            copy.hudWidth = hudWidth;
            copy.hudHeight = hudHeight;
            copy.hudBackground = hudBackground;
            copy.hudTexts = new ArrayList<>();
            if (hudTexts != null) {
                for (HudTextEntry entry : hudTexts) {
                    if (entry != null) {
                        copy.hudTexts.add(entry.copy());
                    }
                }
            }
            return copy;
        }

        private GroupConfig sanitized() {
            GroupConfig copy = copy();
            if (parseUuid(copy.id) == null) {
                copy.id = UUID.randomUUID().toString();
            }
            if (copy.name == null || copy.name.isBlank()) {
                copy.name = "Group";
            }
            if (copy.name.length() > 64) {
                copy.name = copy.name.substring(0, 64);
            }
            if (copy.message == null) {
                copy.message = "";
            }
            if (copy.message.length() > MAX_MESSAGE_LENGTH) {
                copy.message = copy.message.substring(0, MAX_MESSAGE_LENGTH);
            }
            copy.color = 0xFF000000 | (copy.color & 0xFFFFFF);
            copy.shape = Math.clamp(copy.shape, 0, GravityAreaSpec.Shape.values().length - 1);
            copy.half = Math.clamp(copy.half, 0, GravityAreaSpec.Half.values().length - 1);
            copy.sizeX = Math.clamp(copy.sizeX, GravityAreaSpec.MIN_SIZE, GravityAreaSpec.MAX_SIZE);
            copy.sizeY = Math.clamp(copy.sizeY, GravityAreaSpec.MIN_SIZE, GravityAreaSpec.MAX_SIZE);
            copy.sizeZ = Math.clamp(copy.sizeZ, GravityAreaSpec.MIN_SIZE, GravityAreaSpec.MAX_SIZE);
            if (GravityAreaSpec.Shape.byId(copy.shape) == GravityAreaSpec.Shape.CUBE) {
                int size = Math.max(copy.sizeX, Math.max(copy.sizeY, copy.sizeZ));
                copy.sizeX = size;
                copy.sizeY = size;
                copy.sizeZ = size;
            }
            copy.hudX = finite(copy.hudX, 24.0F);
            copy.hudY = finite(copy.hudY, 24.0F);
            copy.hudScale = Math.clamp(finite(copy.hudScale, 1.0F), 0.2F, 8.0F);
            copy.hudRotation = normalizeRotation(finite(copy.hudRotation, 0.0F));
            copy.hudWidth = Math.clamp(copy.hudWidth, 32, 4096);
            copy.hudHeight = Math.clamp(copy.hudHeight, 16, 4096);
            if (copy.hudBackground == null) {
                copy.hudBackground = "";
            }
            List<HudTextEntry> entries = new ArrayList<>();
            if (copy.hudTexts != null) {
                for (HudTextEntry entry : copy.hudTexts) {
                    if (entry != null) {
                        entries.add(entry.sanitized(copy.color, copy.message, copy.hudX, copy.hudY,
                                copy.hudScale, copy.hudRotation, copy.hudWidth, copy.hudHeight, copy.hudBackground));
                    }
                }
            }
            if (entries.isEmpty()) {
                HudTextEntry entry = HudTextEntry.fromGroup(copy.color, copy.message);
                entry.x = copy.hudX;
                entry.y = copy.hudY;
                entry.scale = copy.hudScale;
                entry.rotation = copy.hudRotation;
                entry.width = copy.hudWidth;
                entry.height = copy.hudHeight;
                entry.background = copy.hudBackground;
                entries.add(entry.sanitized(copy.color, copy.message, copy.hudX, copy.hudY,
                        copy.hudScale, copy.hudRotation, copy.hudWidth, copy.hudHeight, copy.hudBackground));
            }
            copy.hudTexts = entries;
            return copy;
        }
    }

    public static class HudTextEntry {
        public String id = UUID.randomUUID().toString();
        public String text = "";
        public boolean useGroupColor = true;
        public int color = 0xFFFFFFFF;
        public float fontSize = 1.0F;
        public String align = "left";
        public String font = "minecraft:default";
        public int offsetX = 8;
        public int offsetY = 8;
        public float x = Float.NaN;
        public float y = Float.NaN;
        public float scale = 1.0F;
        public float rotation = 0.0F;
        public int width = 220;
        public int height = 72;
        public String background = "";
        public int priority = 0;
        public int wrapWidth = 204;
        public int delayTicks = 0;
        public int displayTicks = 100;
        public int fadeTicks = 20;
        public int passDelayCount = 0;
        public int playLimit = 0;
        public boolean playOncePerPlayer = false;

        public static HudTextEntry fromGroup(int groupColor, String groupMessage) {
            HudTextEntry entry = new HudTextEntry();
            entry.text = stripLegacyCodes(groupMessage == null || groupMessage.isBlank() ? "Area tip" : groupMessage);
            entry.useGroupColor = true;
            entry.color = 0xFF000000 | (groupColor & 0xFFFFFF);
            return entry.sanitized(groupColor, groupMessage);
        }

        public HudTextEntry copy() {
            HudTextEntry copy = new HudTextEntry();
            copy.id = id;
            copy.text = text;
            copy.useGroupColor = useGroupColor;
            copy.color = color;
            copy.fontSize = fontSize;
            copy.align = align;
            copy.font = font;
            copy.offsetX = offsetX;
            copy.offsetY = offsetY;
            copy.x = x;
            copy.y = y;
            copy.scale = scale;
            copy.rotation = rotation;
            copy.width = width;
            copy.height = height;
            copy.background = background;
            copy.priority = priority;
            copy.wrapWidth = wrapWidth;
            copy.delayTicks = delayTicks;
            copy.displayTicks = displayTicks;
            copy.fadeTicks = fadeTicks;
            copy.passDelayCount = passDelayCount;
            copy.playLimit = playLimit;
            copy.playOncePerPlayer = playOncePerPlayer;
            return copy;
        }

        private HudTextEntry sanitized(int groupColor, String groupMessage) {
            return sanitized(groupColor, groupMessage, 24.0F, 24.0F, 1.0F, 0.0F, 220, 72, "");
        }

        private HudTextEntry sanitized(int groupColor, String groupMessage, float fallbackX, float fallbackY,
                                       float fallbackScale, float fallbackRotation, int fallbackWidth,
                                       int fallbackHeight, String fallbackBackground) {
            if (parseUuid(id) == null) {
                id = UUID.randomUUID().toString();
            }
            if (text == null) {
                text = stripLegacyCodes(groupMessage == null ? "" : groupMessage);
            }
            if (text.length() > MAX_MESSAGE_LENGTH) {
                text = text.substring(0, MAX_MESSAGE_LENGTH);
            }
            color = 0xFF000000 | (color & 0xFFFFFF);
            if (useGroupColor) {
                color = 0xFF000000 | (groupColor & 0xFFFFFF);
            }
            fontSize = Math.clamp(finite(fontSize, 1.0F), 0.25F, 8.0F);
            if (align == null || (!align.equals("center") && !align.equals("right"))) {
                align = "left";
            }
            if (font == null || font.isBlank()) {
                font = "minecraft:default";
            }
            x = finite(x, fallbackX);
            y = finite(y, fallbackY);
            scale = Math.clamp(finite(scale, fallbackScale), 0.2F, 8.0F);
            rotation = normalizeRotation(finite(rotation, fallbackRotation));
            width = Math.clamp(width <= 0 ? fallbackWidth : width, 32, 4096);
            height = Math.clamp(height <= 0 ? fallbackHeight : height, 16, 4096);
            if (background == null || background.isBlank()) {
                background = fallbackBackground == null ? "" : fallbackBackground;
            }
            priority = Math.clamp(priority, -100000, 100000);
            offsetX = Math.clamp(offsetX, -4096, 4096);
            offsetY = Math.clamp(offsetY, -4096, 4096);
            wrapWidth = Math.clamp(wrapWidth, 16, 4096);
            delayTicks = Math.clamp(delayTicks, 0, 72000);
            displayTicks = Math.clamp(displayTicks, 1, 72000);
            fadeTicks = Math.clamp(fadeTicks, 0, 72000);
            passDelayCount = Math.clamp(passDelayCount, 0, 100000);
            playLimit = Math.clamp(playLimit, 0, 100000);
            return this;
        }
    }

    private static float finite(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }

    private static float normalizeRotation(float value) {
        while (value <= -180.0F) value += 360.0F;
        while (value > 180.0F) value -= 360.0F;
        return value;
    }

    private static String stripLegacyCodes(String value) {
        if (value == null || value.indexOf('\u00a7') < 0) {
            return value == null ? "" : value;
        }
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '\u00a7' && i + 1 < value.length()) {
                i++;
                continue;
            }
            result.append(value.charAt(i));
        }
        return result.toString();
    }
}
