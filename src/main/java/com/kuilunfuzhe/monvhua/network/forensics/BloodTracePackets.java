package com.kuilunfuzhe.monvhua.network.forensics;

import com.kuilunfuzhe.monvhua.features.forensics.ForensicConfig;
import com.kuilunfuzhe.monvhua.features.forensics.blood.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import java.util.*;

public final class BloodTracePackets {
    // At most 96 compact patches (< 16 KiB) and 256 removal IDs (< 10 KiB) per packet.
    public record Sync(String dimension, long generation, boolean clear, List<BloodPatch> patches, List<String> removals) implements CustomPayload {
        public static final Id<Sync> ID = new Id<>(Identifier.of("monvhua", "blood_sync"));
        public static final PacketCodec<RegistryByteBuf, Sync> CODEC = PacketCodec.of(Sync::write, Sync::new);
        private Sync(RegistryByteBuf b) { this(b.readString(128), b.readLong(), b.readBoolean(), readPatches(b), readRemovals(b)); }
        private static int count(RegistryByteBuf b, int max) { int n = b.readVarInt(); if (n < 0 || n > max) throw new IllegalArgumentException("Invalid blood packet size"); return n; }
        private static List<BloodPatch> readPatches(RegistryByteBuf b) {
            int n = count(b, 96); List<BloodPatch> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                var p = new BloodPatch(b.readString(36), b.readBlockPos(), b.readEnumConstant(Direction.class), b.readFloat(), b.readFloat(), b.readFloat(), b.readUnsignedByte(), b.readLong(), b.readVarInt());
                if (!Float.isFinite(p.u()) || !Float.isFinite(p.v()) || Math.abs(p.u()) > 2 || Math.abs(p.v()) > 2
                        || !Float.isFinite(p.radius()) || p.radius() <= 0 || p.radius() > .85f || p.kind() > 1 || p.revision() < 1) throw new IllegalArgumentException("Invalid blood geometry");
                list.add(p);
            }
            return List.copyOf(list);
        }
        private static List<String> readRemovals(RegistryByteBuf b) { int n = count(b, 256); List<String> list = new ArrayList<>(n); for (int i = 0; i < n; i++) list.add(b.readString(36)); return List.copyOf(list); }
        private void write(RegistryByteBuf b) {
            b.writeString(dimension, 128); b.writeLong(generation); b.writeBoolean(clear); b.writeVarInt(patches.size());
            for (var p : patches) { b.writeString(p.id(), 36); b.writeBlockPos(p.pos()); b.writeEnumConstant(p.face()); b.writeFloat(p.u()); b.writeFloat(p.v()); b.writeFloat(p.radius()); b.writeByte(p.kind()); b.writeLong(p.seed()); b.writeVarInt(p.revision()); }
            b.writeVarInt(removals.size()); for (String id : removals) b.writeString(id, 36);
        }
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record Inspect() implements CustomPayload {
        public static final Id<Inspect> ID = new Id<>(Identifier.of("monvhua", "blood_inspect"));
        public static final PacketCodec<RegistryByteBuf, Inspect> CODEC = PacketCodec.of((p, b) -> {}, b -> new Inspect());
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    private static final Map<UUID, Window> WINDOWS = new HashMap<>();
    private static final Map<UUID, Integer> REQUESTS = new HashMap<>();
    private static long sequence;
    private static final class Window {
        final String dimension; final long generation = ++sequence;
        boolean clear = true; final Map<String, Integer> known = new HashMap<>();
        Window(String dimension) { this.dimension = dimension; }
    }
    public static void register() {
        PayloadTypeRegistry.playS2C().register(Sync.ID, Sync.CODEC);
        PayloadTypeRegistry.playC2S().register(Inspect.ID, Inspect.CODEC);
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> { WINDOWS.remove(h.player.getUuid()); REQUESTS.remove(h.player.getUuid()); });
        net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> WINDOWS.remove(player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> { WINDOWS.clear(); REQUESTS.clear(); });
        ServerPlayNetworking.registerGlobalReceiver(Inspect.ID, (p, c) -> c.server().execute(() -> BloodTraceService.safe(() -> {
            int tick = c.server().getTicks(); UUID id = c.player().getUuid();
            if (tick - REQUESTS.getOrDefault(id, tick - 10) < 5) return;
            REQUESTS.put(id, tick);
            for (String line : BloodInspection.inspect(c.player())) c.player().sendMessage(Text.literal(line), false);
        })));
    }
    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 5 != 0) return;
        Map<ServerWorld, Set<BlockPos>> validated = new IdentityHashMap<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!ServerPlayNetworking.canSend(player, Sync.ID)) continue;
            sync(player, validated.computeIfAbsent((ServerWorld) player.getWorld(), w -> new HashSet<>()));
        }
    }
    private static void sync(ServerPlayerEntity player, Set<BlockPos> validated) {
        ServerWorld world = (ServerWorld) player.getWorld(); String dimension = world.getRegistryKey().getValue().toString();
        Window window = WINDOWS.get(player.getUuid());
        if (window == null || !window.dimension.equals(dimension)) { window = new Window(dimension); WINDOWS.put(player.getUuid(), window); }
        Map<String, BloodPatch> wanted = new LinkedHashMap<>();
        if (BloodRules.enabled()) {
            var store = BloodTraceStore.get(world); ChunkPos center = player.getChunkPos();
            int radius = Math.min(ForensicConfig.current.blood.syncRadius, player.getServer().getPlayerManager().getViewDistance());
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                if (!world.isChunkLoaded(center.x + x, center.z + z)) continue;
                for (var e : store.chunk(ChunkPos.toLong(center.x + x, center.z + z))) {
                    if (validated.add(e.patch().pos())) BloodTraceService.validate(world, e.patch().pos());
                    if (store.entry(e.patch().id()) != null) wanted.put(e.patch().id(), e.patch());
                }
            }
        }
        List<String> removals = new ArrayList<>();
        for (String id : window.known.keySet()) if (!wanted.containsKey(id) && removals.size() < 256) removals.add(id);
        List<BloodPatch> updates = new ArrayList<>();
        for (var p : wanted.values()) if (window.known.getOrDefault(p.id(), 0) < p.revision() && updates.size() < 96) updates.add(p);
        if (window.clear || !updates.isEmpty() || !removals.isEmpty()) {
            ServerPlayNetworking.send(player, new Sync(dimension, window.generation, window.clear, List.copyOf(updates), List.copyOf(removals)));
            window.clear = false; removals.forEach(window.known::remove); for (var p : updates) window.known.put(p.id(), p.revision());
        }
    }
}
