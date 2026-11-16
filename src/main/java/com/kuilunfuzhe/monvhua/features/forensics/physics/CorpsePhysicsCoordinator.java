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
import static com.kuilunfuzhe.monvhua.features.forensics.physics.CorpsePhysicsDiagnostics.Stage.*;
import java.util.concurrent.*;
import static net.minecraft.server.command.CommandManager.*;

/** Server-thread state machine with an optional native worker; clients never supply pose results. */
public final class CorpsePhysicsCoordinator {
    private static final org.slf4j.Logger LOGGER=org.slf4j.LoggerFactory.getLogger("monvhua-corpse-physics");
    private static volatile MinecraftServer owner;
    private static volatile CorpsePhysicsBridge provider;
    private static volatile long epoch;
    private static volatile boolean automatic;
    private static boolean continuous=defaultContinuous();
    private static boolean defaultContinuous(){return !Boolean.getBoolean("monvhua.physicsAutomaticGameTest")&&!Boolean.getBoolean("monvhua.physicsG4GameTest")&&!Boolean.getBoolean("monvhua.physicsHistoryGameTest");}
    private static final Map<UUID,Long> handoffExpiry=new ConcurrentHashMap<>();
    static void publish(LiveView v){views.put(v.display(),v);if(v.committedRevision()>0)handoffExpiry.put(v.display(),owner==null?0L:(long)owner.getTicks()+40);else handoffExpiry.remove(v.display());}
    static void unpublish(UUID id){views.remove(id);}
    private static volatile String last="未连接本地物理核心";
    private static final Map<UUID,Job> jobs=new ConcurrentHashMap<>();
    private static long sampleTick=Long.MIN_VALUE;
    private static int sampled,terrainCursor;
    private static long terrainTick=Long.MIN_VALUE;
    private static long applied,failed,cancelled;
    private record Job(String context,float inputYaw,float speed,NbtCompound visual,UUID request,ItemDisplayEntity display,ServerWorld world,Vec3d origin,Vec3d oldPosition,
                       float yaw,float pitch,org.joml.Matrix4f root,String token,long revision,long epoch,long started,
                       List<CorpsePhysicsBridge.Box> snapshot,CompletableFuture<PhysicsPoseDocument> future,boolean slim,CorpsePhysicsBridge.Session session,long startTick,CorpseInitialMotionResolver.Motion motion,CorpsePhysicsBridge.Request input) {}
    /** Immutable publication; the copied item is read only on the client. Never persist live frames. */
    public record LiveView(UUID display,String dimension,Vec3d origin,net.minecraft.item.ItemStack skin,
                           boolean slim,CorpsePhysicsBridge.Session session,UUID victim,long committedRevision) {}
    private static final Map<UUID,LiveView> views=new ConcurrentHashMap<>();
    public static List<LiveView> liveViews(){return List.copyOf(views.values());}
    public static void acknowledge(UUID display,long revision){views.computeIfPresent(display,(id,v)->v.committedRevision==revision?null:v);}
    private static final Map<UUID,Long> progressTicks=new ConcurrentHashMap<>();
    private static final Map<UUID,Long> advanceTicks=new ConcurrentHashMap<>();
    private static final Map<UUID,Long> pausedTicks=new ConcurrentHashMap<>();
    private static final Map<UUID,Integer> progressSteps=new ConcurrentHashMap<>();
    private CorpsePhysicsCoordinator() {}
    public static synchronized void attach(MinecraftServer server,CorpsePhysicsBridge backend) {
        detach();
        if(backend.apiVersion()!=CorpsePhysicsBridge.API_VERSION){backend.close();last="物理 API 版本不匹配";return;}
        owner=server;provider=backend;CorpseProfileManager.start(server);sampleTick=Long.MIN_VALUE;terrainTick=Long.MIN_VALUE;terrainCursor=0;sampled=0;last="已连接本地计算服务";
    }
    public static synchronized void detach() {
        epoch++;automatic=false;KomDyingCompat.reset();CorpseSeparationService.reset();CorpseTransportController.reset();CorpseGrabController.reset();CorpseResidentManager.reset();continuous=defaultContinuous();CorpsePhysicsDiagnostics.clear();CorpseProfileManager.reset();CorpseHistoryController.reset();pausedTicks.clear();
        for(var job:jobs.values()){job.future.cancel(true);if(job.session!=null)job.session.close();}jobs.clear();views.clear();handoffExpiry.clear();progressTicks.clear();progressSteps.clear();advanceTicks.clear();
        var old=provider;provider=null;owner=null;if(old!=null)CompletableFuture.runAsync(old::close);
        last="世界已断开；自动生成关闭";
    }
    public static void unavailable(String reason){last=reason;}
    public static CorpsePhysicsBridge diagnosticProvider(MinecraftServer server){return server==owner?provider:null;}
    public static boolean simulating(ItemDisplayEntity display){return display!=null&&(CorpseSeparationService.busy(display.getUuid())||jobs.containsKey(display.getUuid())||CorpseResidentManager.moving(display.getUuid()));}
    public static boolean pending(ItemDisplayEntity display){return display!=null&&(CorpseSeparationService.busy(display.getUuid())||jobs.containsKey(display.getUuid())||CorpseResidentManager.moving(display.getUuid())||CorpseHistoryController.locked(display.getUuid()));}
    public static boolean automaticEnabled(){return automatic;}
    public static boolean localControl(MinecraftServer server){return server==owner&&provider!=null;}
    public static boolean validateResume(UUID id){
        if(CorpseResidentManager.contains(id))return CorpseResidentManager.resume(id);
        var j=jobs.get(id);if(j==null)return false;
        try {if(valid(j)&&j.snapshot.equals(CorpseTerrainSnapshot.capture(j.world,j.origin)))return true;}
        catch(RuntimeException ignored){}
        cancel(j.display,"恢复前地形/载体校验失败",valid(j)?TERRAIN_CHANGED:CARRIER_INVALID);return false;
    }
    public static String status(){var p=provider;return "服务器物理 mode="+(continuous?"CONTINUOUS":"ONE_SHOT")+" "+CorpseResidentManager.status()+" downed="+KomDyingCompat.count()+" auto="+automatic+" pending="+jobs.size()+" applied="+applied+" fallback="+failed+" cancelled="+cancelled+" | "+(p==null?"无本地后端":p.status())+" | "+last;}
    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            try {
                var backend=(CorpsePhysicsBridge)Class.forName("com.kuilunfuzhe.monvhua.features.physics_preview.LocalCorpsePhysicsProvider").getConstructor().newInstance();
                attach(server,backend);automatic=!Boolean.getBoolean("monvhua.physics.disableAutomatic");
                LOGGER.info("Server physics attached; native readiness: {}",backend.status());
            }catch(ReflectiveOperationException|LinkageError error){unavailable("物理核心不可用，保留静态遗体："+error.getClass().getSimpleName());LOGGER.error("Physics backend unavailable",error);}
        });
        KomDyingCompat.initialize();
        CorpseSeparationService.initialize();
        CorpsePhysicsNetwork.initialize();
        CorpseHistoryController.initialize();CorpseGrabController.initialize();
        ServerTickEvents.END_SERVER_TICK.register(server->{
            DeathMotionContext.expire(server);
            handoffExpiry.entrySet().removeIf(e->{if(server.getTicks()<e.getValue())return false;views.computeIfPresent(e.getKey(),(id,v)->v.committedRevision()>0?null:v);return true;});
            if(server!=owner)return;
            CorpseGrabController.tick(server);CorpseTransportController.tick(server);CorpseResidentManager.tick();
            CorpseHistoryController.tick();
            for(var job:List.copyOf(jobs.values())) {
                long tick=job.world.getTime();
                if(!valid(job)||tick-job.startTick-pausedTicks.getOrDefault(job.display.getUuid(),0L)>240) {cancel(job.display,"任务超时或载体失效，已保留旧姿态",valid(job)?HOST_TIMEOUT:CARRIER_INVALID);continue;}
                if(job.session!=null) {
                    var advanced=advanceTicks.put(job.display.getUuid(),tick);
                    if(advanced!=null&&advanced==tick)continue;
                    if(CorpseHistoryController.paused(job.display.getUuid())&&job.session.paused()){
                        pausedTicks.merge(job.display.getUuid(),advanced==null?1L:Math.max(0,tick-advanced),Long::sum);
                        progressTicks.put(job.display.getUuid(),tick);continue;
                    }
                    var frames=job.session.frames();int step=frames.isEmpty()?0:frames.getLast().step();
                    Integer previous=progressSteps.put(job.display.getUuid(),step);
                    if(previous==null||previous!=step)progressTicks.put(job.display.getUuid(),tick);
                    if(tick-progressTicks.getOrDefault(job.display.getUuid(),tick)>60){cancel(job.display,"原生会话无进度",NO_PROGRESS);continue;}
                    job.session.advanceTo(Math.min(300,step+3));
                }
            }
            var active=List.copyOf(jobs.values());long now=server.getOverworld().getTime();
            if(now!=terrainTick&&!active.isEmpty()) {
                terrainTick=now;
                // Round-robin validation, at most two captures per game tick.
                for(int i=0;i<Math.min(2,active.size());i++) {
                    var job=active.get(Math.floorMod(terrainCursor++,active.size()));
                    try {if(!job.snapshot.equals(CorpseTerrainSnapshot.capture(job.world,job.origin)))cancel(job.display,"模拟期间地形变化",TERRAIN_CHANGED);}
                    catch(RuntimeException e){cancel(job.display,"模拟区域卸载/失效",TERRAIN_CHANGED);}
                }
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity,world)->{if(entity instanceof ItemDisplayEntity d){cancel(d,"载体卸载");views.remove(d.getUuid());CorpseHistoryController.remove(d.getUuid());}});
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{DeathMotionContext.clear(server);if(server==owner)detach();});
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->{
            var root=literal("physics").requires(s->s.hasPermissionLevel(2)).executes(c->{c.getSource().sendFeedback(()->Text.literal(status()+" | test / auto on / auto off"),false);return 1;});
            root.then(literal("status").executes(c->{c.getSource().sendFeedback(()->Text.literal(status()),false);return 1;}));
            var auto=literal("auto");
            for(boolean on:new boolean[]{true,false})auto.then(literal(on?"on":"off").executes(c->{
                if(!local(c.getSource()))return 0;automatic=on;
                if(!on){KomDyingCompat.clearProxies();CorpseResidentManager.reset();for(var j:List.copyOf(jobs.values()))cancel(j.display,"会话自动生成关闭");}
                c.getSource().sendFeedback(()->Text.literal("本次服务器会话死亡姿态自动生成："+(on?"开启":"关闭")),false);return 1;
            }));
            var mode=literal("mode");
            for(boolean value:new boolean[]{true,false})mode.then(literal(value?"continuous":"one-shot").executes(c->{continuous=value;c.getSource().sendFeedback(()->Text.literal("新请求模式："+(value?"持续模拟":"一次性倒地")+"；已有对象保持原模式"),false);return 1;}));
            root.then(mode);
            root.then(literal("wake").then(argument("target",EntityArgumentType.entity()).executes(c->{
                var entity=EntityArgumentType.getEntity(c,"target");var id=entity.getUuid();if(entity instanceof InteractionEntity)id=BodyPartManager.INTERACTION_TO_DISPLAY.get(id);
                boolean ok=id!=null&&CorpseResidentManager.wake(id);c.getSource().sendFeedback(()->Text.literal(ok?"已请求在完整帧边界唤醒；手动冻结时等待解冻":"目标不是有效持续对象"),false);return ok?1:0;
            })));
            root.then(auto).then(literal("test").executes(c->{
                if(!local(c.getSource()))return 0;
                var display=CorpsePoseCommands.createPhysicsTest(c.getSource());if(display==null)return 0;
                boolean submitted=submit(display,display.getPos().add(0,-.01,0),c.getSource().getRotation().y,true,CorpsePhysicsBridge.DESTABILIZATION_SPEED);
                if(submitted)c.getSource().sendFeedback(()->Text.literal("已提交真实地形求解；用 /mvcorpse physics status 查看结果"),false);
                else c.getSource().sendError(Text.literal(last));
                return submitted?1:0;
            }));
            var motionTest=literal("test-motion");
            for(String side:List.of("front","back","left","right","unknown"))motionTest.then(literal(side).executes(c->{
                if(!local(c.getSource()))return 0;
                var display=CorpsePoseCommands.createPhysicsTest(c.getSource());if(display==null)return 0;
                float yaw=c.getSource().getRotation().y;
                var motion=CorpseInitialMotionResolver.test(yaw,side,CorpseProfileManager.current());
                boolean ok=submitMotion(display,display.getPos().add(0,-.01,0),yaw,true,CorpsePhysicsBridge.DESTABILIZATION_SPEED,motion);
                if(ok)c.getSource().sendFeedback(()->Text.literal("受击模拟：攻击来自身体 "+side+"；身体朝向保持不变"),false);
                else c.getSource().sendError(Text.literal(last));return ok?1:0;
            }));
            root.then(motionTest);
            root.then(CorpseHistoryController.commands());
            root.then(CorpseProfileManager.commands());
            CorpsePhysicsDiagnostics.register(root);
            root.then(literal("cancel").then(argument("target",EntityArgumentType.entity()).executes(c->{
                var e=EntityArgumentType.getEntity(c,"target");
                if(e instanceof InteractionEntity){var id=BodyPartManager.INTERACTION_TO_DISPLAY.get(e.getUuid());e=id==null?null:c.getSource().getWorld().getEntity(id);}
                if(e instanceof ItemDisplayEntity d&&(pending(d)||CorpseResidentManager.contains(d.getUuid()))){cancel(d,"管理员取消");return 1;}return 0;
            })));
            dispatcher.register(literal("mvcorpse").then(root));
        });
    }
    private static boolean local(ServerCommandSource source) {
        if(source.getServer()!=owner||provider==null) {
            source.sendError(Text.literal("服务器物理核心不可用；"+status()));return false;
        }
        return true;
    }
    public static void onDeath(ServerPlayerEntity player,ItemDisplayEntity display,String part) {
        if(KomDyingCompat.handoff(player,display,part))return;
        var motion=DeathMotionContext.consume(player,display==null?"":Forensics.binding(display.getItemStack()).getString("id",""));
        if(!automatic||owner!=player.getServer()||!part.equals("all")||display==null)return;
        if(!com.kuilunfuzhe.monvhua.features.physics_preview.CorpseScale.supported(player.getScale())||player.hasVehicle()||player.isTouchingWater()||player.isGliding()
                ||player.getPose()!=EntityPose.STANDING&&player.getPose()!=EntityPose.DYING
                ||!BodyHitGeometry.normalGravity(com.kuilunfuzhe.monvhua.features.gravity.GravityMagic.getSurfaceGravityDirection(player))) {
            CorpsePhysicsDiagnostics.rejected(INPUT_REJECTED);failed++;last="死亡姿态/尺度/重力暂不支持，沿用静态遗体；scale="+player.getScale()+"，物理范围为 0.5–2.0";report(display,"FALLBACK_FINAL",last);return;
        }
        // Knockback/teleports may clear onGround before the death callback even while feet
        // still touch the floor. Test actual nearby collision support without moving the body.
        if(!hasNearbySupport(player)) {
            CorpsePhysicsDiagnostics.rejected(INPUT_REJECTED);failed++;last="脚下 0.25 格内无实际支撑，沿用旧尸体";report(display,"FALLBACK_FINAL",last);return;
        }
        // Explicit arm metadata wins. Missing server-side skin metadata uses the documented slim default.
        boolean slim=slimModel(player,display);
        if(submitMotion(display,player.getPos(),motion.yaw(),slim,CorpsePhysicsBridge.DESTABILIZATION_SPEED,motion)){
            CorpseResidentManager.victim(display.getUuid(),player.getUuid());
            views.computeIfPresent(display.getUuid(),(id,v)->new LiveView(v.display,v.dimension,v.origin,v.skin,v.slim,v.session,player.getUuid(),0));
        }
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
    private static String motionDescription(CorpseInitialMotionResolver.Motion m) {
        return " profile="+m.profile().id()+" hash="+m.profile().hash()+" motion_v=2 source="+m.source()+" quality="+m.quality()+" incoming="+m.incoming()+" velocity=("+m.vx()+",0,"+m.vz()+") clamped="+m.clamped()+" fallback="+m.fallback();
    }
    private static void report(ItemDisplayEntity display,String state,String reason) {
        LOGGER.info("{} corpse={} pos={} yaw={} reason={}",state,display.getUuid(),display.getPos(),display.getYaw(),reason);
    }
    static boolean slimModel(ServerPlayerEntity player,ItemDisplayEntity display) {
        var data=display==null?new NbtCompound():CorpsePoseRecord.data(display.getItemStack());
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
    public static synchronized boolean submit(ItemDisplayEntity display,Vec3d origin,float yaw,boolean slim,float speed) {
        return submitMotion(display,origin,yaw,slim,speed,null);
    }
    private static synchronized boolean submitMotion(ItemDisplayEntity display,Vec3d origin,float yaw,boolean slim,float speed,CorpseInitialMotionResolver.Motion motion) {
        var backend=provider;
        if(backend==null||display.getWorld().getServer()!=owner){CorpsePhysicsDiagnostics.rejected(BACKEND_UNAVAILABLE);return false;}
        if(jobs.containsKey(display.getUuid())||CorpseResidentManager.contains(display.getUuid())){CorpsePhysicsDiagnostics.rejected(CAPACITY);return false;}
        if(!continuous&&(jobs.size()>=8||views.size()>=8)){CorpsePhysicsDiagnostics.rejected(CAPACITY);failed++;last="队列已满，保留原尸体";report(display,"FALLBACK_FINAL",last);return false;}
        var world=(ServerWorld)display.getWorld();long tick=world.getTime();
        if(sampleTick!=tick){sampleTick=tick;sampled=0;}
        if(++sampled>2){CorpsePhysicsDiagnostics.rejected(CAPACITY);failed++;last="本 tick 采样额度已满，保留原尸体";report(display,"FALLBACK_FINAL",last);return false;}
        CorpsePhysicsBridge.Request captured=null;CorpsePhysicsBridge.Session opened=null;
        try {
            if(CorpseCarryService.isCarried(display)||!Forensics.valid(world,display.getItemStack(),display.getUuid().toString())
                    ||!Forensics.binding(display.getItemStack()).getString("part","").equals("all"))throw new IllegalArgumentException("载体不可用");
            var data=CorpsePoseRecord.data(display.getItemStack());
            com.kuilunfuzhe.monvhua.features.physics_preview.CorpseScale.require(data.getFloat("corpse_scale",1));
            if(data.getString("body_pose_mode","").equals("true_skeletal"))throw new IllegalArgumentException("非标准模型");
            for(String key:data.getKeys())if(key.startsWith("pose_")||key.startsWith("bend_"))
                if(data.getFloat(key,Float.NaN)!=(key.endsWith("_scale")?1:0))throw new IllegalArgumentException("非中性局部姿态："+key);
            long start=System.nanoTime();var snapshot=CorpseTerrainSnapshot.capture(world,origin);
            var request=new CorpsePhysicsBridge.Request(slim,-MathHelper.wrapDegrees(yaw),speed,snapshot,
                    motion==null?null:new CorpsePhysicsBridge.Motion(motion.vx(),motion.vz(),motion.dx(),motion.dz(),motion.tilt(),motion.profile().motion().angularSpeed()),motion==null?null:motion.profile(),data.getFloat("corpse_scale",1),CorpsePoseRecord.read(data).map(p->p.parts().keySet()).orElse(Set.copyOf(PhysicsPoseDocument.IDS)));
            captured=request;
            if(continuous)return CorpseResidentManager.open(display,origin,request,backend,motion);
            var session=backend.open(request);opened=session;
            var future=session==null?backend.solve(request):session.completion();
            var job=new Job(display.getItemDisplayContext().name(),yaw,speed,EvidenceSnapshots.visualOnly(data),UUID.randomUUID(),display,world,origin,display.getPos(),display.getYaw(),display.getPitch(),
                    new org.joml.Matrix4f(com.kuilunfuzhe.monvhua.mixin.forensics.CorpseDisplayAccessor.monvhua$transform(display.getDataTracker()).getMatrix()),
                    Forensics.binding(display.getItemStack()).getString("token",""),data.getLong("corpse_pose_revision",0),epoch,start,snapshot,future,slim,session,world.getTime(),motion,request);
            jobs.put(display.getUuid(),job);last="SIMULATING "+job.request+(motion==null?" legacy":motionDescription(motion));
            report(display,"SIMULATING","request="+job.request+" bodyYaw="+yaw+(motion==null?" legacy":motionDescription(motion)));
            if(session!=null){
                var view=new LiveView(display.getUuid(),world.getRegistryKey().getValue().toString(),origin,display.getItemStack().copy(),slim,session,null,0);
                if(!CorpseHistoryController.register(display,view)){cancel(display,"历史内存预算不足",CAPACITY);return false;}
                views.put(display.getUuid(),view);
            }
            future.whenComplete((result,error)->world.getServer().execute(()->complete(job,result,error)));
            return true;
        }catch(RuntimeException|LinkageError e){if(jobs.containsKey(display.getUuid()))cancel(display,"提交失败",INPUT_REJECTED);else {if(opened!=null)opened.close();CorpsePhysicsDiagnostics.record(e instanceof RejectedExecutionException?CAPACITY:INPUT_REJECTED,captured,null,new CorpsePhysicsBridge.Metrics(0,0,0),0);}failed++;last="求解未开始："+e.getMessage();report(display,"FALLBACK_FINAL",last);return false;}
    }
    public static void cancel(ItemDisplayEntity display,String reason) {if(CorpseResidentManager.contains(display.getUuid()))CorpseResidentManager.remove(display.getUuid());else cancel(display,reason,CANCELLED);}
    private static void cancel(ItemDisplayEntity display,String reason,CorpsePhysicsDiagnostics.Stage stage) {
        CorpseHistoryController.remove(display.getUuid());pausedTicks.remove(display.getUuid());
        var job=jobs.remove(display.getUuid());if(job==null)return;
        CorpsePhysicsDiagnostics.record(stage,job.input,null,job.session==null?new CorpsePhysicsBridge.Metrics(0,0,0):job.session.metrics(),System.nanoTime()-job.started);
        job.future.cancel(true);if(job.session!=null)job.session.close();views.remove(display.getUuid());advanceTicks.remove(display.getUuid());progressTicks.remove(display.getUuid());progressSteps.remove(display.getUuid());cancelled++;last="FALLBACK_FINAL: "+reason;
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
    private static synchronized void complete(Job job,PhysicsPoseDocument result,Throwable error) {
        if(jobs.get(job.display.getUuid())!=job)return;
        var stage=CARRIER_INVALID;
        try {
            if(!valid(job))throw new IllegalArgumentException("请求已失效");
            stage=HOST_TIMEOUT;
            if(job.world.getTime()-job.startTick-pausedTicks.getOrDefault(job.display.getUuid(),0L)>240)throw new IllegalArgumentException("请求已超时");
            stage=BACKEND_UNAVAILABLE;
            if(error!=null){Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();if(cause instanceof IllegalArgumentException)stage=INITIALIZATION_REJECTED;}
            if(error!=null)throw new IllegalArgumentException("后端不可用："+error.getMessage());
            stage=switch(result.outcome()){case "TIMEOUT"->SOLVER_TIMEOUT;case "LEFT_REGION"->LEFT_REGION;default->SOLVER_INVALID;};
            if(!result.outcome().equals("SETTLED"))throw new IllegalArgumentException(result.outcome()+": "+result.reason());
            stage=TERRAIN_CHANGED;
            if(!job.snapshot.equals(CorpseTerrainSnapshot.capture(job.world,job.origin)))throw new IllegalArgumentException("现场碰撞形状已变化");
            stage=STATIC_COMMIT_REJECTED;
            var record=new CorpsePoseRecord(Math.addExact(job.revision,1),"generated",job.slim?"slim":"default",result.poses(),result.definition(),result.scale());
            CorpsePoseService.applyGenerated(job.display,record,job.origin);
            CorpseHistoryController.completed(job.display);
            var store=ForensicStore.get(job.world.getServer());var diagnostics=new NbtCompound();
            diagnostics.putString("request",job.request.toString());diagnostics.putInt("algorithm_version",CorpsePhysicsBridge.ALGORITHM_VERSION);
            diagnostics.putFloat("yaw",job.inputYaw);diagnostics.putFloat("initial_lean",CorpsePhysicsBridge.INITIAL_LEAN_DEGREES);
            diagnostics.putFloat("initial_speed",job.speed);diagnostics.putString("motion_source","fixed_game_destabilization");
            if(job.motion!=null) {
                var m=job.motion;diagnostics.putString("motion_source",m.source());diagnostics.putString("direction_quality",m.quality());
                diagnostics.putString("incoming",m.incoming());diagnostics.putString("motion_fallback",m.fallback());
                diagnostics.putFloat("vx",m.vx());diagnostics.putFloat("vz",m.vz());diagnostics.putFloat("direction_x",m.dx());diagnostics.putFloat("direction_z",m.dz());
                diagnostics.putFloat("baseline_x",m.baselineX());diagnostics.putFloat("baseline_z",m.baselineZ());diagnostics.putBoolean("clamped",m.clamped());
                diagnostics.putString("profile_id",m.profile().id());diagnostics.putString("profile_hash",m.profile().hash());
                diagnostics.putString("profile_snapshot",new com.google.gson.Gson().toJson(m.profile()));
                diagnostics.putFloat("initial_angular_speed",m.profile().motion().angularSpeed());diagnostics.putFloat("initial_lean",m.tilt());diagnostics.putFloat("initial_speed",(float)Math.hypot(m.vx(),m.vz()));
            }
            diagnostics.putString("definition",result.definition());diagnostics.putInt("steps",result.step());
            diagnostics.putLong("elapsed_ms",(System.nanoTime()-job.started)/1_000_000);
            store.corpse(Forensics.binding(job.display.getItemStack()).getString("id","")).put("pose_generation_diagnostics",diagnostics);store.markDirty();
            views.computeIfPresent(job.display.getUuid(),(id,v)->new LiveView(v.display,v.dimension,v.origin,v.skin,v.slim,v.session,v.victim,record.revision()));
            handoffExpiry.put(job.display.getUuid(),(long)owner.getTicks()+40);
            stage=APPLIED;
            applied++;last="APPLIED_STATIC "+job.request+" "+((System.nanoTime()-job.started)/1_000_000)+"ms"+(job.motion==null?"":motionDescription(job.motion));
            report(job.display,"APPLIED_STATIC","request="+job.request+" inputYaw="+job.inputYaw+" steps="+result.step());
        }catch(RuntimeException e){CorpseHistoryController.remove(job.display.getUuid());views.remove(job.display.getUuid());failed++;last="FALLBACK_FINAL: "+e.getMessage();report(job.display,"FALLBACK_FINAL","request="+job.request+" inputYaw="+job.inputYaw+" "+e.getMessage());}
        finally{CorpsePhysicsDiagnostics.record(stage,job.input,result,job.session==null?new CorpsePhysicsBridge.Metrics(0,0,0):job.session.metrics(),System.nanoTime()-job.started);pausedTicks.remove(job.display.getUuid());jobs.remove(job.display.getUuid(),job);advanceTicks.remove(job.display.getUuid());progressTicks.remove(job.display.getUuid());progressSteps.remove(job.display.getUuid());}
    }
}
