package com.kuilunfuzhe.monvhua.features.forensics.cleaning;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import java.util.*;
public final class CleaningCommands {
    private CleaningCommands(){}
    public static void initialize(){WaterCleaning.initialize();CommandRegistrationCallback.EVENT.register((d,a,e)->{
        var strength=CommandManager.argument("strength",DoubleArgumentType.doubleArg(0,1));
        for(var method:ForensicCleaningApi.Method.values()){
            var node=CommandManager.literal(method.name().toLowerCase(java.util.Locale.ROOT));
            for(boolean preview:new boolean[]{true,false})node.then(CommandManager.literal(preview?"preview":"apply").executes(c->{
                var request=new ForensicCleaningApi.SceneRequest(UUID.randomUUID(),Vec3ArgumentType.getVec3(c,"center"),DoubleArgumentType.getDouble(c,"radius"),DoubleArgumentType.getDouble(c,"strength"),method);
                var result=preview?ForensicCleaningApi.previewScene(c.getSource().getWorld(),request):ForensicCleaningApi.cleanScene(c.getSource().getWorld(),request);
                var text=Text.literal(result.message()+"；变化补丁 "+result.changed()+"；跳过未加载区块 "+result.skipped());
                if(result.status()==ForensicCleaningApi.Status.REJECTED)c.getSource().sendError(text);else c.getSource().sendFeedback(()->text,false);
                return result.changed();
            }));strength.then(node);
        }
        d.register(CommandManager.literal("mvforensics").requires(s->s.hasPermissionLevel(2)).then(CommandManager.literal("clean").then(CommandManager.literal("scene")
            .then(CommandManager.argument("center",Vec3ArgumentType.vec3(false)).then(CommandManager.argument("radius",DoubleArgumentType.doubleArg(.1,16)).then(strength))))));
        var clean=CommandManager.literal("clean");
        for(boolean weapon:new boolean[]{true,false}){
            var mode=CommandManager.argument(weapon?"hand":"part",StringArgumentType.word())
                .suggests((c,b)->CommandSource.suggestMatching(weapon?List.of("mainhand","offhand"):List.of("all","hands","torso","left_sole","right_sole"),b));
            var amount=CommandManager.argument("strength",DoubleArgumentType.doubleArg(0,1));
            for(boolean preview:new boolean[]{true,false})amount.then(CommandManager.literal(preview?"preview":"apply")
                .executes(c->cleanTargets(c,weapon,preview)));
            mode.then(amount);
            clean.then(CommandManager.literal(weapon?"weapon":"body").then(CommandManager.argument("targets",EntityArgumentType.entities()).then(mode)));
        }
        d.register(CommandManager.literal("mvforensics").requires(s->s.hasPermissionLevel(2)).then(clean));
    });}
    private static int cleanTargets(CommandContext<ServerCommandSource> c,boolean weapon,boolean preview)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var source=c.getSource();var server=source.getServer();
        double strength=DoubleArgumentType.getDouble(c,"strength");
        String mode=StringArgumentType.getString(c,weapon?"hand":"part");
        Hand hand=mode.equals("mainhand")?Hand.MAIN_HAND:mode.equals("offhand")?Hand.OFF_HAND:null;
        Set<ForensicCleaningApi.Part> parts=EnumSet.noneOf(ForensicCleaningApi.Part.class);
        if(!weapon)for(var part:ForensicCleaningApi.Part.values())if(mode.equals("all")||mode.equals(part.slot))parts.add(part);
        if(weapon?hand==null:parts.isEmpty()){source.sendError(Text.literal("无效手别或部位，请使用补全选项"));return 0;}
        int changed=0;
        for(var target:EntityArgumentType.getEntities(c,"targets")){
            ForensicCleaningApi.Result result;
            if(weapon){
                if(!(target instanceof ServerPlayerEntity p)){source.sendError(Text.literal("武器目标必须是在线玩家"));continue;}
                var captured=ForensicCleaningApi.currentWeaponTarget(server,p.getUuid(),hand);
                if(captured.isEmpty()){source.sendError(Text.literal(p.getName().getString()+"：手持物品无效或残留引用无效"));continue;}
                var request=new ForensicCleaningApi.WeaponRequest(UUID.randomUUID(),p.getUuid(),hand,captured.get().residueId(),captured.get().revision(),strength);
                result=preview?ForensicCleaningApi.previewWeapon(server,request):ForensicCleaningApi.cleanWeapon(server,request);
            }else{
                UUID player=target instanceof ServerPlayerEntity?target.getUuid():null;
                UUID corpse=target instanceof ItemDisplayEntity?target.getUuid():null;
                var life=player!=null?ForensicCleaningApi.currentPlayerLifeId(server,player)
                    :corpse!=null?ForensicCleaningApi.currentCorpseLifeId(server,corpse):Optional.<String>empty();
                if(life.isEmpty()){source.sendError(Text.literal("身体目标必须是在线存活玩家或已加载遗体显示实体"));continue;}
                var request=new ForensicCleaningApi.BodyRequest(UUID.randomUUID(),player,corpse,life.get(),parts,strength);
                result=preview?ForensicCleaningApi.previewBody(server,request):ForensicCleaningApi.cleanBody(server,request);
            }
            Text text=Text.literal(target.getName().getString()+"："+result.message()+"；可见残留移除 "+result.visibleRemoved()+"；可转移量移除 "+result.transferableRemoved());
            if(result.status()==ForensicCleaningApi.Status.REJECTED)source.sendError(text);else source.sendFeedback(()->text,false);
            changed+=result.changed();
        }
        return changed;
    }
}
