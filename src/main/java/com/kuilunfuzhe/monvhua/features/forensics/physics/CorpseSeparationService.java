package com.kuilunfuzhe.monvhua.features.forensics.physics;

import com.kuilunfuzhe.monvhua.features.forensics.*;
import com.kuilunfuzhe.monvhua.features.block.body.BodyPartManager;
import com.kuilunfuzhe.monvhua.features.physics_preview.PhysicsPoseDocument;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.*;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.entity.decoration.InteractionEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import java.util.*;
import java.util.concurrent.*;
import static net.minecraft.server.command.CommandManager.*;

/** Main-thread transaction; native capture/prepare is asynchronous and held until commit. */
public final class CorpseSeparationService {
    private CorpseSeparationService() {}
    private static Txn active;
    private static final Set<UUID> repairing=new HashSet<>();
    private static final class Txn {
        final ServerCommandSource command;final java.util.function.BiConsumer<Boolean,String> completion;boolean notified;final ServerWorld world;final ItemDisplayEntity source;
        final ItemStack original;final Vec3d position,origin;final String part,event=UUID.randomUUID().toString();
        final long started;final CorpseHistoryController.Entry old;
        final List<Piece> pieces=new ArrayList<>();
        ItemDisplayEntity child;InteractionEntity interaction;boolean committed,touched;
        Txn(ServerCommandSource command,ItemDisplayEntity source,String part,CorpseHistoryController.Entry old,java.util.function.BiConsumer<Boolean,String> completion){
            this.completion=completion;this.command=command;world=(ServerWorld)source.getWorld();this.source=source;this.part=part;this.old=old;
            original=source.getItemStack().copy();position=source.getPos();origin=old==null?position:old.view.origin();started=world.getTime();
        }
    }
    record Piece(ItemDisplayEntity display,Vec3d origin,CorpsePoseRecord pose,ItemStack stack,
                 CorpsePhysicsBridge.Request request,CorpsePhysicsBridge.Session session){}
    public static boolean busy(UUID id){return active!=null&&active.source.getUuid().equals(id);}
    public static void reset(){if(active!=null)fail(active,"服务器物理会话已结束");}
    public static void initialize(){
        ServerEntityEvents.ENTITY_UNLOAD.register((entity,world)->{if(entity instanceof ItemDisplayEntity d&&(entity.getRemovalReason()==Entity.RemovalReason.DISCARDED||entity.getRemovalReason()==Entity.RemovalReason.KILLED))markRemoved(d);});
        ServerTickEvents.END_SERVER_TICK.register(server->{if(server.getTicks()%100==0&&active==null)recoverMissing(server);var t=active;if(t!=null&&(t.source.isRemoved()||t.world.getTime()-t.started>100))fail(t,"分离超时或源载体卸载");});
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->{
            var detach=literal("detach");
            for(String part:PhysicsPoseDocument.IDS)if(!part.equals("torso"))detach.then(argument("target",EntityArgumentType.entity()).then(literal(part).executes(c->{try{return begin(c.getSource(),resolve(c.getSource(),EntityArgumentType.getEntity(c,"target")),part);}catch(RuntimeException e){c.getSource().sendError(Text.literal(e.getMessage()));return 0;}})));
            dispatcher.register(literal("mvcorpse").then(literal("physics").requires(s->s.hasPermissionLevel(2)).then(detach)
                .then(literal("topology").then(argument("target",EntityArgumentType.entity()).executes(c->{
                    try{var d=resolve(c.getSource(),EntityArgumentType.getEntity(c,"target"));var p=CorpsePoseRecord.read(CorpsePoseRecord.data(d.getItemStack())).orElseThrow(()->new IllegalArgumentException("无规范物理姿态"));
                        c.getSource().sendFeedback(()->Text.literal("object="+d.getUuid()+" parts="+p.parts().keySet()+" root="+p.rootPart()+" scale="+p.scale()+" revision="+p.revision()+" separating="+busy(d.getUuid())),false);return 1;
                    }catch(RuntimeException e){c.getSource().sendError(Text.literal(e.getMessage()));return 0;}
                })))));
        });
    }
    private static ItemDisplayEntity resolve(ServerCommandSource command,Entity entity){
        if(entity instanceof InteractionEntity){var id=BodyPartManager.INTERACTION_TO_DISPLAY.get(entity.getUuid());entity=id==null?null:command.getWorld().getEntity(id);}
        if(!(entity instanceof ItemDisplayEntity d))throw new IllegalArgumentException("请选择遗体 display 或 interaction");return d;
    }
    private static int begin(ServerCommandSource command,ItemDisplayEntity display,String part){
        return begin(command,display,part,null);
    }
    // Package-private gameplay entry: caller validates weapon, jump, reach and connection on the server.
    static void cut(ItemDisplayEntity display,String part,java.util.function.BiConsumer<Boolean,String> completion){
        begin(null,display,part,completion);
    }
    private static int begin(ServerCommandSource command,ItemDisplayEntity display,String part,java.util.function.BiConsumer<Boolean,String> completion){
        Txn created=null;
        try{
            if(active!=null)throw new IllegalArgumentException("已有肢体分离任务，请稍候");
            var world=(ServerWorld)display.getWorld();
            if(!CorpsePhysicsCoordinator.localControl(world.getServer())||!Forensics.hasBinding(display.getItemStack())||!Forensics.valid(world,display.getItemStack(),display.getUuid().toString()))throw new IllegalArgumentException("物理后端或遗体绑定无效");
            var p=CorpsePoseRecord.read(CorpsePoseRecord.data(display.getItemStack())).orElseThrow(()->new IllegalArgumentException("请先生成规范遗体物理姿态"));
            if(!p.parts().containsKey("torso")||p.parts().size()<2||!p.parts().containsKey(part))throw new IllegalArgumentException("该对象不包含可分离的指定部位");
            if(display.getYaw()!=0||display.getPitch()!=0||display.getItemDisplayContext()!=ItemDisplayContext.NONE||!com.kuilunfuzhe.monvhua.mixin.forensics.CorpseDisplayAccessor.monvhua$transform(display.getDataTracker()).getMatrix().equals(new org.joml.Matrix4f(),1e-6f))throw new IllegalArgumentException("不支持额外载体变换");
            if(CorpseCarryService.isCarried(display)||CorpseGrabController.held(display.getUuid())||CorpseTransportController.held(display.getUuid()))throw new IllegalArgumentException("请先放下或松开遗体");
            var old=CorpseHistoryController.get(display.getUuid());
            if(!CorpseResidentManager.contains(display.getUuid())){
                if(old!=null&&old.frozen())throw new IllegalArgumentException("请先结束时间轴/冻结操作");old=null;
            }
            if(old!=null&&(!old.current()||old.frozen()||old.timeline||!old.session.continuous()||old.session.dynamicPart()!=null))throw new IllegalArgumentException("请结束冻结、局部固定及时间轴操作");
            if(old==null&&CorpsePhysicsCoordinator.pending(display))throw new IllegalArgumentException("遗体正在一次性求解，请稍候");
            if(!CorpseResidentManager.capacity(old==null?2:1)||!CorpseHistoryController.separationCapacity(old==null?2:1))throw new IllegalArgumentException("分离将超过物理对象或历史容量上限");
            var carriers=ForensicStore.get(world.getServer()).corpse(Forensics.binding(display.getItemStack()).getString("id","")).getCompoundOrEmpty("carriers");
            if(!carriers.getCompoundOrEmpty(part).isEmpty())throw new IllegalArgumentException("该部位已有独立载体，请检查绑定");
            var t=new Txn(command,display,part,old,completion);created=t;active=t;
            CompletableFuture<CorpsePhysicsBridge.Transfer> captured;
            if(old!=null)captured=old.session.captureForTransfer();else{
                var pose=new PhysicsPoseDocument("SETTLED",0,p.parts(),0,"static separation",p.definition(),p.scale());
                var velocities=new LinkedHashMap<String,CorpsePhysicsBridge.Velocity>();for(String id:p.parts().keySet())velocities.put(id,new CorpsePhysicsBridge.Velocity(0,0,0,0,0,0));
                captured=CompletableFuture.completedFuture(new CorpsePhysicsBridge.Transfer(pose,velocities));
            }
            captured.whenComplete((state,error)->world.getServer().execute(()->{if(active!=t)return;if(error!=null){fail(t,"捕获失败："+reason(error));return;}prepare(t,state);}));
            if(command!=null)command.sendFeedback(()->Text.literal("已请求分离 "+part+"；正在准备独立物理对象"),false);return 1;
        }catch(RuntimeException e){if(created!=null&&active==created)fail(created,e.getMessage());else{if(command!=null)command.sendError(Text.literal(e.getMessage()));if(completion!=null)completion.accept(false,e.getMessage());}return 0;}
    }
    /** Pure partition: positions are recentered once, velocities and rotations remain unchanged. */
    public static CorpsePhysicsBridge.Transfer partition(CorpsePhysicsBridge.Transfer state,Set<String> ids,Vec3d offset){
        PhysicsPoseDocument.validateParts(ids);if(!state.pose().poses().keySet().containsAll(ids))throw new IllegalArgumentException("Missing partition part");
        var poses=new LinkedHashMap<String,PhysicsPoseDocument.Pose>();var velocities=new LinkedHashMap<String,CorpsePhysicsBridge.Velocity>();
        for(String id:PhysicsPoseDocument.IDS)if(ids.contains(id)){var p=state.pose().poses().get(id);poses.put(id,new PhysicsPoseDocument.Pose(p.x()-(float)offset.x,p.y()-(float)offset.y,p.z()-(float)offset.z,p.qx(),p.qy(),p.qz(),p.qw()));velocities.put(id,state.velocities().get(id));}
        return new CorpsePhysicsBridge.Transfer(new PhysicsPoseDocument("SIMULATING",0,poses,0,"separation seed",state.pose().definition(),state.pose().scale()),velocities);
    }
    private static boolean valid(Txn t){return (t.old==null||CorpseResidentManager.residentValid(t.source.getUuid()))&&active==t&&!t.source.isRemoved()&&t.world.getEntity(t.source.getUuid())==t.source&&t.position.equals(t.source.getPos())&&ItemStack.areEqual(t.original,t.source.getItemStack())&&Forensics.valid(t.world,t.source.getItemStack(),t.source.getUuid().toString());}
    private static void prepare(Txn t,CorpsePhysicsBridge.Transfer state){
        try{
            if(!valid(t))throw new IllegalArgumentException("准备期间遗体已改变");
            var ids=new HashSet<>(state.pose().poses().keySet());if(!ids.remove(t.part)||!ids.contains("torso"))throw new IllegalArgumentException("物理结构已改变");
            t.child=EntityType.ITEM_DISPLAY.create(t.world,SpawnReason.TRIGGERED);if(t.child==null)throw new IllegalArgumentException("无法创建肢体载体");
            for(boolean child:new boolean[]{false,true}){
                var selected=child?Set.of(t.part):ids;String root=child?t.part:"torso";var center=state.pose().poses().get(root);
                var offset=new Vec3d(Math.floor(center.x()),Math.floor(center.y()),Math.floor(center.z()));var origin=t.origin.add(offset);
                var split=partition(state,selected,offset);var old=CorpsePoseRecord.read(CorpsePoseRecord.data(t.original)).orElseThrow();
                var record=new CorpsePoseRecord(Math.addExact(old.revision(),1),"separated",old.armModel(),split.pose().poses(),old.definition(),old.scale());
                var stack=filtered(t.original,record);var data=CorpsePoseRecord.data(stack);data.putString("separation_event",t.event);data.putLong("corpse_topology_revision",record.revision());stack.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(data));
                var display=child?t.child:t.source;
                if(child){data=visualData(CorpsePoseRecord.data(stack),record,true);stack.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(data));var binding=Forensics.binding(stack);binding.putString("part",t.part);binding.putString("token","");Forensics.setBinding(stack,binding);}
                var request=new CorpsePhysicsBridge.Request(old.armModel().equals("slim"),0,0,CorpseTerrainSnapshot.capture(t.world,origin),null,null,old.scale(),selected);
                var session=CorpsePhysicsCoordinator.diagnosticProvider(t.world.getServer()).prepare(request,split);
                t.pieces.add(new Piece(display,origin,record,stack,request,session));
            }
            CompletableFuture.allOf(t.pieces.stream().map(p->p.session().ready()).toArray(CompletableFuture[]::new)).whenComplete((v,e)->t.world.getServer().execute(()->{
                if(active!=t)return;if(e!=null){fail(t,"物理对象初始化失败："+reason(e));return;}commit(t);
            }));
        }catch(RuntimeException|LinkageError e){fail(t,"准备失败："+reason(e));}
    }
    public static ItemStack filtered(ItemStack original,CorpsePoseRecord pose){
        var stack=original.copy();var data=visualData(CorpsePoseRecord.data(stack),pose,false);stack.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(data));return stack;
    }
    public static NbtCompound visualData(NbtCompound original,CorpsePoseRecord pose,boolean independent){
        var data=original.copy();if(independent)for(String key:List.copyOf(data.getKeys()))if(key.startsWith("body_part_inv"))data.remove(key);
        for(String key:List.of("corpse_wounds","model_paint_faces")){var list=new NbtList();for(var e:data.getListOrEmpty(key))if(e instanceof NbtCompound mark){String surface=mark.getString("surface","");if(WoundGeometry.SURFACES.stream().anyMatch(s->s.name().equals(surface)&&pose.parts().containsKey(s.part())))list.add(mark.copy());}data.put(key,list);}
        data.put(CorpsePoseRecord.KEY,pose.encode());data.putLong("corpse_pose_revision",pose.revision());data.putFloat("corpse_scale",pose.scale());return data;
    }
    private static void commit(Txn t){
        try{
            if(!valid(t)||!CorpseResidentManager.capacity(t.old==null?2:1)||!CorpseHistoryController.separationCapacity(t.old==null?2:1))throw new IllegalArgumentException("提交前载体或容量已改变");
            var parent=t.pieces.get(0);var child=t.pieces.get(1);
            for(var p:t.pieces){var candidate=EntityType.ITEM_DISPLAY.create(t.world,SpawnReason.TRIGGERED);if(candidate==null)throw new IllegalArgumentException("无法校验载体");candidate.setPosition(p.origin());candidate.setItemStack(p.stack());if(!CorpsePoseGeometry.clear(t.world,candidate,CorpsePoseRecord.data(p.stack()),p.origin()))throw new IllegalArgumentException("分离位置与地形碰撞或区块未加载");}
            t.child.setPosition(child.origin());t.child.setItemDisplayContext(ItemDisplayContext.NONE);t.child.setItemStack(child.stack());
            t.interaction=EntityType.INTERACTION.create(t.world,SpawnReason.TRIGGERED);if(t.interaction==null)throw new IllegalArgumentException("无法创建肢体交互");t.interaction.setPosition(child.origin());
            t.child.addCommandTag("monvhua_split_candidate");
            if(!t.world.spawnEntity(t.child)||!t.world.spawnEntity(t.interaction))throw new IllegalArgumentException("载体生成失败");
            BodyPartManager.INTERACTION_TO_DISPLAY.put(t.interaction.getUuid(),t.child.getUuid());BodyPartManager.DISPLAY_TO_INTERACTION.put(t.child.getUuid(),t.interaction.getUuid());Forensics.link(t.child,t.interaction);
            t.touched=true;t.source.setPosition(parent.origin());t.source.setItemStack(parent.stack());
            Forensics.claim(t.child);if(!Forensics.valid(t.world,t.child.getItemStack(),t.child.getUuid().toString()))throw new IllegalArgumentException("离体部位绑定失败");
            CorpseResidentManager.installSeparation(t.source,parent.origin(),parent.request(),parent.session(),t.child,child.origin(),child.request(),child.session());
            t.committed=true;
            if(t.old!=null)t.old.session.close();
            t.child.removeCommandTag("monvhua_split_candidate");
            recordObject(t.source);recordObject(t.child);
            var store=ForensicStore.get(t.world.getServer());var corpse=store.corpse(Forensics.binding(t.original).getString("id",""));var events=corpse.getListOrEmpty("separation_events");var event=new NbtCompound();event.putString("id",t.event);event.putString("parent",t.source.getUuid().toString());event.putString("child",t.child.getUuid().toString());event.putString("part",t.part);event.putLong("tick",t.world.getTime());event.putString("state","COMMITTED");events.add(event);corpse.put("separation_events",events);store.markDirty();
            CorpseGeometrySync.update(t.source);CorpseGeometrySync.update(t.child);
            for(var p:t.pieces)p.session().releaseTransfer();
            active=null;notify(t,true,"已分离 "+t.part+"；离体对象="+t.child.getUuid());
        }catch(RuntimeException|LinkageError e){if(!t.committed)fail(t,"提交失败："+reason(e));else{active=null;if(t.old!=null)t.old.session.close();for(var p:t.pieces)p.session().releaseTransfer();notify(t,true,"该部位已断开");org.slf4j.LoggerFactory.getLogger("monvhua-corpse-physics").error("Separation committed but post-commit update failed",e);}}
    }
    private static void fail(Txn t,String reason){
        if(active!=t)return;
        for(var p:t.pieces)p.session().close();
        if(!t.committed){
            if(t.touched){t.source.setPosition(t.position);t.source.setItemStack(t.original.copy());CorpseGeometrySync.update(t.source);}
            if(t.child!=null){if(Forensics.valid(t.world,t.child.getItemStack(),t.child.getUuid().toString())&&Forensics.hasBinding(t.child.getItemStack()))Forensics.retire(t.world,t.child.getItemStack());Forensics.discardPair(t.world,t.child);}
            if(t.interaction!=null&&!t.interaction.isRemoved())t.interaction.discard();
            if(t.old!=null){t.old.session.releaseTransfer();CorpseHistoryController.completed(t.source);}
        }
        active=null;notify(t,false,reason+"；原遗体保留");
    }
    private static void notify(Txn t,boolean success,String message){
        if(t.notified)return;t.notified=true;
        if(t.command!=null){if(success)t.command.sendFeedback(()->Text.literal(message),true);else t.command.sendError(Text.literal(message));}
        if(t.completion!=null)t.completion.accept(success,message);
    }
    /** Authoritative latest static checkpoint; no inventory is copied into the detached object's stack. */
    public static void recordObject(ItemDisplayEntity display){
        var data=CorpsePoseRecord.data(display.getItemStack());if(!data.contains("separation_event")||!(display.getWorld() instanceof ServerWorld world))return;
        var store=ForensicStore.get(world.getServer());var corpse=store.corpse(Forensics.binding(display.getItemStack()).getString("id",""));var objects=corpse.getCompoundOrEmpty("separated_objects");var n=new NbtCompound();
        var visual=display.getItemStack().copy();var publicData=CorpsePoseRecord.data(visual);publicData.remove("body_part_inv_items");visual.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(publicData));
        n.put("visual_stack",ItemStack.CODEC.encodeStart(NbtOps.INSTANCE,visual).getOrThrow());
        n.putLong("topology_revision",data.getLong("corpse_topology_revision",0));n.putString("dimension",world.getRegistryKey().getValue().toString());n.putDouble("x",display.getX());n.putDouble("y",display.getY());n.putDouble("z",display.getZ());n.put("pose",data.getCompoundOrEmpty(CorpsePoseRecord.KEY).copy());n.putString("part",Forensics.binding(display.getItemStack()).getString("part","all"));n.putString("event",data.getString("separation_event",""));objects.put(display.getUuid().toString(),n);corpse.put("separated_objects",objects);store.markDirty();
    }
    private static void markRemoved(ItemDisplayEntity display){
        if(repairing.contains(display.getUuid())||active!=null&&(display==active.source||display==active.child))return;
        if(!(display.getWorld() instanceof ServerWorld world)||!Forensics.hasBinding(display.getItemStack()))return;
        var store=ForensicStore.get(world.getServer());var binding=Forensics.binding(display.getItemStack());var objects=store.corpse(binding.getString("id","")).getCompoundOrEmpty("separated_objects");
        if(objects.contains(display.getUuid().toString())){objects.getCompoundOrEmpty(display.getUuid().toString()).putBoolean("removed",true);if(Forensics.valid(world,display.getItemStack(),display.getUuid().toString()))Forensics.retire(world,display.getItemStack());store.markDirty();}
    }
    /** Reconcile a chunk's older entity copy with the latest committed topology checkpoint. */
    public static void reconcile(ItemDisplayEntity display){
        if(repairing.contains(display.getUuid())||active!=null&&(display==active.source||display==active.child))return;
        if(!(display.getWorld() instanceof ServerWorld world)||!Forensics.hasBinding(display.getItemStack()))return;
        var binding=Forensics.binding(display.getItemStack());var n=ForensicStore.get(world.getServer()).corpse(binding.getString("id","")).getCompoundOrEmpty("separated_objects").getCompoundOrEmpty(display.getUuid().toString());
        if(n.isEmpty()){if(display.getCommandTags().contains("monvhua_split_candidate"))display.discard();return;}
        if(n.getBoolean("removed",false)){Forensics.discardPair(world,display);return;}
        try{
            var pose=CorpsePoseRecord.decode(n.getCompoundOrEmpty("pose"));var current=CorpsePoseRecord.read(CorpsePoseRecord.data(display.getItemStack()));
            if(current.isEmpty()||current.get().revision()<=pose.revision()){
                var stack=filtered(display.getItemStack(),pose);var data=CorpsePoseRecord.data(stack);data.putString("separation_event",n.getString("event",""));data.putLong("corpse_topology_revision",n.getLong("topology_revision",pose.revision()));stack.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(data));
                display.setItemStack(stack);display.setPosition(n.getDouble("x",display.getX()),n.getDouble("y",display.getY()),n.getDouble("z",display.getZ()));
                display.removeCommandTag("monvhua_split_candidate");
            }
        }catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger("monvhua-corpse-physics").warn("Invalid separated checkpoint for {}",display.getUuid(),e);}
    }
    /** Repair only loaded checkpoint chunks, and only while the original carrier token still owns the UUID. */
    private static void recoverMissing(net.minecraft.server.MinecraftServer server){
        int repaired=0;
        for(var corpse:ForensicStore.get(server).corpseSnapshots())for(String object:corpse.getCompoundOrEmpty("separated_objects").getKeys()){
            if(repaired>=2)return;var n=corpse.getCompoundOrEmpty("separated_objects").getCompoundOrEmpty(object);if(n.getBoolean("removed",false))continue;
            try{
                UUID id=UUID.fromString(object);var key=net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD,net.minecraft.util.Identifier.of(n.getString("dimension","")));var world=server.getWorld(key);
                if(world==null)continue;var pos=new Vec3d(n.getDouble("x",0),n.getDouble("y",0),n.getDouble("z",0));if(!world.isChunkLoaded(BlockPos.ofFloored(pos)))continue;
                if(world.getEntity(id) instanceof ItemDisplayEntity existing){ensureInteraction(existing);continue;}
                var stack=ItemStack.CODEC.parse(NbtOps.INSTANCE,n.getCompoundOrEmpty("visual_stack")).result().orElse(ItemStack.EMPTY);if(stack.isEmpty()||!Forensics.valid(world,stack,object))continue;
                // Whole-body inventories remain with the original parent: never reconstruct a missing inventory carrier from a visual-only checkpoint.
                if(n.getString("part","all").equals("all"))continue;
                var display=EntityType.ITEM_DISPLAY.create(world,SpawnReason.TRIGGERED);if(display==null)continue;display.setUuid(id);display.setPosition(pos);display.setItemDisplayContext(ItemDisplayContext.NONE);display.setItemStack(stack);
                if(!world.spawnEntity(display))continue;
                repairing.add(id);
                try{ensureInteraction(display);repaired++;}finally{repairing.remove(id);}
            }catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger("monvhua-corpse-physics").warn("Unable to restore separated object {}",object,e);}
        }
    }
    private static void ensureInteraction(ItemDisplayEntity display){
        var world=(ServerWorld)display.getWorld();var link=BodyPartManager.DISPLAY_TO_INTERACTION.get(display.getUuid());
        if(link!=null&&world.getEntity(link) instanceof InteractionEntity){CorpseGeometrySync.update(display);return;}
        var interaction=EntityType.INTERACTION.create(world,SpawnReason.TRIGGERED);if(interaction==null)throw new IllegalStateException("Unable to restore interaction");interaction.setPosition(display.getPos());
        if(!world.spawnEntity(interaction))throw new IllegalStateException("Interaction restore rejected");
        BodyPartManager.INTERACTION_TO_DISPLAY.put(interaction.getUuid(),display.getUuid());BodyPartManager.DISPLAY_TO_INTERACTION.put(display.getUuid(),interaction.getUuid());Forensics.link(display,interaction);CorpseGeometrySync.update(display);
    }
    /** Shared family selection for later dissolve/cleanup; removed objects are excluded. */
    public static List<UUID> family(net.minecraft.server.MinecraftServer server,String corpseId){
        var result=new LinkedHashSet<UUID>();var corpse=ForensicStore.get(server).corpse(corpseId);
        for(String key:corpse.getCompoundOrEmpty("separated_objects").getKeys())if(!corpse.getCompoundOrEmpty("separated_objects").getCompoundOrEmpty(key).getBoolean("removed",false))try{result.add(UUID.fromString(key));}catch(IllegalArgumentException ignored){}
        for(String part:corpse.getCompoundOrEmpty("carriers").getKeys())try{result.add(UUID.fromString(corpse.getCompoundOrEmpty("carriers").getCompoundOrEmpty(part).getString("location","")));}catch(IllegalArgumentException ignored){}
        return List.copyOf(result);
    }
    private static String reason(Throwable e){while(e.getCause()!=null)e=e.getCause();return Objects.toString(e.getMessage(),e.getClass().getSimpleName());}
}
