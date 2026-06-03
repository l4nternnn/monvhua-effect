package com.kuilunfuzhe.monvhua.network.imitate;

import com.kuilunfuzhe.monvhua.features.imitate.ImitateManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public record ImitateSelectPacket(String roleName) implements CustomPayload {

    public static final Id<ImitateSelectPacket> ID = new Id<>(Identifier.of("monvhua", "imitate_select"));
    public static final PacketCodec<RegistryByteBuf, ImitateSelectPacket> CODEC =
            PacketCodec.of(ImitateSelectPacket::write, ImitateSelectPacket::new);

    private ImitateSelectPacket(RegistryByteBuf buf) {
        this(buf.readString());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeString(roleName);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static boolean registered = false;

    public static void register() {
        if (!registered) {
            net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(ID, CODEC);
            registered = true;
        }
    }

    public static void registerHandler() {
        ServerPlayNetworking.registerGlobalReceiver(ID, (packet, context) -> {
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                String roleName = packet.roleName();

                if ("cancel".equals(roleName)) {
                    ImitateManager.clearImitate(player);
                    player.sendMessage(Text.literal("§a已取消模仿"), true);
                } else if ("reset".equals(roleName)) {
                    ImitateManager.clearImitate(player);
                    player.sendMessage(Text.literal("§e已重置模仿效果"), true);
                } else {
                    ImitateManager.setImitate(player, roleName);
                    Text coloredName = ImitateManager.getColoredRoleName(roleName);
                    player.sendMessage(Text.literal("§a已开始模仿: ").append(coloredName), true);
                }

                syncToClient(player);
            });
        });
    }

    private static void syncToClient(ServerPlayerEntity player) {
        String roleName = ImitateManager.getImitateName(player);
        long endTime = 0;
        long switchCooldownEnd = 0;
        long soundWaveCooldownEnd = 0;

        if (roleName != null) {
            endTime = ImitateManager.getImitateEndTime(player.getUuid());
        }

        switchCooldownEnd = ImitateManager.getSwitchCooldownEndTime(player.getUuid());
        soundWaveCooldownEnd = ImitateManager.getSoundWaveCooldownEndTime(player.getUuid());

        ServerPlayNetworking.send(player, new ImitateSyncS2CPacket(roleName, endTime, switchCooldownEnd, soundWaveCooldownEnd));
    }
}