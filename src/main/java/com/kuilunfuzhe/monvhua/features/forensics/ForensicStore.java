package com.kuilunfuzhe.monvhua.features.forensics;

import com.mojang.serialization.Codec;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

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
    /** Append-only bounded post-death history; never rewrites original wounds, death, or blood balances. */
    public boolean appendPostmortem(String id, NbtCompound hit) {
        NbtCompound record = corpse(id);
        if (record.isEmpty() || hit.getString("id", "").isEmpty() || !hit.contains("wound_surface")
                || !Float.isFinite(hit.getFloat("amount", 0)) || hit.getFloat("amount", 0) <= 0) return false;
        NbtList added = record.getListOrEmpty("postmortem_hits");
        if (added.size() >= PostmortemWounds.MAX_WOUNDS) return false;
        for (NbtElement e : added) if (e instanceof NbtCompound h && h.getString("id", "").equals(hit.getString("id", ""))) return false;
        NbtCompound copy = hit.copy();
        copy.putBoolean("postmortem", true); copy.remove("death_trigger"); copy.remove("potentially_lethal");
        added.add(copy); record.put("postmortem_hits", added);
        NbtList candidates = record.getListOrEmpty(record.contains("inspection_hits") ? "inspection_hits" : "hits").copy();
        candidates.add(copy.copy());
        NbtList strongest = new NbtList();
        for (String part : Forensics.PARTS) for (NbtCompound h : InspectionRecords.strongest(candidates, part, 3)) strongest.add(h.copy());
        record.put("inspection_hits", strongest); markDirty(); return true;
    }
    public void collect(UUID viewer, String corpseId, String part, List<NbtCompound> records) {
        NbtCompound viewers = section("collections");
        NbtCompound viewerRoot = viewers.getCompoundOrEmpty(viewer.toString());
        NbtCompound corpseRoot = viewerRoot.getCompoundOrEmpty(corpseId);
        NbtList list = new NbtList();
        for (NbtCompound record : records) list.add(record.copy());
        corpseRoot.put(part, list); viewerRoot.put(corpseId, corpseRoot); viewers.put(viewer.toString(), viewerRoot); markDirty();
    }
    public void disclose(UUID viewer, String corpseId, String part, String evidenceId) {
        if(evidenceId==null||evidenceId.isEmpty())return;
        NbtCompound corpse=corpse(corpseId);if(corpse.isEmpty())return;
        NbtCompound found=null;
        for(String bucket:List.of("hits","postmortem_hits")) for(NbtElement e:corpse.getListOrEmpty(bucket))
            if(e instanceof NbtCompound h&&evidenceId.equals(h.getString("id",""))){found=h.copy();break;}
        if(found==null)return;
        found.putString("disclosed_source","showcase");
        NbtCompound root=section("disclosed");NbtCompound viewerRoot=root.getCompoundOrEmpty(viewer.toString());
        NbtCompound caseRoot=viewerRoot.getCompoundOrEmpty(corpseId);NbtList list=caseRoot.getListOrEmpty(part);
        for(NbtElement e:list)if(e instanceof NbtCompound h&&evidenceId.equals(h.getString("id","")))return;
        list.add(found);caseRoot.put(part,list);viewerRoot.put(corpseId,caseRoot);root.put(viewer.toString(),viewerRoot);markDirty();
    }
    public List<NbtCompound> disclosed(UUID viewer,String corpseId,String part){
        NbtList list=section("disclosed").getCompoundOrEmpty(viewer.toString()).getCompoundOrEmpty(corpseId).getListOrEmpty(part);
        List<NbtCompound> out=new ArrayList<>();for(NbtElement e:list)if(e instanceof NbtCompound h)out.add(h.copy());return out;
    }

    public List<NbtCompound> collected(UUID viewer, String corpseId, String part) {
        NbtList list = section("collections").getCompoundOrEmpty(viewer.toString())
                .getCompoundOrEmpty(corpseId).getListOrEmpty(part);
        List<NbtCompound> result = new ArrayList<>();
        for (NbtElement element : list) if (element instanceof NbtCompound record) result.add(record.copy());
        return result;
    }
    public void observe(UUID viewer, String corpseId, NbtCompound snapshot) {
        NbtCompound viewers = section("observations"), entries = viewers.getCompoundOrEmpty(viewer.toString());
        entries.put(corpseId, snapshot.copy()); viewers.put(viewer.toString(), entries); markDirty();
    }
    public java.util.Set<String> collectedCaseIds(UUID viewer) {
        return java.util.Set.copyOf(section("collections").getCompoundOrEmpty(viewer.toString()).getKeys());
    }
    public NbtCompound observations(UUID viewer) {
        return section("observations").getCompoundOrEmpty(viewer.toString()).copy();
    }
    public List<NbtCompound> corpseSnapshots() {
        List<NbtCompound> result = new ArrayList<>();
        NbtCompound corpses = section("corpses");
        for (String id : corpses.getKeys()) {
            NbtCompound snapshot = corpses.getCompoundOrEmpty(id).copy();
            snapshot.putString("corpse_id", id);
            result.add(snapshot);
        }
        return result;
    }
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
