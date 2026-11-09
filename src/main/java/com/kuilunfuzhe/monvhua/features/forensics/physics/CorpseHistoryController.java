package com.kuilunfuzhe.monvhua.features.forensics.physics;

import com.kuilunfuzhe.monvhua.features.forensics.*;
import com.kuilunfuzhe.monvhua.features.block.body.BodyPartManager;
import com.kuilunfuzhe.monvhua.features.physics_preview.PhysicsPoseDocument;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import java.util.*;
import java.util.concurrent.*;
import static net.minecraft.server.command.CommandManager.*;

/** Integrated-server control authority. No history is written to public corpse data. */
public final class CorpseHistoryController {
    private static final Identifier ID=Identifier.of("monvhua","corpse_physics_controller");
    public static final Item History_Cortol_ITEM =Registry.register(Registries.ITEM,ID,new Item(new Item.Settings()
            .registryKey(RegistryKey.of(RegistryKeys.ITEM,ID)).maxCount(1)));
    private static final int MAX_OBJECTS=16;
    private static final long BUDGET=32L*1024*1024, BYTES_PER_FRAME=4096;
    private static final Map<UUID,Entry> entries=new ConcurrentHashMap<>();
    private static final Map<UUID,UUID> selected=new ConcurrentHashMap<>();
    private static int defaultSeconds=5;
    private static long sequence;

    public static final class Entry {
        public final UUID id;
        public final CorpsePhysicsCoordinator.LiveView view;
        public final CorpsePhysicsBridge.Session session;
        private final ItemDisplayEntity display;
        private final long order=sequence++;
        private ItemStack expected;
        private Vec3d position;
        private float yaw,pitch;
        private org.joml.Matrix4f root;
        public volatile boolean manual,timeline,active,valid=true;
        public volatile long selectedFrame=-1;public volatile int seconds;
        private UUID viewer;
        Entry(ItemDisplayEntity display,CorpsePhysicsCoordinator.LiveView view,int seconds){
            this.id=display.getUuid();this.display=display;this.view=view;
            this.session=view==null?null:view.session();this.seconds=seconds;active=view!=null;refresh();
        }
        private void refresh(){expected=display.getItemStack().copy();position=display.getPos();yaw=display.getYaw();pitch=display.getPitch();
            root=new org.joml.Matrix4f(com.kuilunfuzhe.monvhua.mixin.forensics.CorpseDisplayAccessor.monvhua$transform(display.getDataTracker()).getMatrix());}
        public boolean current(){return valid&&matches();}
        public boolean frozen(){return manual||timeline;}
        public boolean confirmed(){return !active||session!=null&&session.paused();}
        public long first(){var ticks=steps();return ticks.isEmpty()?-1:ticks.getFirst();}
        public long last(){var ticks=steps();return ticks.isEmpty()?-1:ticks.getLast();}
        public List<Long> steps(){return session==null?List.of():session.historyTicks();}
        public long nearest(double requested){
            var ids=steps();if(ids.isEmpty())return -1;
            long best=ids.getFirst();for(long id:ids)if(Math.abs(id-requested)<Math.abs(best-requested))best=id;
            return best;
        }
        public long adjacent(long step,int direction){
            var ids=steps();if(ids.isEmpty())return -1;
            if(direction<0){for(int i=ids.size()-1;i>=0;i--)if(ids.get(i)<step)return ids.get(i);return ids.getFirst();}
            for(long id:ids)if(id>step)return id;return ids.getLast();
        }
        Vec3d targetPoint(){return CorpsePoseGeometry.bloodOrigin(display);}
        public PhysicsPoseDocument pose(){
            if(session==null||!confirmed())return null;
            long first=first(),last=last();if(first<0)return null;
            long step=selectedFrame<0?last:nearest(selectedFrame);
            return session.poseAt(step);
        }
        private boolean sameItem(){
            var current=display.getItemStack();if(ItemStack.areEqual(expected,current))return true;
            // Inventory persistence is not a pose/skin/paint edit and must not destroy a live timeline.
            var a=expected.copy();var b=current.copy();
            for(var stack:List.of(a,b)){var data=CorpsePoseRecord.data(stack);data.remove("body_part_inv_items");stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,net.minecraft.component.type.NbtComponent.of(data));}
            if(!ItemStack.areEqual(a,b))return false;expected=current.copy();return true;
        }
        private boolean matches(){return !display.isRemoved()&&display.getWorld().getEntityById(display.getId())==display
                &&position.equals(display.getPos())&&yaw==display.getYaw()&&pitch==display.getPitch()
                &&root.equals(com.kuilunfuzhe.monvhua.mixin.forensics.CorpseDisplayAccessor.monvhua$transform(display.getDataTracker()).getMatrix(),1e-6f)
                &&sameItem()&&!CorpseCarryService.isCarried(display);}
    }
    public static void initialize(){ /* forces item registration */ }
    public static List<Entry> entries(){return List.copyOf(entries.values());}
    public static Entry get(UUID id){return entries.get(id);}
    public static boolean locked(UUID id){var e=entries.get(id);return e!=null&&e.valid&&e.frozen();}
    private static long reservation(int seconds){return (seconds*12L+1)*BYTES_PER_FRAME+64*1024;}
    private static long used(){return entries.values().stream().mapToLong(e->reservation(e.seconds)).sum();}
    private static boolean reserve(long extra,boolean newEntry,UUID except){
        while(used()+extra>BUDGET||newEntry&&entries.size()>=MAX_OBJECTS){
            var handoffs=CorpsePhysicsCoordinator.liveViews().stream().map(CorpsePhysicsCoordinator.LiveView::display).collect(java.util.stream.Collectors.toSet());
            var oldest=entries.values().stream().filter(e->!e.active&&!e.frozen()&&!e.id.equals(except)&&!handoffs.contains(e.id)).min(Comparator.comparingLong(e->e.order)).orElse(null);
            if(oldest==null)return false;remove(oldest.id);
        }
        return true;
    }
    public static boolean register(ItemDisplayEntity display,CorpsePhysicsCoordinator.LiveView view){
        if(!view.session().supportsHistory()||view.session().historyHz()!=12)return true; // old provider still supports ordinary deaths
        remove(display.getUuid()); // a new solve invalidates the previous timeline and its selection
        if(!reserve(reservation(defaultSeconds),true,display.getUuid()))return false;
        view.session().historySeconds(defaultSeconds);
        entries.put(display.getUuid(),new Entry(display,view,defaultSeconds));return true;
    }
    public static void completed(ItemDisplayEntity display){var e=entries.get(display.getUuid());if(e!=null){e.refresh();e.active=e.session!=null&&e.session.continuous();}}
    public static void remove(UUID id){var e=entries.remove(id);if(e!=null)e.valid=false;selected.values().removeIf(id::equals);}
    public static void reset(){entries.values().forEach(e->e.valid=false);entries.clear();selected.clear();defaultSeconds=5;sequence=0;}
    public static void tick(){
        for(var e:entries.values()){
            if(!e.matches()){remove(e.id);continue;}
            var viewer=e.viewer==null?null:e.display.getWorld().getServer().getPlayerManager().getPlayer(e.viewer);
            if(e.timeline&&(viewer==null||viewer.getWorld()!=e.display.getWorld()||!viewer.isAlive())){
                e.timeline=false;e.viewer=null;updatePause(e);
            }
        }
    }
    private static void updatePause(Entry e){
        if(!e.frozen())e.selectedFrame=-1;
        if(e.active){
            if(!e.frozen()&&!CorpsePhysicsCoordinator.validateResume(e.id))return;
            e.session.setPaused(e.frozen());
        }
    }
    public static boolean paused(UUID id){var e=entries.get(id);return e!=null&&e.frozen();}

    /** Called only on the integrated server, after the client has selected a visible model. */
    public static Entry operate(ServerPlayerEntity player,UUID target,boolean open){
        if(!player.hasPermissionLevel(2)||(!player.getMainHandStack().isOf(History_Cortol_ITEM)&&!player.getMainHandStack().isOf(CorpseGrabController.ITEM))||!player.getServer().isSingleplayer())throw new IllegalArgumentException("仅管理员持控制器可操作本地尸体");
        if(!CorpsePhysicsCoordinator.localControl(player.getServer()))throw new IllegalArgumentException("未连接本地物理核心");
        boolean previouslySelected=target==null;
        if(target==null)target=selected.get(player.getUuid());
        if(target==null)throw new IllegalArgumentException("请先瞄准尸体");
        var entity=player.getWorld().getEntity(target);
        if(entity!=null&&!(entity instanceof ItemDisplayEntity)){
            var mapped=BodyPartManager.INTERACTION_TO_DISPLAY.get(target);entity=mapped==null?null:player.getWorld().getEntity(mapped);
        }
        if(!(entity instanceof ItemDisplayEntity display)||!Forensics.valid(player.getWorld(),display.getItemStack(),display.getUuid().toString()))throw new IllegalArgumentException("尸体已失效");
        var e=entries.get(display.getUuid());
        if(e==null){
            if(CorpsePhysicsCoordinator.pending(display))throw new IllegalArgumentException("当前核心不支持历史工具");
            if(!reserve(reservation(1),true,display.getUuid()))throw new IllegalArgumentException("历史预算已满");
            e=new Entry(display,null,1);entries.put(e.id,e);
        }
        if(!e.matches())throw new IllegalArgumentException("尸体已改变，请重新选择");
        // Validate an unobstructed ray to an actual body volume on the authority thread.
        boolean visible=previouslySelected&&open?CorpseHistoryPicking.reachable(player,e):CorpseHistoryPicking.hit(player,e)>=0;
        if(!visible)throw new IllegalArgumentException("目标超出距离、被遮挡或未瞄准尸体");
        selected.put(player.getUuid(),e.id);
        if(open){CorpseGrabController.releaseTarget(e.id);e.timeline=true;e.viewer=player.getUuid();}
        else e.manual=!e.manual;
        updatePause(e);return e;
    }
    public static void closeTimeline(UUID id,UUID player){var e=entries.get(id);if(e!=null&&Objects.equals(e.viewer,player)){e.timeline=false;e.viewer=null;updatePause(e);}}
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> commands(){
        var root=literal("history").requires(s->s.hasPermissionLevel(2)&&s.getServer().isSingleplayer());
        root.then(literal("status").executes(c->{
            var p=c.getSource().getPlayer();var id=p==null?null:selected.get(p.getUuid());var e=id==null?null:entries.get(id);
            String detail=e==null?"": "；所选窗口="+e.seconds+"s，可用帧="+e.first()+".."+e.last()+"，手动冻结="+e.manual+"，界面冻结="+e.timeline;
            c.getSource().sendFeedback(()->Text.literal("历史默认="+defaultSeconds+"s，对象="+entries.size()+"/16，预留="+used()+"/"+BUDGET+" bytes；一次性模式最多 300 帧；持续模式按活动段限额"+detail),false);return 1;
        }));
        var duration=literal("duration");
        for(boolean all:new boolean[]{true,false})duration.then(literal(all?"default":"selected").then(argument("seconds",com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,60)).executes(c->{
            int n=com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"seconds");
            if(all)defaultSeconds=n;
            else {var e=selection(c.getSource());if(e==null)return 0;
                if(e.session==null){c.getSource().sendError(Text.literal("该尸体没有过程记录"));return 0;}
                if(!reserve(Math.max(0,reservation(n)-reservation(e.seconds)),false,e.id)){c.getSource().sendError(Text.literal("历史内存预算不足"));return 0;}
                e.session.historySeconds(n);e.seconds=n;
                if(e.selectedFrame>=0)e.selectedFrame=e.nearest(e.selectedFrame);
            }
            c.getSource().sendFeedback(()->Text.literal("记录窗口="+n+"s；已淘汰帧不会恢复，运行上限不变"),false);return 1;
        })));
        root.then(duration);
        root.then(literal("clear").then(literal("selected").executes(c->{var e=selection(c.getSource());if(e==null)return 0;if(e.session!=null)e.session.clearHistory();e.selectedFrame=-1;return 1;})));
        return root;
    }
    private static Entry selection(ServerCommandSource source){
        var p=source.getPlayer();var id=p==null?null:selected.get(p.getUuid());var e=id==null?null:entries.get(id);
        if(e==null)source.sendError(Text.literal("请先用控制器选择尸体"));return e;
    }
}
