package com.kuilunfuzhe.monvhua.features.swing;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Per-click diagnostics: never called from the tick/render loop. */
public final class SwingDiagnostics {
    private static final AtomicLong NEXT = new AtomicLong();
    public final long id = NEXT.incrementAndGet();
    private final Set<String> failures = new LinkedHashSet<>();
    private int rejected;

    public void log(String message) { MonvhuaMod.LOGGER.info("[SwingDiag #{}] {}", id, message); }

    public void reject(ServerWorld world, BlockPos pivot, BlockPos pos, boolean zPlane, String stage) {
        rejected++;
        if (failures.size() >= 40) return;
        String detail = "plane=" + (zPlane ? "Z" : "X") + " stage=" + stage
                + " local=" + pos.subtract(pivot).toShortString() + " " + describe(world, pos);
        if (failures.add(detail)) log("REJECT " + detail);
    }

    public void failed(ServerWorld world, BlockPos pivot) {
        log("NO_MATCH rejectedChecks=" + rejected + " distinctDetails=" + failures.size()
                + " (max 40). Search supports widths 2-7 and lengths 2-20, with two suspension columns and a continuous seat row.");
        int count = 0;
        // Include the neighboring planes: this exposes a seat/backrest offset from its chains.
        for (BlockPos p : BlockPos.iterate(pivot.add(-5, -21, -5), pivot.add(5, 0, 5))) {
            if (!world.isChunkLoaded(p)) continue;
            var state = world.getBlockState(p);
            if (!(SwingBlockRoles.rope(state) || SwingBlockRoles.backrest(state))) continue;
            if (count++ < 120) log("NEARBY local=" + p.subtract(pivot).toShortString() + " " + describe(world, p));
        }
        log("NEARBY_END found=" + count + " logged=" + Math.min(count, 120));
    }

    public static String describe(ServerWorld world, BlockPos pos) {
        if (!world.isChunkLoaded(pos)) return "pos=" + pos.toShortString() + " UNLOADED";
        var s = world.getBlockState(pos);
        return "pos=" + pos.toShortString() + " state=" + s + " primary=" + SwingBlockRoles.primaryRope(s)
                + " rope=" + SwingBlockRoles.rope(s) + " seat=" + SwingBlockRoles.seat(s)
                + " backrest=" + SwingBlockRoles.backrest(s) + " blockEntity=" + (world.getBlockEntity(pos) != null);
    }
}
