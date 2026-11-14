package com.kuilunfuzhe.monvhua.features.forensics.physics;

import com.kuilunfuzhe.monvhua.features.forensics.*;
import com.kuilunfuzhe.monvhua.features.forensics.blood.BloodTraceService;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import java.util.*;

/** Two server-validated downward strikes, with progress held only in the forensic store. */
public final class CorpseDismemberment {
    public static final TagKey<Item> WEAPONS=TagKey.of(RegistryKeys.ITEM,Identifier.of("monvhua","forensics/can_dismember"));
    private static final String KEY="joint_cuts";
    private CorpseDismemberment(){}
    public static boolean downwardStrike(boolean grounded,boolean restricted,double fallDistance,float cooldown){
        return !grounded&&!restricted&&Double.isFinite(fallDistance)&&fallDistance>0&&Float.isFinite(cooldown)&&cooldown>=.9f;
    }
    // Vanilla server players report descent through fallDistance; velocity.y can remain zero.
    public static boolean eligible(ServerPlayerEntity player,ItemStack weapon,float cooldown){
        return weapon.isIn(WEAPONS)&&downwardStrike(player.isOnGround(),player.isSpectator()||!player.isAlive()
            ||player.getAbilities().flying||player.isGliding()||player.isClimbing()||player.isTouchingWater()
            ||player.isInLava()||player.hasVehicle(),player.fallDistance,cooldown);
    }
    /** Test against the posed connection, including torso-side hits. Radii scale with the body. */
    public static String connection(List<CorpsePoseGeometry.Surface> surfaces,Vec3d origin,
                                    CorpsePoseGeometry.WoundHit hit,float scale,boolean slim){
        String struck=hit.wound().getString("part","");
        if(!surfaces.stream().anyMatch(s->s.part().equals("torso")))return null;
        String best=null;double distance=Double.POSITIVE_INFINITY;
        for(String part:List.of("head","left_arm","right_arm","left_leg","right_leg")){
            if(!struck.equals("torso")&&!struck.equals(part))continue;
            if(surfaces.stream().noneMatch(s->s.part().equals(part)))continue;
            String on=struck.equals("torso")?"torso":part;
            var surface=surfaces.stream().filter(s->s.part().equals(on)).findFirst().orElse(null);if(surface==null)continue;
            boolean left=part.startsWith("left"),arm=part.endsWith("arm");
            float x=part.equals("head")?0:arm?(left?4:-4):struck.equals("torso")?(left?2:-2):(left?1.9f:-1.9f);
            float y=part.endsWith("leg")?12:arm&&slim&&!struck.equals("torso")?.5f:0;
            var point=surface.matrix().transformPosition(new Vector3f(x/16,y/16,0));
            double d=origin.add(point.x,point.y,point.z).squaredDistanceTo(hit.position());
            double radius=(part.equals("head")?.30:arm?.22:.20)*scale;
            if(d<=radius*radius&&d<distance){distance=d;best=part;}
        }
        return best;
    }
    /** Idempotent bounded progress; an unsuccessful separation keeps the first cut for a retry. */
    public static int registerCut(NbtCompound corpse,String part,String attack){
        if(!List.of("head","left_arm","right_arm","left_leg","right_leg").contains(part)||attack==null||attack.isBlank())return 0;
        var cuts=corpse.getCompoundOrEmpty(KEY);var cut=cuts.getCompoundOrEmpty(part);
        if(cut.getString("last_attack","").equals(attack)||cut.getInt("count",0)>=2)return 0;
        int result=cut.getInt("count",0)>=1?2:1;
        cut.putInt("count",1);cut.putString("last_attack",attack);cuts.put(part,cut);corpse.put(KEY,cuts);return result;
    }
    public static void committed(NbtCompound corpse,String part){
        var cuts=corpse.getCompoundOrEmpty(KEY);var cut=cuts.getCompoundOrEmpty(part);cut.putInt("count",2);cuts.put(part,cut);corpse.put(KEY,cuts);
    }
    public static void strike(ServerPlayerEntity player,ItemDisplayEntity display,String part,Vec3d position,String attack){
        if(busy(display)||!player.getWeaponStack().isIn(WEAPONS))return;
        var store=ForensicStore.get(player.getServer());var corpse=store.corpse(Forensics.binding(display.getItemStack()).getString("id",""));
        int stage=registerCut(corpse,part,attack);if(stage==0)return;store.markDirty();
        BloodTraceService.jointCut(display,position,part,attack);
        if(stage==1){player.sendMessage(Text.literal("连接处已受损，再次跳劈可切断该部位"),true);return;}
        CorpseSeparationService.cut(display,part,(success,message)->{
            if(success){committed(corpse,part);store.markDirty();}
            var online=player.getServer().getPlayerManager().getPlayer(player.getUuid());
            if(online!=null)online.sendMessage(Text.literal(success?"该部位已断开":"分离未完成："+message+"；连接处损伤保留"),true);
        });
    }
    private static boolean busy(ItemDisplayEntity display){return CorpseSeparationService.busy(display.getUuid());}
}
