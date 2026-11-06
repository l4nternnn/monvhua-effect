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
    public Transfer transfer = new Transfer();
    public static final class Transfer {
        public boolean enabled = true, carryEnabled = true, allowEvidenceItemPickup = false;
        public boolean corpseDrips = true, contactTransfer = true, footprints = true, weaponStains = true;
        public int sampleTicks = 5, dripMinTicks = 10, stationaryDripTicks = 30, wetTicks = 2400, soleCapacity = 4;
        public double dripDistance = .75, maxDropDistance = 3, discontinuityDistance = 4, stepDistance = .65;
        public int maxSourcesPerHolder = 4, maxBatchesPerHolder = 8, maxTransferRecords = 8192;
        public boolean valid() {
            return sampleTicks >= 1 && sampleTicks <= 100 && dripMinTicks >= 1 && dripMinTicks <= 1200
                    && stationaryDripTicks >= 1 && stationaryDripTicks <= 1200 && wetTicks >= 20 && wetTicks <= 72000
                    && soleCapacity >= 1 && soleCapacity <= 16 && maxSourcesPerHolder >= 1 && maxSourcesPerHolder <= 4
                    && maxBatchesPerHolder >= 1 && maxBatchesPerHolder <= 8 && maxTransferRecords >= 64 && maxTransferRecords <= 65536
                    && Double.isFinite(dripDistance) && dripDistance >= .1 && dripDistance <= 8
                    && Double.isFinite(maxDropDistance) && maxDropDistance >= .1 && maxDropDistance <= 8
                    && Double.isFinite(discontinuityDistance) && discontinuityDistance >= 1 && discontinuityDistance <= 16
                    && Double.isFinite(stepDistance) && stepDistance >= .1 && stepDistance <= 3;
        }
    }
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
            if (next == null || next.blood == null || !next.blood.valid() || next.transfer == null || !next.transfer.valid() || next.detailedHitLimit < 16 || next.detailedHitLimit > 256
                    || !Double.isFinite(next.inspectionDistance) || next.inspectionDistance < 1 || next.inspectionDistance > 6
                    || !Float.isFinite(next.moderateThreshold) || !Float.isFinite(next.severeThreshold)
                    || next.moderateThreshold <= 0 || next.severeThreshold <= next.moderateThreshold)
                throw new IllegalArgumentException("Invalid forensic limits");
            current = next;
        } catch (Exception error) { LoggerFactory.getLogger("monvhua-forensics").error("Could not load forensic configuration; retaining defaults", error); }
    }
}
