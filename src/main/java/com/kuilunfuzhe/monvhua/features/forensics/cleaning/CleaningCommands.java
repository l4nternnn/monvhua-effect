package com.kuilunfuzhe.monvhua.features.forensics.cleaning;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import java.util.UUID;
public final class CleaningCommands {
    private CleaningCommands(){}
    public static void initialize(){CommandRegistrationCallback.EVENT.register((d,a,e)->{
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
    });}
}
