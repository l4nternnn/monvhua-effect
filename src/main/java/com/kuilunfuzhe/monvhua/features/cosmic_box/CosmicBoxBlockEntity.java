package com.kuilunfuzhe.monvhua.features.cosmic_box;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.Codec;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Uuids;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CosmicBoxBlockEntity extends BlockEntity {
    private static final String TARGETS_KEY = "targets_json";
    private static final String BEAM_STYLE_KEY = "beam_style";
    private static final String BEAM_ACTIVE_START_TICK_KEY = "beam_active_start_tick";
    private static final int MAX_STORED_TARGETS = 128;

    private List<TargetRef> targets = List.of();
    private boolean beamActive;
    private long beamActiveStartTick;
    private CosmicBoxBeamStyle beamStyle = CosmicBoxBeamStyle.COSMIC;

    public CosmicBoxBlockEntity(BlockPos pos, BlockState state) {
        super(CosmicBoxBlockEntities.COSMIC_BOX_BLOCK_ENTITY, pos, state);
    }

    public List<TargetRef> getTargets() {
        return targets;
    }

    public boolean hasTarget() {
        return !targets.isEmpty();
    }

    public boolean isBeamActive() {
        return beamActive;
    }

    public long getBeamActiveStartTick() {
        return beamActiveStartTick;
    }

    public CosmicBoxBeamStyle getBeamStyle() {
        return beamStyle;
    }

    public void setBeamStyle(CosmicBoxBeamStyle beamStyle) {
        this.beamStyle = beamStyle == null ? CosmicBoxBeamStyle.COSMIC : beamStyle;
        sync();
    }

    public void setTargets(List<TargetRef> targets) {
        List<TargetRef> sanitized = new ArrayList<>();
        for (TargetRef target : targets) {
            if (target != null && target.uuid() != null && sanitized.size() < MAX_STORED_TARGETS) {
                sanitized.add(new TargetRef(target.uuid(), target.name()));
            }
        }
        this.targets = Collections.unmodifiableList(sanitized);
        sync();
    }

    public void setBeamActive(boolean beamActive) {
        if (beamActive && !this.beamActive && world != null) {
            this.beamActiveStartTick = world.getTime();
        }
        this.beamActive = beamActive;
        sync();
    }

    public void toggleBeam() {
        setBeamActive(!beamActive);
    }

    private void sync() {
        markDirty();
        if (world instanceof ServerWorld serverWorld) {
            serverWorld.getChunkManager().markForUpdate(pos);
        }
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }

    @Override
    protected void readData(ReadView view) {
        super.readData(view);
        this.targets = readTargets(view);
        this.beamActive = view.read("beam_active", Codec.BOOL).orElse(false);
        this.beamActiveStartTick = view.read(BEAM_ACTIVE_START_TICK_KEY, Codec.LONG).orElse(0L);
        this.beamStyle = CosmicBoxBeamStyle.byId(view.read(BEAM_STYLE_KEY, Codec.STRING).orElse(CosmicBoxBeamStyle.COSMIC.id()));
    }

    @Override
    protected void writeData(WriteView view) {
        super.writeData(view);
        view.put(TARGETS_KEY, Codec.STRING, writeTargetsJson(targets));
        view.put("beam_active", Codec.BOOL, beamActive);
        view.put(BEAM_ACTIVE_START_TICK_KEY, Codec.LONG, beamActiveStartTick);
        view.put(BEAM_STYLE_KEY, Codec.STRING, beamStyle.id());
    }

    private static List<TargetRef> readTargets(ReadView view) {
        String json = view.read(TARGETS_KEY, Codec.STRING).orElse("");
        List<TargetRef> parsed = parseTargetsJson(json);
        if (!parsed.isEmpty()) {
            return parsed;
        }

        Optional<UUID> legacyUuid = view.read("target_uuid", Uuids.CODEC);
        if (legacyUuid.isPresent()) {
            String legacyName = view.read("target_name", Codec.STRING).orElse("");
            return List.of(new TargetRef(legacyUuid.get(), legacyName));
        }
        return List.of();
    }

    private static String writeTargetsJson(List<TargetRef> targets) {
        JsonArray array = new JsonArray();
        for (TargetRef target : targets) {
            JsonObject object = new JsonObject();
            object.addProperty("uuid", target.uuid().toString());
            object.addProperty("name", target.name());
            array.add(object);
        }
        return array.toString();
    }

    private static List<TargetRef> parseTargetsJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }

        List<TargetRef> parsed = new ArrayList<>();
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonArray()) {
                return List.of();
            }
            for (JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonObject() || parsed.size() >= MAX_STORED_TARGETS) {
                    continue;
                }
                JsonObject object = element.getAsJsonObject();
                if (!object.has("uuid")) {
                    continue;
                }
                UUID uuid = UUID.fromString(object.get("uuid").getAsString());
                String name = object.has("name") ? object.get("name").getAsString() : "";
                parsed.add(new TargetRef(uuid, name));
            }
        } catch (IllegalArgumentException | JsonSyntaxException ignored) {
            return List.of();
        }
        return Collections.unmodifiableList(parsed);
    }

    public record TargetRef(UUID uuid, String name) {
        public TargetRef(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name == null ? "" : name;
        }
    }
}
