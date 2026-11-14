package com.kuilunfuzhe.monvhua.features.physics_preview;

import com.kuilunfuzhe.monvhua.renderer.body.special.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.render.*;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import org.joml.Matrix4f;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.Path;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

/** Independent local preview; never sends packets or mutates an entity, item or case. */
public final class PhysicsPreviewClient implements ClientModInitializer {
    public static PhysicsPreviewClient instance;
    private final ThreadPoolExecutor io=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),r->{Thread t=new Thread(r,"Monvhua pose import");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private Future<?> pending;
    private long generation;
    private ClientWorld owner;
    private PhysicsPoseDocument document;
    private CombinedBodySpecialModelRenderer renderer;
    private Object loadedModels;
    private Map<String,Matrix4f> matrices;
    private List<CombinedBodySpecialModelRenderer.EvidenceSurface> surfaces=List.of();
    private Vec3d anchor;
    private String mode="overlay",skin="slim",filename="";
    private boolean marks;
    private final NbtCompound fixtureMarks=PhysicsPreviewFixtures.marks();
    private static final Identifier DEBUG=Identifier.of("monvhua","textures/physics_preview/calibration.png");
    private static final int[] COLORS={0xFFD166,0x40E080,0xFF6688,0xFF9944,0x44CCFF,0xAA88FF};
    private Path previewDirectory;
    public Path directory(){return previewDirectory;}
    @Override public void onInitializeClient() {
        instance=this;
        previewDirectory=MinecraftClient.getInstance().runDirectory.toPath().resolve("simplephysics-preview");
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)-> {
            var preview=literal("preview")
                .then(literal("list").executes(c->list()))
                .then(literal("samples").executes(c->samples()))
                .then(literal("load").then(argument("filename",StringArgumentType.string()).executes(c->load(StringArgumentType.getString(c,"filename")))))
                .then(literal("clear").executes(c->{clear();say("预览已清除");return 1;}))
                .then(literal("status").executes(c->{say(document==null?"未加载姿态；目录："+directory():filename+" | "+document.outcome()+" | step="+document.step()+" | tilt="+document.tilt()+" | "+document.definition()+" | "+mode);return 1;}))
                .then(literal("inspect").executes(c->inspect()));
            var modes=literal("mode");for(String value:List.of("model","wire","overlay"))modes.then(literal(value).executes(c->{mode=value;say("显示模式："+value);return 1;}));
            var skins=literal("skin");for(String value:List.of("slim","debug","steve","self"))skins.then(literal(value).executes(c->{
                skin=value;
                if(document!=null){matrices=StandardBodyPhysicsAdapter.matrices(document,isSlim());surfaces=renderer.physicsPreviewSurfaces(matrices,data());}
                return 1;}));
            preview.then(modes).then(skins).then(literal("marks")
                .then(literal("on").executes(c->{marks=true;return 1;})).then(literal("off").executes(c->{marks=false;return 1;})));
            dispatcher.register(literal("mvphysics").then(preview));
        });
        ClientTickEvents.END_CLIENT_TICK.register(client->{if(owner!=null&&owner!=client.world)clear();});
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->clear());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client->{clear();io.shutdownNow();});
        WorldRenderEvents.AFTER_ENTITIES.register(this::render);
    }
    private void cancelPending(){generation++;if(pending!=null)pending.cancel(true);pending=null;io.purge();}
    public void clear(){cancelPending();owner=null;document=null;renderer=null;loadedModels=null;matrices=null;surfaces=List.of();anchor=null;}
    private int list() {
        cancelPending();long token=generation;var client=MinecraftClient.getInstance();owner=client.world;
        try{pending=io.submit(()->{try{var files=PhysicsPreviewFiles.list(directory());client.execute(()->{if(token==generation)say(files.isEmpty()?"目录暂无 JSON："+directory():String.join(", ",files));});}catch(Exception e){error(client,token,e);}});return 1;}
        catch(RejectedExecutionException e){say("文件任务繁忙，请重试");return 0;}
    }
    private int samples() {
        cancelPending();long token=generation;var client=MinecraftClient.getInstance();owner=client.world;
        try{pending=io.submit(()->{
            try {
                java.nio.file.Files.createDirectories(directory());
                for(String name:List.of("floor","slab","stairs","neutral-0","neutral-90","neutral-180","neutral-270")) {
                    try(var in=getClass().getResourceAsStream("/assets/monvhua/physics_preview/fixtures/"+name+".json")) {
                        if(in==null)throw new IllegalStateException("Missing built-in sample: "+name);
                        try {java.nio.file.Files.write(directory().resolve(name+".json"),in.readAllBytes(),java.nio.file.StandardOpenOption.CREATE_NEW);}
                        catch(java.nio.file.FileAlreadyExistsException existing) { /* Never overwrite user files. */ }
                    }
                }
                client.execute(()->{if(token==generation)say("样例已准备，可运行 /mvphysics preview load floor.json；已有文件不会覆盖");});
            }catch(Exception e){error(client,token,e);}
        });return 1;}catch(RejectedExecutionException e){say("文件任务繁忙，请重试");return 0;}
    }
    public int load(String file) {
        var client=MinecraftClient.getInstance();if(client.world==null||client.player==null)return 0;
        cancelPending();long token=generation;owner=client.world;var world=owner;
        var look=client.player.getRotationVec(1);var origin=client.player.getPos().add(new Vec3d(look.x,0,look.z).normalize().multiply(3));
        try{pending=io.submit(()->{
            try{var parsed=PhysicsPreviewFiles.read(directory(),file);client.execute(()->{
                if(token!=generation||client.world!=world)return;
                try {
                    var nextRenderer=new CombinedBodySpecialModelRenderer(client.getLoadedEntityModels());
                    var nextMatrices=StandardBodyPhysicsAdapter.matrices(parsed,isSlim());
                    var nextSurfaces=nextRenderer.physicsPreviewSurfaces(nextMatrices,data());
                    renderer=nextRenderer;loadedModels=client.getLoadedEntityModels();document=parsed;matrices=nextMatrices;surfaces=nextSurfaces;anchor=origin;filename=file;pending=null;
                    say("已加载 "+file+"："+parsed.outcome()+"；模型映射预览，不重新计算地形碰撞");
                    if(!parsed.outcome().equals("SETTLED"))say("此结果未稳定，仅用于诊断，不可作为合格尸体姿态");
                }catch(Exception e){say("模型准备失败："+e.getMessage());}
            });}catch(Exception e){error(client,token,e);}
        });return 1;}catch(RejectedExecutionException e){say("文件任务繁忙，请重试");return 0;}
    }
    private void error(MinecraftClient client,long token,Exception e){client.execute(()->{if(token==generation){pending=null;say("导入失败："+e.getMessage());}});}
    private boolean isSlim() {
        var p=MinecraftClient.getInstance().player;
        return skin.equals("slim") || (skin.equals("self")&&p!=null&&p.getSkinTextures().model()==SkinTextures.Model.SLIM);
    }
    private BodyPartSpecialModelRenderer.Data data() {
        var client=MinecraftClient.getInstance();
        Identifier texture=skin.equals("slim")?Identifier.ofVanilla("textures/entity/player/slim/alex.png"):
                skin.equals("steve")?Identifier.ofVanilla("textures/entity/player/wide/steve.png"):DEBUG;
        if(skin.equals("self")&&client.player!=null)texture=client.player.getSkinTextures().texture();
        return new BodyPartSpecialModelRenderer.Data(RenderLayer.getEntityCutoutNoCull(texture),texture,isSlim()?"slim":"default",marks?fixtureMarks:null);
    }
    private void render(WorldRenderContext context) {
        if(document==null||context.world()!=owner||context.matrixStack()==null||context.consumers()==null)return;
        var client=MinecraftClient.getInstance();
        if(loadedModels!=client.getLoadedEntityModels()){
            renderer=new CombinedBodySpecialModelRenderer(client.getLoadedEntityModels());loadedModels=client.getLoadedEntityModels();surfaces=renderer.physicsPreviewSurfaces(matrices,data());
        }
        if(context.camera().getPos().squaredDistanceTo(anchor)>64*64)return;
        var stack=context.matrixStack();var camera=context.camera().getPos();stack.push();
        try {
            stack.translate(anchor.x-camera.x,anchor.y-camera.y,anchor.z-camera.z);
            if(!mode.equals("wire"))renderer.renderPhysicsPreview(stack,context.consumers(),data(),matrices);
            if(!mode.equals("model")) {
                var vertices=context.consumers().getBuffer(RenderLayer.getLines());
                for(int i=0;i<PhysicsPoseDocument.IDS.size();i++) {
                    String id=PhysicsPoseDocument.IDS.get(i);var p=document.poses().get(id);if(p==null)continue;var size=StandardBodyPhysicsAdapter.size(id,isSlim()).mul(.5f*document.scale());int color=COLORS[i];
                    stack.push();try{stack.translate(p.x(),p.y(),p.z());stack.multiply(p.rotation());
                        VertexRendering.drawBox(stack,vertices,-size.x,-size.y,-size.z,size.x,size.y,size.z,((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f,1);
                        for(var pivot:jointPivots(id))VertexRendering.drawBox(stack,vertices,pivot.x-.02,pivot.y-.02,pivot.z-.02,pivot.x+.02,pivot.y+.02,pivot.z+.02,1,1,1,1);
                    }finally{stack.pop();}
                }
            }
        }finally{stack.pop();}
    }
    private List<org.joml.Vector3f> jointPivots(String id) {
        float shoulder=document.definition().equals("slim_six_v1")?1.5f/16:.125f;
        return switch(id) {
            case "head" -> List.of(new org.joml.Vector3f(0,-.25f,0));
            case "left_arm" -> List.of(new org.joml.Vector3f(-shoulder,.375f,0));
            case "right_arm" -> List.of(new org.joml.Vector3f(shoulder,.375f,0));
            case "left_leg","right_leg" -> List.of(new org.joml.Vector3f(0,.375f,0));
            case "torso" -> List.of(new org.joml.Vector3f(0,.375f,0),new org.joml.Vector3f(.25f,.375f,0),new org.joml.Vector3f(-.25f,.375f,0),new org.joml.Vector3f(.125f,-.375f,0),new org.joml.Vector3f(-.125f,-.375f,0));
            default -> List.of();
        };
    }
    public int inspect() {
        var client=MinecraftClient.getInstance();if(document==null||client.player==null)return 0;
        Vec3d from=client.player.getEyePos().subtract(anchor),to=from.add(client.player.getRotationVec(1).multiply(12));
        var hit=PhysicsPreviewPicker.pick(surfaces,from,to);
        if(hit==null)say("准星没有命中预览表面");
        else {
            String part=com.kuilunfuzhe.monvhua.features.forensics.WoundGeometry.SURFACES.stream().filter(v->v.name().equals(hit.surface())).findFirst().orElseThrow().part();
            say(String.format(Locale.ROOT,"%s (%s) / %s / u=%.4f v=%.4f / %.3f 格",hit.surface(),part,hit.face(),hit.u(),hit.v(),hit.distance()));
        }
        return 1;
    }
    public PhysicsPoseDocument diagnosticDocument(){return document;}
    public List<CombinedBodySpecialModelRenderer.EvidenceSurface> diagnosticSurfaces(){return surfaces;}
    private static void say(String text){var p=MinecraftClient.getInstance().player;if(p!=null)p.sendMessage(Text.literal("[物理预览] "+text),false);}
}
