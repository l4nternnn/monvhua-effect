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
    private final Map<String,double[]> remainders=new HashMap<>();
    private final Set<String> retired = new HashSet<>();
    private final Set<String> bottleWashed = new HashSet<>();
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<Long, Set<String>> chunks = new HashMap<>();
    private final Map<BlockPos, Set<String>> blocks = new HashMap<>();
    public BloodTraceStore() {}
    private BloodTraceStore(NbtCompound root) {
        if (root.getInt("version", 1) < 1 || root.getInt("version", 1) > 3) throw new IllegalStateException("Unsupported blood trace version");
        var fractional=root.getCompoundOrEmpty("cleaning_fractions");
        for(String id:fractional.getKeys()){
            long[] bits=fractional.getLongArray(id).orElse(new long[0]);
            if(bits.length==256){double[] values=new double[256];for(int i=0;i<256;i++){double v=Double.longBitsToDouble(bits[i]);values[i]=Double.isFinite(v)&&v>=0&&v<1?v:0;}remainders.put(id,values);}
        }
        for(NbtElement id:root.getListOrEmpty("retired")) retired.add(id.asString().orElse(""));
        for(NbtElement id:root.getListOrEmpty("bottle_washed")) bottleWashed.add(id.asString().orElse(""));
        for (NbtElement element : root.getListOrEmpty("patches")) if (element instanceof NbtCompound n) {
            BloodPatch patch = BloodPatch.read(n);
            if (!patch.id().isEmpty() && Float.isFinite(patch.radius()) && patch.radius() > 0 && patch.radius() <= .85f
                    && Float.isFinite(patch.u()) && Float.isFinite(patch.v())) {
                put(new Entry(patch, n.getString("source", ""), n.getString("event", ""), n.getString("support", ""), n.getBoolean("directional", false)));
            }
        }
    }
    private NbtCompound write() {
        NbtCompound root = new NbtCompound(); root.putInt("version", 3); NbtList list = new NbtList();
        for (Entry entry : entries.values()) {
            NbtCompound n = entry.patch.write(); n.putString("source", entry.source); n.putString("event", entry.event);
            n.putString("support", entry.support); n.putBoolean("directional", entry.directional); list.add(n);
        }
        var fractional=new NbtCompound();remainders.forEach((id,values)->{long[] bits=new long[256];for(int i=0;i<256;i++)bits[i]=Double.doubleToLongBits(values[i]);fractional.putLongArray(id,bits);});root.put("cleaning_fractions",fractional);
        root.put("patches", list); NbtList washed=new NbtList();for(String id:bottleWashed)if(entries.containsKey(id))washed.add(NbtString.of(id));root.put("bottle_washed",washed);NbtList dead=new NbtList();for(String id:retired)dead.add(NbtString.of(id));root.put("retired",dead);return root;
    }
    public boolean bottleWashed(String id){return bottleWashed.contains(id);}
    public void markBottleWashed(String id){if(entries.containsKey(id)){bottleWashed.add(id);markDirty();}}
    public static BloodTraceStore get(ServerWorld world) { return world.getPersistentStateManager().getOrCreate(TYPE); }
    public Collection<Entry> at(BlockPos pos) { return blocks.getOrDefault(pos, Set.of()).stream().map(entries::get).filter(Objects::nonNull).toList(); }
    public Collection<Entry> chunk(long key) { return chunks.getOrDefault(key, Set.of()).stream().map(entries::get).filter(Objects::nonNull).toList(); }
    public Entry entry(String id) { return entries.get(id); }
    public Set<Long> chunkKeys() { return Set.copyOf(chunks.keySet()); }
    public double[] cleaningRemainders(String id){return remainders.getOrDefault(id,new double[256]).clone();}
    public void cleaningRemainders(String id,double[] values){if(!entries.containsKey(id)||values.length!=256)throw new IllegalArgumentException("Invalid cleaning target");remainders.put(id,values.clone());markDirty();}
    public Collection<Entry> entries() { return List.copyOf(entries.values()); }
    public int size() { return entries.size(); }
    public boolean submit(Entry entry) {
        BloodPatch p = entry.patch;
        if(retired.contains(p.id()))return false;
        Entry old = entries.get(p.id());
        if (old != null && (!old.patch.pos().equals(p.pos()) || (p.revision() < old.patch.revision() || (p.revision() == old.patch.revision() && p.treatment().equals(old.patch.treatment()))))) return false;
        if (old == null) {
            if(retired.size()+entries.size()>=65536)return false;
            var config = ForensicConfig.current.blood;
            if (entries.size() >= config.worldPatchLimit || chunks.getOrDefault(new ChunkPos(p.pos()).toLong(), Set.of()).size() >= config.chunkPatchLimit
                    || at(p.pos()).stream().filter(e -> e.patch.face() == p.face()).count() >= 64) return false;
        }
        if(old != null && entry.patch.treatment().isEmpty() && !old.patch.treatment().isEmpty()) {
            char[] mask=old.patch.treatment().toCharArray();
            double added=Math.max(0,entry.patch.mass()-new BloodPatch(old.patch.id(),old.patch.pos(),old.patch.face(),old.patch.u(),old.patch.v(),old.patch.radius(),old.patch.kind(),old.patch.seed(),old.patch.revision()).mass());
            double fraction=Math.min(1,added/Math.max(1,entry.patch.mass()));
            for(int i=0;i<mask.length;i++)mask[i]=(char)Math.min(255,mask[i]+Math.round((255-mask[i])*fraction));
            entry=new Entry(entry.patch.treated(new String(mask),entry.patch.revision()),entry.source,entry.event,entry.support,entry.directional);
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
        retired.add(id);remainders.remove(id);bottleWashed.remove(id);
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
