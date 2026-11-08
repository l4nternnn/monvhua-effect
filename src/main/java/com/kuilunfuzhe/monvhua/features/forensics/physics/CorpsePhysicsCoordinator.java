package com.kuilunfuzhe.monvhua.features.forensics.physics;

import com.kuilunfuzhe.monvhua.features.forensics.*;
import com.kuilunfuzhe.monvhua.features.block.body.BodyPartManager;
import com.kuilunfuzhe.monvhua.features.physics_preview.PhysicsPoseDocument;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.entity.decoration.InteractionEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.math.*;
import net.minecraft.text.Text;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import java.util.*;
import java.util.concurrent.*;
import static net.minecraft.server.command.CommandManager.*;

/** Server-thread state machine with an optional in-process client worker; no client result packets. */
public final class CorpsePhysicsCoordinator {
    private static final org.slf4j.Logger LOGGER=org.slf4j.LoggerFactory.getLogger("monvhua-corpse-physics");
    private static volatile MinecraftServer owner;
    private static volatile CorpsePhysicsBridge provider;
    private static volatile long epoch;
    private static volatile boolean automatic;
    private static volatile String last="未连接本地物理核心";
    private static final Map<UUID,Job> jobs=new ConcurrentHashMap<>();
    private static long sampleTick=Long.MIN_VALUE;
    private static int sampled;
    private static long applied,failed,cancelled;
    private record Job(String context,float inputYaw,float speed,NbtCompound visual,UUID request,ItemDisplayEntity display,ServerWorld world,Vec3d origin,Vec3d oldPosition,
                       float yaw,float pitch,org.joml.Matrix4f root,String token,long revision,long epoch,long started,
                       List<CorpsePhysicsBridge.Box> snapshot,CompletableFuture<PhysicsPoseDocument> future,boolean slim) {}
    private CorpsePhysicsCoordinator() {}
    public static synchronized void attach(MinecraftServer server,CorpsePhysicsBridge backend) {
        detach();
        if(backend.apiVersion()!=CorpsePhysicsBridge.API_VERSION){backend.close();last="物理 API 版本不匹配";return;}
        owner=server;provider=backend;sampleTick=Long.MIN_VALUE;sampled=0;last="已连接本地计算服务";
    }
    public static synchronized void detach() {
        epoch++;automatic=false;
        for(var job:jobs.values())job.future.cancel(true);jobs.clear();
        var old=provider;provider=null;owner=null;if(old!=null)CompletableFuture.runAsync(old::close);
        last="世界已断开；自动生成关闭";
    }
    public static void unavailable(String reason){last=reason;}
    public static boolean pending(ItemDisplayEntity display){return display!=null&&jobs.containsKey(display.getUuid());}
    public static String status(){var p=provider;return "单人试验 auto="+automatic+" pending="+jobs.size()+" applied="+applied+" fallback="+failed+" cancelled="+cancelled+" | "+(p==null?"无本地后端":p.status())+" | "+last;}
    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(server!=owner)return;
            for(var job:List.copyOf(jobs.values()))if(!valid(job)||System.nanoTime()-job.started>2_000_000_000L)
                cancel(job.display,"任务超时或载体失效，已保留旧姿态");
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity,world)->{if(entity instanceof ItemDisplayEntity d)cancel(d,"载体卸载");});
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{if(server==owner)detach();});
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->{
            var root=literal("physics").requires(s->s.hasPermissionLevel(2)).executes(c->{c.getSource().sendFeedback(()->Text.literal(status()+" | test / auto on / auto off"),false);return 1;});
            root.then(literal("status").executes(c->{c.getSource().sendFeedback(()->Text.literal(status()),false);return 1;}));
            var auto=literal("auto");
            for(boolean on:new boolean[]{true,false})auto.then(literal(on?"on":"off").executes(c->{
                if(!local(c.getSource()))return 0;automatic=on;
                if(!on)for(var j:List.copyOf(jobs.values()))cancel(j.display,"会话自动生成关闭");
                c.getSource().sendFeedback(()->Text.literal("本次单人会话死亡姿态自动生成："+(on?"开启":"关闭")),false);return 1;
            }));
            root.then(auto).then(literal("test").executes(c->{
                if(!local(c.getSource()))return 0;
                var display=CorpsePoseCommands.createPhysicsTest(c.getSource());if(display==null)return 0;
                boolean submitted=submit(display,display.getPos().add(0,-.01,0),c.getSource().getRotation().y,true,CorpsePhysicsBridge.DESTABILIZATION_SPEED);
                if(submitted)c.getSource().sendFeedback(()->Text.literal("已提交真实地形求解；用 /mvcorpse physics status 查看结果"),false);
                else c.getSource().sendError(Text.literal(last));
                return submitted?1:0;
            }));
            root.then(literal("cancel").then(argument("target",EntityArgumentType.entity()).executes(c->{
                var e=EntityArgumentType.getEntity(c,"target");
                if(e instanceof InteractionEntity){var id=BodyPartManager.INTERACTION_TO_DISPLAY.get(e.getUuid());e=id==null?null:c.getSource().getWorld().getEntity(id);}
                if(e instanceof ItemDisplayEntity d&&pending(d)){cancel(d,"管理员取消");return 1;}return 0;
            })));
            dispatcher.register(literal("mvcorpse").then(root));
        });
    }
    private static boolean local(ServerCommandSource source) {
        if(source.getServer()!=owner||provider==null||!source.getServer().isSingleplayer()) {
            source.sendError(Text.literal("E 阶段仅支持有物理核心的本地单人世界；"+status()));return false;
        }
        return true;
    }
    public static void onDeath(ServerPlayerEntity player,ItemDisplayEntity display,String part) {
        if(!automatic||owner!=player.getServer()||!part.equals("all")||display==null)return;
        if(player.getScale()!=1||player.hasVehicle()||player.isTouchingWater()||player.isGliding()
                ||player.getPose()!=EntityPose.STANDING&&player.getPose()!=EntityPose.DYING
                ||!BodyHitGeometry.normalGravity(com.kuilunfuzhe.monvhua.features.gravity.GravityMagic.getSurfaceGravityDirection(player))) {
            failed++;last="死亡姿态/尺度/重力暂不支持，沿用旧尸体";report(display,"FALLBACK_FINAL",last);return;
        }
        // Knockback/teleports may clear onGround before the death callback even while feet
        // still touch the floor. Test actual nearby collision support without moving the body.
        if(!hasNearbySupport(player)) {
            failed++;last="脚下 0.25 格内无实际支撑，沿用旧尸体";report(display,"FALLBACK_FINAL",last);return;
        }
        // Explicit arm metadata wins. Missing server-side skin metadata uses the documented slim default.
        boolean slim=slimModel(player,display);
        // E1 uses a labelled small forward destabilization, not inferred attack impulse.
        submit(display,player.getPos(),player.getYaw(),slim,CorpsePhysicsBridge.DESTABILIZATION_SPEED);
    }
    public static boolean hasNearbySupport(ServerPlayerEntity player) {
        var world=player.getWorld();var feet=player.getBoundingBox();
        var probe=new Box(feet.minX,player.getY()-.25,feet.minZ,feet.maxX,player.getY()+.002,feet.maxZ);
        for(BlockPos pos:BlockPos.iterate(BlockPos.ofFloored(probe.minX,probe.minY,probe.minZ),BlockPos.ofFloored(probe.maxX,probe.maxY,probe.maxZ))) {
            if(!world.isChunkLoaded(pos))return false;
            for(var local:world.getBlockState(pos).getCollisionShape(world,pos).getBoundingBoxes()) {
                var box=local.offset(pos);
                if(box.maxY>=probe.minY&&box.maxY<=probe.maxY&&box.maxX>probe.minX&&box.minX<probe.maxX
                        &&box.maxZ>probe.minZ&&box.minZ<probe.maxZ)return true;
            }
        }
        return false;
    }
    private static void report(ItemDisplayEntity display,String state,String reason) {
        LOGGER.info("{} corpse={} pos={} yaw={} reason={}",state,display.getUuid(),display.getPos(),display.getYaw(),reason);
    }
    private static boolean slimModel(ServerPlayerEntity player,ItemDisplayEntity display) {
        var data=CorpsePoseRecord.data(display.getItemStack());
        if(data.contains("arm_model"))return data.getString("arm_model","slim").equals("slim");
        // Existing signed skin metadata is read locally; never initiate a skin/network fetch here.
        for(var property:player.getGameProfile().getProperties().get("textures"))try {
            if(property.value().length()>32768)continue;
            var json=com.google.gson.JsonParser.parseString(new String(java.util.Base64.getDecoder().decode(property.value()),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var textures=json.getAsJsonObject("textures");if(textures==null||!textures.has("SKIN"))continue;
            var skin=textures.getAsJsonObject("SKIN");var metadata=skin.getAsJsonObject("metadata");
            return metadata!=null&&metadata.has("model")&&metadata.get("model").getAsString().equals("slim");
        }catch(RuntimeException ignored){/* Unknown/offline profile uses the selected default body type. */}
        return true;
    }
    public static boolean submit(ItemDisplayEntity display,Vec3d origin,float yaw,boolean slim,float speed) {
        var backend=provider;
        if(backend==null||display.getWorld().getServer()!=owner||jobs.containsKey(display.getUuid()))return false;
        if(jobs.size()>=8){failed++;last="队列已满，保留原尸体";report(display,"FALLBACK_FINAL",last);return false;}
        var world=(ServerWorld)display.getWorld();long tick=world.getTime();
        if(sampleTick!=tick){sampleTick=tick;sampled=0;}
        if(++sampled>2){failed++;last="本 tick 采样额度已满，保留原尸体";report(display,"FALLBACK_FINAL",last);return false;}
        try {
            if(CorpseCarryService.isCarried(display)||!Forensics.valid(world,display.getItemStack(),display.getUuid().toString())
                    ||!Forensics.binding(display.getItemStack()).getString("part","").equals("all"))throw new IllegalArgumentException("载体不可用");
            var data=CorpsePoseRecord.data(display.getItemStack());
            if(data.getFloat("corpse_scale",1)!=1||data.getString("body_pose_mode","").equals("true_skeletal"))throw new IllegalArgumentException("非标准模型");
            for(String key:data.getKeys())if(key.startsWith("pose_")||key.startsWith("bend_"))
                if(data.getFloat(key,Float.NaN)!=(key.endsWith("_scale")?1:0))throw new IllegalArgumentException("非中性局部姿态："+key);
            long start=System.nanoTime();var snapshot=CorpseTerrainSnapshot.capture(world,origin);
            var future=backend.solve(new CorpsePhysicsBridge.Request(slim,-MathHelper.wrapDegrees(yaw),speed,snapshot));
            var job=new Job(display.getItemDisplayContext().name(),yaw,speed,EvidenceSnapshots.visualOnly(data),UUID.randomUUID(),display,world,origin,display.getPos(),display.getYaw(),display.getPitch(),
                    new org.joml.Matrix4f(com.kuilunfuzhe.monvhua.mixin.forensics.CorpseDisplayAccessor.monvhua$transform(display.getDataTracker()).getMatrix()),
                    Forensics.binding(display.getItemStack()).getString("token",""),data.getLong("corpse_pose_revision",0),epoch,start,snapshot,future,slim);
            jobs.put(display.getUuid(),job);last="SOLVING "+job.request;
            future.whenComplete((result,error)->world.getServer().execute(()->complete(job,result,error)));
            return true;
        }catch(RuntimeException|LinkageError e){failed++;last="求解未开始："+e.getMessage();report(display,"FALLBACK_FINAL",last);return false;}
    }
    public static void cancel(ItemDisplayEntity display,String reason) {
        var job=jobs.remove(display.getUuid());if(job==null)return;
        job.future.cancel(true);cancelled++;last="FALLBACK_FINAL: "+reason;
        report(display,"CANCELLED",reason);
    }
    private static boolean valid(Job j) {
        return j.epoch==epoch&&j.world.getServer()==owner&&!j.display.isRemoved()
                &&j.world.getEntity(j.display.getUuid())==j.display
                &&j.context.equals(j.display.getItemDisplayContext().name())
                &&j.visual.equals(EvidenceSnapshots.visualOnly(CorpsePoseRecord.data(j.display.getItemStack())))
                &&net.minecraft.util.Identifier.of("monvhua","combined_body").equals(j.display.getItemStack().get(DataComponentTypes.ITEM_MODEL))
                &&j.oldPosition.equals(j.display.getPos())&&j.yaw==j.display.getYaw()&&j.pitch==j.display.getPitch()
                &&j.root.equals(com.kuilunfuzhe.monvhua.mixin.forensics.CorpseDisplayAccessor.monvhua$transform(j.display.getDataTracker()).getMatrix(),1e-6f)
                &&j.revision==CorpsePoseRecord.data(j.display.getItemStack()).getLong("corpse_pose_revision",0)
                &&j.token.equals(Forensics.binding(j.display.getItemStack()).getString("token",""))
                &&Forensics.valid(j.world,j.display.getItemStack(),j.display.getUuid().toString());
    }
    private static void complete(Job job,PhysicsPoseDocument result,Throwable error) {
        if(jobs.get(job.display.getUuid())!=job)return;
        try {
            if(!valid(job)||System.nanoTime()-job.started>2_000_000_000L)throw new IllegalArgumentException("请求已失效/超时");
            if(error!=null)throw new IllegalArgumentException("后端不可用："+error.getMessage());
            if(!result.outcome().equals("SETTLED"))throw new IllegalArgumentException(result.outcome()+": "+result.reason());
            if(!job.snapshot.equals(CorpseTerrainSnapshot.capture(job.world,job.origin)))throw new IllegalArgumentException("现场碰撞形状已变化");
            var record=new CorpsePoseRecord(Math.addExact(job.revision,1),"generated",job.slim?"slim":"default",result.poses(),result.definition());
            CorpsePoseService.applyGenerated(job.display,record,job.origin);
            var store=ForensicStore.get(job.world.getServer());var diagnostics=new NbtCompound();
            diagnostics.putString("request",job.request.toString());diagnostics.putInt("algorithm_version",CorpsePhysicsBridge.ALGORITHM_VERSION);
            diagnostics.putFloat("yaw",job.inputYaw);diagnostics.putFloat("initial_lean",CorpsePhysicsBridge.INITIAL_LEAN_DEGREES);
            diagnostics.putFloat("initial_speed",job.speed);diagnostics.putString("motion_source","fixed_game_destabilization");
            diagnostics.putString("definition",result.definition());diagnostics.putInt("steps",result.step());
            diagnostics.putLong("elapsed_ms",(System.nanoTime()-job.started)/1_000_000);
            store.corpse(Forensics.binding(job.display.getItemStack()).getString("id","")).put("pose_generation_diagnostics",diagnostics);store.markDirty();
            applied++;last="APPLIED_STATIC "+job.request+" "+((System.nanoTime()-job.started)/1_000_000)+"ms";
            report(job.display,"APPLIED_STATIC","request="+job.request+" inputYaw="+job.inputYaw+" steps="+result.step());
        }catch(RuntimeException e){failed++;last="FALLBACK_FINAL: "+e.getMessage();report(job.display,"FALLBACK_FINAL","request="+job.request+" inputYaw="+job.inputYaw+" "+e.getMessage());}
        finally{jobs.remove(job.display.getUuid(),job);}
    }
}
