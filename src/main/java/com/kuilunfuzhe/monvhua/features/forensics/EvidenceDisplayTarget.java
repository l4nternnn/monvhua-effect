package com.kuilunfuzhe.monvhua.features.forensics;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import java.util.UUID;

/** Server validated target contract for future crosshair activation. */
public interface EvidenceDisplayTarget {
    UUID anchorId();
    Box interactionBounds();
    boolean acceptsCrosshair(ServerPlayerEntity player);
    EvidenceOpenResult open(ServerPlayerEntity player);

    record EvidenceOpenResult(boolean accepted, String reason) {
        public static EvidenceOpenResult openAccepted() { return new EvidenceOpenResult(true, ""); }
        public static EvidenceOpenResult rejected(String reason) { return new EvidenceOpenResult(false, reason == null ? "无法打开线索总结" : reason); }
    }
}
