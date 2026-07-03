package com.kuilunfuzhe.monvhua.features.hot_backpack_save;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HotBackpackState {
    public Map<String, PlayerRecord> records = new LinkedHashMap<>();
    public Map<String, Snapshot> pendingApply = new LinkedHashMap<>();
    public Map<String, Snapshot> undo = new LinkedHashMap<>();

    public void sanitize() {
        if (records == null) records = new LinkedHashMap<>();
        if (pendingApply == null) pendingApply = new LinkedHashMap<>();
        if (undo == null) undo = new LinkedHashMap<>();
        for (PlayerRecord record : records.values()) {
            record.sanitize();
        }
    }

    public static final class PlayerRecord {
        public String uuid = "";
        public String name = "";
        public String roleTag = "";
        public boolean online;
        public List<Snapshot> history = new ArrayList<>();

        public void sanitize() {
            if (history == null) history = new ArrayList<>();
            for (Snapshot snapshot : history) {
                snapshot.sanitize();
            }
        }
    }

    public static final class Snapshot {
        public String uuid = "";
        public String name = "";
        public String roleTag = "";
        public long timestamp;
        public String reason = "";
        public List<String> items = new ArrayList<>();
        public List<String> tags = new ArrayList<>();
        public Map<String, Integer> scoreboard = new LinkedHashMap<>();
        public List<EffectRecord> effects = new ArrayList<>();
        public float health = 20.0F;
        public float absorption;
        public int food = 20;
        public float saturation = 5.0F;
        public int experienceLevel;
        public float experienceProgress;
        public int totalExperience;
        public int selectedSlot;
        public String gameMode = "survival";

        public void sanitize() {
            if (items == null) items = new ArrayList<>();
            while (items.size() < 41) items.add("");
            if (tags == null) tags = new ArrayList<>();
            if (scoreboard == null) scoreboard = new LinkedHashMap<>();
            if (effects == null) effects = new ArrayList<>();
        }
    }

    public static final class EffectRecord {
        public String id = "";
        public int duration;
        public int amplifier;
        public boolean ambient;
        public boolean showParticles = true;
        public boolean showIcon = true;
    }
}
