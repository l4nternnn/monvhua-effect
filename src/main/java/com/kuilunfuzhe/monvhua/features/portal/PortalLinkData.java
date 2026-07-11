package com.kuilunfuzhe.monvhua.features.portal;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public record PortalLinkData(BlockPos targetPos, Direction targetFacing, Vec3d targetCenter) {
    public PortalLinkData(BlockPos targetPos, Direction targetFacing) {
        this(targetPos, targetFacing, Vec3d.ofCenter(targetPos));
    }
}
