package com.kuilunfuzhe.monvhua.features.portal.client;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import net.minecraft.util.math.BlockPos;

import java.util.concurrent.atomic.AtomicBoolean;

public final class PortalRemoteRenderContext {
    private static final ThreadLocal<Boolean> PORTAL_PASS = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<BlockPos> REMOTE_SOURCE = new ThreadLocal<>();
    private static final AtomicBoolean WORKER_CONTEXT_LOGGED = new AtomicBoolean(false);

    private PortalRemoteRenderContext() {
    }

    public static void beginPortalPass(BlockPos sourcePos) {
        if (sourcePos == null) {
            endPortalPass();
            return;
        }
        PORTAL_PASS.set(true);
        REMOTE_SOURCE.set(sourcePos.toImmutable());
    }

    public static void endPortalPass() {
        PORTAL_PASS.remove();
        REMOTE_SOURCE.remove();
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

    public static void markRemoteRendererAlive() {
    }

    public static void clearRemoteRendererAlive() {
        PORTAL_PASS.remove();
        REMOTE_SOURCE.remove();
    }
}
