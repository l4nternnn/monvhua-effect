package com.kuilunfuzhe.monvhua.features.chestlink;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Server-wide, persistent entrance-to-source chest mappings. */
public final class ChestLinkStore extends PersistentState {
    public record Endpoint(String dimension, BlockPos pos) {
        public static final Codec<Endpoint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("dimension").forGetter(Endpoint::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(Endpoint::pos)
        ).apply(instance, Endpoint::new));

        public Endpoint {
            pos = pos.toImmutable();
        }
    }

    public record Mapping(Endpoint entrance, Endpoint source) {
        public static final Codec<Mapping> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Endpoint.CODEC.fieldOf("entrance").forGetter(Mapping::entrance),
                Endpoint.CODEC.fieldOf("source").forGetter(Mapping::source)
        ).apply(instance, Mapping::new));
    }

    public static final Codec<ChestLinkStore> CODEC = Mapping.CODEC.listOf()
            .xmap(ChestLinkStore::new, store -> new ArrayList<>(store.mappings.values()));
    public static final PersistentStateType<ChestLinkStore> TYPE = new PersistentStateType<>(
            "monvhua_chest_links", ChestLinkStore::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<Endpoint, Mapping> mappings = new LinkedHashMap<>();

    public ChestLinkStore() {}

    private ChestLinkStore(List<Mapping> saved) {
        for (Mapping mapping : saved) {
            if (mapping != null && mapping.entrance() != null && mapping.source() != null) {
                mappings.put(mapping.entrance(), mapping);
            }
        }
    }

    public static ChestLinkStore get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
    }

    public Optional<Mapping> find(Endpoint entrance) {
        return Optional.ofNullable(mappings.get(entrance));
    }

    public List<Mapping> all() {
        return List.copyOf(mappings.values());
    }

    public void put(Endpoint entrance, Endpoint source) {
        Mapping next = new Mapping(entrance, source);
        if (!next.equals(mappings.put(entrance, next))) markDirty();
    }
}
