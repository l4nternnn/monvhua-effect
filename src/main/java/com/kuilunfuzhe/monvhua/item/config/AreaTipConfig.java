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
            return copy;
        }
    }
}
