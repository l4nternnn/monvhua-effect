package com.kuilunfuzhe.monvhua.item.paint;

import com.kuilunfuzhe.monvhua.features.paint.PaintOverlayStore;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class PaintBrushItem extends Item {
    public static final int MAX_PAINT_PIXELS = 50;
    private static final String PAINT_COLOR_KEY = "paint_color";
    private static final String PAINT_REMAINING_KEY = "paint_remaining";

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

    public static int getPaintColor(ItemStack stack) {
        NbtCompound nbt = customData(stack);
        return nbt.getInt(PAINT_COLOR_KEY, 0xFFFFFF) & 0xFFFFFF;
    }

    public static int getRemainingPaint(ItemStack stack) {
        NbtCompound nbt = customData(stack);
        return MathHelper.clamp(nbt.getInt(PAINT_REMAINING_KEY, 0), 0, MAX_PAINT_PIXELS);
    }

    public static boolean hasPaint(ItemStack stack) {
        return stack.getItem() instanceof PaintBrushItem && getRemainingPaint(stack) > 0;
    }

    public static void loadPaint(ItemStack stack, int color) {
        if (!(stack.getItem() instanceof PaintBrushItem)) {
            return;
        }
        NbtCompound nbt = customData(stack);
        nbt.putInt(PAINT_COLOR_KEY, color & 0xFFFFFF);
        nbt.putInt(PAINT_REMAINING_KEY, MAX_PAINT_PIXELS);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    public static int consumePaint(ItemStack stack, int amount) {
        if (!(stack.getItem() instanceof PaintBrushItem) || amount <= 0) {
            return 0;
        }
        NbtCompound nbt = customData(stack);
        int remaining = MathHelper.clamp(nbt.getInt(PAINT_REMAINING_KEY, 0), 0, MAX_PAINT_PIXELS);
        int used = Math.min(remaining, amount);
        int next = remaining - used;
        nbt.putInt(PAINT_REMAINING_KEY, next);
        if (next <= 0) {
            nbt.remove(PAINT_COLOR_KEY);
        }
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        return used;
    }

    private static NbtCompound customData(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        return component == null ? new NbtCompound() : component.copyNbt();
    }
}
