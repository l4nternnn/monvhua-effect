package com.kuilunfuzhe.monvhua.features.forensics.cards;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import java.util.*;
import static net.minecraft.server.command.CommandManager.*;

/** Opt-in experiment. Normal corpse inspection and existing stage rules are not modified. */
public final class ClueCardService {
    private static final Map<UUID,ClueCaptureTarget> PENDING=new HashMap<>();
    private static final Map<UUID,Long> LAST_REQUEST=new HashMap<>(),LAST_CAPTURE=new HashMap<>();
    private static final Map<UUID,Integer> REQUEST_COUNTS=new HashMap<>();
    private ClueCardService(){}
    public static void initialize(){
        ClueCardItems.initialize();ClueCardConfig.load();ClueCardPackets.register();
        ServerPlayNetworking.registerGlobalReceiver(ClueCardPackets.Request.ID,(packet,context)->context.server().execute(()->handle(context.player(),packet)));
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{UUID id=handler.getPlayer().getUuid();PENDING.remove(id);LAST_REQUEST.remove(id);LAST_CAPTURE.remove(id);REQUEST_COUNTS.remove(id);});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{PENDING.clear();LAST_REQUEST.clear();LAST_CAPTURE.clear();REQUEST_COUNTS.clear();});
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->dispatcher.register(literal("mvclue")
            .then(literal("open").executes(ctx->{send(ctx.getSource().getPlayerOrThrow(),"","",true,false);return 1;}))
            .then(literal("board").executes(ctx->{send(ctx.getSource().getPlayerOrThrow(),"","",true,false,"board",true);return 1;}))
            .then(literal("craft").executes(ctx->{send(ctx.getSource().getPlayerOrThrow(),"","",true,false,"craft",true);return 1;}))
            .then(literal("import_wounds").executes(ctx->{var p=ctx.getSource().getPlayerOrThrow();try{send(p,"",importWounds(p),true,false);return 1;}catch(IllegalArgumentException error){ctx.getSource().sendError(Text.literal(error.getMessage()));return 0;}}))
            .then(literal("share").then(argument("card",StringArgumentType.word()).suggests((ctx,builder)->{
                var owner=ctx.getSource().getPlayer();return owner==null?builder.buildFuture():net.minecraft.command.CommandSource.suggestMatching(store(owner).summaries(owner.getUuid()).stream().map(ClueCardStore.Summary::id),builder);
            }).then(argument("target",EntityArgumentType.player()).executes(ctx->{var p=ctx.getSource().getPlayerOrThrow();boolean shared=com.kuilunfuzhe.monvhua.features.forensics.ForensicShowcaseCompat.shareClueCard(p,StringArgumentType.getString(ctx,"card"),EntityArgumentType.getPlayer(ctx,"target"));send(p,"","",false,false);return shared?1:0;}))))
            .then(literal("experiment").requires(source->source.hasPermissionLevel(2))
                .then(literal("start").then(argument("id",StringArgumentType.word()).then(argument("players",EntityArgumentType.players()).executes(ctx->{
                    var players=EntityArgumentType.getPlayers(ctx,"players");var store=ClueCardStore.get(ctx.getSource().getServer());var members=new LinkedHashMap<UUID,String>();
                    for(var p:players){int free=0;for(int i=0;i<36;i++)if(p.getInventory().getStack(i).isEmpty())free++;if(free<2){ctx.getSource().sendError(Text.literal("每名参与者背包需要至少两个空槽"));return 0;}}
                    for(var p:players)members.put(p.getUuid(),p.getName().getString());
                    try{store.start(StringArgumentType.getString(ctx,"id"),members);}catch(IllegalArgumentException e){ctx.getSource().sendError(Text.literal(e.getMessage()));return 0;}
                    PENDING.clear();for(var p:players){p.giveItemStack(ClueCardItems.blanks(store.session()));p.giveItemStack(new ItemStack(ClueCardItems.BAG));p.sendMessage(Text.literal("线索卡实验已开启：已发放 8 张空白卡和卡袋"),false);}
                    ctx.getSource().sendFeedback(()->Text.literal("实验已开启："+store.session()),true);return players.size();
                }))))
                .then(literal("close_recording").executes(ctx->{ClueCardStore.get(ctx.getSource().getServer()).closeRecording();ctx.getSource().sendFeedback(()->Text.literal("记录与副本制作已关闭，仍可编辑和出示已有卡片"),true);return 1;}))
                .then(literal("reload").executes(ctx->{ClueCardConfig.load();ctx.getSource().sendFeedback(()->Text.literal("线索卡预填配置已重载"),false);return 1;}))
            )));
    }
    private static ClueCardStore store(ServerPlayerEntity p){return ClueCardStore.get(p.getServer());}
    private static Vec3d target(ServerPlayerEntity p){var eye=p.getEyePos();var hit=((net.minecraft.server.world.ServerWorld)p.getWorld()).raycast(new RaycastContext(eye,eye.add(p.getRotationVec(1).multiply(4)),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,p));
        if(hit.getType()!=HitResult.Type.BLOCK||!((net.minecraft.server.world.ServerWorld)p.getWorld()).isChunkLoaded(hit.getBlockPos()))throw new IllegalArgumentException("请对准四格以内的可见方块表面");return hit.getPos();}
    private static int blankSlot(ServerPlayerEntity p){String session=store(p).session();for(int i=0;i<p.getInventory().size();i++){var s=p.getInventory().getStack(i);if(s.isOf(ClueCardItems.BLANK)&&session.equals(ClueCardItems.data(s,"clue_session")))return i;}throw new IllegalArgumentException("缺少本次实验的空白卡");}
    public static void propose(ServerPlayerEntity p){if(!p.isAlive()||p.isSpectator()||!store(p).recording(p.getUuid()))throw new IllegalArgumentException("当前未开放记录，需先开启实验");
        Vec3d point=target(p);PENDING.put(p.getUuid(),new ClueCaptureTarget(point,p.getEyePos(),p.getServer().getTicks()+1200,p.getWorld().getRegistryKey().getValue().toString()));send(p,"","记录点已选定。松开潜行不会改变记录点；确认后才扣卡。",true,true);}
    private static String capture(ServerPlayerEntity p){
        ClueCaptureTarget pending=PENDING.get(p.getUuid());long now=p.getServer().getTicks();
        if(pending==null)throw new IllegalArgumentException("尚未选择记录点，请手持空白卡重新选点");
        if(!p.isAlive()||p.isSpectator())throw new IllegalArgumentException("当前状态无法记录");
        pending.validate(p.getEyePos(),p.getWorld().getRegistryKey().getValue().toString(),now);
        var world=(net.minecraft.server.world.ServerWorld)p.getWorld();Vec3d rayEnd=pending.point().add(pending.point().subtract(pending.eye()).normalize().multiply(.02));
        for(double d=0;d<=pending.eye().distanceTo(rayEnd);d+=.25)if(!world.isChunkLoaded(BlockPos.ofFloored(pending.eye().lerp(rayEnd,Math.min(1,d/Math.max(.001,pending.eye().distanceTo(rayEnd)))))))throw new IllegalArgumentException("记录区域尚未加载，请稍后重试");
        var hit=world.raycast(new RaycastContext(pending.eye(),rayEnd,RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,p));
        if(hit.getType()!=HitResult.Type.BLOCK||hit.getPos().squaredDistanceTo(pending.point())>.01)throw new IllegalArgumentException("原记录点已被遮挡或表面已变化，请重新选点");
        if(now-LAST_CAPTURE.getOrDefault(p.getUuid(),-100L)<40)throw new IllegalArgumentException("记录操作过快，请稍后重试");
        int slot=blankSlot(p);if(store(p).ownedCount(p.getUuid())>=64)throw new IllegalArgumentException("卡袋已满");
        var snapshot=ClueCardSnapshot.capture(p,pending.point(),pending.eye());var audit=new NbtCompound();audit.putDouble("x",pending.point().x);audit.putDouble("y",pending.point().y);audit.putDouble("z",pending.point().z);audit.putString("dimension",pending.dimension());audit.putLong("tick",now);
        var fields=new ClueCardStore.Fields("现场记录",ClueCardConfig.time(p),ClueCardConfig.place(p,pending.point()),"","");
        String id=store(p).create(p.getUuid(),fields,snapshot,audit);p.getInventory().getStack(slot).decrement(1);p.getInventory().markDirty();LAST_CAPTURE.put(p.getUuid(),now);PENDING.remove(p.getUuid());return id;
    }
    private static void handle(ServerPlayerEntity p,ClueCardPackets.Request request){
        if(request==null||request.op()==null||request.id()==null||request.other()==null)return;
        long now=p.getServer().getTicks();if(now!=LAST_REQUEST.getOrDefault(p.getUuid(),-100L)){LAST_REQUEST.put(p.getUuid(),now);REQUEST_COUNTS.put(p.getUuid(),0);}
        int count=REQUEST_COUNTS.merge(p.getUuid(),1,Integer::sum);if(count>8){if(count==9)send(p,"","操作失败：请求过于频繁，请稍后重试",false,false,request.op(),false);return;}
        String selected=request.id(),notice="";try{
            if(!store(p).participant(p.getUuid()))throw new IllegalArgumentException("未加入当前线索卡实验");
            switch(request.op()){
                case "list" -> {}
                case "capture" -> {selected=capture(p);notice="记录已保存到卡袋";}
                case "cancel" -> PENDING.remove(p.getUuid());
                case "import_wounds" -> {notice=importWounds(p);selected="";}
                case "view" -> {boolean fresh=store(p).collect(p.getUuid(),selected);if(fresh)notice="已收录出示的记录";}
                case "save" -> {if(request.fields()==null)throw new IllegalArgumentException("缺少卡片内容");store(p).edit(p.getUuid(),selected,request.revision(),request.fields());notice="记录已保存";}
                case "copy" -> {int slot=blankSlot(p);selected=store(p).duplicate(p.getUuid(),selected);p.getInventory().getStack(slot).decrement(1);p.getInventory().markDirty();notice="副本已生成，原卡保留";}
                case "publish" -> {store(p).publish(p.getUuid(),selected);notice="记录已出示，该版本已锁定";broadcast(p);}
                case "compare" -> {store(p).compare(p.getUuid(),selected,request.other());notice="比较已展示，相关版本已锁定";broadcast(p);}
                case "withdraw" -> {withdraw(p,selected);notice="卡片已取出；手持潜行右键可放回卡袋";}
                default -> throw new IllegalArgumentException("不支持的卡片操作");
            }
            send(p,selected,notice,false,false,request.op(),true);
        }catch(IllegalArgumentException e){send(p,"","操作失败："+e.getMessage(),false,false,request.op(),false);}
    }
    private static void broadcast(ServerPlayerEntity source){for(var p:source.getServer().getPlayerManager().getPlayerList())if(p!=source&&store(p).participant(p.getUuid()))send(p,"","有新的公开记录或比较",false,false);}
    public static void onShowcaseViewed(ServerPlayerEntity viewer,String shareId){
        viewer.getServer().execute(()->{String id=store(viewer).openShare(viewer.getUuid(),shareId);if(id.isEmpty())return;boolean fresh=store(viewer).collect(viewer.getUuid(),id);
            var summary=store(viewer).summaries(viewer.getUuid()).stream().filter(c->c.id().equals(id)).findFirst().orElseThrow();
            send(viewer,id,fresh?"已收录"+summary.presenter()+"出示的记录":"该记录已收录",false,false);
        });
    }
    public static void send(ServerPlayerEntity p,String selected,String notice,boolean open,boolean confirm){
        send(p,selected,notice,open,confirm,"",true);
    }
    private static void send(ServerPlayerEntity p,String selected,String notice,boolean open,boolean confirm,String responseOp,boolean success){
        if(!ServerPlayNetworking.canSend(p,ClueCardPackets.State.ID))return;
        var s=store(p);var latest=s.latest(p.getUuid());ClueCardPackets.Detail detail=null;
        if(!selected.isEmpty()&&s.readable(p.getUuid(),selected)){var n=s.snapshot(p.getUuid(),selected);detail=new ClueCardPackets.Detail(selected,n.getInt("width",0),n.getInt("height",0),n.getIntArray("pixels").orElse(new int[0]),n.contains("body")?NbtCompound.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,n.getCompoundOrEmpty("body")).getOrThrow().toString():"");}
        ServerPlayNetworking.send(p,new ClueCardPackets.State(s.participant(p.getUuid())?s.session():"",s.recording(p.getUuid()),s.summaries(p.getUuid()),latest[0],latest[1],selected,detail,notice,open,confirm,responseOp,success));
    }
    public static void openCard(ServerPlayerEntity p,String id){if(!store(p).readable(p.getUuid(),id))throw new IllegalArgumentException("无权读取该卡片");store(p).collect(p.getUuid(),id);send(p,id,"",true,false);}
    private static void withdraw(ServerPlayerEntity p,String id){if(p.getInventory().getEmptySlot()<0)throw new IllegalArgumentException("背包已满");if(!store(p).inBag(p.getUuid(),id))throw new IllegalArgumentException("该卡片已在袋外");
        if(!p.getInventory().insertStack(ClueCardItems.card(id)))throw new IllegalArgumentException("背包已满");store(p).setInBag(p.getUuid(),id,false);p.getInventory().markDirty();}
    private static String importWounds(ServerPlayerEntity p){
        var ledger=store(p);if(!ledger.recording(p.getUuid()))throw new IllegalArgumentException("当前未开放伤口卡导入");
        var forensic=com.kuilunfuzhe.monvhua.features.forensics.ForensicStore.get(p.getServer());var observations=forensic.observations(p.getUuid());int before=ledger.ownedCount(p.getUuid());boolean full=false;
        outer:for(String corpse:forensic.collectedCaseIds(p.getUuid()))for(String part:com.kuilunfuzhe.monvhua.features.forensics.Forensics.PARTS)for(var hit:forensic.collected(p.getUuid(),corpse,part)){
            String evidence=hit.getString("id","");if(evidence.isEmpty())continue;
            var wound=com.kuilunfuzhe.monvhua.features.forensics.EvidenceSnapshots.publicWound(hit);wound.remove("death_trigger");
            var body=new NbtCompound();var observation=observations.getCompoundOrEmpty(corpse).copy();observation.remove("environment");
            var visual=observation.getCompoundOrEmpty("visual");visual.remove("model_paint_faces");visual.remove("local_skin");observation.put("visual",visual);
            body.put("observation",observation);var wounds=new net.minecraft.nbt.NbtList();wounds.add(wound);body.put("wounds",wounds);var snapshot=new NbtCompound();snapshot.put("body",body);
            String name=observation.getString("name","遗体");String partName=switch(part){case "head"->"头部";case "torso"->"躯干";case "left_arm"->"左臂";case "right_arm"->"右臂";case "left_leg"->"左腿";case "right_leg"->"右腿";default->"未知部位";};
            var fields=new ClueCardStore.Fields(name+" · "+partName,"","","已探查的伤口："+wound.getString("kinds","")+"；程度："+wound.getString("severity",""),"");
            try{ledger.importWound(p.getUuid(),corpse+"/"+evidence,corpse,name,fields,snapshot);}catch(IllegalArgumentException e){full=true;break outer;}
        }
        return "已导入 "+(ledger.ownedCount(p.getUuid())-before)+" 张伤口子卡（不消耗空白卡）"+(full?"；卡库容量已满":"");
    }
    public static void deposit(ServerPlayerEntity p,String id){var stack=p.getMainHandStack();if(!stack.isOf(ClueCardItems.CARD)||!id.equals(ClueCardItems.data(stack,"clue_id")))throw new IllegalArgumentException("请手持对应记录卡");
        store(p).setInBag(p.getUuid(),id,true);stack.decrement(1);p.getInventory().markDirty();p.sendMessage(Text.literal("卡片已放回卡袋"),true);}
}
