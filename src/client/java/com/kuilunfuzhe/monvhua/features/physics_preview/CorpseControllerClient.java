package com.kuilunfuzhe.monvhua.features.physics_preview;

import com.kuilunfuzhe.monvhua.features.forensics.*;
import com.kuilunfuzhe.monvhua.features.forensics.physics.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import java.util.*;

public final class CorpseControllerClient {
    private static boolean attackLatched,requestPending;
    public static void tick(MinecraftClient client){if(!client.options.attackKey.isPressed())attackLatched=false;}
    public static boolean holding(){var c=MinecraftClient.getInstance();return c.player!=null&&c.player.getMainHandStack().isOf(CorpseHistoryController.History_Cortol_ITEM);}
    public static boolean attack(){
        if(!holding())return false;
        var target=pick();if(target==null)return false;
        if(!attackLatched){attackLatched=true;request(target,false);}return true;
    }
    public static boolean use(){
        var c=MinecraftClient.getInstance();if(!holding()||!c.player.isSneaking())return false;
        request(pick(),true);return true;
    }
    private static UUID pick(){
        var c=MinecraftClient.getInstance();if(c.world==null||c.player==null)return null;
        double nearest=Double.POSITIVE_INFINITY;UUID target=null;
        for(var e:CorpseHistoryController.entries()){
            if(!e.valid||e.view==null)continue;
            double distance=CorpseHistoryPicking.hit(c.player,e);
            if(distance>=0&&distance<nearest){nearest=distance;target=e.id;}
        }
        if(RemoteCorpsePhysics.remote()){
            var hit=RemoteCorpsePhysics.pick(c.player);if(hit!=null){nearest=hit.distance();target=hit.id();}
        }
        var from=c.player.getEyePos();var to=from.add(c.player.getRotationVec(1).multiply(c.player.getEntityInteractionRange()));
        var block=c.world.raycast(new RaycastContext(from,to,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,c.player));
        if(block.getType()!=HitResult.Type.MISS)to=block.getPos();
        for(var entity:c.world.getEntities())if(entity instanceof ItemDisplayEntity display){
            if(Forensics.binding(display.getItemStack()).getString("id","").isEmpty())continue;
            if(RemoteCorpsePhysics.remote()&&RemoteCorpsePhysics.views().stream().anyMatch(v->v.display().equals(display.getUuid())))continue;
            var existing=CorpseHistoryController.get(display.getUuid());if(existing!=null&&(existing.active||existing.frozen()))continue;
            var hit=CorpsePoseGeometry.pick(display,from,to);
            if(hit!=null){double distance=from.distanceTo(hit.position());if(distance<nearest){nearest=distance;target=display.getUuid();}}
        }
        return target;
    }
    private static void request(UUID id,boolean open){
        var c=MinecraftClient.getInstance();var server=c.getServer();
        if(c.player==null)return;
        if(server==null){RemoteCorpsePhysics.timelineAction(open?"open":"freeze",id,-1);return;}
        if(requestPending)return;requestPending=true;
        var playerId=c.player.getUuid();var world=c.world;
        server.execute(()->{
            CorpseHistoryController.Entry entry=null;String error=null;
            try {var player=server.getPlayerManager().getPlayer(playerId);if(player==null)throw new IllegalArgumentException("玩家已离线");entry=CorpseHistoryController.operate(player,id,open);}
            catch(RuntimeException e){error=e.getMessage();}
            var result=entry;var failure=error;
            c.execute(()->{
                requestPending=false;
                if(c.getServer()!=server||c.world!=world||c.player==null||c.currentScreen!=null){if(result!=null&&open)server.execute(()->CorpseHistoryController.closeTimeline(result.id,playerId));return;}
                if(failure!=null){message(failure);return;}
                if(open)c.setScreen(new CorpseTimelineScreen(result,server,playerId));
                else message(result.manual?"已请求冻结；界面内可查看历史":"已取消手动冻结；返回最新状态");
            });
        });
    }
    private static void message(String text){var p=MinecraftClient.getInstance().player;if(p!=null)p.sendMessage(Text.literal(text),false);}
}
