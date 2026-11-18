package com.kuilunfuzhe.monvhua.features.forensics.cards;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.*;
import static net.minecraft.server.command.CommandManager.*;

/** Admin-only case metadata; never sends case evidence or private snapshots. */
public final class ClueCaseAdmin {
    public record Request(String op,String id,String players,int bags,int cards,int revision,String previous) implements CustomPayload {
        public Request(String op,String id,String players,int bags,int cards,int revision){this(op,id,players,bags,cards,revision,"");}
        public static final Id<Request> ID=new Id<>(Identifier.of("monvhua","clue_case_request"));
        public static final PacketCodec<RegistryByteBuf,Request> CODEC=PacketCodec.of((p,b)->b.writeString(ClueCardPackets.JSON.toJson(p),4096),b->ClueCardPackets.JSON.fromJson(b.readString(4096),Request.class));
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record State(List<ClueCardStore.CaseEntry> cases,int bags,int cards,int revision,String notice,boolean open) implements CustomPayload {
        public static final Id<State> ID=new Id<>(Identifier.of("monvhua","clue_case_state"));
        public static final PacketCodec<RegistryByteBuf,State> CODEC=PacketCodec.of((p,b)->b.writeString(ClueCardPackets.JSON.toJson(p),131072),b->ClueCardPackets.JSON.fromJson(b.readString(131072),State.class));
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    private static final Map<UUID,Integer> LAST=new HashMap<>();
    private ClueCaseAdmin(){}
    public static void initialize(){
        PayloadTypeRegistry.playC2S().register(Request.ID,Request.CODEC);PayloadTypeRegistry.playS2C().register(State.ID,State.CODEC);
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->LAST.remove(h.player.getUuid()));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(s->LAST.clear());
        ServerPlayNetworking.registerGlobalReceiver(Request.ID,(r,c)->c.server().execute(()->{
            var p=c.player();if(!p.hasPermissionLevel(2))return;int tick=c.server().getTicks();if(tick-LAST.getOrDefault(p.getUuid(),tick-5)<3){send(p,"操作过快，请稍后重试",false);return;}LAST.put(p.getUuid(),tick);
            var store=ClueCardStore.get(c.server());String notice="";
            try{
                if(r.op()==null||r.id()==null||r.players()==null)throw new IllegalArgumentException("无法识别此项操作");
                if(!r.op().equals("list")&&r.revision()!=store.adminRevision())throw new IllegalArgumentException("案件列表已更新，请核对后重新操作");
                switch(r.op()){
                    case "list" -> {}
                    case "add" -> {store.addCase(r.id().isBlank()?store.nextDefaultCaseId():r.id(),members(resolve(p.getCommandSource(),r.players())));notice="案件已添加";}
                    case "members" -> {store.addMembers(r.id(),members(resolve(p.getCommandSource(),r.players())));changed(p.getServer());notice="参与者已添加";}
                    case "rebind" -> {var target=resolve(p.getCommandSource(),r.players());if(target.size()!=1)throw new IllegalArgumentException("接续身份须指定一名玩家");var player=target.iterator().next();store.rebindMember(r.id(),r.previous()==null?"":r.previous(),player.getUuid(),player.getName().getString());changed(p.getServer());notice="参与者身份及线索已接续";}
                    case "open" -> {String id=r.id();if(id.isBlank()){id=store.nextDefaultCaseId();store.addCase(id,members(resolve(p.getCommandSource(),r.players().isBlank()?"@a":r.players())));}store.openCase(id);changed(p.getServer());notice="案件已开启："+id;}
                    case "close" -> {String id=r.id().isBlank()?store.session():r.id();store.closeCase(id);changed(p.getServer());notice="案件已关闭记录，可继续阅读与出示已有卡片";}
                    case "delete" -> {store.deleteCase(r.id());changed(p.getServer());notice="案件及其记录已删除";}
                    case "defaults" -> {store.defaults(r.bags(),r.cards());notice="默认发放数量已保存";}
                    case "give_bags" -> {give(p.getCommandSource(),resolve(p.getCommandSource(),r.players()),r.bags(),false);notice="卡袋已发放";}
                    case "give_cards" -> {give(p.getCommandSource(),resolve(p.getCommandSource(),r.players()),r.cards(),true);notice="空白卡已发放";}
                    default -> throw new IllegalArgumentException("无法执行此项管理操作");
                }
            }catch(IllegalArgumentException|CommandSyntaxException e){notice="操作失败："+e.getMessage();}
            send(p,notice,false);
        }));
    }
    public static void open(ServerPlayerEntity p){if(!p.hasPermissionLevel(2))throw new IllegalArgumentException("案件管理需要管理员权限");send(p,"",true);}
    private static void send(ServerPlayerEntity p,String notice,boolean open){if(!p.hasPermissionLevel(2)||!ServerPlayNetworking.canSend(p,State.ID))return;var s=ClueCardStore.get(p.getServer());ServerPlayNetworking.send(p,new State(s.cases(),s.defaultBags(),s.defaultCards(),s.adminRevision(),notice,open));}
    private static Collection<ServerPlayerEntity> resolve(ServerCommandSource source,String text) throws CommandSyntaxException {
        if(text.isBlank())throw new IllegalArgumentException("请填写玩家名或玩家选择器");var reader=new StringReader(text.strip());var selector=EntityArgumentType.players().parse(reader);if(reader.canRead())throw new IllegalArgumentException("请填写一个玩家名或选择器");return selector.getPlayers(source);
    }
    private static Map<UUID,String> members(Collection<ServerPlayerEntity> players){var out=new LinkedHashMap<UUID,String>();for(var p:players)out.put(p.getUuid(),p.getName().getString());return out;}
    private static void changed(net.minecraft.server.MinecraftServer server){ClueCardService.invalidateCaptures();for(var p:server.getPlayerManager().getPlayerList())ClueCardService.send(p,"","案件状态已更新",false,false);}
    private static com.mojang.brigadier.Command<ServerCommandSource> guard(com.mojang.brigadier.Command<ServerCommandSource> action){return c->{try{return action.run(c);}catch(IllegalArgumentException e){c.getSource().sendError(Text.literal(e.getMessage()));return 0;}};}
    public static LiteralArgumentBuilder<ServerCommandSource> commands(String root){
        var node=literal(root).requires(s->s.hasPermissionLevel(2));
        node.then(literal("manage").executes(guard(c->{open(c.getSource().getPlayerOrThrow());return 1;})));
        node.then(literal("add").then(argument("id",StringArgumentType.word()).then(argument("players",EntityArgumentType.players()).executes(guard(c->{ClueCardStore.get(c.getSource().getServer()).addCase(StringArgumentType.getString(c,"id"),members(EntityArgumentType.getPlayers(c,"players")));c.getSource().sendFeedback(()->Text.literal("案件已添加"),true);return 1;})))));
        node.then(literal("start").executes(guard(c->startDefault(c.getSource(),resolve(c.getSource(),"@a"))))
            .then(caseArg().executes(guard(c->{var s=ClueCardStore.get(c.getSource().getServer());s.openCase(StringArgumentType.getString(c,"id"));changed(c.getSource().getServer());c.getSource().sendFeedback(()->Text.literal("案件已开启，物资需另行发放"),true);return 1;}))
            .then(argument("players",EntityArgumentType.players()).executes(guard(c->{var s=ClueCardStore.get(c.getSource().getServer());String id=StringArgumentType.getString(c,"id");if(s.hasCase(id))throw new IllegalArgumentException("已有案件请使用 start <id> 开启，参与者保持原记录");s.start(id,members(EntityArgumentType.getPlayers(c,"players")));changed(c.getSource().getServer());c.getSource().sendFeedback(()->Text.literal("案件已建立并开启，物资需另行发放"),true);return 1;})))));
        // Keep the named-case branch first: a case ID is also valid player-name syntax.
        node.then(literal("start").then(argument("default_players",EntityArgumentType.players()).executes(guard(c->startDefault(c.getSource(),EntityArgumentType.getPlayers(c,"default_players"))))));
        for(String op:List.of("close","delete"))node.then(literal(op).then(caseArg().executes(guard(c->{var s=ClueCardStore.get(c.getSource().getServer());String id=StringArgumentType.getString(c,"id");if(op.equals("delete"))s.deleteCase(id);else s.closeCase(id);changed(c.getSource().getServer());c.getSource().sendFeedback(()->Text.literal(op.equals("delete")?"案件已删除":"案件已关闭记录"),true);return 1;}))));
        node.then(literal("close").executes(guard(c->{var s=ClueCardStore.get(c.getSource().getServer());if(s.session().isEmpty())throw new IllegalArgumentException("当前没有案件");s.closeCase(s.session());changed(c.getSource().getServer());c.getSource().sendFeedback(()->Text.literal("已关闭当前案件："+s.session()),true);return 1;})));
        node.then(literal("members").then(caseArg().then(argument("players",EntityArgumentType.players()).executes(guard(c->{ClueCardStore.get(c.getSource().getServer()).addMembers(StringArgumentType.getString(c,"id"),members(EntityArgumentType.getPlayers(c,"players")));changed(c.getSource().getServer());c.getSource().sendFeedback(()->Text.literal("参与者已添加"),true);return 1;})))));
        node.then(literal("rebind").then(caseArg().then(argument("previous",StringArgumentType.word()).suggests((c,b)->CommandSource.suggestMatching(ClueCardStore.get(c.getSource().getServer()).cases().stream().filter(e->e.id().equals(StringArgumentType.getString(c,"id"))).flatMap(e->e.players().stream()),b)).then(argument("player",EntityArgumentType.player()).executes(guard(c->{var player=EntityArgumentType.getPlayer(c,"player");ClueCardStore.get(c.getSource().getServer()).rebindMember(StringArgumentType.getString(c,"id"),StringArgumentType.getString(c,"previous"),player.getUuid(),player.getName().getString());changed(c.getSource().getServer());c.getSource().sendFeedback(()->Text.literal("参与者身份及线索已接续"),true);return 1;}))))));
        node.then(literal("close_recording").executes(guard(c->{ClueCardStore.get(c.getSource().getServer()).closeRecording();changed(c.getSource().getServer());return 1;})));
        node.then(literal("list").executes(guard(c->{var s=ClueCardStore.get(c.getSource().getServer());for(var entry:s.cases())c.getSource().sendFeedback(()->Text.literal(entry.id()+" · "+(entry.open()?"开启":entry.current()?"当前/关闭":"关闭")+" · "+entry.players().size()+"人 · "+entry.cards()+"张"),false);return s.cases().size();})));
        node.then(literal("defaults").then(argument("bags",IntegerArgumentType.integer(1,64)).then(argument("cards",IntegerArgumentType.integer(1,64)).executes(guard(c->{ClueCardStore.get(c.getSource().getServer()).defaults(IntegerArgumentType.getInteger(c,"bags"),IntegerArgumentType.getInteger(c,"cards"));c.getSource().sendFeedback(()->Text.literal("默认发放数量已保存"),true);return 1;})))));
        for(boolean cards:new boolean[]{false,true})node.then(literal(cards?"give_cards":"give_bags").then(argument("players",EntityArgumentType.players()).then(argument("count",IntegerArgumentType.integer(1,64)).executes(guard(c->give(c.getSource(),EntityArgumentType.getPlayers(c,"players"),IntegerArgumentType.getInteger(c,"count"),cards))))));
        node.then(literal("reload").executes(c->{ClueCardConfig.load();c.getSource().sendFeedback(()->Text.literal("案件记录预填设置已重新加载"),false);return 1;}));return node;
    }
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<ServerCommandSource,String> caseArg(){return argument("id",StringArgumentType.word()).suggests((c,b)->CommandSource.suggestMatching(ClueCardStore.get(c.getSource().getServer()).cases().stream().map(ClueCardStore.CaseEntry::id),b));}
    private static int startDefault(ServerCommandSource source,Collection<ServerPlayerEntity> players){
        var people=members(players);if(people.isEmpty()||people.size()>16)throw new IllegalArgumentException("请选择 1 至 16 名案件参与者");
        var store=ClueCardStore.get(source.getServer());String id=store.nextDefaultCaseId();store.start(id,people);changed(source.getServer());
        source.sendFeedback(()->Text.literal("案件已建立并开启："+id+"（"+people.size()+" 人）；物资需另行发放"),true);return 1;
    }
    private static int give(ServerCommandSource source,Collection<ServerPlayerEntity> players,int count,boolean cards){
        if(count<1||count>64||players.isEmpty())throw new IllegalArgumentException("请选择接收者，单人数量为 1 至 64");var s=ClueCardStore.get(source.getServer());s.requireDistribution(players.stream().map(ServerPlayerEntity::getUuid).toList());
        // Validate every inventory before issuing any item; no overflow drops or partial batch grants.
        for(var p:players){int space=0;var incoming=cards?ClueCardItems.blanks(s.accessToken(),1):new ItemStack(ClueCardItems.BAG);for(int i=0;i<36;i++){var stack=p.getInventory().getStack(i);if(stack.isEmpty())space+=incoming.getMaxCount();else if(ItemStack.areItemsAndComponentsEqual(stack,incoming))space+=Math.max(0,stack.getMaxCount()-stack.getCount());}if(space<count)throw new IllegalArgumentException(p.getName().getString()+" 的背包空间不足，未发放任何物品");}
        for(var p:players){if(cards)p.getInventory().insertStack(ClueCardItems.blanks(s.accessToken(),count));else for(int i=0;i<count;i++)p.getInventory().insertStack(new ItemStack(ClueCardItems.BAG));p.getInventory().markDirty();p.sendMessage(Text.literal("已收到案件物资："+(cards?"空白卡":"卡袋")+" × "+count),false);}
        source.sendFeedback(()->Text.literal("已向 "+players.size()+" 名玩家各发放 "+count+(cards?" 张空白卡":" 个线索卡袋")),true);return players.size();
    }
}
