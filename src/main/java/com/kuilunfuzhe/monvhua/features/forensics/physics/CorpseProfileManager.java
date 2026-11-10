package com.kuilunfuzhe.monvhua.features.forensics.physics;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import java.nio.file.*;
import java.util.concurrent.*;
import static net.minecraft.server.command.CommandManager.*;

/** IO is bounded and off game/render/native threads. A registry is published only after full validation. */
public final class CorpseProfileManager {
    private record State(CorpsePhysicsProfiles.Registry registry,String selected){}
    private static volatile State state=new State(CorpsePhysicsProfiles.baseline(),CorpsePhysicsProfiles.F5.id());
    private static volatile MinecraftServer owner;
    private static long generation;
    private static boolean loading,loaded;
    private static volatile String last="内置 F5 配置";
    private static final ThreadPoolExecutor io=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),r->{var t=new Thread(r,"Corpse profile IO");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    public static CorpsePhysicsProfiles.Profile current(){var s=state;return s.registry.profiles().get(s.selected);}
    public static synchronized boolean loading(){return loading;}
    public static synchronized void reset(){generation++;owner=null;loading=false;loaded=false;state=new State(CorpsePhysicsProfiles.baseline(),CorpsePhysicsProfiles.F5.id());last="内置 F5 配置";}
    public static synchronized void start(MinecraftServer server){reset();owner=server;reload(server,null);}
    public static String status(){var p=current();return p.id()+" hash="+p.hash()+" | "+last;}
    private static synchronized void reload(MinecraftServer server,ServerCommandSource source){
        if(server!=owner){reply(source,"尚未连接本地物理服务",true);return;}
        if(loading){reply(source,"配置正在加载，请稍候",true);return;}
        loading=true;long token=generation;
        try{io.execute(()->{
            CorpsePhysicsProfiles.Registry parsed=null;String failure=null;
            try{
                var file=FabricLoader.getInstance().getConfigDir().resolve("monvhua/corpse-physics.json");Files.createDirectories(file.getParent());
                try{Files.writeString(file,CorpsePhysicsProfiles.example(),StandardOpenOption.CREATE_NEW);}catch(FileAlreadyExistsException ignored){}
                try(var stream=Files.newInputStream(file)){
                    byte[] bytes=stream.readNBytes(65537);if(bytes.length>65536)throw new IllegalArgumentException("配置超过 64 KiB");
                    var decoder=java.nio.charset.StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT);
                    parsed=CorpsePhysicsProfiles.parse(decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString());
                }
            }catch(Exception e){failure=e.getMessage();}
            var registry=parsed;var error=failure;
            server.execute(()->finish(server,token,registry,error,source));
        });}catch(RejectedExecutionException e){loading=false;reply(source,"配置 IO 队列繁忙",true);}
    }
    private static synchronized void finish(MinecraftServer server,long token,CorpsePhysicsProfiles.Registry registry,String error,ServerCommandSource source){
        if(server!=owner||token!=generation)return;loading=false;
        if(error==null){
            String selected=loaded?state.selected:registry.defaultId();
            if(!registry.profiles().containsKey(selected))error="重载删除了当前选中的配置，请先 use 其他配置";
            else{state=new State(registry,selected);loaded=true;last="配置加载成功；只影响新请求";reply(source,last,false);return;}
        }
        last="配置加载失败，保留上一有效配置："+error;
        org.slf4j.LoggerFactory.getLogger("monvhua-corpse-physics").warn("{}",last);reply(source,last,true);
    }
    private static void reply(ServerCommandSource s,String message,boolean error){if(s!=null){if(error)s.sendError(Text.literal(message));else s.sendFeedback(()->Text.literal(message),false);}}
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> commands(){
        var root=literal("profile").requires(s->s.hasPermissionLevel(2));
        root.then(literal("list").executes(c->{reply(c.getSource(),String.join(", ",new java.util.TreeSet<>(state.registry.profiles().keySet())),false);return 1;}));
        root.then(literal("show").executes(c->{reply(c.getSource(),status()+"\n"+current(),false);return 1;}));
        root.then(literal("reload").executes(c->{reload(c.getSource().getServer(),c.getSource());return 1;}));
        root.then(literal("use").then(argument("id",com.mojang.brigadier.arguments.StringArgumentType.word()).suggests((c,b)->net.minecraft.command.CommandSource.suggestMatching(state.registry.profiles().keySet(),b)).executes(c->{
            return use(c.getSource(),com.mojang.brigadier.arguments.StringArgumentType.getString(c,"id"));
        })));
        return root;
    }
    private static synchronized int use(ServerCommandSource source,String id){
        if(source.getServer()!=owner){reply(source,"尚未连接本地物理服务",true);return 0;}
        var s=state;if(!s.registry.profiles().containsKey(id)){reply(source,"未知配置："+id,true);return 0;}
        state=new State(s.registry,id);loaded=true;reply(source,"新请求使用 "+id+"；活动会话保持原配置",false);return 1;
    }
}
