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
import net.minecraft.entity.Entity;
import java.util.*;

public final class ForensicPackets {
    public record CarryRequest(int target, boolean release) implements CustomPayload {
        public static final Id<CarryRequest> ID = new Id<>(Identifier.of("monvhua", "forensic_carry"));
        public static final PacketCodec<RegistryByteBuf, CarryRequest> CODEC = PacketCodec.of(CarryRequest::write, CarryRequest::new);
        private CarryRequest(RegistryByteBuf buf) { this(buf.readVarInt(), buf.readBoolean()); }
        private void write(RegistryByteBuf buf) { buf.writeVarInt(target); buf.writeBoolean(release); }
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
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
    public record SummaryRequest(String corpse, String part, int requestId) implements CustomPayload {
        public static final Id<SummaryRequest> ID = new Id<>(Identifier.of("monvhua", "forensic_summary_request"));
        public static final PacketCodec<RegistryByteBuf, SummaryRequest> CODEC = PacketCodec.of(SummaryRequest::write, SummaryRequest::new);
        private SummaryRequest(RegistryByteBuf buf) { this(buf.readString(64), buf.readString(32), buf.readVarInt()); }
        private void write(RegistryByteBuf buf) { buf.writeString(corpse, 64); buf.writeString(part, 32); buf.writeVarInt(requestId); }
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record SummaryResult(String corpse, String part, int requestId, net.minecraft.nbt.NbtCompound snapshot) implements CustomPayload {
        public static final Id<SummaryResult> ID = new Id<>(Identifier.of("monvhua", "forensic_summary_result"));
        public static final PacketCodec<RegistryByteBuf, SummaryResult> CODEC = PacketCodec.of(SummaryResult::write, SummaryResult::new);
        private SummaryResult(RegistryByteBuf buf) { this(buf.readString(64), buf.readString(32), buf.readVarInt(), Objects.requireNonNull(buf.readNbt())); }
        private void write(RegistryByteBuf buf) { buf.writeString(corpse,64); buf.writeString(part,32); buf.writeVarInt(requestId); buf.writeNbt(snapshot); }
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    private static final Map<UUID, Integer> LAST_REQUEST = new HashMap<>();
    public static void register() {
        PayloadTypeRegistry.playC2S().register(CarryRequest.ID, CarryRequest.CODEC);
        PayloadTypeRegistry.playC2S().register(Request.ID, Request.CODEC);
        PayloadTypeRegistry.playS2C().register(Result.ID, Result.CODEC);
        PayloadTypeRegistry.playC2S().register(SummaryRequest.ID, SummaryRequest.CODEC);
        PayloadTypeRegistry.playS2C().register(SummaryResult.ID, SummaryResult.CODEC);
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
                java.util.List<String> lines;
                if (packet.part.equals("__stains")) {
                    Entity target = packet.target == -1 ? player : player.getWorld().getEntityById(packet.target);
                    lines = target instanceof net.minecraft.server.network.ServerPlayerEntity other
                            ? com.kuilunfuzhe.monvhua.features.forensics.blood.BloodTransferService.inspect(player, other) : List.of();
                } else lines = Forensics.inspect(player, packet.target, packet.part);
                ServerPlayNetworking.send(player, new Result(packet.target, packet.part, packet.requestId, lines));
            }
        }));
        ServerPlayNetworking.registerGlobalReceiver(SummaryRequest.ID, (packet, context) -> context.server().execute(() -> {
            var player = context.player();
            if (!ForensicConfig.current.enabled || !player.isAlive() || player.isSpectator()) return;
            String part = Forensics.PARTS.contains(packet.part()) ? packet.part() : "torso";
            int now = context.server().getTicks();
            Integer last = LAST_REQUEST.put(player.getUuid(), now);
            if (last != null && now - last < 2) {
                var busy = new net.minecraft.nbt.NbtCompound(); busy.putString("message", "请求过于频繁，请重新选择");
                ServerPlayNetworking.send(player, new SummaryResult(packet.corpse(), part, packet.requestId(), busy)); return;
            }
            ServerPlayNetworking.send(player, new SummaryResult(packet.corpse(), part, packet.requestId(),
                    EvidenceSnapshots.summary(player, packet.corpse())));
        }));
        ServerPlayNetworking.registerGlobalReceiver(CarryRequest.ID, (packet, context) -> context.server().execute(() -> {
            var player = context.player();
            if (!ForensicConfig.current.enabled || !player.isAlive() || player.isSpectator()) return;
            var message = com.kuilunfuzhe.monvhua.features.forensics.CorpseCarryService.handle(player, packet.target(), packet.release());
            if (message != null) player.sendMessage(Text.literal(message), true);
        }));
    }
}
