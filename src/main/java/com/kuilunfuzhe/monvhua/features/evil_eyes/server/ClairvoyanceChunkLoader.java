package com.kuilunfuzhe.monvhua.features.evil_eyes.server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ClairvoyanceChunkLoader {
    private static final int LOAD_RADIUS = 2;
    private static final long RELEASE_DELAY_TICKS = 20L * 20L;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<ChunkKey, ChunkLease> LEASES = new HashMap<>();

    private ClairvoyanceChunkLoader() {
    }

    public static void startWatching(UUID viewerId, ServerWorld world, Vec3d targetPos, long now) {
        if (world == null) {
            return;
        }
        Session session = SESSIONS.computeIfAbsent(viewerId, Session::new);
        updateSession(session, world, targetPos, now);
    }

    public static void stopWatching(UUID viewerId, long now) {
        Session session = SESSIONS.remove(viewerId);
        if (session == null) {
            return;
        }
        releaseAll(session, now);
    }

    public static void tick(MinecraftServer server) {
        long now = server.getOverworld().getTime();
        for (Session session : new HashSet<>(SESSIONS.values())) {
            ServerWorld world = session.world;
            if (world == null) {
                releaseAll(session, now);
                SESSIONS.remove(session.viewerId);
                continue;
            }
            if (!world.isChunkLoaded(ChunkPos.toLong(session.centerChunkX, session.centerChunkZ))) {
                // keep leases alive; the target world may still be valid
            }
        }
        for (ChunkLease lease : LEASES.values()) {
            if (lease.refCount <= 0 && lease.releaseAt > 0L && now >= lease.releaseAt) {
                if (!lease.originallyForced && lease.world != null) {
                    lease.world.setChunkForced(lease.chunkX, lease.chunkZ, false);
                }
                lease.releaseAt = 0L;
            }
        }
        LEASES.entrySet().removeIf(entry -> {
            ChunkLease lease = entry.getValue();
            return lease.refCount <= 0 && lease.releaseAt == 0L && !lease.originallyForced;
        });
    }

    public static void update(UUID viewerId, ServerWorld world, Vec3d targetPos, long now) {
        if (world == null) {
            return;
        }
        Session session = SESSIONS.computeIfAbsent(viewerId, Session::new);
        updateSession(session, world, targetPos, now);
    }

    private static void updateSession(Session session, ServerWorld world, Vec3d targetPos, long now) {
        session.world = world;
        int centerChunkX = ((int) Math.floor(targetPos.x)) >> 4;
        int centerChunkZ = ((int) Math.floor(targetPos.z)) >> 4;
        session.centerChunkX = centerChunkX;
        session.centerChunkZ = centerChunkZ;

        Set<ChunkKey> desired = new HashSet<>();
        for (int dx = -LOAD_RADIUS; dx <= LOAD_RADIUS; dx++) {
            for (int dz = -LOAD_RADIUS; dz <= LOAD_RADIUS; dz++) {
                desired.add(new ChunkKey(world.getRegistryKey(), ChunkPos.toLong(centerChunkX + dx, centerChunkZ + dz)));
            }
        }

        for (ChunkKey key : new HashSet<>(session.heldChunks)) {
            if (!desired.contains(key)) {
                session.heldChunks.remove(key);
                release(key, now);
            }
        }
        for (ChunkKey key : desired) {
            if (!session.heldChunks.contains(key)) {
                acquire(session, world, key, now);
            }
        }
    }

    private static void releaseAll(Session session, long now) {
        for (ChunkKey chunkKey : new HashSet<>(session.heldChunks)) {
            release(chunkKey, now);
        }
        session.heldChunks.clear();
    }

    private static void acquire(Session session, ServerWorld world, ChunkKey chunkKey, long now) {
        ChunkLease lease = LEASES.get(chunkKey);
        if (lease == null) {
            lease = new ChunkLease();
            lease.world = world;
            lease.chunkX = ChunkPos.getPackedX(chunkKey.pos);
            lease.chunkZ = ChunkPos.getPackedZ(chunkKey.pos);
            lease.originallyForced = world.getForcedChunks().contains(chunkKey.pos);
            lease.refCount = 0;
            LEASES.put(chunkKey, lease);
        }
        lease.world = world;
        if (lease.refCount == 0 && !lease.originallyForced) {
            world.setChunkForced(lease.chunkX, lease.chunkZ, true);
        }
        lease.refCount++;
        lease.releaseAt = 0L;
        session.heldChunks.add(chunkKey);
    }

    private static void release(ChunkKey chunkKey, long now) {
        ChunkLease lease = LEASES.get(chunkKey);
        if (lease == null) {
            return;
        }
        if (lease.refCount > 0) {
            lease.refCount--;
        }
        if (lease.refCount <= 0) {
            lease.releaseAt = now + RELEASE_DELAY_TICKS;
        }
    }

    private static final class Session {
        private final UUID viewerId;
        private ServerWorld world;
        private int centerChunkX;
        private int centerChunkZ;
        private final Set<ChunkKey> heldChunks = new HashSet<>();

        private Session(UUID viewerId) {
            this.viewerId = viewerId;
        }
    }

    private record ChunkKey(RegistryKey<World> worldKey, long pos) {
    }

    private static final class ChunkLease {
        private ServerWorld world;
        private int chunkX;
        private int chunkZ;
        private int refCount;
        private boolean originallyForced;
        private long releaseAt;
    }
}
