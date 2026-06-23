package com.kuilunfuzhe.monvhua.gui.body.bodypose;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class BodyPosePresetStore {
    static final int MAX_PAGES = 6;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("body_pose");
    private static final Path STORE_PATH = CONFIG_DIR.resolve("body_pose_editor.json");

    private BodyPosePresetStore() {
    }

    static StoreData load() {
        if (Files.isRegularFile(STORE_PATH)) {
            try (Reader reader = Files.newBufferedReader(STORE_PATH, StandardCharsets.UTF_8)) {
                return sanitize(GSON.fromJson(reader, StoreData.class));
            } catch (IOException | RuntimeException e) {
                e.printStackTrace();
            }
        }
        StoreData data = new StoreData();
        data.pages.add(new PageData(newId(), "界面 1", new EditorStateData("界面 1")));
        save(data);
        return data;
    }

    static void save(StoreData data) {
        StoreData sanitized = sanitize(data);
        try {
            Files.createDirectories(CONFIG_DIR);
            try (Writer writer = Files.newBufferedWriter(STORE_PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(sanitized, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static StoreData sanitize(StoreData data) {
        if (data == null) {
            data = new StoreData();
        }
        if (data.pages == null) {
            data.pages = new ArrayList<>();
        }
        if (data.presets == null) {
            data.presets = new ArrayList<>();
        }
        data.pages.removeIf(page -> page == null);
        data.presets.removeIf(preset -> preset == null || preset.state == null);
        if (data.pages.isEmpty()) {
            data.pages.add(new PageData(newId(), "界面 1", new EditorStateData("界面 1")));
        }
        while (data.pages.size() > MAX_PAGES) {
            data.pages.remove(data.pages.size() - 1);
        }
        for (int i = 0; i < data.pages.size(); i++) {
            PageData page = data.pages.get(i);
            if (isBlank(page.id)) {
                page.id = newId();
            }
            if (isBlank(page.name)) {
                page.name = "界面 " + (i + 1);
            }
            if (page.state == null) {
                page.state = new EditorStateData(page.name);
            }
            sanitizeState(page.state, page.name);
        }
        for (int i = 0; i < data.presets.size(); i++) {
            PresetData preset = data.presets.get(i);
            if (isBlank(preset.id)) {
                preset.id = newId();
            }
            if (isBlank(preset.name)) {
                preset.name = "预设 " + (i + 1);
            }
            sanitizeState(preset.state, preset.name);
        }
        if (data.activePageIndex < 0 || data.activePageIndex >= data.pages.size()) {
            data.activePageIndex = 0;
        }
        if (data.selectedPresetId == null) {
            data.selectedPresetId = "";
        }
        return data;
    }

    private static void sanitizeState(EditorStateData state, String fallbackName) {
        if (isBlank(state.name)) {
            state.name = fallbackName;
        }
        if (state.selectedSkin == null) {
            state.selectedSkin = "";
        }
        if (state.selectedPlayerName == null) {
            state.selectedPlayerName = "";
        }
        if (state.selectedSkinSource == null) {
            state.selectedSkinSource = "LOCAL";
        }
        if (state.selectedPart == null) {
            state.selectedPart = "";
        }
        if (state.poseEditMode == null) {
            state.poseEditMode = "SKELETAL";
        }
        if (state.defaultItemDisplayMode == null) {
            state.defaultItemDisplayMode = "BLOCK";
        }
        if (state.partPoses == null) {
            state.partPoses = new LinkedHashMap<>();
        }
        if (state.skeletalPoses == null) {
            state.skeletalPoses = new LinkedHashMap<>();
        }
        if (state.trueSkeletalPoses == null) {
            state.trueSkeletalPoses = new LinkedHashMap<>();
        }
        if (state.editorItems == null) {
            state.editorItems = new ArrayList<>();
        }
        state.editorItems.removeIf(item -> item == null || isBlank(item.itemId));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    static final class StoreData {
        int activePageIndex;
        String selectedPresetId = "";
        List<PageData> pages = new ArrayList<>();
        List<PresetData> presets = new ArrayList<>();
    }

    static final class PageData {
        String id;
        String name;
        EditorStateData state;

        PageData() {
        }

        PageData(String id, String name, EditorStateData state) {
            this.id = id;
            this.name = name;
            this.state = state;
        }
    }

    static final class PresetData {
        String id;
        String name;
        EditorStateData state;

        PresetData() {
        }

        PresetData(String id, String name, EditorStateData state) {
            this.id = id;
            this.name = name;
            this.state = state;
        }
    }

    static final class EditorStateData {
        String name;
        String selectedSkin = "";
        String selectedPlayerName = "";
        String selectedSkinSource = "LOCAL";
        String selectedPart = "";
        boolean slimModel = true;
        float modelOffsetX;
        float modelOffsetY;
        float modelOffsetZ;
        float modelPitch;
        float modelYaw;
        float modelRoll;
        float wholeBodyScale = 1.0F;
        String poseEditMode = "SKELETAL";
        String defaultItemDisplayMode = "BLOCK";
        boolean showWholePreview = true;
        boolean showCoordinateAxes = true;
        boolean coordinateAxesMovable = true;
        Map<String, PoseData> partPoses = new LinkedHashMap<>();
        Map<String, PoseData> skeletalPoses = new LinkedHashMap<>();
        Map<String, PoseData> trueSkeletalPoses = new LinkedHashMap<>();
        List<EditorItemData> editorItems = new ArrayList<>();

        EditorStateData() {
        }

        EditorStateData(String name) {
            this.name = name;
        }
    }

    static final class PoseData {
        float pitch;
        float yaw;
        float roll;
        float bendPitch;
        float bendYaw;
        float bendRoll;
        float offsetX;
        float offsetY;
        float offsetZ;
        float scale = 1.0F;
        boolean visible = true;
    }

    static final class EditorItemData {
        String itemId;
        float offsetX;
        float offsetY;
        float offsetZ;
        float pitch;
        float yaw;
        float roll;
        String displayMode = "BLOCK";
    }
}
