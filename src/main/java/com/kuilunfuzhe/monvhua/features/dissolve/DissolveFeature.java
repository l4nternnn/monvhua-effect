package com.kuilunfuzhe.monvhua.features.dissolve;

import com.kuilunfuzhe.monvhua.features.dissolve.server.DissolveServerController;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

public final class DissolveFeature {
    private static boolean serverInitialized;

    private DissolveFeature() {
    }

    public static void initializeServer() {
        if (serverInitialized) {
            return;
        }
        serverInitialized = true;
        DissolveParticleTypes.register();
        DissolveServerController.initialize();
    }

    public static boolean start(ServerPlayerEntity target, DissolveProfile profile) {
        return DissolveServerController.start(target, profile == null ? DissolveProfile.DEFAULT : profile);
    }

    public static void stop(UUID targetUuid, StopReason reason) {
        DissolveServerController.stop(targetUuid, reason == null ? StopReason.CANCELLED : reason);
    }

    public static boolean isDissolving(UUID targetUuid) {
        return DissolveServerController.isDissolving(targetUuid);
    }

    public static boolean canPaintPlayer(UUID targetUuid) {
        return DissolveLock.canPaint(targetUuid);
    }

    public enum StopReason {
        CANCELLED(0),
        FINISHED(1),
        DEATH(2),
        DISCONNECT(3),
        WORLD_CHANGE(4);

        private final int id;

        StopReason(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        public static StopReason byId(int id) {
            for (StopReason reason : values()) {
                if (reason.id == id) {
                    return reason;
                }
            }
            return CANCELLED;
        }
    }
}
