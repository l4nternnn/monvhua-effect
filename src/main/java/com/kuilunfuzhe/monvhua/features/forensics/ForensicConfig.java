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
    public static ForensicConfig current = new ForensicConfig();
    public static void load() {
        var path = FabricLoader.getInstance().getConfigDir().resolve("monvhua-forensics.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            if (!Files.exists(path)) { Files.writeString(path, gson.toJson(current)); return; }
            ForensicConfig next = gson.fromJson(Files.readString(path), ForensicConfig.class);
            if (next == null || next.detailedHitLimit < 16 || next.detailedHitLimit > 256
                    || !Double.isFinite(next.inspectionDistance) || next.inspectionDistance < 1 || next.inspectionDistance > 6
                    || !Float.isFinite(next.moderateThreshold) || !Float.isFinite(next.severeThreshold)
                    || next.moderateThreshold <= 0 || next.severeThreshold <= next.moderateThreshold)
                throw new IllegalArgumentException("Invalid forensic limits");
            current = next;
        } catch (Exception error) { LoggerFactory.getLogger("monvhua-forensics").error("Could not load forensic configuration; retaining defaults", error); }
    }
}
