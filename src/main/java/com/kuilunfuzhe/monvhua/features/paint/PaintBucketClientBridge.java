package com.kuilunfuzhe.monvhua.features.paint;

import net.minecraft.util.math.BlockPos;

public final class PaintBucketClientBridge {
    private static Opener opener;

    private PaintBucketClientBridge() {
    }

    public static void setOpener(Opener opener) {
        PaintBucketClientBridge.opener = opener;
    }

    public static boolean open(BlockPos pos) {
        return opener != null && opener.open(pos);
    }

    @FunctionalInterface
    public interface Opener {
        boolean open(BlockPos pos);
    }
}
