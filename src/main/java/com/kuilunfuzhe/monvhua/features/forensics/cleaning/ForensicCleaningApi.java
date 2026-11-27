package com.kuilunfuzhe.monvhua.features.forensics.cleaning;

import com.kuilunfuzhe.monvhua.features.forensics.*;
import com.kuilunfuzhe.monvhua.features.forensics.blood.*;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import java.util.*;

/** Trusted server API. Call on server thread. Callers own access checks and resource charges. */
public final class ForensicCleaningApi {
    public static final int API_VERSION=2;
    public enum Part { HANDS("hands"), TORSO("torso"), LEFT_SOLE("left_sole"), RIGHT_SOLE("right_sole");
        public final String slot; Part(String slot){this.slot=slot;}
    }
    public enum Method { WASH, WIPE }
    public enum Status { APPLIED, NO_CHANGE, REJECTED, DUPLICATE }
    public record Result(Status status,int changed,int visibleRemoved,int transferableRemoved,int skipped,String message) {
        static Result reject(String s){return new Result(Status.REJECTED,0,0,0,0,s);}
    }
    /** Exactly one of player/corpseDisplay must be non-null. Corpse target is an already loaded display UUID. */
    public record BodyRequest(UUID actionId,UUID player,UUID corpseDisplay,String expectedLifeId,Set<Part> parts,double strength) {
        public BodyRequest { parts=Set.copyOf(parts); }
    }
    public record SceneRequest(UUID actionId,Vec3d center,double radius,double strength,Method method) {}
    /** Capture on the server at action start. Empty references denote a genuinely unstained item. */
    public record WeaponTarget(String residueId,long revision) {}
    public record WeaponRequest(UUID actionId,UUID player,Hand hand,String expectedResidueId,long expectedRevision,double strength) {}
    private record Budget(int tick,int used){}
    private static final Map<MinecraftServer,Budget> BUDGETS=new WeakHashMap<>();
    private ForensicCleaningApi(){}
    private static Result validate(MinecraftServer s,UUID id,double strength){
        if(!s.isOnThread())return Result.reject("必须在服务器线程调用");
        if(!BloodRules.enabled()||!ForensicConfig.current.cleaning.enabled)return Result.reject("清洗功能已关闭");
        if(id==null||!Double.isFinite(strength)||strength<0||strength>1)return Result.reject("无效动作 ID 或强度");
        return null;
    }
    private static Result receipt(MinecraftServer s,UUID id,String key,boolean preview){
        if(preview)return null;
        var store=CleaningActionStore.get(s);long now=BloodTraceService.now(s);store.prune(now);
        var old=store.get(id.toString());
        if(!old.isEmpty())return old.getString("fingerprint","").equals(key)
            ?new Result(Status.DUPLICATE,old.getInt("changed",0),old.getInt("visible",0),old.getInt("transferred",0),old.getInt("skipped",0),"动作已提交，未重复清洗")
            :Result.reject("同一动作 ID 不可使用不同参数");
        return store.size()>=ForensicConfig.current.cleaning.actionLimit?Result.reject("清洗记录容量已满"):null;
    }
    private static boolean budget(MinecraftServer s,int amount,boolean preview){
        var b=BUDGETS.get(s);int used=b!=null&&b.tick==s.getTicks()?b.used:0;
        if(used+amount>ForensicConfig.current.cleaning.perTickBudget)return false;
        if(!preview)BUDGETS.put(s,new Budget(s.getTicks(),used+amount));return true;
    }
    private static Result finish(MinecraftServer s,UUID id,String key,boolean preview,int changed,int visible,int wet,int skipped){
        if(!preview)CleaningActionStore.get(s).put(id.toString(),key,BloodTraceService.now(s),changed,visible,wet,skipped);
        return new Result(changed>0?Status.APPLIED:Status.NO_CHANGE,changed,visible,wet,skipped,preview?"预览；未修改":"清洗完成");
    }
    /** Capture this when an external cleaning action starts, not after a respawn. */
    public static Optional<String> currentPlayerLifeId(MinecraftServer s,UUID player){
        if(!s.isOnThread())throw new IllegalStateException("Server thread required");
        var p=s.getPlayerManager().getPlayer(player);
        return p==null||!p.isAlive()?Optional.empty():Optional.of(ForensicStore.get(s).life(player).getString("id",""));
    }
    public static Optional<String> currentCorpseLifeId(MinecraftServer s,UUID display){
        if(!s.isOnThread())throw new IllegalStateException("Server thread required");
        for(var world:s.getWorlds())if(world.getEntity(display) instanceof ItemDisplayEntity d&&Forensics.valid(world,d.getItemStack(),display.toString())){
            String id=ForensicStore.get(s).corpse(Forensics.binding(d.getItemStack()).getString("id","")).getString("id","");
            return id.isEmpty()?Optional.empty():Optional.of(id);
        }
        return Optional.empty();
    }
    public static Result cleanBody(MinecraftServer server,BodyRequest request){return body(server,request,false);}
    public static Result previewBody(MinecraftServer server,BodyRequest request){return body(server,request,true);}
    private static Result body(MinecraftServer s,BodyRequest r,boolean preview){
        var failure=validate(s,r.actionId,r.strength);if(failure!=null)return failure;
        if(!BloodTransferService.enabled()||(r.player==null)==(r.corpseDisplay==null)||r.parts.isEmpty()||r.expectedLifeId==null||r.expectedLifeId.isEmpty())return Result.reject("无效身体目标");
        String key="body/"+r.player+"/"+r.corpseDisplay+"/"+r.expectedLifeId+"/"+r.parts.stream().sorted().toList()+"/"+r.strength;
        failure=receipt(s,r.actionId,key,preview);if(failure!=null)return failure;
        String life;
        if(r.player!=null){
            var p=s.getPlayerManager().getPlayer(r.player);if(p==null||!p.isAlive())return Result.reject("目标玩家不在线或已死亡");
            life=ForensicStore.get(s).life(r.player).getString("id","");
        }else{
            ItemDisplayEntity d=null;
            for(var w:s.getWorlds())if(w.getEntity(r.corpseDisplay) instanceof ItemDisplayEntity found){d=found;break;}
            if(d==null||!Forensics.valid((ServerWorld)d.getWorld(),d.getItemStack(),d.getUuid().toString()))return Result.reject("尸体未加载或载体无效");
            var binding=Forensics.binding(d.getItemStack());String part=binding.getString("part","");
            for(Part slot:r.parts)if(!part.equals("all")&&!matches(part,slot))return Result.reject("部位不属于该尸体载体");
            // Shared hand contamination cannot be erased through one detached arm.
            if(!part.equals("all")&&r.parts.contains(Part.HANDS))return Result.reject("共享手部污染需完整遗体处理");
            life=ForensicStore.get(s).corpse(binding.getString("id","")).getString("id","");
        }
        if(!life.equals(r.expectedLifeId))return Result.reject("目标生命已变化");
        if(!budget(s,r.parts.size(),preview))return Result.reject("本 tick 清洗预算不足");
        var store=BloodTransferStore.get(s);int changed=0,visible=0,wet=0;
        for(Part part:r.parts){boolean has=r.strength>0&&(store.visible(life+"/"+part.slot)>0||store.available(life+"/"+part.slot,BloodTraceService.now(s))>0);var c=store.clean(life+"/"+part.slot,r.strength,BloodTraceService.now(s),!preview);if(has)changed++;visible+=c.visible();wet+=c.transferable();}
        return finish(s,r.actionId,key,preview,changed,visible,wet,0);
    }
    private static boolean matches(String part,Part slot){return switch(slot){case TORSO->part.equals("torso");case LEFT_SOLE->part.equals("left_leg");case RIGHT_SOLE->part.equals("right_leg");case HANDS->part.endsWith("arm");};}
    /** No residue records or item components are created by capture, preview or cleaning. */
    public static Optional<WeaponTarget> currentWeaponTarget(MinecraftServer s,UUID player,Hand hand){
        if(!s.isOnThread())throw new IllegalStateException("Server thread required");
        if(player==null||hand==null)return Optional.empty();
        var p=s.getPlayerManager().getPlayer(player);
        if(p==null||!p.isAlive())return Optional.empty();
        return weaponTarget(BloodTransferStore.get(s),p.getStackInHand(hand));
    }
    static Optional<WeaponTarget> weaponTarget(BloodTransferStore store,ItemStack stack){
        if(stack.isEmpty()||stack.getMaxCount()!=1||stack.getCount()!=1)return Optional.empty();
        String ref=BloodTransferService.itemRef(stack);
        if(!ref.isEmpty()&&store.record(ref).isEmpty())return Optional.empty();
        return Optional.of(new WeaponTarget(ref,store.revision(ref)));
    }
    public static Result cleanWeapon(MinecraftServer server,WeaponRequest request){return weapon(server,request,false);}
    public static Result previewWeapon(MinecraftServer server,WeaponRequest request){return weapon(server,request,true);}
    private static Result weapon(MinecraftServer s,WeaponRequest r,boolean preview){
        if(r==null)return Result.reject("无效武器请求");
        var failure=validate(s,r.actionId,r.strength);if(failure!=null)return failure;
        if(!BloodTransferService.enabled()||r.player==null||r.hand==null||r.expectedResidueId==null||r.expectedRevision<0)
            return Result.reject("无效武器目标");
        String key="weapon/"+r.player+"/"+r.hand+"/"+r.expectedResidueId+"/"+r.expectedRevision+"/"+r.strength;
        // A successful commit changed revision: replay must find its receipt before checking it.
        failure=receipt(s,r.actionId,key,preview);if(failure!=null)return failure;
        var target=currentWeaponTarget(s,r.player,r.hand);
        if(target.isEmpty()||!target.get().residueId.equals(r.expectedResidueId)||target.get().revision!=r.expectedRevision)
            return Result.reject("手持武器或残留版本已变化");
        if(!budget(s,1,preview))return Result.reject("本 tick 清洗预算不足");
        var store=BloodTransferStore.get(s);long now=BloodTraceService.now(s);
        boolean has=r.strength>0&&(store.visible(r.expectedResidueId)>0||store.available(r.expectedResidueId,now)>0);
        var result=store.clean(r.expectedResidueId,r.strength,now,!preview);
        return finish(s,r.actionId,key,preview,has?1:0,result.visible(),result.transferable(),0);
    }
    public static Result cleanScene(ServerWorld world,SceneRequest request){return scene(world,request,false);}
    public static Result previewScene(ServerWorld world,SceneRequest request){return scene(world,request,true);}
    private static Result scene(ServerWorld w,SceneRequest r,boolean preview){
        return scene(w,r,preview,null,false);
    }
    public static Result washBlockRing(ServerWorld w,BlockPos center,boolean bottle){
        return scene(w,new SceneRequest(UUID.randomUUID(),center.toCenterPos(),3,1,Method.WASH),false,center,bottle);
    }
    private static Result scene(ServerWorld w,SceneRequest r,boolean preview,BlockPos ring,boolean bottle){
        var s=w.getServer();var failure=validate(s,r.actionId,r.strength);if(failure!=null)return failure;
        if(r.center==null||!Double.isFinite(r.center.x)||!Double.isFinite(r.center.y)||!Double.isFinite(r.center.z)||!Double.isFinite(r.radius)||r.radius<.1||r.radius>ForensicConfig.current.cleaning.maxRadius||r.method==null||Math.abs(r.center.x)>30000000||Math.abs(r.center.z)>30000000)return Result.reject("无效中心、半径或方法");
        String key="scene/"+w.getRegistryKey().getValue()+"/"+r.center+"/"+r.radius+"/"+r.strength+"/"+r.method;
        failure=receipt(s,r.actionId,key,preview);if(failure!=null)return failure;
        var traces=BloodTraceStore.get(w);var updates=new ArrayList<BloodTraceStore.Entry>();
        var fractions=new HashMap<String,double[]>();
        var removed=new HashMap<String,Double>();int skipped=0;
        int x0=MathHelper.floor(r.center.x-r.radius)>>4,x1=MathHelper.floor(r.center.x+r.radius)>>4;
        int z0=MathHelper.floor(r.center.z-r.radius)>>4,z1=MathHelper.floor(r.center.z+r.radius)>>4;
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
            if(!w.isChunkLoaded(x,z)){skipped++;continue;}
            for(var e:traces.chunk(ChunkPos.toLong(x,z))){
                var p=e.patch();if(!w.getBlockState(p.pos()).toString().equals(e.support()))continue;
                if(p.pos().getSquaredDistance(r.center)>Math.pow(r.radius+2,2))continue;
                if(ring!=null&&(Math.abs(p.pos().getX()-ring.getX())>1||Math.abs(p.pos().getY()-ring.getY())>1||Math.abs(p.pos().getZ()-ring.getZ())>1))continue;
                double strength=bottle&&!traces.bottleWashed(p.id())?.5:r.strength;
                var treatment=planPatch(p,r.center,r.radius,strength,traces.cleaningRemainders(p.id()));
                if(!treatment.changed())continue;
                var next=treatment.patch();fractions.put(p.id(),treatment.remainders());
                updates.add(new BloodTraceStore.Entry(next,e.source(),e.event(),e.support(),e.directional()));
                removed.merge(e.event(),p.mass()-next.mass(),Double::sum);
                if(updates.size()>ForensicConfig.current.cleaning.maxPatches)return Result.reject("范围超过处理上限，请缩小半径");
            }
        }
        if(!budget(s,updates.size(),preview))return Result.reject("本 tick 清洗预算不足");
        // Shared event denominator includes untouched and unloaded patches, preventing local over-consumption.
        var totals=new HashMap<String,Double>();
        for(var e:traces.entries())if(removed.containsKey(e.event()))totals.merge(e.event(),e.patch().mass(),Double::sum);
        int wet=0,visible=0;var transfers=BloodTransferStore.get(s);
        for(var event:removed.entrySet()){
            double ratio=Math.min(1,event.getValue()/Math.max(1e-9,totals.getOrDefault(event.getKey(),0.0)));
            var c=transfers.clean(event.getKey(),ratio,BloodTraceService.now(s),!preview);wet+=c.transferable();visible+=c.visible();
            // A fully washed ground event must also retire residue already copied to soles.
            // Partial washing retains the remaining source and its existing transfers.
            if(!preview&&ratio>=1-1e-9)transfers.discard(event.getKey());
        }
        if(!preview)for(var e:updates){traces.submit(e);traces.cleaningRemainders(e.patch().id(),fractions.get(e.patch().id()));if(bottle)traces.markBottleWashed(e.patch().id());}
        return finish(s,r.actionId,key,preview,updates.size(),visible,wet,skipped);
    }
    /** Face-cell clipping. Does not change geometry or infer a scrub direction. */
    public static BloodPatch cleanPatch(BloodPatch p,Vec3d center,double radius,double strength){return planPatch(p,center,radius,strength,new double[256]).patch();}
    public record Treatment(BloodPatch patch,double[] remainders,boolean changed){}
    public static Treatment planPatch(BloodPatch p,Vec3d center,double radius,double strength,double[] previous){
        if(previous.length!=256)throw new IllegalArgumentException("Invalid fractional grid");
        double[] next=previous.clone();
        if(!Double.isFinite(strength)||strength<0||strength>1||!Double.isFinite(radius)||radius<0)throw new IllegalArgumentException("Invalid cleaning parameters");
        char[] grid=p.treatment().isEmpty()?new char[256]:p.treatment().toCharArray();if(p.treatment().isEmpty())Arrays.fill(grid,(char)255);
        var origin=com.kuilunfuzhe.monvhua.features.paint.PaintSurface.point(p.pos(),p.face(),0,0,1,0);
        boolean changed=false, maskChanged=false;
        for(int y=0;y<16;y++)for(int x=0;x<16;x++){
            double u=(x+.5)/16,v=(y+.5)/16;
            if(!cellCovered(p,x,y)||origin.add(p.right().multiply(u)).add(p.down().multiply(v)).squaredDistanceTo(center)>radius*radius)continue;
            int i=y*16+x,value=grid[i];if(value==0||strength==0)continue;
            double requested=value*strength+next[i];int amount=Math.min(value,(int)Math.floor(requested+1e-9));
            next[i]=amount==value?0:Math.max(0,requested-amount);changed=true;
            if(amount>0){grid[i]=(char)(value-amount);maskChanged=true;}
        }
        return new Treatment(maskChanged?p.treated(new String(grid),p.revision()):p,next,changed);
    }
    private static boolean cellCovered(BloodPatch p,int x,int y){for(int dy=0;dy<4;dy++)for(int dx=0;dx<4;dx++)if(p.covers((x*4+dx+.5)/64,(y*4+dy+.5)/64))return true;return false;}
}
