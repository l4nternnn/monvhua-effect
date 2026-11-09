package com.kuilunfuzhe.monvhua.features.forensics.blood;
import com.kuilunfuzhe.monvhua.network.forensics.BodyStainPackets;
import com.kuilunfuzhe.monvhua.features.paint.PaintRenderLayers;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import java.util.*;
public final class BodyStainClient {
    private static final Map<String,BodyStainPackets.Sync> LEVELS=new HashMap<>();
    private static final Map<PlayerEntityRenderState,UUID> PLAYERS=new WeakHashMap<>();
    public static void capture(PlayerEntityRenderState state,UUID player){PLAYERS.put(state,player);}
    public static int level(String key,boolean torso){var p=LEVELS.get(key);return p==null?0:torso?p.torso():p.hands();}
    public static void initialize(){
        ClientPlayConnectionEvents.DISCONNECT.register((h,c)->{LEVELS.clear();PLAYERS.clear();});
        ClientPlayNetworking.registerGlobalReceiver(BodyStainPackets.Sync.ID,(p,c)->c.client().execute(()->{if(p.hands()==0&&p.torso()==0)LEVELS.remove(p.key());else if(LEVELS.size()<4096||LEVELS.containsKey(p.key()))LEVELS.put(p.key(),p);}));
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type,renderer,helper,context)->{if(renderer instanceof PlayerEntityRenderer player)helper.register(new Layer(player));});
    }
    private static final class Layer extends FeatureRenderer<PlayerEntityRenderState,PlayerEntityModel> {
        Layer(PlayerEntityRenderer r){super(r);}
        public void render(MatrixStack matrices,VertexConsumerProvider consumers,int light,PlayerEntityRenderState state,float yaw,float pitch){
            if(state.invisible)return;
            var id=PLAYERS.get(state);if(id==null)return;String key="p/"+id;
            var model=getContextModel();
            draw(model.body,matrices,consumers,light,level(key,true),-4,4,1,11);
            draw(model.leftArm,matrices,consumers,light,level(key,false),-1,2,5,10);
            draw(model.rightArm,matrices,consumers,light,level(key,false),-2,1,5,10);
        }
    }
    /** Vanilla local front is negative Z. Applies the animated model part transform. */
    private static void draw(ModelPart part,MatrixStack m,VertexConsumerProvider consumers,int light,int amount,int x0,int x1,int y0,int y1){
        if(amount<=0||!part.visible)return;m.push();part.applyTransform(m);
        var vertices=consumers.getBuffer(PaintRenderLayers.paintOverlay());
        for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++){
            if(Math.floorMod(x*31+y*17,11)>Math.min(9,amount+2))continue;
            float z=-2.55f/16, a=x/16f,b=y/16f,c=(x+1)/16f,d=(y+1)/16f;
            vertex(vertices,m,a,b,z,light,amount);vertex(vertices,m,a,d,z,light,amount);vertex(vertices,m,c,d,z,light,amount);vertex(vertices,m,c,b,z,light,amount);
        }m.pop();
    }
    private static void vertex(VertexConsumer v,MatrixStack m,float x,float y,float z,int light,int amount){v.vertex(m.peek().getPositionMatrix(),x,y,z).color(110,8,16,Math.min(235,80+amount*12)).texture(0,0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0,0,-1);}
}
