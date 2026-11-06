package com.kuilunfuzhe.monvhua.network.forensics;

import com.kuilunfuzhe.monvhua.features.forensics.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.*;

public final class ForensicPackets {
    public record Request(int target, String part, boolean cycle, int requestId) implements CustomPayload {
        public static final Id<Request> ID = new Id<>(Identifier.of("monvhua", "forensic_request"));
        public static final PacketCodec<RegistryByteBuf, Request> CODEC = PacketCodec.of(Request::write, Request::new);
        private Request(RegistryByteBuf buf) { this(buf.readVarInt(), buf.readString(32), buf.readBoolean(), buf.readVarInt()); }
        private void write(RegistryByteBuf buf) { buf.writeVarInt(target); buf.writeString(part, 32); buf.writeBoolean(cycle); buf.writeVarInt(requestId); }
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record Result(int target, String part, int requestId, List<String> lines) implements CustomPayload {
        public static final Id<Result> ID = new Id<>(Identifier.of("monvhua", "forensic_result"));
        public static final PacketCodec<RegistryByteBuf, Result> CODEC = PacketCodec.of(Result::write, Result::new);
        private Result(RegistryByteBuf buf) { this(buf.readVarInt(), buf.readString(32), buf.readVarInt(), readLines(buf)); }
        private static List<String> readLines(RegistryByteBuf buf) {
            int size = buf.readVarInt(); if (size < 0 || size > 512) throw new IllegalArgumentException("Invalid inspection size");
            List<String> lines = new ArrayList<>(); for (int i = 0; i < size; i++) lines.add(buf.readString(512)); return List.copyOf(lines);
        }
        private void write(RegistryByteBuf buf) { buf.writeVarInt(target); buf.writeString(part, 32); buf.writeVarInt(requestId); buf.writeVarInt(lines.size()); for (String line : lines) buf.writeString(line, 512); }
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    private static final Map<UUID, Integer> LAST_REQUEST = new HashMap<>();
    public static void register() {
        PayloadTypeRegistry.playC2S().register(Request.ID, Request.CODEC);
        PayloadTypeRegistry.playS2C().register(Result.ID, Result.CODEC);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_REQUEST.remove(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST_REQUEST.clear());
        ServerPlayNetworking.registerGlobalReceiver(Request.ID, (packet, context) -> context.server().execute(() -> {
            var player = context.player(); int now = context.server().getTicks();
            Integer last = LAST_REQUEST.get(player.getUuid());
            if (last != null && now - last < 3) {
                if (!packet.cycle) ServerPlayNetworking.send(player, new Result(packet.target, packet.part, packet.requestId, List.of("检查过于频繁，请稍后重新选择部位")));
                return;
            }
            LAST_REQUEST.put(player.getUuid(), now);
            if (packet.cycle) {
                if (ForensicConfig.current.enabled && player.isAlive() && !player.isSpectator()) player.sendMessage(Text.literal(WeaponProfiles.cycle(player.getMainHandStack())), true);
            } else {
                var lines = Forensics.inspect(player, packet.target, packet.part);
                ServerPlayNetworking.send(player, new Result(packet.target, packet.part, packet.requestId, lines));
            }
        }));
    }
}
