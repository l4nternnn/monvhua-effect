package com.kuilunfuzhe.monvhua.features.action;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class ActionConfig {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("monvhua/actions.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ActionConfig instance;

    public int version = 1;
    public List<ActionDef> actions = new ArrayList<>();
    public Map<Integer, List<String>> timelineSchedule = new LinkedHashMap<>();

    public static class ActionDef {
        public String id = "";
        public String name = "";
        public boolean enabled = true;
        public int requiredPermissionLevel = 0;
        public String actionType = "CHAT";
        public Map<String, Object> actionParams = new LinkedHashMap<>();
        public List<TriggerDef> triggers = new ArrayList<>();
    }

    public static class TriggerDef {
        public String type = "MANUAL";
        public Map<String, Object> params = new LinkedHashMap<>();
    }

    public static ActionConfig getInstance() {
        if (instance == null) instance = load();
        return instance;
    }

    public static void setInstance(ActionConfig config) {
        instance = normalize(config);
        instance.save();
    }

    private static ActionConfig load() {
        if (CONFIG_PATH.toFile().exists()) {
            try (Reader reader = new FileReader(CONFIG_PATH.toFile())) {
                return normalize(GSON.fromJson(reader, ActionConfig.class));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        ActionConfig config = createDefault();
        config.save();
        return config;
    }

    private static ActionConfig normalize(ActionConfig config) {
        if (config == null) return createDefault();
        if (config.actions == null) config.actions = new ArrayList<>();
        if (config.timelineSchedule == null) config.timelineSchedule = new LinkedHashMap<>();
        for (int i = 0; i < config.actions.size(); i++) {
            ActionDef def = config.actions.get(i);
            if (def == null) {
                config.actions.remove(i);
                i--;
                continue;
            }
            if (def.actionParams == null) def.actionParams = new LinkedHashMap<>();
            if (def.triggers == null) def.triggers = new ArrayList<>();
            for (int j = 0; j < def.triggers.size(); j++) {
                TriggerDef t = def.triggers.get(j);
                if (t == null) {
                    def.triggers.remove(j);
                    j--;
                    continue;
                }
                if (t.params == null) t.params = new LinkedHashMap<>();
            }
        }
        migrateTimelineScheduleInstances(config);
        return config;
    }

    private static void migrateTimelineScheduleInstances(ActionConfig config) {
        if (config.timelineSchedule.isEmpty()) return;
        for (Map.Entry<Integer, List<String>> entry : config.timelineSchedule.entrySet()) {
            List<String> ids = entry.getValue();
            if (ids == null || ids.isEmpty()) continue;
            List<String> migrated = new ArrayList<>(ids.size());
            for (String id : ids) {
                if (id == null || id.isBlank()) {
                    migrated.add(id);
                    continue;
                }
                Optional<ActionDef> opt = config.findById(id);
                if (opt.isEmpty() || isTimelineInstance(opt.get())) {
                    migrated.add(id);
                    continue;
                }
                ActionDef instance = createTimelineInstance(config, opt.get(), entry.getKey());
                migrated.add(instance.id);
            }
            entry.setValue(migrated);
        }
    }

    public static boolean isTimelineInstance(ActionDef def) {
        return def != null && Boolean.TRUE.equals(def.actionParams.get("_timeline_instance"));
    }

    public static ActionDef createTimelineInstance(ActionConfig config, ActionDef source, int second) {
        ActionDef instance = copyActionDef(source);
        if (instance == null) return null;
        String sourceId = source.id == null ? "" : source.id;
        instance.id = nextTimelineInstanceId(config, sourceId, second);
        instance.name = (source.name == null || source.name.isBlank() ? sourceId : source.name) + " @ " + Math.max(0, second) + "s";
        instance.triggers = new ArrayList<>();
        instance.actionParams.put("_timeline_instance", true);
        instance.actionParams.put("_source_action_id", sourceId);
        instance.actionParams.put("_timeline_second", Math.max(0, second));
        config.actions.add(instance);
        return instance;
    }

    private static String nextTimelineInstanceId(ActionConfig config, String sourceId, int second) {
        String safeSource = sourceId == null || sourceId.isBlank() ? "action" : sourceId.replaceAll("[^A-Za-z0-9_\\-]", "_");
        String base = "__tl_" + Math.max(0, second) + "_" + safeSource;
        String id = base;
        int suffix = 1;
        while (config.findById(id).isPresent()) {
            id = base + "_" + suffix++;
        }
        return id;
    }

    private static ActionConfig createDefault() {
        ActionConfig config = new ActionConfig();
        config.version = 1;
        config.actions = new ArrayList<>();
        return config;
    }

    public void save() {
        CONFIG_PATH.getParent().toFile().mkdirs();
        try (Writer writer = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static ActionConfig fromJson(String json) {
        return normalize(GSON.fromJson(json, ActionConfig.class));
    }

    public Optional<ActionDef> findById(String id) {
        return actions.stream().filter(a -> a.id.equals(id)).findFirst();
    }

    public List<ActionDef> getEnabled() {
        return actions.stream().filter(a -> a.enabled).collect(Collectors.toList());
    }

    public boolean setEnabled(String id, boolean enabled) {
        Optional<ActionDef> opt = findById(id);
        if (opt.isPresent()) {
            opt.get().enabled = enabled;
            save();
            return true;
        }
        return false;
    }

    public static ActionDef actionDefFromJson(String json) {
        ActionDef def = GSON.fromJson(json, ActionDef.class);
        if (def == null) return null;
        if (def.actionParams == null) def.actionParams = new LinkedHashMap<>();
        if (def.triggers == null) def.triggers = new ArrayList<>();
        return def;
    }

    public static ActionDef copyActionDef(ActionDef source) {
        if (source == null) return null;
        return actionDefFromJson(GSON.toJson(source));
    }
}
