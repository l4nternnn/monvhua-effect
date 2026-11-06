package com.kuilunfuzhe.monvhua.features.forensics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;
import java.nio.file.Files;

public final class ForensicConfig {
    public boolean enabled = true;
    public int detailedHitLimit = 256;
    public double inspectionDistance = 3;
    public float moderateThreshold = 4;
    public float severeThreshold = 8;
    public Blood blood = new Blood();
    public static final class Blood {
        public boolean enabled = true;
        public boolean generate = true;
        public int impactBudget = 120;
        public int poolBudget = 160;
        public int recentTicks = 1200;
        public int poolTicks = 2400;
        public int chunkPatchLimit = 2048;
        public int worldPatchLimit = 32768;
        public int syncRadius = 4;
        public boolean valid() {
            return impactBudget >= 0 && impactBudget <= 1000 && poolBudget >= 0 && poolBudget <= 1000
                    && recentTicks >= 20 && recentTicks <= 72000 && poolTicks >= 20 && poolTicks <= 72000
                    && chunkPatchLimit >= 64 && chunkPatchLimit <= 4096 && worldPatchLimit >= chunkPatchLimit
                    && worldPatchLimit <= 65536 && syncRadius >= 1 && syncRadius <= 8;
        }
    }
    public static ForensicConfig current = new ForensicConfig();
    public static void load() {
        var path = FabricLoader.getInstance().getConfigDir().resolve("monvhua-forensics.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            if (!Files.exists(path)) { Files.writeString(path, gson.toJson(current)); return; }
            ForensicConfig next = gson.fromJson(Files.readString(path), ForensicConfig.class);
            if (next == null || next.blood == null || !next.blood.valid() || next.detailedHitLimit < 16 || next.detailedHitLimit > 256
                    || !Double.isFinite(next.inspectionDistance) || next.inspectionDistance < 1 || next.inspectionDistance > 6
                    || !Float.isFinite(next.moderateThreshold) || !Float.isFinite(next.severeThreshold)
                    || next.moderateThreshold <= 0 || next.severeThreshold <= next.moderateThreshold)
                throw new IllegalArgumentException("Invalid forensic limits");
            current = next;
        } catch (Exception error) { LoggerFactory.getLogger("monvhua-forensics").error("Could not load forensic configuration; retaining defaults", error); }
    }
}
