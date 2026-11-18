package com.kuilunfuzhe.monvhua.features.forensics;

import com.kuilunfuzhe.monvhua.network.forensics.ForensicSharePackets;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import java.lang.reflect.*;
import java.util.*;
import static net.minecraft.server.command.CommandManager.*;
import static net.minecraft.command.argument.EntityArgumentType.*;
import static com.mojang.brigadier.arguments.StringArgumentType.*;

/** Optional Showcase bridge. It is reflection based so the forensic core works without Showcase. */
public final class ForensicShowcaseCompat {
    private record Payload(ServerPlayerEntity owner,String corpse,String part,String evidence){ }
    private static final Map<String,Payload> SHARES=new HashMap<>();
    private static boolean ready;
    private static final org.slf4j.Logger LOGGER=org.slf4j.LoggerFactory.getLogger("monvhua/ShowcaseCompat");
    private static String failureReason="接口尚未就绪";
    private ForensicShowcaseCompat(){}
    public static void initialize(){
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->dispatcher.register(literal("monvhua-forensic-share")
            .then(argument("corpse",word()).suggests((ctx,builder)->{
                var owner=ctx.getSource().getPlayer();
                if(owner==null)return builder.buildFuture();
                var ids=ForensicStore.get(ctx.getSource().getServer()).collectedCaseIds(owner.getUuid());
                return CommandSource.suggestMatching(ids.stream().sorted().toList(),builder);
            }).then(argument("part",word()).suggests((ctx,builder)->suggestParts(ctx,builder)).then(argument("evidence",word()).suggests((ctx,builder)->suggestEvidence(ctx,builder)).then(argument("target",player()).executes(ctx->{
                var owner=ctx.getSource().getPlayer();var target=getPlayer(ctx,"target");
                return create(owner,getString(ctx,"corpse"),getString(ctx,"part"),getString(ctx,"evidence"),target)?1:0;
            })))))));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server->tryRegisterCallback());
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server->{SHARES.clear();});
    }
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestParts(
            com.mojang.brigadier.context.CommandContext<net.minecraft.server.command.ServerCommandSource> ctx,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        ServerPlayerEntity owner;
        try { owner=ctx.getSource().getPlayer(); } catch (Exception e) { return builder.buildFuture(); }
        if(owner==null)return builder.buildFuture();
        String corpse;
        try { corpse=getString(ctx,"corpse"); } catch (IllegalArgumentException e) { return builder.buildFuture(); }
        // Read all parts from the stored case instead of suggesting the internal "unknown" bucket.
        var store=ForensicStore.get(ctx.getSource().getServer());
        var parts=new java.util.LinkedHashSet<String>();
        for(String part:Forensics.PARTS) if(!part.equals("unknown") && !store.collected(owner.getUuid(),corpse,part).isEmpty()) parts.add(part);
        return CommandSource.suggestMatching(parts,builder);
    }
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestEvidence(
            com.mojang.brigadier.context.CommandContext<net.minecraft.server.command.ServerCommandSource> ctx,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        ServerPlayerEntity owner;
        try { owner=ctx.getSource().getPlayer(); } catch (Exception e) { return builder.buildFuture(); }
        if(owner==null)return builder.buildFuture();
        String corpse,part;
        try { corpse=getString(ctx,"corpse"); part=getString(ctx,"part"); }
        catch (IllegalArgumentException e) { return builder.buildFuture(); }
        var records=ForensicStore.get(ctx.getSource().getServer()).collected(owner.getUuid(),corpse,part);
        var ids=records.stream().map(h->h.getString("id","")).filter(id->!id.isEmpty()).distinct().sorted().toList();
        return CommandSource.suggestMatching(ids,builder);
    }
    private static boolean create(ServerPlayerEntity owner,String corpse,String part,String evidence,ServerPlayerEntity target){
        if(owner==null||target==null||corpse.isEmpty()||part.isEmpty()||evidence.isEmpty())return false;
        var records=ForensicStore.get(owner.getServer()).collected(owner.getUuid(),corpse,part);
        if(records.stream().noneMatch(h->evidence.equals(h.getString("id","")))){owner.sendMessage(Text.literal("尚未收录该记录，无法出示"),false);return false;}
        if(!tryRegisterCallback()){
            owner.sendMessage(Text.literal("Showcase 适配不可用："+failureReason+"；线索未分享，详情见服务端日志"),false);return false;
        }
        try{
            Class<?> manager=Class.forName("com.showcase.command.ShowcaseManager");
            ItemStack card=new ItemStack(Items.PAPER);card.set(DataComponentTypes.CUSTOM_NAME,Text.literal("线索："+part));
            Method method=manager.getMethod("createItemShare",ServerPlayerEntity.class,ItemStack.class,Integer.class,Collection.class);
            String id=(String)method.invoke(null,owner,card,300,List.of(target));SHARES.put(id,new Payload(owner,corpse,part,evidence));
            owner.sendMessage(Text.literal("记录已出示给 "+target.getDisplayName().getString()),false);return true;
        }catch(ReflectiveOperationException|LinkageError|RuntimeException e){
            report("创建分享",e);owner.sendMessage(Text.literal("Showcase 分享失败："+failureReason+"；详情见服务端日志"),false);return false;
        }
    }
    private static boolean tryRegisterCallback(){
        if(ready)return true;
        if(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("showcase")){
            failureReason="当前服务端未加载 Showcase（仅客户端安装不够）";return false;
        }
        try{
            Class<?> api=Class.forName("com.showcase.api.ShowcaseAPI");Class<?> callback=Class.forName("com.showcase.event.ShowcaseViewedCallback");
            Object instance=api.getMethod("getInstance").invoke(null);
            Object proxy=Proxy.newProxyInstance(callback.getClassLoader(),new Class[]{callback},(p,m,args)->{
                if(m.getDeclaringClass()==Object.class)return switch(m.getName()){
                    case "hashCode"->System.identityHashCode(p);case "equals"->p==args[0];case "toString"->"MonvhuaShowcaseViewedCallback";default->null;
                };
                if(m.getName().equals("onShowcaseViewed")&&args!=null&&args.length>=3){
                    var viewer=(ServerPlayerEntity)args[0];String id=(String)args[2];Payload payload=SHARES.get(id);
                    if(payload!=null){ForensicStore.get(viewer.getServer()).disclose(viewer.getUuid(),payload.corpse(),payload.part(),payload.evidence());ForensicSharePackets.send(viewer,payload.corpse(),payload.part(),payload.evidence());}
                    com.kuilunfuzhe.monvhua.features.forensics.cards.ClueCardService.onShowcaseViewed(viewer,id);
                }
                return ActionResult.PASS;
            });
            api.getMethod("onShowcaseViewed",callback).invoke(instance,proxy);ready=true;LOGGER.info("Showcase forensic sharing callback registered");return true;
        }catch(ReflectiveOperationException|LinkageError|RuntimeException e){ready=false;report("注册查看回调",e);return false;}
    }
    /** The card ledger freezes the version before a Showcase reader can open the link. */
    public static boolean shareClueCard(ServerPlayerEntity owner,String cardId,ServerPlayerEntity target){
        var ledger=com.kuilunfuzhe.monvhua.features.forensics.cards.ClueCardStore.get(owner.getServer());
        if(owner==target||!ledger.participant(target.getUuid())||!ledger.readable(owner.getUuid(),cardId)){owner.sendMessage(Text.literal("目标或记录权限无效"),false);return false;}
        if(!tryRegisterCallback()){owner.sendMessage(Text.literal("Showcase 适配不可用："+failureReason),false);return false;}
        try{
            Class<?> manager=Class.forName("com.showcase.command.ShowcaseManager");
            Method method=manager.getMethod("createItemShare",ServerPlayerEntity.class,ItemStack.class,Integer.class,Collection.class);
            ledger.lock(owner.getUuid(),cardId);
            ItemStack item=com.kuilunfuzhe.monvhua.features.forensics.cards.ClueCardItems.card(cardId);
            item.set(DataComponentTypes.CUSTOM_NAME,Text.literal("线索记录"));
            String shareId=(String)method.invoke(null,owner,item,300,List.of(target));
            if(shareId==null||shareId.isEmpty())throw new IllegalStateException("Showcase returned an empty share ID");
            ledger.rememberShare(shareId,target.getUuid(),cardId);
            com.kuilunfuzhe.monvhua.features.forensics.cards.ClueCardService.announceShare(owner,target,cardId,shareId);
            owner.sendMessage(Text.literal("记录已出示给 "+target.getName().getString()+"，该版本已锁定"),false);return true;
        }catch(ReflectiveOperationException|LinkageError|RuntimeException error){report("创建线索卡分享",error);owner.sendMessage(Text.literal("分享未完成；为防止已生成链接的内容被替换，该版本保持锁定"),false);return false;}
    }
    private static void report(String step,Throwable error){
        Throwable cause=error instanceof InvocationTargetException invocation&&invocation.getCause()!=null?invocation.getCause():error;
        failureReason=step+"失败（"+cause.getClass().getSimpleName()+"）";
        String version=net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("showcase")
            .map(mod->mod.getMetadata().getVersion().getFriendlyString()).orElse("not loaded");
        LOGGER.error("Showcase forensic bridge failure: step={}, showcase={}, runtime={}",step,version,
            net.fabricmc.loader.api.FabricLoader.getInstance().getMappingResolver().getCurrentRuntimeNamespace(),cause);
    }
}
