package com.kuilunfuzhe.monvhua.features.forensics.blood;

import com.kuilunfuzhe.monvhua.features.forensics.ForensicConfig;
import com.mojang.serialization.Codec;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import java.util.*;

public final class BloodTraceStore extends PersistentState {
    public record Entry(BloodPatch patch, String source, String event, String support, boolean directional) {}
    public static final Codec<BloodTraceStore> CODEC = NbtCompound.CODEC.xmap(BloodTraceStore::new, BloodTraceStore::write);
    public static final PersistentStateType<BloodTraceStore> TYPE = new PersistentStateType<>("monvhua_blood_traces", BloodTraceStore::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<Long, Set<String>> chunks = new HashMap<>();
    private final Map<BlockPos, Set<String>> blocks = new HashMap<>();
    public BloodTraceStore() {}
    private BloodTraceStore(NbtCompound root) {
        if (root.getInt("version", 1) != 1) throw new IllegalStateException("Unsupported blood trace version");
        for (NbtElement element : root.getListOrEmpty("patches")) if (element instanceof NbtCompound n) {
            BloodPatch patch = BloodPatch.read(n);
            if (!patch.id().isEmpty() && Float.isFinite(patch.radius()) && patch.radius() > 0 && patch.radius() <= .85f
                    && Float.isFinite(patch.u()) && Float.isFinite(patch.v())) {
                put(new Entry(patch, n.getString("source", ""), n.getString("event", ""), n.getString("support", ""), n.getBoolean("directional", false)));
            }
        }
    }
    private NbtCompound write() {
        NbtCompound root = new NbtCompound(); root.putInt("version", 1); NbtList list = new NbtList();
        for (Entry entry : entries.values()) {
            NbtCompound n = entry.patch.write(); n.putString("source", entry.source); n.putString("event", entry.event);
            n.putString("support", entry.support); n.putBoolean("directional", entry.directional); list.add(n);
        }
        root.put("patches", list); return root;
    }
    public static BloodTraceStore get(ServerWorld world) { return world.getPersistentStateManager().getOrCreate(TYPE); }
    public Collection<Entry> at(BlockPos pos) { return blocks.getOrDefault(pos, Set.of()).stream().map(entries::get).filter(Objects::nonNull).toList(); }
    public Collection<Entry> chunk(long key) { return chunks.getOrDefault(key, Set.of()).stream().map(entries::get).filter(Objects::nonNull).toList(); }
    public Entry entry(String id) { return entries.get(id); }
    public Set<Long> chunkKeys() { return Set.copyOf(chunks.keySet()); }
    public int size() { return entries.size(); }
    public boolean submit(Entry entry) {
        BloodPatch p = entry.patch;
        Entry old = entries.get(p.id());
        if (old != null && (!old.patch.pos().equals(p.pos()) || p.revision() <= old.patch.revision())) return false;
        if (old == null) {
            var config = ForensicConfig.current.blood;
            if (entries.size() >= config.worldPatchLimit || chunks.getOrDefault(new ChunkPos(p.pos()).toLong(), Set.of()).size() >= config.chunkPatchLimit
                    || at(p.pos()).stream().filter(e -> e.patch.face() == p.face()).count() >= 64) return false;
        }
        put(entry); markDirty(); return true;
    }
    private void put(Entry entry) {
        BloodPatch p = entry.patch; entries.put(p.id(), entry);
        chunks.computeIfAbsent(new ChunkPos(p.pos()).toLong(), k -> new LinkedHashSet<>()).add(p.id());
        blocks.computeIfAbsent(p.pos(), k -> new LinkedHashSet<>()).add(p.id());
    }
    public void remove(String id) {
        Entry old = entries.remove(id); if (old == null) return;
        long chunk = new ChunkPos(old.patch.pos()).toLong();
        Set<String> ids = chunks.get(chunk); ids.remove(id); if (ids.isEmpty()) chunks.remove(chunk);
        Set<String> local = blocks.get(old.patch.pos()); local.remove(id); if (local.isEmpty()) blocks.remove(old.patch.pos());
        markDirty();
    }
    public int clear(BlockPos from, BlockPos to, boolean dryRun) {
        List<String> ids = entries.values().stream().filter(e -> {
            BlockPos p = e.patch.pos(); return p.getX() >= Math.min(from.getX(), to.getX()) && p.getX() <= Math.max(from.getX(), to.getX())
                    && p.getY() >= Math.min(from.getY(), to.getY()) && p.getY() <= Math.max(from.getY(), to.getY())
                    && p.getZ() >= Math.min(from.getZ(), to.getZ()) && p.getZ() <= Math.max(from.getZ(), to.getZ());
        }).map(e -> e.patch.id()).toList();
        if (!dryRun) ids.forEach(this::remove); return ids.size();
    }
}
