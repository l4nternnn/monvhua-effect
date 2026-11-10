package com.kuilunfuzhe.monvhua.features.forensics;

import com.mojang.brigadier.arguments.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.*;
import static net.minecraft.server.command.CommandManager.*;

/** Ownership comes exclusively from the authenticated connection, never from C2S data. */
public final class EvidenceAnchorNetwork {
    public record Snapshot(NbtCompound data) implements CustomPayload {
        public static final Id<Snapshot> ID=new Id<>(Identifier.of("monvhua","evidence_anchors"));
        public static final PacketCodec<RegistryByteBuf,Snapshot> CODEC=PacketCodec.of((p,b)->b.writeNbt(p.data),b->new Snapshot(Objects.requireNonNull(b.readNbt())));
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record Edit(String action,String id,double x,double y,double z,float yaw,float pitch,float roll,float width) implements CustomPayload {
        public static final Id<Edit> ID=new Id<>(Identifier.of("monvhua","evidence_anchor_edit"));
        public static final PacketCodec<RegistryByteBuf,Edit> CODEC=PacketCodec.of((p,b)->{b.writeString(p.action,16);b.writeString(p.id,36);b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);b.writeFloat(p.yaw);b.writeFloat(p.pitch);b.writeFloat(p.roll);b.writeFloat(p.width);},b->new Edit(b.readString(16),b.readString(36),b.readDouble(),b.readDouble(),b.readDouble(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat()));
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    private static final Map<UUID,Integer> lastEdit=new HashMap<>();
    private static com.mojang.brigadier.Command<ServerCommandSource> guard(com.mojang.brigadier.Command<ServerCommandSource> command){
        return context->{try{return command.run(context);}catch(IllegalArgumentException error){throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Text.literal(Objects.toString(error.getMessage(),"无效的面板操作"))).create();}};
    }
    public static void initialize(){
        PayloadTypeRegistry.playS2C().register(Snapshot.ID,Snapshot.CODEC);PayloadTypeRegistry.playC2S().register(Edit.ID,Edit.CODEC);
        ServerPlayConnectionEvents.JOIN.register((h,s,server)->sync(h.player));
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->lastEdit.remove(h.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(s->lastEdit.clear());
        ServerPlayNetworking.registerGlobalReceiver(Edit.ID,(p,c)->c.server().execute(()->{
            var player=c.player();int tick=c.server().getTicks();
            if(tick-lastEdit.getOrDefault(player.getUuid(),tick-10)<2)return;lastEdit.put(player.getUuid(),tick);
            try{edit(player,p);}catch(IllegalArgumentException error){player.sendMessage(Text.literal(error.getMessage()),false);sync(player);}
        }));
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->dispatcher.register(commands()));
    }
    static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> commands(){
            var root=literal("evidence");
            root.then(literal("place").executes(guard(c->{var p=c.getSource().getPlayerOrThrow();placeFront(p);return 1;})));
            root.then(placement("place_at",false));
            root.then(literal("clear_all").executes(guard(c->{var p=c.getSource().getPlayerOrThrow();EvidenceAnchorStore.get(p.getServer()).clear(p.getUuid());sync(p);return 1;})));
            root.then(literal("remove").then(argument("id",StringArgumentType.word()).executes(guard(c->{var p=c.getSource().getPlayerOrThrow();EvidenceAnchorStore.get(p.getServer()).remove(p.getUuid(),UUID.fromString(StringArgumentType.getString(c,"id")));sync(p);return 1;}))));
            root.then(literal("status").executes(guard(c->{var p=c.getSource().getPlayerOrThrow();var list=EvidenceAnchorStore.get(p.getServer()).owned(p.getUuid());c.getSource().sendFeedback(()->Text.literal("私人世界面板 "+list.size()+"/20 "+list.stream().map(a->a.id().toString()).toList()),false);return list.size();})));
            root.then(literal("for").requires(s->s.hasPermissionLevel(2)).then(argument("player",EntityArgumentType.player()).then(placement("place_at",true))
                .then(literal("clear_all").executes(guard(c->{var p=EntityArgumentType.getPlayer(c,"player");EvidenceAnchorStore.get(p.getServer()).clear(p.getUuid());sync(p);return 1;})))));
            root.then(literal("clear_server").requires(s->s.hasPermissionLevel(2)).executes(guard(c->{EvidenceAnchorStore.get(c.getSource().getServer()).clearAll();for(var p:c.getSource().getServer().getPlayerManager().getPlayerList())sync(p);return 1;})));
        return literal("mvcorpse").then(root);
    }
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> placement(String name,boolean other){
        return literal(name).requires(s->s.hasPermissionLevel(2)).then(argument("x",DoubleArgumentType.doubleArg(-30_000_000,30_000_000)).then(argument("y",DoubleArgumentType.doubleArg(-30_000_000,30_000_000)).then(argument("z",DoubleArgumentType.doubleArg(-30_000_000,30_000_000)).then(argument("yaw",FloatArgumentType.floatArg(-360,360)).then(argument("pitch",FloatArgumentType.floatArg(-360,360)).then(argument("roll",FloatArgumentType.floatArg(-360,360)).executes(guard(c->{
            var player=other?EntityArgumentType.getPlayer(c,"player"):c.getSource().getPlayerOrThrow();
            create(player,c.getSource().getWorld().getRegistryKey().getValue().toString(),DoubleArgumentType.getDouble(c,"x"),DoubleArgumentType.getDouble(c,"y"),DoubleArgumentType.getDouble(c,"z"),FloatArgumentType.getFloat(c,"yaw"),FloatArgumentType.getFloat(c,"pitch"),FloatArgumentType.getFloat(c,"roll"),1.5f);return 1;
        }))))))));
    }
    private static void placeFront(ServerPlayerEntity p){placeFront(p,1.5f);}
    private static void placeFront(ServerPlayerEntity p,float width){var pos=p.getEyePos().add(p.getRotationVec(1).multiply(3));create(p,p.getWorld().getRegistryKey().getValue().toString(),pos.x,pos.y,pos.z,180-p.getYaw(),-p.getPitch(),0,width);}
    private static void create(ServerPlayerEntity p,String dim,double x,double y,double z,float yaw,float pitch,float roll,float width){if(!p.isAlive()||p.isSpectator())throw new IllegalArgumentException("当前状态不能生成面板");EvidenceAnchorStore.get(p.getServer()).put(new EvidenceAnchorStore.Anchor(UUID.randomUUID(),p.getUuid(),dim,x,y,z,yaw,pitch,roll,width));sync(p);}
    private static void edit(ServerPlayerEntity p,Edit e){
        var store=EvidenceAnchorStore.get(p.getServer());
        if(e.action.equals("sync")){sync(p);return;}
        if(!p.isAlive()||p.isSpectator())throw new IllegalArgumentException("当前状态不能编辑线索面板");
        switch(e.action){
            case "place" -> placeFront(p,e.width);
            case "place_at" -> {if(!p.hasPermissionLevel(2))throw new IllegalArgumentException("指定坐标生成需要管理员权限");create(p,p.getWorld().getRegistryKey().getValue().toString(),e.x,e.y,e.z,e.yaw,e.pitch,e.roll,e.width);}
            case "clear" -> {store.clear(p.getUuid());sync(p);}
            case "remove" -> {store.remove(p.getUuid(),UUID.fromString(e.id));sync(p);}
            case "rotate" -> {var a=store.require(p.getUuid(),UUID.fromString(e.id));store.put(new EvidenceAnchorStore.Anchor(a.id(),a.owner(),a.dimension(),a.x(),a.y(),a.z(),e.yaw,e.pitch,e.roll,a.width()));sync(p);}
            case "size" -> {if(!Float.isFinite(e.width)||e.width<.5f||e.width>8)throw new IllegalArgumentException("尺寸需要在 0.5 至 8 格之间");for(var a:store.owned(p.getUuid()))store.put(new EvidenceAnchorStore.Anchor(a.id(),a.owner(),a.dimension(),a.x(),a.y(),a.z(),a.yaw(),a.pitch(),a.roll(),e.width));sync(p);}
            default -> throw new IllegalArgumentException("未知面板操作");
        }
    }
    public static void sync(ServerPlayerEntity p){
        if(!ServerPlayNetworking.canSend(p,Snapshot.ID))return;
        var store=EvidenceAnchorStore.get(p.getServer());var n=new NbtCompound();n.putLong("revision",store.revision());var list=new NbtList();for(var a:store.owned(p.getUuid()))list.add(a.nbt());n.put("anchors",list);ServerPlayNetworking.send(p,new Snapshot(n));
    }
}
