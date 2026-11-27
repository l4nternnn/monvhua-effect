package com.kuilunfuzhe.monvhua.features.forensics.cards;

import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.*;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.util.*;

/** Opt-in real GPU checks in a disposable world, using public frozen record fixtures. */
public final class ClueCardUiGameChecks {
    private ClueCardUiGameChecks(){}
    public static void run(ClientGameTestContext context){
        try(var world=context.worldBuilder().create()){
            context.waitTicks(20);
            for(String kind:List.of("raster","scene","body","item")){
                ClueCardPersonalScreen[] parent={null};
                context.runOnClient(client->{
                    var detail=detail(kind);
                    var fields=new ClueCardStore.Fields("一份需要完整阅读的记录标题","第22天18:02","走廊尽头靠近楼梯的房间",
                            "我看见现场有血迹，需要结合其它线索判断。".repeat(12),"我尚不能确定这份证据与凶手的关联。".repeat(12));
                    var card=new ClueCardStore.Summary(kind,true,false,true,1,fields,"",true,"","","","scene",false,false,"我确认发生时间：第22天18:02");
                    ClueCardClient.state=new ClueCardPackets.State("ui-fixture",true,List.of(card),"","",kind,detail,"",false,false,"",true,List.of());
                    ClueCardClient.DETAILS.put(kind,detail);parent[0]=new ClueCardPersonalScreen(kind);client.setScreen(parent[0]);
                });
                context.waitTicks(6);context.takeScreenshot("clue-bag-"+kind);
                context.runOnClient(client->{
                    var p=parent[0];require(value(p,"thumbnail")!=null,"Snapshot capture failed: "+kind);
                    int x=number(p,"fx")+number(p,"lw")+16,y=number(p,"fy")+90;
                    require(p.mouseScrolled(x,y,0,-100),"Right detail scroll not handled");
                    require(((Number)value(p,"detailScroll")).doubleValue()>0,"Long text cannot scroll");
                });
                context.waitTicks(2);context.takeScreenshot("clue-bag-scrolled-"+kind);
                context.runOnClient(client->{
                    var p=parent[0];int x=number(p,"fx")+number(p,"lw")+16,y=number(p,"fy")+90;
                    p.mouseScrolled(x,y,0,100);p.mouseScrolled(x,y,0,-.25);
                });
                context.waitTicks(2);
                context.runOnClient(client->{
                    var p=parent[0];int tx=number(p,"thumbX")+4,ty=number(p,"thumbY")+number(p,"thumbH")/2;
                    require(p.mouseClicked(tx,ty,0),"Thumbnail click ignored");require(client.currentScreen instanceof ClueCardImageScreen,"Missing image viewer");
                });
                context.waitTicks(6);context.takeScreenshot("clue-image-"+kind);
                context.runOnClient(client->{
                    var viewer=(ClueCardImageScreen)client.currentScreen;
                    require(value(viewer,"image")!=null,"Viewer GPU image failed: "+kind);
                    var transform=(ClueImageTransform)value(viewer,"transform");
                    double mx=viewer.width/2.0,my=viewer.height/2.0;
                    viewer.mouseScrolled(mx,my,0,3);require(transform.zoom()>1,"Wheel did not zoom");
                    double oldX=transform.x();viewer.mouseClicked(mx,my,0);viewer.mouseDragged(mx+20,my+10,0,20,10);viewer.mouseReleased(mx+20,my+10,0);
                    require(transform.x()>oldX,"Left drag did not pan");
                });
                context.waitTicks(2);context.takeScreenshot("clue-image-zoom-"+kind);
                context.runOnClient(client->{
                    client.currentScreen.mouseClicked(0,0,1);require(client.currentScreen==parent[0],"Right click did not restore parent");
                    require(((Number)value(parent[0],"detailScroll")).doubleValue()>0,"Returning lost detail scroll");
                    parent[0].mouseClicked(number(parent[0],"thumbX")+4,number(parent[0],"thumbY")+number(parent[0],"thumbH")/2,0);
                });
                context.waitTicks(3);
                context.runOnClient(client->{
                    require(client.currentScreen instanceof ClueCardImageScreen,"Viewer reopen failed");
                    client.currentScreen.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0);require(client.currentScreen==parent[0],"Escape did not restore parent");
                    require(value(parent[0],"selected").equals(kind),"Selected card changed");client.setScreen(null);
                });
            }
            context.runOnClient(client->{ClueCardClient.state=null;ClueCardClient.DETAILS.clear();});
        }
        try{Files.writeString(Path.of("clue-card-ui-pass.txt"),"PASS: frozen raster/scene/body/item GPU capture; readable scrollable details; thumbnail entry; cursor zoom/drag; Esc/right-click restore parent.\n");}
        catch(java.io.IOException error){throw new AssertionError(error);}
    }
    private static ClueCardPackets.Detail detail(String kind){
        int[] pixels=new int[3072];
        if(kind.equals("raster"))for(int y=0;y<48;y++)for(int x=0;x<64;x++)pixels[y*64+x]=0xff000000|((x*4)<<16)|((y*5)<<8)|0x70;
        String body="",scene="",item="";
        if(kind.equals("body")){var n=new NbtCompound();n.put("observation",new NbtCompound());n.put("wounds",new NbtList());body=json(n);}
        if(kind.equals("scene")){
            var n=new NbtCompound();n.putDouble("eye_x",3);n.putDouble("eye_y",2);n.putDouble("eye_z",3);n.putDouble("tangent",1);
            var blocks=new NbtList();var b=new NbtCompound();b.put("state",net.minecraft.block.BlockState.CODEC.encodeStart(NbtOps.INSTANCE,Blocks.BRICKS.getDefaultState()).getOrThrow());blocks.add(b);n.put("blocks",blocks);scene=json(n);
        }
        if(kind.equals("item"))item=ItemStack.CODEC.encodeStart(net.minecraft.client.MinecraftClient.getInstance().world.getRegistryManager().getOps(JsonOps.INSTANCE),new ItemStack(Items.DIAMOND_SWORD)).getOrThrow().toString();
        return new ClueCardPackets.Detail(kind,64,48,pixels,body,scene,item);
    }
    private static String json(NbtCompound n){return NbtCompound.CODEC.encodeStart(JsonOps.INSTANCE,n).getOrThrow().toString();}
    private static Object value(Object object,String field){
        for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try{var f=type.getDeclaredField(field);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException ignored){}catch(IllegalAccessException error){throw new AssertionError(error);}
        throw new AssertionError("Missing field "+field);
    }
    private static int number(Object object,String field){return ((Number)value(object,field)).intValue();}
    private static void require(boolean valid,String message){if(!valid)throw new AssertionError(message);}
}
