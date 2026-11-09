package com.kuilunfuzhe.monvhua.features.forensics.physics;

import com.kuilunfuzhe.monvhua.features.physics_preview.PhysicsPoseDocument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.fabricmc.loader.api.FabricLoader;
import java.util.*;
import java.nio.file.*;
import java.util.concurrent.*;
import static net.minecraft.server.command.CommandManager.*;

/** Bounded value-only cache. No entities, world coordinates, player identities or sessions retained. */
public final class CorpsePhysicsDiagnostics {
    public enum Stage { APPLIED, INPUT_REJECTED, INITIALIZATION_REJECTED, BACKEND_UNAVAILABLE, SOLVER_TIMEOUT, SOLVER_INVALID,
        LEFT_REGION, TERRAIN_CHANGED, CARRIER_INVALID, HOST_TIMEOUT, NO_PROGRESS, STATIC_COMMIT_REJECTED, CANCELLED, CAPACITY, CONTINUOUS_STOPPED }
    public record Entry(long sequence,Stage stage,CorpsePhysicsBridge.Request input,PhysicsPoseDocument result,CorpsePhysicsBridge.Metrics metrics,long elapsedNanos){}
    private static final ArrayDeque<Entry> recent=new ArrayDeque<>();
    private static final EnumMap<Stage,Long> counts=new EnumMap<>(Stage.class);
    private static long sequence,generation;
    private static final long[] terrainNanos=new long[256];
    private static int terrainCount,terrainCursor;
    public static synchronized void terrainCapture(long nanos){terrainNanos[terrainCursor++%terrainNanos.length]=nanos;terrainCount=Math.min(terrainNanos.length,terrainCount+1);if(terrainCursor==terrainNanos.length)terrainCursor=0;}
    private static String terrainReport(){if(terrainCount==0)return "terrainSamples=0";var sorted=Arrays.copyOf(terrainNanos,terrainCount);Arrays.sort(sorted);return "terrainSamples="+terrainCount+" p50Ms="+sorted[terrainCount/2]/1e6+" p95Ms="+sorted[(int)((terrainCount-1)*.95)]/1e6+" maxMs="+sorted[terrainCount-1]/1e6;}
    private static final ThreadPoolExecutor io=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(2),r->{var t=new Thread(r,"Corpse diagnostic IO");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    public static synchronized void clear(){recent.clear();counts.clear();sequence=0;generation++;terrainCount=0;terrainCursor=0;}
    public static synchronized void record(Stage stage,CorpsePhysicsBridge.Request input,PhysicsPoseDocument result,CorpsePhysicsBridge.Metrics metrics,long elapsed){
        counts.merge(stage,1L,Long::sum);recent.addLast(new Entry(++sequence,stage,input,result,metrics,elapsed));if(recent.size()>16)recent.removeFirst();
    }
    public static void rejected(Stage stage){record(stage,null,null,new CorpsePhysicsBridge.Metrics(0,0,0),0);}
    public static synchronized Entry latest(){return recent.peekLast();}
    public static synchronized String report(){var e=recent.peekLast();return "最近记录="+recent.size()+"/16 分类="+counts+(e==null?"":" | last="+e.sequence+" "+e.stage+" step="+(e.result==null?"none":e.result.step())+" elapsedMs="+e.elapsedNanos/1_000_000+" metrics="+e.metrics)+" | "+terrainReport();}
    public static void register(com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> root){
        root.then(literal("report").executes(c->{c.getSource().sendFeedback(()->Text.literal(report()+" | "+CorpseResidentManager.status()),false);return 1;}));
        var export=literal("export-last");
        for(boolean reproduction:new boolean[]{false,true})export.then(literal(reproduction?"reproduction":"summary").executes(c->export(c.getSource(),reproduction)));
        root.then(export);
    }
    private static synchronized int export(ServerCommandSource source,boolean reproduction){
        var e=latest();var backend=CorpsePhysicsCoordinator.diagnosticProvider(source.getServer());
        if(backend==null||e==null||e.input==null){source.sendError(Text.literal("没有可导出的求解输入；使用 report 查看采样前拒绝原因"));return 0;}
        long token=generation;var server=source.getServer();
        try{io.execute(()->{
            String message;boolean success;
            try{
                String json=backend.diagnostic(e.input,e.result,e.metrics,e.stage.name(),reproduction);
                byte[] bytes=json.getBytes(java.nio.charset.StandardCharsets.UTF_8);if(bytes.length>1024*1024)throw new IllegalArgumentException("Diagnostic exceeds 1 MiB");
                synchronized(CorpsePhysicsDiagnostics.class){if(token!=generation)return;}
                Path dir=FabricLoader.getInstance().getGameDir().resolve("simplephysics-diagnostics");Files.createDirectories(dir);
                String name="diagnostic-"+UUID.randomUUID()+".json";Path temporary=dir.resolve(name+".tmp");
                try{
                    Files.write(temporary,bytes,StandardOpenOption.CREATE_NEW);
                    synchronized(CorpsePhysicsDiagnostics.class){if(token!=generation)return;}
                    try{Files.move(temporary,dir.resolve(name),StandardCopyOption.ATOMIC_MOVE);}
                    catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,dir.resolve(name));}
                }finally{Files.deleteIfExists(temporary);}
                message="诊断已导出：simplephysics-diagnostics/"+name+" ("+bytes.length+" bytes, "+(reproduction?"局部地形复现包":"不含地形摘要")+")";success=true;
            }catch(Exception|LinkageError error){message="诊断导出失败："+error.getMessage();success=false;}
            String result=message;boolean ok=success;server.execute(()->{
                synchronized(CorpsePhysicsDiagnostics.class){if(token!=generation)return;}
                if(ok)source.sendFeedback(()->Text.literal(result),false);else source.sendError(Text.literal(result));
            });
        });return 1;}catch(RejectedExecutionException error){source.sendError(Text.literal("诊断 IO 队列已满，请稍后重试"));return 0;}
    }
}
