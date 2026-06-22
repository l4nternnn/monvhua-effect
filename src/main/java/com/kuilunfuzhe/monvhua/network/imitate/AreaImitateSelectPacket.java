package com.kuilunfuzhe.monvhua.network.imitate;

import com.kuilunfuzhe.monvhua.features.imitate.ImitateManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public record AreaImitateSelectPacket(String roleName, double centerX, double centerY, double centerZ, double radius) implements CustomPayload {

    public static final Id<AreaImitateSelectPacket> ID = new Id<>(Identifier.of("monvhua", "area_imitate_select"));
    public static final PacketCodec<RegistryByteBuf, AreaImitateSelectPacket> CODEC =
            PacketCodec.of(AreaImitateSelectPacket::write, AreaImitateSelectPacket::new);

    private AreaImitateSelectPacket(RegistryByteBuf buf) {
        this(buf.readString(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeString(roleName);
        buf.writeDouble(centerX);
        buf.writeDouble(centerY);
        buf.writeDouble(centerZ);
        buf.writeDouble(radius);
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
                String roleName = packet.roleName;
                double centerX = packet.centerX;
                double centerY = packet.centerY;
                double centerZ = packet.centerZ;
                double radius = packet.radius;

                ImitateManager.setAreaImitate(player, roleName, centerX, centerY, centerZ, radius);
                Text coloredName = ImitateManager.getColoredRoleName(roleName);
                player.sendMessage(Text.literal("§a已开始区域模仿: ").append(coloredName).append(Text.literal(" §a范围: " + radius + "格")), true);

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