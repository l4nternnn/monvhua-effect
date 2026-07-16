package com.kuilunfuzhe.monvhua.features.portal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class PortalGroupStore extends PersistentState {
    private static final int MAX_GROUP_NAME = 32;
    private static final int MAX_ENDPOINTS = 2;

    private static final Codec<StoredGroup> STORED_GROUP_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(StoredGroup::id),
            BlockPos.CODEC.listOf().fieldOf("endpoints").forGetter(StoredGroup::endpoints)
    ).apply(instance, StoredGroup::new));

    public static final Codec<PortalGroupStore> CODEC = STORED_GROUP_CODEC.listOf()
            .xmap(PortalGroupStore::new, PortalGroupStore::toStoredGroups);

    public static final PersistentStateType<PortalGroupStore> TYPE = new PersistentStateType<>(
            "monvhua_portal_groups",
            PortalGroupStore::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Map<String, LinkedHashSet<BlockPos>> groups = new LinkedHashMap<>();

    public PortalGroupStore() {
    }

    private PortalGroupStore(List<StoredGroup> storedGroups) {
        boolean changed = false;
        for (StoredGroup stored : storedGroups) {
            String id = sanitize(stored.id());
            if (id.isEmpty()) {
                changed = true;
                continue;
            }
            LinkedHashSet<BlockPos> endpoints = groups.computeIfAbsent(id, ignored -> new LinkedHashSet<>());
            for (BlockPos endpoint : stored.endpoints()) {
                if (endpoint != null && endpoints.size() < MAX_ENDPOINTS) {
                    endpoints.add(endpoint.toImmutable());
                }
            }
            changed |= endpoints.size() != stored.endpoints().size();
        }
        if (changed) {
            markDirty();
        }
    }

    public static PortalGroupStore get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE);
    }

    public List<String> names() {
        return new ArrayList<>(groups.keySet());
    }

    public List<BlockPos> endpoints(String groupId) {
        LinkedHashSet<BlockPos> endpoints = groups.get(sanitize(groupId));
        return endpoints == null ? List.of() : List.copyOf(endpoints);
    }

    public void ensureGroup(String groupId) {
        String id = sanitize(groupId);
        if (!id.isEmpty() && !groups.containsKey(id)) {
            groups.put(id, new LinkedHashSet<>());
            markDirty();
        }
    }

    public void setEndpoints(String groupId, Collection<BlockPos> positions) {
        String id = sanitize(groupId);
        if (id.isEmpty()) {
            return;
        }
        LinkedHashSet<BlockPos> next = new LinkedHashSet<>();
        if (positions != null) {
            for (BlockPos position : positions) {
                if (position != null && next.size() < MAX_ENDPOINTS) {
                    next.add(position.toImmutable());
                }
            }
        }
        LinkedHashSet<BlockPos> previous = groups.put(id, next);
        if (previous == null || !previous.equals(next)) {
            markDirty();
        }
    }

    public void removeEndpoint(BlockPos pos) {
        if (pos == null) {
            return;
        }
        BlockPos immutable = pos.toImmutable();
        boolean changed = false;
        for (LinkedHashSet<BlockPos> endpoints : groups.values()) {
            changed |= endpoints.remove(immutable);
        }
        if (changed) {
            markDirty();
        }
    }

    public void deleteGroup(String groupId) {
        if (groups.remove(sanitize(groupId)) != null) {
            markDirty();
        }
    }

    private List<StoredGroup> toStoredGroups() {
        List<StoredGroup> stored = new ArrayList<>();
        for (Map.Entry<String, LinkedHashSet<BlockPos>> entry : groups.entrySet()) {
            stored.add(new StoredGroup(entry.getKey(), List.copyOf(entry.getValue())));
        }
        return stored;
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > MAX_GROUP_NAME ? trimmed.substring(0, MAX_GROUP_NAME) : trimmed;
    }

    private record StoredGroup(String id, List<BlockPos> endpoints) {
        private StoredGroup {
            endpoints = endpoints == null ? List.of() : endpoints;
        }
    }
}
