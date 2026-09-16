package com.kuilunfuzhe.monvhua.command;

import com.kuilunfuzhe.monvhua.features.swing.SwingEntity;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.text.Text;
import net.minecraft.server.command.CommandManager;

public final class SwingCommand {
    private SwingCommand() {}
    public static void register(CommandDispatcher<ServerCommandSource> d, net.minecraft.registry.RegistryWrapper.WrapperLookup r, CommandManager.RegistrationEnvironment e) {
        d.register(CommandManager.literal("monvhua-swing")
                .requires(s -> s.hasPermissionLevel(2))
                .then(CommandManager.literal("diagnose")
                        .then(CommandManager.argument("beam", net.minecraft.command.argument.BlockPosArgumentType.blockPos())
                                .executes(c -> {
                                    var source = c.getSource();
                                    var pos = net.minecraft.command.argument.BlockPosArgumentType.getLoadedBlockPos(c, "beam");
                                    var trace = new com.kuilunfuzhe.monvhua.features.swing.SwingDiagnostics();
                                    trace.log("READ_ONLY_DIAGNOSE dimension=" + source.getWorld().getRegistryKey().getValue());
                                    var result = com.kuilunfuzhe.monvhua.features.swing.SwingAssemblyDetector.detect(source.getWorld(), pos, trace);
                                    source.sendFeedback(() -> Text.literal("只读诊断 #" + trace.id + (result == null ? "：未匹配，查看 [SwingDiag] 日志" : "：匹配成功，未修改方块")), false);
                                    return result == null ? 0 : 1;
                                })))
                .then(CommandManager.literal("restore").executes(c -> restoreNearest(c.getSource())))
                .then(CommandManager.literal("remove").executes(c -> removeNearest(c.getSource()))));
    }
    private static SwingEntity nearest(ServerCommandSource source) {
        ServerWorld w = source.getWorld();
        var p = source.getPosition();
        Box box = new Box(p.x - 16, p.y - 16, p.z - 16, p.x + 16, p.y + 16, p.z + 16);
        java.util.List<SwingEntity> entities = w.getEntitiesByClass(SwingEntity.class, box, Entity::isAlive);
        return entities.stream().min(java.util.Comparator.comparingDouble((SwingEntity e) -> e.squaredDistanceTo(p))).orElse(null);
    }
    private static int restoreNearest(ServerCommandSource s) { SwingEntity e=nearest(s); if(e==null){s.sendFeedback(()->Text.literal("附近没有秋千"),false);return 0;} e.removeAllPassengers(); e.restoreStructure(); e.discard(); s.sendFeedback(()->Text.literal("秋千结构已恢复"),true); return 1; }
    private static int removeNearest(ServerCommandSource s) { SwingEntity e=nearest(s); if(e==null){s.sendFeedback(()->Text.literal("附近没有秋千"),false);return 0;} e.removeAllPassengers(); e.discard(); s.sendFeedback(()->Text.literal("秋千实体已移除（方块不会恢复）"),true); return 1; }
}
