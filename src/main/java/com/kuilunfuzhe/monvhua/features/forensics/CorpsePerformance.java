package com.kuilunfuzhe.monvhua.features.forensics;

import java.util.*;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import static net.minecraft.server.command.CommandManager.literal;

/** Optional bounded sampling of this module's work, not a whole-server MSPT measurement. Server thread only. */
public final class CorpsePerformance {
    public enum Metric { MAINTENANCE, GEOMETRY, INVENTORY, PHYSICS, NETWORK }
    public enum Counter { GEOMETRY_REBUILT, GEOMETRY_CACHED, INVENTORY_ENCODED, INVENTORY_CACHED,
        PHYSICS_ADVANCED, PHYSICS_IDLE_SKIPPED, NETWORK_VIEW_ENCODED, NETWORK_PACKET_SENT, NETWORK_PACKET_SKIPPED }
    private static final int LIMIT = 512;
    private static final EnumMap<Metric, Samples> samples = new EnumMap<>(Metric.class);
    private static final EnumMap<Counter, Long> counters = new EnumMap<>(Counter.class);
    private static boolean enabled;
    private static final class Samples {
        final long[] values = new long[LIMIT];
        int size, cursor;
        long total, calls;
        void add(long nanos) { values[cursor] = nanos; cursor=(cursor+1)%LIMIT; size = Math.min(size + 1, LIMIT); total += nanos; calls++; }
        String report() {
            var sorted = Arrays.copyOf(values, size); Arrays.sort(sorted);
            return String.format(Locale.ROOT, "calls=%d recent=%d p50=%.3fms p95=%.3fms p99=%.3fms max=%.3fms total=%.1fms",
                    calls, size, sorted[(size-1)/2]/1e6, sorted[(int)((size-1)*.95)]/1e6,
                    sorted[(int)((size-1)*.99)]/1e6, sorted[size-1]/1e6, total/1e6);
        }
    }
    public static long begin() { return enabled ? System.nanoTime() : 0; }
    public static void end(Metric metric, long start) {
        if (enabled && start != 0) samples.computeIfAbsent(metric, k -> new Samples()).add(System.nanoTime() - start);
    }
    public static void count(Counter counter) { if (enabled) counters.merge(counter, 1L, Long::sum); }
    public static void reset() { enabled = false; samples.clear(); counters.clear(); }
    public static String report() {
        var text = new StringBuilder("遗体性能采样=" + enabled + " " + CorpseLoadedIndex.status() + "（模块耗时，非全服MSPT）");
        samples.forEach((key, value) -> text.append("\n").append(key).append(": ").append(value.report()));
        return text.append("\n计数=").append(counters).toString();
    }
    public static void register(com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> root) {
        var command = literal("performance");
        command.then(literal("start").executes(c -> { reset(); enabled = true; c.getSource().sendFeedback(() -> Text.literal("遗体性能采样已开始（最近512次/模块）；停止后可继续查看报告"), false); return 1; }));
        command.then(literal("stop").executes(c -> { enabled = false; c.getSource().sendFeedback(() -> Text.literal(report()), false); return 1; }));
        command.then(literal("report").executes(c -> { c.getSource().sendFeedback(() -> Text.literal(report()), false); return 1; }));
        command.then(literal("reset").executes(c -> { reset(); c.getSource().sendFeedback(() -> Text.literal("遗体性能采样已关闭并清空"), false); return 1; }));
        command.then(literal("mods").executes(c -> {
            var loader=net.fabricmc.loader.api.FabricLoader.getInstance();
            var result=new StringBuilder("运行时模组（已加载的内部版本）：");
            for(var id:List.of("c2me","lithium","servercore","entityculling","ferritecore","spark","sodium","iris","distanthorizons","chunky","scalablelux","simplephysics","kom_dying","carpet"))
                result.append("\n").append(id).append("=").append(loader.getModContainer(id).map(m->m.getMetadata().getVersion().getFriendlyString()).orElse("未加载"));
            c.getSource().sendFeedback(() -> Text.literal(result.toString()),false);return 1;
        }));
        root.then(command);
    }
}
