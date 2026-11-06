package com.kuilunfuzhe.monvhua.features.forensics;

import com.mojang.serialization.Codec;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import java.util.UUID;

/** Server-only evidence. Items carry references, never this document. */
public final class ForensicStore extends PersistentState {
    public static final Codec<ForensicStore> CODEC = NbtCompound.CODEC.xmap(ForensicStore::new, s -> s.root.copy());
    public static final PersistentStateType<ForensicStore> TYPE = new PersistentStateType<>(
            "monvhua_forensics", ForensicStore::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    private final NbtCompound root;
    public ForensicStore() { this(new NbtCompound()); }
    private ForensicStore(NbtCompound root) {
        this.root = root;
        int version = root.getInt("schema_version", 1);
        if (version != 1) throw new IllegalStateException("Unsupported forensic store version " + version);
        root.putInt("schema_version", 1);
    }
    public static ForensicStore get(MinecraftServer server) { return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE); }
    private NbtCompound section(String name) {
        if (!root.contains(name)) root.put(name, new NbtCompound());
        return root.getCompoundOrEmpty(name);
    }
    public NbtCompound life(UUID player) {
        NbtCompound lives = section("lives");
        if (!lives.contains(player.toString())) {
            NbtCompound life = new NbtCompound();
            life.putString("id", UUID.randomUUID().toString());
            life.putString("victim", player.toString());
            life.put("hits", new NbtList());
            lives.put(player.toString(), life);
            markDirty();
        }
        return lives.getCompoundOrEmpty(player.toString());
    }
    public void append(UUID player, NbtCompound hit) {
        NbtCompound life = life(player);
        NbtList hits = life.getListOrEmpty("hits");
        hits.add(hit);
        // Keep the strongest evidence even when the chronological detail buffer is compacted.
        NbtList candidates = life.contains("inspection_hits") ? life.getListOrEmpty("inspection_hits").copy() : hits.copy();
        if (life.contains("inspection_hits")) candidates.add(hit.copy());
        NbtList strongest = new NbtList();
        for (String part : Forensics.PARTS) for (NbtCompound entry : InspectionRecords.strongest(candidates, part, 3)) strongest.add(entry);
        life.put("inspection_hits", strongest);
        if (hits.size() > ForensicConfig.current.detailedHitLimit) {
            NbtCompound old = hits.getCompound(0).orElseThrow();
            hits.remove(0);
            NbtCompound summary = life.getCompoundOrEmpty("summary");
            String key = old.getString("part", "unknown") + "/" + old.getString("kinds", "unknown");
            summary.putInt(key, summary.getInt(key, 0) + 1);
            life.put("summary", summary);
        }
        life.put("hits", hits);
        markDirty();
    }
    public NbtCompound corpse(String id) { return section("corpses").getCompoundOrEmpty(id); }
    public boolean isCarrier(String id, String part, String token, String location) {
        NbtCompound slot = corpse(id).getCompoundOrEmpty("carriers").getCompoundOrEmpty(part);
        return !token.isEmpty() && !slot.isEmpty() && slot.getString("token", "").equals(token)
                && slot.getString("location", "").equals(location);
    }
    public boolean claimCarrier(String id, String part, String token, String location) {
        NbtCompound corpse = corpse(id);
        if (corpse.isEmpty() || token.isEmpty()) return false;
        NbtCompound carriers = corpse.getCompoundOrEmpty("carriers");
        NbtCompound slot = new NbtCompound(); slot.putString("token", token); slot.putString("location", location);
        carriers.put(part, slot); corpse.put("carriers", carriers); markDirty(); return true;
    }
    public void retireCarrier(String id, String part) {
        NbtCompound corpse = corpse(id);
        if (corpse.isEmpty()) return;
        corpse.getCompoundOrEmpty("carriers").remove(part); markDirty();
    }
    public String freeze(UUID player, String name, String dimension, long tick, double x, double y, double z, String cause) {
        NbtCompound life = life(player);
        String id = life.getString("corpse", "");
        if (!id.isEmpty()) return id;
        id = UUID.randomUUID().toString();
        NbtCompound corpse = life.copy();
        corpse.putString("name", name);
        corpse.putString("dimension", dimension);
        corpse.putLong("death_tick", tick);
        corpse.putString("death_cause", cause);
        corpse.putDouble("x", x); corpse.putDouble("y", y); corpse.putDouble("z", z);
        section("corpses").put(id, corpse);
        life.putString("corpse", id);
        markDirty();
        return id;
    }
    /** Carpet recreates a living player with the same UUID without vanilla respawn. */
    public void ensureLivingLife(UUID player) {
        NbtCompound lives = section("lives");
        NbtCompound previous = lives.getCompoundOrEmpty(player.toString());
        if (previous.contains("corpse") || previous.getBoolean("spawn_started", false)) newLife(player);
    }
    public void newLife(UUID player) { section("lives").remove(player.toString()); markDirty(); }
}
