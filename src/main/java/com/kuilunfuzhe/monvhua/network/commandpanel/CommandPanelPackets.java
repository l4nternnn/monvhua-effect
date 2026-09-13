package com.kuilunfuzhe.monvhua.network.commandpanel;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class CommandPanelPackets {
    private CommandPanelPackets() {}
    private static boolean executeRegistered, saveRegistered, requestRegistered, dataRegistered, permissionRegistered;
    public record PermissionS2C(boolean editable) implements CustomPayload { public static final Id<PermissionS2C> ID=new Id<>(Identifier.of("monvhua","command_panel_permission")); public static final PacketCodec<RegistryByteBuf,PermissionS2C> CODEC=PacketCodec.of((p,b)->b.writeBoolean(p.editable),b->new PermissionS2C(b.readBoolean())); public Id<? extends CustomPayload> getId(){return ID;} public static void register(){if(!permissionRegistered){PayloadTypeRegistry.playS2C().register(ID,CODEC);permissionRegistered=true;}} }
    public record ExecuteC2S(String command) implements CustomPayload {
        public static final Id<ExecuteC2S> ID = new Id<>(Identifier.of("monvhua", "command_panel_execute"));
        public static final PacketCodec<RegistryByteBuf, ExecuteC2S> CODEC = PacketCodec.of((p,b)->b.writeString(p.command,32767), b->new ExecuteC2S(b.readString(32767)));
        public Id<? extends CustomPayload> getId(){return ID;}
        public static void register(){if(!executeRegistered){PayloadTypeRegistry.playC2S().register(ID,CODEC);executeRegistered=true;}}
    }
    public record SaveC2S(long revision, String json) implements CustomPayload {
        public static final Id<SaveC2S> ID = new Id<>(Identifier.of("monvhua", "command_panel_save"));
        public static final PacketCodec<RegistryByteBuf, SaveC2S> CODEC = PacketCodec.of((p,b)->{b.writeVarLong(p.revision);b.writeString(p.json,32767);}, b->new SaveC2S(b.readVarLong(),b.readString(32767)));
        public Id<? extends CustomPayload> getId(){return ID;}
        public static void register(){if(!saveRegistered){PayloadTypeRegistry.playC2S().register(ID,CODEC);saveRegistered=true;}}
    }
    public record RequestC2S() implements CustomPayload { public static final Id<RequestC2S> ID=new Id<>(Identifier.of("monvhua","command_panel_request")); public static final PacketCodec<RegistryByteBuf,RequestC2S> CODEC=PacketCodec.unit(new RequestC2S()); public Id<? extends CustomPayload> getId(){return ID;} public static void register(){if(!requestRegistered){PayloadTypeRegistry.playC2S().register(ID,CODEC);requestRegistered=true;}} }
    public record DataS2C(long revision, String json) implements CustomPayload { public static final Id<DataS2C> ID=new Id<>(Identifier.of("monvhua","command_panel_data")); public static final PacketCodec<RegistryByteBuf,DataS2C> CODEC=PacketCodec.of((p,b)->{b.writeVarLong(p.revision);b.writeString(p.json,32767);},b->new DataS2C(b.readVarLong(),b.readString(32767))); public Id<? extends CustomPayload> getId(){return ID;} public static void register(){if(!dataRegistered){PayloadTypeRegistry.playS2C().register(ID,CODEC);dataRegistered=true;}} }
}
