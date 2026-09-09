package com.kuilunfuzhe.monvhua.features.glitch;

import com.mojang.serialization.Codec;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Stored separately per dimension; this intentionally never mixes plane definitions across worlds. */
public final class GlitchPlaneStore extends PersistentState {
    private static final Codec<GlitchPlaneStore> CODEC = GlitchPlane.CODEC.listOf()
            .xmap(GlitchPlaneStore::new, store -> store.planes);
    private static final PersistentStateType<GlitchPlaneStore> TYPE = new PersistentStateType<>(
            "monvhua_glitch_planes", GlitchPlaneStore::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    private final List<GlitchPlane> planes;

    public GlitchPlaneStore() { this(List.of()); }
    private GlitchPlaneStore(List<GlitchPlane> planes) { this.planes = new ArrayList<>(planes == null ? List.of() : planes); }
    public static GlitchPlaneStore get(ServerWorld world) { return world.getPersistentStateManager().getOrCreate(TYPE); }
    public List<GlitchPlane> all() { return planes.stream().sorted(Comparator.comparing(GlitchPlane::name)).toList(); }
    public Optional<GlitchPlane> find(String name) { return planes.stream().filter(p -> p.name().equalsIgnoreCase(name)).findFirst(); }
    public boolean add(GlitchPlane plane) { if (find(plane.name()).isPresent()) return false; planes.add(plane); markDirty(); return true; }
    public boolean replace(GlitchPlane plane) { for (int i = 0; i < planes.size(); i++) if (planes.get(i).id().equals(plane.id())) { planes.set(i, plane); markDirty(); return true; } return false; }
    public Optional<GlitchPlane> remove(String name) { Optional<GlitchPlane> found = find(name); found.ifPresent(p -> { planes.remove(p); markDirty(); }); return found; }
}
