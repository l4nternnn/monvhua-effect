package com.kuilunfuzhe.monvhua.features.forensics.physics;

import net.minecraft.nbt.NbtCompound;

/** Per-connection full-snapshot suppression. Heartbeats preserve the existing client's two-second lease. */
public final class CorpseSnapshotGate {
    private static final long HEARTBEAT_NANOS = 500_000_000L;
    private NbtCompound last;
    private long sentAt;
    public boolean shouldSend(NbtCompound snapshot, long now) {
        return last == null || !last.equals(snapshot) || now - sentAt >= HEARTBEAT_NANOS;
    }
    /** Only update after a successful send, and never retain caller-owned mutable data. */
    public void sent(NbtCompound snapshot, long now) { last=snapshot.copy(); sentAt=now; }
}
