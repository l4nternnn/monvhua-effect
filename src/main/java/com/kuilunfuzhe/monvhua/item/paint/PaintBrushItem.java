package com.kuilunfuzhe.monvhua.item.paint;

import com.kuilunfuzhe.monvhua.features.paint.PaintOverlayStore;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class PaintBrushItem extends Item {
    public PaintBrushItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        return ActionResult.FAIL;
    }

    public static int[] getPixel(Vec3d hitPos, BlockPos blockPos, Direction face) {
        double localX = MathHelper.clamp(hitPos.x - blockPos.getX(), 0.0D, 0.999999D);
        double localY = MathHelper.clamp(hitPos.y - blockPos.getY(), 0.0D, 0.999999D);
        double localZ = MathHelper.clamp(hitPos.z - blockPos.getZ(), 0.0D, 0.999999D);
        double u;
        double v;
        switch (face) {
            case UP -> {
                u = localX;
                v = localZ;
            }
            case DOWN -> {
                u = localX;
                v = 1.0D - localZ;
            }
            case NORTH -> {
                u = 1.0D - localX;
                v = 1.0D - localY;
            }
            case SOUTH -> {
                u = localX;
                v = 1.0D - localY;
            }
            case WEST -> {
                u = localZ;
                v = 1.0D - localY;
            }
            case EAST -> {
                u = 1.0D - localZ;
                v = 1.0D - localY;
            }
            default -> {
                u = localX;
                v = localY;
            }
        }
        return new int[]{
                MathHelper.clamp((int) (u * PaintOverlayStore.SIZE), 0, PaintOverlayStore.SIZE - 1),
                MathHelper.clamp((int) (v * PaintOverlayStore.SIZE), 0, PaintOverlayStore.SIZE - 1)
        };
    }
}
