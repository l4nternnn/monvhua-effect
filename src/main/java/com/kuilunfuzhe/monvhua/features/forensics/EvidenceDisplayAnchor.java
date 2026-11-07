package com.kuilunfuzhe.monvhua.features.forensics;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import java.util.UUID;

/** Future world mounted summary UI context; no block/entity implementation yet. */
public interface EvidenceDisplayAnchor {
    UUID anchorId();
    Identifier type();
    ServerWorld world();
    BlockPos blockPos();
    float yaw();
    float pitch();
    float roll();
    float scale();
}
