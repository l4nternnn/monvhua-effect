package com.kuilunfuzhe.monvhua.features.forensics;

import com.kuilunfuzhe.monvhua.features.block.body.BodyPartManager;
import com.kuilunfuzhe.monvhua.features.physics_preview.PhysicsPoseDocument;
import com.kuilunfuzhe.monvhua.mixin.forensics.CorpseDisplayAccessor;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.entity.decoration.InteractionEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import static net.minecraft.server.command.CommandManager.*;

/** Deliberately administrator-only, local files only, opt-in development operation. */
public final class CorpsePoseCommands {
    private CorpsePoseCommands() {}
    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->dispatcher.register(commands()));
    }
    static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> commands(){
            // This root also hosts ordinary players' private evidence commands. Gate each admin branch.
            var root=literal("mvcorpse");
            root.then(literal("create-test").requires(s->s.hasPermissionLevel(2)).executes(c->createTest(c.getSource())));
            root.then(literal("samples").requires(s->s.hasPermissionLevel(2)).executes(c->samples(c.getSource())));
            for(String action:new String[]{"status","verify","clear"})root.then(literal(action).requires(s->s.hasPermissionLevel(2)).then(argument("target",EntityArgumentType.entity()).executes(c->
                    execute(c.getSource(),EntityArgumentType.getEntity(c,"target"),action,""))));
            root.then(literal("apply").requires(s->s.hasPermissionLevel(2)).then(argument("target",EntityArgumentType.entity()).then(argument("filename",StringArgumentType.string()).executes(c->
                    execute(c.getSource(),EntityArgumentType.getEntity(c,"target"),"apply",StringArgumentType.getString(c,"filename"))))));
        return root;
    }
    private static int samples(ServerCommandSource source) {
        try {
            if(!Boolean.getBoolean("monvhua.physicsCorpseDebug"))throw new IllegalArgumentException("文件导入开发开关关闭：runClient -PphysicsCorpseDebug=true；自动物理试验用 /mvcorpse physics test");
            Path dir=FabricLoader.getInstance().getConfigDir().resolve("simplephysics-corpses");Files.createDirectories(dir);
            for(String name:new String[]{"floor","slab","stairs"})try(var in=CorpsePoseCommands.class.getResourceAsStream("/assets/monvhua/physics_preview/fixtures/"+name+".json")) {
                if(in==null)throw new IllegalArgumentException("Missing sample "+name);
                try{Files.write(dir.resolve(name+".json"),in.readAllBytes(),StandardOpenOption.CREATE_NEW);}
                catch(FileAlreadyExistsException existing){/* Keep local edits. */}
            }
            source.sendFeedback(()->Text.literal("服务端样例目录："+dir.toAbsolutePath()+"（未覆盖已有文件）"),false);return 1;
        }catch(Exception e){source.sendError(Text.literal(e.getMessage()));return 0;}
    }
    private static int createTest(ServerCommandSource source) {
        if(!Boolean.getBoolean("monvhua.physicsCorpseDebug")){source.sendError(Text.literal("文件导入开发开关关闭：runClient -PphysicsCorpseDebug=true；自动物理试验请用 /mvcorpse physics test"));return 0;}
        return createPhysicsTest(source)==null?0:1;
    }
    public static ItemDisplayEntity createPhysicsTest(ServerCommandSource source) {
        try {
            var player=source.getPlayerOrThrow();var world=source.getWorld();
            var direction=player.getRotationVec(1);var pos=player.getPos().add(new net.minecraft.util.math.Vec3d(direction.x,0,direction.z).normalize().multiply(2));
            var hit=world.raycast(new net.minecraft.world.RaycastContext(pos.add(0,2,0),pos.add(0,-4,0),net.minecraft.world.RaycastContext.ShapeType.COLLIDER,net.minecraft.world.RaycastContext.FluidHandling.NONE,player));
            if(hit.getType()!=net.minecraft.util.hit.HitResult.Type.BLOCK||hit.getSide()!=net.minecraft.util.math.Direction.UP)throw new IllegalArgumentException("前方没有测试地面");
            var display=net.minecraft.entity.EntityType.ITEM_DISPLAY.create(world,net.minecraft.entity.SpawnReason.TRIGGERED);
            if(display==null)throw new IllegalStateException("Cannot create display");
            display.setPosition(hit.getPos().add(0,.01,0));display.addCommandTag("monvhua_physics_test");
            var stack=new net.minecraft.item.ItemStack(net.minecraft.item.Items.NETHERITE_SCRAP);
            stack.set(DataComponentTypes.ITEM_MODEL,net.minecraft.util.Identifier.of("monvhua","combined_body"));
            var n=new NbtCompound();n.putString("arm_model","slim");stack.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(n));display.setItemStack(stack);
            var interaction=new InteractionEntity(net.minecraft.entity.EntityType.INTERACTION,world);interaction.setPosition(display.getPos());
            if(!world.spawnEntity(display))throw new IllegalStateException("Display spawn rejected");
            if(!world.spawnEntity(interaction)){display.discard();throw new IllegalStateException("Interaction spawn rejected");}
            BodyPartManager.DISPLAY_TO_INTERACTION.put(display.getUuid(),interaction.getUuid());
            BodyPartManager.INTERACTION_TO_DISPLAY.put(interaction.getUuid(),display.getUuid());Forensics.link(display,interaction);
            var store=ForensicStore.get(source.getServer());String id=store.freeze(java.util.UUID.randomUUID(),"[物理调试] 合成测试尸体",world.getRegistryKey().getValue().toString(),world.getTime(),display.getX(),display.getY(),display.getZ(),"imported_debug_fixture");
            NbtCompound binding=new NbtCompound();binding.putString("id",id);binding.putString("part","all");Forensics.setBinding(stack,binding);display.setItemStack(stack);Forensics.claim(display);CorpseGeometrySync.update(display);
            source.sendFeedback(()->Text.literal("已创建 slim 测试载体 "+display.getUuid()+"（合成测试绑定，不是真实死亡记录）"),false);return display;
        }catch(Exception e){source.sendError(Text.literal(e.getMessage()));return null;}
    }
    private static int execute(ServerCommandSource source,net.minecraft.entity.Entity entity,String action,String file) {
        try {
            if(entity instanceof InteractionEntity i) {
                var id=BodyPartManager.INTERACTION_TO_DISPLAY.get(i.getUuid());entity=id==null?null:source.getWorld().getEntity(id);
            }
            if(!(entity instanceof ItemDisplayEntity display))throw new IllegalArgumentException("请选择单一尸体 display 或 interaction");
            if(action.equals("status")||action.equals("verify")) {
                var data=CorpsePoseRecord.data(display.getItemStack());var pose=CorpsePoseRecord.read(data);
                source.sendFeedback(()->Text.literal("corpse_pose="+pose.map(p->p.source()+" revision="+p.revision()+" "+p.armModel()+" scale="+p.scale()).orElse(data.contains(CorpsePoseRecord.KEY)?"invalid (legacy fallback)":"legacy")
                        +" | clear="+CorpsePoseGeometry.clear(source.getWorld(),display,data,display.getPos())
                        +" | support="+CorpsePoseGeometry.supported(source.getWorld(),display,data,display.getPos())),false);return 1;
            }
            if(!Boolean.getBoolean("monvhua.physicsCorpseDebug"))throw new IllegalArgumentException("开发开关关闭：添加 JVM 参数 -Dmonvhua.physicsCorpseDebug=true 后重启");
            validateTarget(display);
            var old=CorpsePoseRecord.data(display.getItemStack());NbtCompound next=old.copy();
            long revision=Math.addExact(old.getLong("corpse_pose_revision",0),1);
            if(action.equals("apply")) {
                validateNeutral(display,old);
                var document=read(file);
                var record=CorpsePoseRecord.imported(document,revision,old.getString("arm_model","slim"));
                next.putFloat("corpse_scale",record.scale());next.put(CorpsePoseRecord.KEY,record.encode());
                CorpsePoseService.validateGeometry(display,next,display.getPos());
            } else next.remove(CorpsePoseRecord.KEY);
            next.putLong("corpse_pose_revision",revision);
            var bounds=CorpseGeometrySync.bounds(CorpseGeometrySync.placement(display,next),next);
            if(bounds==null)throw new IllegalArgumentException("Invalid geometry");
            // All validation finishes before the tracked stack and interaction bounds are committed.
            CorpsePoseService.commitStatic(display,next);
            var stack=display.getItemStack();
            var store=ForensicStore.get(source.getServer());var corpse=store.corpse(Forensics.binding(stack).getString("id",""));
            if(action.equals("apply")&&!corpse.contains("initial_debug_pose"))corpse.put("initial_debug_pose",next.getCompoundOrEmpty(CorpsePoseRecord.KEY).copy());
            corpse.putLong("last_pose_revision",revision);corpse.putString("last_pose_operation",action);store.markDirty();
            source.sendFeedback(()->Text.literal("已"+(action.equals("apply")?"应用 imported_debug 静态姿态":"恢复旧姿态")+"，revision="+revision),true);return 1;
        }catch(Exception e){source.sendError(Text.literal("尸体姿态："+e.getMessage()));return 0;}
    }
    public static void validateTarget(ItemDisplayEntity display) {
        if(!display.getCommandTags().contains("monvhua_physics_test"))throw new IllegalArgumentException("仅允许 monvhua_physics_test 测试载体");
        if(!(display.getWorld() instanceof ServerWorld world)||!Forensics.hasBinding(display.getItemStack())
                ||!Forensics.binding(display.getItemStack()).getString("part","").equals("all")
                ||!Forensics.valid(world,display.getItemStack(),display.getUuid().toString()))throw new IllegalArgumentException("不是有效完整尸体绑定");
        if(!net.minecraft.util.Identifier.of("monvhua","combined_body").equals(display.getItemStack().get(DataComponentTypes.ITEM_MODEL)))throw new IllegalArgumentException("需要完整标准身体模型");
        var id=BodyPartManager.DISPLAY_TO_INTERACTION.get(display.getUuid());
        if(id==null||!(world.getEntity(id) instanceof InteractionEntity))throw new IllegalArgumentException("交互实体尚未加载");
        if(com.kuilunfuzhe.monvhua.features.forensics.physics.CorpsePhysicsCoordinator.pending(display))throw new IllegalArgumentException("姿态正在生成，请稍候");
        if(CorpseCarryService.isCarried(display))throw new IllegalArgumentException("搬运/恢复期间不可修改姿态");
    }
    private static void validateNeutral(ItemDisplayEntity display,NbtCompound n) {
        if(display.getItemDisplayContext()!=net.minecraft.item.ItemDisplayContext.NONE
                ||!CorpseDisplayAccessor.monvhua$transform(display.getDataTracker()).getMatrix().equals(new Matrix4f(),1e-5f)
                ||display.getPitch()!=0)throw new IllegalArgumentException("仅支持中性 display transform、pitch=0、item_display=none 的测试载体；旧载体未被改写");
        if(!com.kuilunfuzhe.monvhua.features.physics_preview.CorpseScale.supported(n.getFloat("corpse_scale",1))||n.getString("body_pose_mode","").equals("true_skeletal"))throw new IllegalArgumentException("仅支持 0.5–2.0 整体倍率，不支持 true_skeletal");
        for(String key:n.getKeys())if(key.startsWith("pose_")||key.startsWith("bend_")) {
            float expected=key.endsWith("_scale")?1:0;
            if(n.getFloat(key,Float.NaN)!=expected)throw new IllegalArgumentException("存在非中性旧姿态："+key);
        }
    }
    public static PhysicsPoseDocument read(String filename) throws java.io.IOException {
        if(!filename.matches("[A-Za-z0-9_-]+\\.json"))throw new IllegalArgumentException("仅允许目录内的 JSON 文件名");
        Path dir=FabricLoader.getInstance().getConfigDir().resolve("simplephysics-corpses");Files.createDirectories(dir);
        Path base=dir.toRealPath(),file=base.resolve(filename).toRealPath();
        if(!file.getParent().equals(base)||!Files.isRegularFile(file)||Files.size(file)>256*1024)throw new IllegalArgumentException("文件路径或大小不合法");
        try(var in=Files.newInputStream(file)){byte[] bytes=in.readNBytes(256*1024+1);if(bytes.length>256*1024)throw new IllegalArgumentException("文件过大");return PhysicsPoseDocument.parse(new String(bytes,StandardCharsets.UTF_8));}
    }
}
