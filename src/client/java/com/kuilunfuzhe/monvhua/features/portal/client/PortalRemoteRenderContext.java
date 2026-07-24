package com.kuilunfuzhe.monvhua.features.portal.client;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.features.portal.PortalViewConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.util.concurrent.atomic.AtomicBoolean;

public final class PortalRemoteRenderContext {
    private static final ThreadLocal<Boolean> PORTAL_PASS = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<BlockPos> REMOTE_SOURCE = new ThreadLocal<>();
    private static final ThreadLocal<PortalPassContext> CURRENT_CONTEXT = new ThreadLocal<>();
    private static final AtomicBoolean WORKER_CONTEXT_LOGGED = new AtomicBoolean(false);
    private static int lastPassContextLogTick = Integer.MIN_VALUE;
    private static BlockPos lastPassContextLogSource;

    private PortalRemoteRenderContext() {
    }

    public static void beginPortalPass(BlockPos sourcePos) {
        beginPortalPass(PortalPassContext.remoteCache(sourcePos));
    }

    public static void beginPortalPass(PortalPassContext context) {
        if (context == null || context.sourcePos() == null || context.chunkSource() == null) {
            endPortalPass();
            return;
        }
        BlockPos sourcePos = context.sourcePos();
        if (sourcePos == null) {
            endPortalPass();
            return;
        }
        PORTAL_PASS.set(true);
        REMOTE_SOURCE.set(sourcePos);
        CURRENT_CONTEXT.set(context);
        logPassContext(context);
    }

    public static void endPortalPass() {
        PORTAL_PASS.remove();
        REMOTE_SOURCE.remove();
        CURRENT_CONTEXT.remove();
    }

    public static void beginWorkerPass(BlockPos sourcePos) {
        beginPortalPass(sourcePos);
        if (WORKER_CONTEXT_LOGGED.compareAndSet(false, true)) {
            MonvhuaMod.LOGGER.info(
                    "[Monvhua] Portal Sodium worker context active: source={} thread={}",
                    sourcePos,
                    Thread.currentThread().getName()
            );
        }
    }

    public static void endWorkerPass() {
        endPortalPass();
    }

    public static boolean isPortalPass() {
        return PORTAL_PASS.get();
    }

    public static BlockPos getRemoteSourcePos() {
        return REMOTE_SOURCE.get();
    }

    public static PortalPassContext current() {
        return CURRENT_CONTEXT.get();
    }

    public static void markRemoteRendererAlive() {
    }

    public static void clearRemoteRendererAlive() {
        PORTAL_PASS.remove();
        REMOTE_SOURCE.remove();
        CURRENT_CONTEXT.remove();
    }

    private static void logPassContext(PortalPassContext context) {
        int tick = clientTick();
        BlockPos sourcePos = context.sourcePos();
        if (sourcePos != null
                && sourcePos.equals(lastPassContextLogSource)
                && tick >= lastPassContextLogTick
                && tick - lastPassContextLogTick < PortalViewConfig.PORTAL_FREEZE_LOG_INTERVAL_TICKS) {
            return;
        }
        lastPassContextLogTick = tick;
        lastPassContextLogSource = sourcePos;
        MinecraftClient client = MinecraftClient.getInstance();
        MonvhuaMod.LOGGER.info(
                "[Monvhua] Portal pass context begin: source={} thread={} {}",
                sourcePos,
                Thread.currentThread().getName(),
                context.debugSummary(client.world)
        );
    }

    private static int clientTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player == null ? 0 : client.player.age;
    }
}
