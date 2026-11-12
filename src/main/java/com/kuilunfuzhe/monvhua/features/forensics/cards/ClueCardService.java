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
    private static final Map<UUID,NbtCompound> PREVIEWS=new HashMap<>();
    private static final Map<UUID,Long> LAST_PROPOSE=new HashMap<>();
    private static final Map<UUID,Long> LAST_REQUEST=new HashMap<>(),LAST_CAPTURE=new HashMap<>();
    private static final Map<UUID,Integer> REQUEST_COUNTS=new HashMap<>();
    private record ItemPreview(int slot,NbtCompound original,long expires){}
    private static final Map<UUID,ItemPreview> ITEM_PREVIEWS=new HashMap<>();
    private ClueCardService(){}
    public static void initialize(){
        ClueCardItems.initialize();ClueCardConfig.load();ClueCardPackets.register();ClueCaseAdmin.initialize();
        ServerPlayNetworking.registerGlobalReceiver(ClueCardPackets.Request.ID,(packet,context)->context.server().execute(()->handle(context.player(),packet)));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server->{if(server.getTicks()%200==0){
            ITEM_PREVIEWS.entrySet().removeIf(e->server.getTicks()>e.getValue().expires());
            var expired=PENDING.entrySet().stream().filter(e->server.getTicks()>e.getValue().expires()).map(Map.Entry::getKey).toList();
            for(var id:expired){PENDING.remove(id);PREVIEWS.remove(id);}
        }});
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->send(handler.player,"","",false,false));
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{UUID id=handler.getPlayer().getUuid();ITEM_PREVIEWS.remove(id);PENDING.remove(id);PREVIEWS.remove(id);LAST_PROPOSE.remove(id);LAST_REQUEST.remove(id);LAST_CAPTURE.remove(id);REQUEST_COUNTS.remove(id);});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{ITEM_PREVIEWS.clear();PENDING.clear();PREVIEWS.clear();LAST_PROPOSE.clear();LAST_REQUEST.clear();LAST_CAPTURE.clear();REQUEST_COUNTS.clear();});
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->dispatcher.register(literal("mvclue")
            .then(literal("open").executes(ctx->{send(ctx.getSource().getPlayerOrThrow(),"","",true,false);return 1;}))
            .then(literal("board").executes(ctx->{send(ctx.getSource().getPlayerOrThrow(),"","",true,false,"board",true);return 1;}))
            .then(literal("craft").executes(ctx->{send(ctx.getSource().getPlayerOrThrow(),"","",true,false,"craft",true);return 1;}))
            .then(literal("import_wounds").executes(ctx->{var p=ctx.getSource().getPlayerOrThrow();try{send(p,"",importWounds(p),true,false);return 1;}catch(IllegalArgumentException error){ctx.getSource().sendError(Text.literal(error.getMessage()));return 0;}}))
            .then(literal("share").then(argument("card",StringArgumentType.word()).suggests((ctx,builder)->{
                var owner=ctx.getSource().getPlayer();return owner==null?builder.buildFuture():net.minecraft.command.CommandSource.suggestMatching(store(owner).summaries(owner.getUuid()).stream().map(ClueCardStore.Summary::id),builder);
            }).then(argument("target",EntityArgumentType.player()).executes(ctx->{var p=ctx.getSource().getPlayerOrThrow();boolean shared=com.kuilunfuzhe.monvhua.features.forensics.ForensicShowcaseCompat.shareClueCard(p,StringArgumentType.getString(ctx,"card"),EntityArgumentType.getPlayer(ctx,"target"));send(p,"","",false,false);return shared?1:0;}))))
            .then(ClueCaseAdmin.commands("case"))
            .then(ClueCaseAdmin.commands("experiment"))));
    }
    static void invalidateCaptures(){ITEM_PREVIEWS.clear();PENDING.clear();PREVIEWS.clear();LAST_PROPOSE.clear();}

    private static ClueCardStore store(ServerPlayerEntity p){return ClueCardStore.get(p.getServer());}
    private static Vec3d target(ServerPlayerEntity p){var eye=p.getEyePos();var hit=((net.minecraft.server.world.ServerWorld)p.getWorld()).raycast(new RaycastContext(eye,eye.add(p.getRotationVec(1).multiply(4)),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,p));
        if(hit.getType()!=HitResult.Type.BLOCK||!((net.minecraft.server.world.ServerWorld)p.getWorld()).isChunkLoaded(hit.getBlockPos()))throw new IllegalArgumentException("请对准四格以内的可见方块表面");return hit.getPos();}
    private static int blankSlot(ServerPlayerEntity p){String session=store(p).accessToken();for(int i=0;i<p.getInventory().size();i++){var s=p.getInventory().getStack(i);if(s.isOf(ClueCardItems.BLANK)&&session.equals(ClueCardItems.data(s,"clue_session")))return i;}throw new IllegalArgumentException("缺少本次案件的空白卡");}
    private static void requireRecording(ServerPlayerEntity p){
        var ledger=store(p);if(ledger.session().isEmpty())throw new IllegalArgumentException("尚未选择案件，请管理员开启案件");
        if(!ledger.participant(p.getUuid()))throw new IllegalArgumentException("当前账号未加入此案件，请管理员添加参与者或接续原参与者身份");
        if(!ledger.recording(p.getUuid()))throw new IllegalArgumentException("当前案件已关闭记录，请管理员重新开启");
        if(!p.isAlive()||p.isSpectator())throw new IllegalArgumentException("当前状态无法记录");
    }
    public static void propose(ServerPlayerEntity p){requireRecording(p);
        long now=p.getServer().getTicks();if(now-LAST_PROPOSE.getOrDefault(p.getUuid(),-100L)<20)throw new IllegalArgumentException("采集过于频繁，请稍后重试");
        blankSlot(p);LAST_PROPOSE.put(p.getUuid(),now);
        Vec3d point=target(p);var preview=ClueCardSnapshot.capture(p,point,p.getEyePos());PREVIEWS.put(p.getUuid(),preview);PENDING.put(p.getUuid(),new ClueCaptureTarget(point,p.getEyePos(),p.getServer().getTicks()+1200,p.getWorld().getRegistryKey().getValue().toString()));send(p,"","记录点已选定。松开潜行不会改变记录点；确认后才扣卡。",true,true);}
    private static String capture(ServerPlayerEntity p){
        ClueCaptureTarget pending=PENDING.get(p.getUuid());long now=p.getServer().getTicks();
        if(pending==null)throw new IllegalArgumentException("尚未选择记录点，请手持空白卡重新选点");
        if(!p.isAlive()||p.isSpectator())throw new IllegalArgumentException("当前状态无法记录");
        pending.validate(p.getEyePos(),p.getWorld().getRegistryKey().getValue().toString(),now);
        if(now-LAST_CAPTURE.getOrDefault(p.getUuid(),-100L)<40)throw new IllegalArgumentException("记录操作过快，请稍后重试");
        int slot=blankSlot(p);
        var snapshot=PREVIEWS.get(p.getUuid());if(snapshot==null)throw new IllegalArgumentException("预览已失效，请重新选点");var audit=new NbtCompound();audit.putDouble("x",pending.point().x);audit.putDouble("y",pending.point().y);audit.putDouble("z",pending.point().z);audit.putString("dimension",pending.dimension());audit.putLong("tick",now);audit.putInt("spatial_schema",2);audit.putDouble("camera_x",pending.eye().x);audit.putDouble("camera_y",pending.eye().y);audit.putDouble("camera_z",pending.eye().z);audit.putLong("observed_tick",snapshot.getCompoundOrEmpty("scene").getLong("revision",now));audit.putBoolean("complete_volume",false);
        var fields=new ClueCardStore.Fields("现场记录",ClueCardConfig.time(p),ClueCardConfig.place(p,pending.point()),"","");
        String id=store(p).create(p.getUuid(),fields,snapshot,audit);p.getInventory().getStack(slot).decrement(1);p.getInventory().markDirty();LAST_CAPTURE.put(p.getUuid(),now);PENDING.remove(p.getUuid());PREVIEWS.remove(p.getUuid());return id;
    }
    private static ItemStack investigationItem(ServerPlayerEntity p,int slot){
        if(slot<0||slot>40)throw new IllegalArgumentException("无效的物品栏位置");
        var stack=p.getInventory().getStack(slot);
        if(stack.isEmpty()||stack.isOf(ClueCardItems.BLANK)||stack.isOf(ClueCardItems.CARD)||stack.isOf(ClueCardItems.BAG)||stack.isOf(ClueCardItems.MANAGER))throw new IllegalArgumentException("请选择需要调查的物品");
        return stack;
    }
    private static void previewItem(ServerPlayerEntity p,String slotText){
        requireRecording(p);
        int slot;try{slot=Integer.parseInt(slotText);}catch(NumberFormatException e){throw new IllegalArgumentException("无效的物品栏位置");}
        blankSlot(p);var original=ClueItemSnapshot.encode(investigationItem(p,slot),p.getRegistryManager());
        if(original.toString().length()>65536)throw new IllegalArgumentException("该物品内容超出记录容量，无法制作线索卡");
        ITEM_PREVIEWS.put(p.getUuid(),new ItemPreview(slot,original,p.getServer().getTicks()+1200));
    }
    private static String captureItem(ServerPlayerEntity p){
        var preview=ITEM_PREVIEWS.get(p.getUuid());long now=p.getServer().getTicks();
        if(preview==null||now>preview.expires())throw new IllegalArgumentException("物品预览已过期，请重新选择");
        requireRecording(p);
        if(!preview.original().equals(ClueItemSnapshot.encode(investigationItem(p,preview.slot()),p.getRegistryManager())))throw new IllegalArgumentException("物品已变化，请重新选择后记录");
        if(now-LAST_CAPTURE.getOrDefault(p.getUuid(),-100L)<40)throw new IllegalArgumentException("记录操作过快，请稍后重试");
        int blank=blankSlot(p);var stack=investigationItem(p,preview.slot());
        var audit=new NbtCompound();audit.put("original_item",preview.original().copy());audit.putLong("tick",now);audit.putString("dimension",p.getWorld().getRegistryKey().getValue().toString());
        var fields=new ClueCardStore.Fields(stack.getName().getString(),ClueCardConfig.time(p),ClueCardConfig.place(p,p.getPos()),"","");
        String id=store(p).create(p.getUuid(),fields,ClueItemSnapshot.snapshot(preview.original()),audit);
        p.getInventory().getStack(blank).decrement(1);p.getInventory().markDirty();ITEM_PREVIEWS.remove(p.getUuid());LAST_CAPTURE.put(p.getUuid(),now);return id;
    }
    private static void handle(ServerPlayerEntity p,ClueCardPackets.Request request){
        if(request==null||request.op()==null||request.id()==null||request.other()==null)return;
        long now=p.getServer().getTicks();if(now!=LAST_REQUEST.getOrDefault(p.getUuid(),-100L)){LAST_REQUEST.put(p.getUuid(),now);REQUEST_COUNTS.put(p.getUuid(),0);}
        int count=REQUEST_COUNTS.merge(p.getUuid(),1,Integer::sum);if(count>8){if(count==9)send(p,"","操作失败：请求过于频繁，请稍后重试",false,false,request.op(),false);return;}
        String selected=request.id(),notice="";try{
            if(!store(p).participant(p.getUuid()))throw new IllegalArgumentException("当前账号未加入此案件，请管理员添加参与者或接续原参与者身份");
            switch(request.op()){
                case "list" -> {if(store(p).recording(p.getUuid()))notice=importWounds(p);}
                case "item_preview" -> {previewItem(p,request.id());selected="__item";notice="物品已选定，确认记录后保留原物品";}
                case "capture_item" -> {selected=captureItem(p);notice="物品记录已保存，原物品保留";}
                case "cancel_item" -> {ITEM_PREVIEWS.remove(p.getUuid());selected="";}
                case "capture" -> {selected=capture(p);notice="记录已保存到卡袋";}
                case "cancel" -> {PENDING.remove(p.getUuid());PREVIEWS.remove(p.getUuid());}
                case "import_wounds" -> {notice=importWounds(p);selected="";}
                case "view" -> {boolean fresh=store(p).collect(p.getUuid(),selected);if(fresh)notice="已收录出示的记录";}
                case "save" -> {if(request.fields()==null)throw new IllegalArgumentException("缺少卡片内容");store(p).edit(p.getUuid(),selected,request.revision(),request.fields());notice="记录已保存";}
                case "copy" -> {int cost=store(p).copyCost(p.getUuid(),selected),slot=cost>0?blankSlot(p):-1;selected=store(p).duplicate(p.getUuid(),selected);if(cost>0){p.getInventory().getStack(slot).decrement(cost);p.getInventory().markDirty();}notice=cost==0?"伤情副本已生成，不另行消耗空白卡":"副本已生成，原卡保留";}
                case "publish" -> {store(p).publish(p.getUuid(),selected);notice="记录已出示，内容已定稿";broadcast(p);}
                case "compare" -> {store(p).compare(p.getUuid(),selected,request.other());notice="比较已公开，相关记录已定稿";broadcast(p);}
                case "withdraw" -> {withdraw(p,selected);notice="记录卡已取出；手持记录卡潜行右键可放回卡袋";}
                default -> throw new IllegalArgumentException("无法执行此项线索卡操作");
            }
            send(p,selected,notice,false,false,request.op(),true);
        }catch(IllegalArgumentException e){send(p,"","操作失败："+e.getMessage(),false,false,request.op(),false);}
    }
    private static void broadcast(ServerPlayerEntity source){for(var p:source.getServer().getPlayerManager().getPlayerList())if(p!=source&&store(p).participant(p.getUuid()))send(p,"","已有新的公开记录或线索比较",false,false);}
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
        var s=store(p);if(open&&!confirm&&s.recording(p.getUuid())){String imported=importWounds(p);if(!imported.isEmpty())notice=notice.isEmpty()?imported:notice+"；"+imported;}if(open&&notice.isEmpty()&&!s.session().isEmpty()&&!s.participant(p.getUuid()))notice="当前账号未加入此案件，请管理员添加参与者或接续原参与者身份";var latest=s.latest(p.getUuid());ClueCardPackets.Detail detail=null;
        if(selected.equals("__item")&&ITEM_PREVIEWS.containsKey(p.getUuid()))detail=projectDetail("__item",ClueItemSnapshot.snapshot(ITEM_PREVIEWS.get(p.getUuid()).original()));
        if(confirm&&PREVIEWS.containsKey(p.getUuid()))detail=projectDetail("__capture",PREVIEWS.get(p.getUuid()));
        if(!selected.isEmpty()&&s.readable(p.getUuid(),selected))detail=projectDetail(selected,s.snapshot(p.getUuid(),selected));
        ServerPlayNetworking.send(p,new ClueCardPackets.State(s.participant(p.getUuid())?s.accessToken():"",s.recording(p.getUuid()),s.summaries(p.getUuid()),latest[0],latest[1],selected,detail,notice,open,confirm,responseOp,success,s.history(p.getUuid())));
    }
    private static ClueCardPackets.Detail projectDetail(String id,NbtCompound n){
        return new ClueCardPackets.Detail(id,n.getInt("width",0),n.getInt("height",0),n.getIntArray("pixels").orElse(new int[0]),json(n,"body"),sceneJson(n),json(n,"item"));
    }
    private static String sceneJson(NbtCompound n){
        if(!n.contains("scene"))return "";
        var projection=n.getCompoundOrEmpty("scene").copy();projection.remove("revision");
        return NbtCompound.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,projection).getOrThrow().toString();
    }
    private static String json(NbtCompound n,String key){return n.contains(key)?NbtCompound.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,n.getCompoundOrEmpty(key)).getOrThrow().toString():"";}
    public static void openCard(ServerPlayerEntity p,String id){if(!store(p).readable(p.getUuid(),id))throw new IllegalArgumentException("无权读取该卡片");store(p).collect(p.getUuid(),id);send(p,id,"",true,false);}
    private static void withdraw(ServerPlayerEntity p,String id){if(p.getInventory().getEmptySlot()<0)throw new IllegalArgumentException("背包已满");if(!store(p).inBag(p.getUuid(),id))throw new IllegalArgumentException("该记录卡已取出");
        if(!p.getInventory().insertStack(ClueCardItems.card(id)))throw new IllegalArgumentException("背包已满");store(p).setInBag(p.getUuid(),id,false);p.getInventory().markDirty();}
    private static String importWounds(ServerPlayerEntity p){
        var ledger=store(p);if(!ledger.recording(p.getUuid()))throw new IllegalArgumentException("当前未开放伤情记录收录");
        var forensic=com.kuilunfuzhe.monvhua.features.forensics.ForensicStore.get(p.getServer());var observations=forensic.observations(p.getUuid());int before=ledger.ownedCount(p.getUuid()),spent=0;String blocked="";
        corpses:for(String corpse:forensic.collectedCaseIds(p.getUuid()).stream().sorted().toList())for(String part:com.kuilunfuzhe.monvhua.features.forensics.Forensics.PARTS)for(var hit:forensic.collected(p.getUuid(),corpse,part)){
            if(!ledger.canImportCorpse(p.getUuid(),corpse))continue corpses;
            String evidence=hit.getString("id","");if(evidence.isEmpty())continue;
            var wound=com.kuilunfuzhe.monvhua.features.forensics.EvidenceSnapshots.publicWound(hit);wound.remove("death_trigger");
            var body=new NbtCompound();var observation=observations.getCompoundOrEmpty(corpse).copy();observation.remove("environment");
            var visual=observation.getCompoundOrEmpty("visual");visual.remove("model_paint_faces");visual.remove("local_skin");observation.put("visual",visual);
            body.put("observation",observation);var wounds=new net.minecraft.nbt.NbtList();wounds.add(wound);body.put("wounds",wounds);var snapshot=new NbtCompound();snapshot.put("body",body);
            String name=observation.getString("name","遗体");String partName=switch(part){case "head"->"头部";case "torso"->"躯干";case "left_arm"->"左臂";case "right_arm"->"右臂";case "left_leg"->"左腿";case "right_leg"->"右腿";default->"未知部位";};
            var fields=new ClueCardStore.Fields(name+" · "+partName,"","","已探查的伤口："+wound.getString("kinds","")+"；程度："+wound.getString("severity",""),"");
            int slot=-1;if(!ledger.corpseRecorded(p.getUuid(),corpse))try{slot=blankSlot(p);}catch(IllegalArgumentException e){blocked="空白卡不足，部分遗体伤情尚未收录";continue corpses;}
            try{var result=ledger.importWound(p.getUuid(),corpse+"/"+evidence,corpse,name,fields,snapshot,slot>=0?1:0);if(result.blankCost()>0){p.getInventory().getStack(slot).decrement(result.blankCost());p.getInventory().markDirty();spent+=result.blankCost();}}catch(IllegalArgumentException e){blocked=e.getMessage();continue corpses;}
        }
        int added=ledger.ownedCount(p.getUuid())-before;return (added>0?"已收录 "+added+" 条伤情记录，消耗 "+spent+" 张空白卡":"")+(blocked.isEmpty()?"":(added>0?"；":"")+blocked);
    }
    public static void onWoundsObserved(ServerPlayerEntity p,String corpse){if(!store(p).recording(p.getUuid()))return;store(p).observeCorpse(p.getUuid(),corpse);String notice=importWounds(p);if(!notice.isEmpty())send(p,"",notice,false,false);}
    public static void deposit(ServerPlayerEntity p,String id){var stack=p.getMainHandStack();if(!stack.isOf(ClueCardItems.CARD)||!id.equals(ClueCardItems.data(stack,"clue_id")))throw new IllegalArgumentException("请手持对应记录卡");
        store(p).setInBag(p.getUuid(),id,true);stack.decrement(1);p.getInventory().markDirty();p.sendMessage(Text.literal("卡片已放回卡袋"),true);}
}
