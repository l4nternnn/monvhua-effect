package com.kuilunfuzhe.monvhua.network.commandpanel;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class CommandPanelPackets {
    private CommandPanelPackets() {}
    private static boolean executeRegistered, requestRegistered, dataRegistered, permissionRegistered, uploadRegistered, sharedRegistered, decisionRegistered, syncRequestRegistered, viewRegistered, rosterRegistered, viewResultRegistered, reloadRegistered;
    public record PanelViewC2S(byte operation, long sessionId, int requestSeq, String roleTag, String playerUuid) implements CustomPayload {
        public static final Id<PanelViewC2S> ID=new Id<>(Identifier.of("monvhua","command_panel_view"));
        public static final PacketCodec<RegistryByteBuf,PanelViewC2S> CODEC=PacketCodec.of((p,b)->{b.writeByte(p.operation);b.writeVarLong(p.sessionId);b.writeVarInt(p.requestSeq);b.writeString(p.roleTag,64);b.writeString(p.playerUuid,40);},b->new PanelViewC2S(b.readByte(),b.readVarLong(),b.readVarInt(),b.readString(64),b.readString(40)));
        public Id<? extends CustomPayload> getId(){return ID;} public static void register(){if(!viewRegistered){PayloadTypeRegistry.playC2S().register(ID,CODEC);viewRegistered=true;}}
    }
    public record PanelRosterS2C(long sessionId,long revision,String entries) implements CustomPayload {
        public static final Id<PanelRosterS2C> ID=new Id<>(Identifier.of("monvhua","command_panel_roster"));
        public static final PacketCodec<RegistryByteBuf,PanelRosterS2C> CODEC=PacketCodec.of((p,b)->{b.writeVarLong(p.sessionId);b.writeVarLong(p.revision);b.writeString(p.entries,32767);},b->new PanelRosterS2C(b.readVarLong(),b.readVarLong(),b.readString(32767)));
        public Id<? extends CustomPayload> getId(){return ID;} public static void register(){if(!rosterRegistered){PayloadTypeRegistry.playS2C().register(ID,CODEC);rosterRegistered=true;}}
    }
    public record PanelViewResultS2C(long sessionId,int requestSeq,boolean accepted,String roleTag,String playerUuid) implements CustomPayload {
        public static final Id<PanelViewResultS2C> ID=new Id<>(Identifier.of("monvhua","command_panel_view_result"));
        public static final PacketCodec<RegistryByteBuf,PanelViewResultS2C> CODEC=PacketCodec.of((p,b)->{b.writeVarLong(p.sessionId);b.writeVarInt(p.requestSeq);b.writeBoolean(p.accepted);b.writeString(p.roleTag,64);b.writeString(p.playerUuid,40);},b->new PanelViewResultS2C(b.readVarLong(),b.readVarInt(),b.readBoolean(),b.readString(64),b.readString(40)));
        public Id<? extends CustomPayload> getId(){return ID;} public static void register(){if(!viewResultRegistered){PayloadTypeRegistry.playS2C().register(ID,CODEC);viewResultRegistered=true;}}
    }
    public record PermissionS2C(boolean editable) implements CustomPayload { public static final Id<PermissionS2C> ID=new Id<>(Identifier.of("monvhua","command_panel_permission")); public static final PacketCodec<RegistryByteBuf,PermissionS2C> CODEC=PacketCodec.of((p,b)->b.writeBoolean(p.editable),b->new PermissionS2C(b.readBoolean())); public Id<? extends CustomPayload> getId(){return ID;} public static void register(){if(!permissionRegistered){PayloadTypeRegistry.playS2C().register(ID,CODEC);permissionRegistered=true;}} }
    public record ExecuteC2S(String command) implements CustomPayload {
        public static final Id<ExecuteC2S> ID = new Id<>(Identifier.of("monvhua", "command_panel_execute"));
        public static final PacketCodec<RegistryByteBuf, ExecuteC2S> CODEC = PacketCodec.of((p,b)->b.writeString(p.command,32767), b->new ExecuteC2S(b.readString(32767)));
        public Id<? extends CustomPayload> getId(){return ID;}
        public static void register(){if(!executeRegistered){PayloadTypeRegistry.playC2S().register(ID,CODEC);executeRegistered=true;}}
    }
    public record RequestC2S() implements CustomPayload { public static final Id<RequestC2S> ID=new Id<>(Identifier.of("monvhua","command_panel_request")); public static final PacketCodec<RegistryByteBuf,RequestC2S> CODEC=PacketCodec.unit(new RequestC2S()); public Id<? extends CustomPayload> getId(){return ID;} public static void register(){if(!requestRegistered){PayloadTypeRegistry.playC2S().register(ID,CODEC);requestRegistered=true;}} }
    public record DataS2C(long revision, String json) implements CustomPayload { public static final Id<DataS2C> ID=new Id<>(Identifier.of("monvhua","command_panel_data")); public static final PacketCodec<RegistryByteBuf,DataS2C> CODEC=PacketCodec.of((p,b)->{b.writeVarLong(p.revision);b.writeString(p.json,32767);},b->new DataS2C(b.readVarLong(),b.readString(32767))); public Id<? extends CustomPayload> getId(){return ID;} public static void register(){if(!dataRegistered){PayloadTypeRegistry.playS2C().register(ID,CODEC);dataRegistered=true;}} }
    public record SyncRequestS2C(String targets) implements CustomPayload { public static final Id<SyncRequestS2C> ID=new Id<>(Identifier.of("monvhua","command_panel_sync_request")); public static final PacketCodec<RegistryByteBuf,SyncRequestS2C> CODEC=PacketCodec.of((p,b)->b.writeString(p.targets,32767),b->new SyncRequestS2C(b.readString(32767))); public Id<? extends CustomPayload> getId(){return ID;} public static void register(){if(!syncRequestRegistered){PayloadTypeRegistry.playS2C().register(ID,CODEC);syncRequestRegistered=true;}} }
    public record SyncUploadC2S(String json) implements CustomPayload { public static final Id<SyncUploadC2S> ID=new Id<>(Identifier.of("monvhua","command_panel_sync_upload")); public static final PacketCodec<RegistryByteBuf,SyncUploadC2S> CODEC=PacketCodec.of((p,b)->b.writeString(p.json,32767),b->new SyncUploadC2S(b.readString(32767))); public Id<? extends CustomPayload> getId(){return ID;} public static void register(){if(!uploadRegistered){PayloadTypeRegistry.playC2S().register(ID,CODEC);uploadRegistered=true;}} }
    public record SharedPanelS2C(String sourceName,long revision,String json) implements CustomPayload { public static final Id<SharedPanelS2C> ID=new Id<>(Identifier.of("monvhua","command_panel_shared")); public static final PacketCodec<RegistryByteBuf,SharedPanelS2C> CODEC=PacketCodec.of((p,b)->{b.writeString(p.sourceName,256);b.writeVarLong(p.revision);b.writeString(p.json,32767);},b->new SharedPanelS2C(b.readString(256),b.readVarLong(),b.readString(32767))); public Id<? extends CustomPayload> getId(){return ID;} public static void register(){if(!sharedRegistered){PayloadTypeRegistry.playS2C().register(ID,CODEC);sharedRegistered=true;}} }
    public record SyncDecisionC2S(long revision,boolean accept) implements CustomPayload { public static final Id<SyncDecisionC2S> ID=new Id<>(Identifier.of("monvhua","command_panel_sync_decision")); public static final PacketCodec<RegistryByteBuf,SyncDecisionC2S> CODEC=PacketCodec.of((p,b)->{b.writeVarLong(p.revision);b.writeBoolean(p.accept);},b->new SyncDecisionC2S(b.readVarLong(),b.readBoolean())); public Id<? extends CustomPayload> getId(){return ID;} public static void register(){if(!decisionRegistered){PayloadTypeRegistry.playC2S().register(ID,CODEC);decisionRegistered=true;}} }
    public record ReloadS2C() implements CustomPayload { public static final Id<ReloadS2C> ID=new Id<>(Identifier.of("monvhua","command_panel_reload")); public static final PacketCodec<RegistryByteBuf,ReloadS2C> CODEC=PacketCodec.unit(new ReloadS2C()); public Id<? extends CustomPayload> getId(){return ID;} public static void register(){if(!reloadRegistered){PayloadTypeRegistry.playS2C().register(ID,CODEC);reloadRegistered=true;}} }
}
