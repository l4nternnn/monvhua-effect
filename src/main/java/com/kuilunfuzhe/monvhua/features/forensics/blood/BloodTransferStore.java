package com.kuilunfuzhe.monvhua.features.forensics.blood;

import com.kuilunfuzhe.monvhua.features.forensics.ForensicConfig;
import com.mojang.serialization.Codec;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.*;
import java.util.*;

/** Authoritative finite transfer batches. Public items contain opaque references only. */
public final class BloodTransferStore extends PersistentState {
    public static final Codec<BloodTransferStore> CODEC = NbtCompound.CODEC.xmap(BloodTransferStore::new, s -> s.root.copy());
    public static final PersistentStateType<BloodTransferStore> TYPE = new PersistentStateType<>(
            "monvhua_blood_transfers", BloodTransferStore::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    private final NbtCompound root;
    public BloodTransferStore() { this(new NbtCompound()); }
    private BloodTransferStore(NbtCompound root) {
        if (root.getInt("version", 1) != 1) throw new IllegalStateException("Unsupported transfer version");
        this.root = root; root.putInt("version", 1);
        if (!root.contains("records")) root.put("records", new NbtCompound());
    }
    public static BloodTransferStore get(MinecraftServer server) { return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE); }
    private NbtCompound records() { return root.getCompoundOrEmpty("records"); }
    public NbtCompound record(String id) { return records().getCompoundOrEmpty(id); }
    public boolean reserve(String id) {
        if (id.isEmpty()) return false;
        if (records().contains(id)) return true;
        if (records().getKeys().size() >= ForensicConfig.current.transfer.maxTransferRecords) return false;
        NbtCompound n = new NbtCompound(); n.put("batches", new NbtList()); records().put(id, n); markDirty(); return true;
    }
    public int visible(String id) { return record(id).getInt("visible", 0); }
    public int available(String id, long now) {
        return record(id).getListOrEmpty("batches").stream().filter(e -> e instanceof NbtCompound)
                .map(e -> (NbtCompound)e).filter(n -> n.getLong("until", 0) > now)
                .mapToInt(n -> Math.max(0, n.getInt("amount", 0))).sum();
    }
    public boolean add(String id, String source, int amount, long until, long now) {
        if (amount <= 0 || until <= now || !reserve(id)) return false;
        NbtCompound n = record(id); NbtList batches = n.getListOrEmpty("batches");
        batches.removeIf(e -> !(e instanceof NbtCompound b) || b.getLong("until", 0) <= now || b.getInt("amount", 0) <= 0);
        Set<String> sources = new HashSet<>();
        for (NbtElement e : batches) if (e instanceof NbtCompound b) sources.add(b.getString("source", "mixed"));
        if (!sources.contains(source) && sources.size() >= ForensicConfig.current.transfer.maxSourcesPerHolder) {
            source = "mixed";
            for (NbtElement e : batches) if (e instanceof NbtCompound b) b.putString("source", "mixed");
        }
        NbtCompound batch = new NbtCompound(); batch.putString("source", source); batch.putInt("amount", amount); batch.putLong("until", until);
        batches.add(batch);
        while (batches.size() > ForensicConfig.current.transfer.maxBatchesPerHolder) {
            NbtCompound a = batches.getCompound(0).orElseThrow(), b = batches.getCompound(1).orElseThrow();
            a.putString("source", a.getString("source", "").equals(b.getString("source", "")) ? a.getString("source", "mixed") : "mixed");
            a.putInt("amount", a.getInt("amount", 0) + b.getInt("amount", 0));
            a.putLong("until", Math.min(a.getLong("until", 0), b.getLong("until", 0))); batches.remove(1);
        }
        n.put("batches", batches); n.putInt("visible", Math.min(16, n.getInt("visible", 0) + amount)); markDirty(); return true;
    }
    public int transfer(String from, String to, int max, int capacity, long now) {
        if (from.equals(to) || max <= 0 || available(from, now) <= 0 || !reserve(to)) return 0;
        int wanted = Math.min(max, Math.max(0, capacity - available(to, now))), moved = 0;
        for (NbtElement e : record(from).getListOrEmpty("batches")) if (e instanceof NbtCompound b && b.getLong("until", 0) > now) {
            int take = Math.min(wanted - moved, Math.max(0, b.getInt("amount", 0)));
            if (take > 0 && add(to, b.getString("source", "mixed"), take, b.getLong("until", 0), now)) {
                b.putInt("amount", b.getInt("amount", 0) - take); moved += take;
            }
        }
        if (moved > 0) markDirty(); return moved;
    }
    /** Terminal traces retain a compact source summary, never a new transferable balance. */
    public String sourceSummary(String id, long now) {
        Set<String> found = new HashSet<>();
        for (NbtElement e : record(id).getListOrEmpty("batches")) if (e instanceof NbtCompound b
                && b.getLong("until", 0) > now && b.getInt("amount", 0) > 0) found.add(b.getString("source", "mixed"));
        return found.size() == 1 ? found.iterator().next() : "mixed";
    }
    public int consume(String id, int count, long now) {
        int used = 0;
        for (NbtElement e : record(id).getListOrEmpty("batches")) if (e instanceof NbtCompound b && b.getLong("until", 0) > now) {
            int take = Math.min(Math.max(0, count - used), Math.max(0, b.getInt("amount", 0)));
            b.putInt("amount", b.getInt("amount", 0) - take); used += take;
        }
        if (used > 0) markDirty(); return used;
    }
}
