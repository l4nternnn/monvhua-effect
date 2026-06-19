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
    public static final int MAX_PAINT_PIXELS = 100;
    public static final int COLOR_SLOTS = 9;
    private static final String PAINT_COLOR_KEY = "paint_color";
    private static final String PAINT_REMAINING_KEY = "paint_remaining";
    private static final String PAINT_REMAINING_EXACT_KEY = "paint_remaining_percent";
    private static final String SELECTED_SLOT_KEY = "paint_selected_slot";
    private static final String SLOT_COLOR_PREFIX = "paint_color_";
    private static final String SLOT_REMAINING_PREFIX = "paint_remaining_";
    private static final String SLOT_REMAINING_EXACT_PREFIX = "paint_remaining_percent_";

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
        return getPaintColor(stack, getSelectedSlot(stack));
    }

    public static int getPaintColor(ItemStack stack, int slot) {
        NbtCompound nbt = customData(stack);
        slot = clampSlot(slot);
        if (nbt.contains(SLOT_COLOR_PREFIX + slot)) {
            return nbt.getInt(SLOT_COLOR_PREFIX + slot, 0xFFFFFF) & 0xFFFFFF;
        }
        if (!hasAnySlotData(nbt) && slot == getSelectedSlot(stack)) {
            return nbt.getInt(PAINT_COLOR_KEY, 0xFFFFFF) & 0xFFFFFF;
        }
        return 0xFFFFFF;
    }

    public static int getRemainingPaint(ItemStack stack) {
        return getRemainingPaint(stack, getSelectedSlot(stack));
    }

    public static int getRemainingPaint(ItemStack stack, int slot) {
        double remaining = getRemainingPaintPercent(stack, slot);
        return remaining <= 0.0D ? 0 : MathHelper.clamp((int) Math.ceil(remaining), 0, MAX_PAINT_PIXELS);
    }

    public static double getRemainingPaintPercent(ItemStack stack) {
        return getRemainingPaintPercent(stack, getSelectedSlot(stack));
    }

    public static double getRemainingPaintPercent(ItemStack stack, int slot) {
        NbtCompound nbt = customData(stack);
        slot = clampSlot(slot);
        if (nbt.contains(SLOT_REMAINING_EXACT_PREFIX + slot)) {
            return Math.clamp(nbt.getDouble(SLOT_REMAINING_EXACT_PREFIX + slot, 0.0D), 0.0D, MAX_PAINT_PIXELS);
        }
        if (nbt.contains(SLOT_REMAINING_PREFIX + slot)) {
            return MathHelper.clamp(nbt.getInt(SLOT_REMAINING_PREFIX + slot, 0), 0, MAX_PAINT_PIXELS);
        }
        if (!hasAnySlotData(nbt) && slot == getSelectedSlot(stack)) {
            if (nbt.contains(PAINT_REMAINING_EXACT_KEY)) {
                return Math.clamp(nbt.getDouble(PAINT_REMAINING_EXACT_KEY, 0.0D), 0.0D, MAX_PAINT_PIXELS);
            }
            return MathHelper.clamp(nbt.getInt(PAINT_REMAINING_KEY, 0), 0, MAX_PAINT_PIXELS);
        }
        return 0;
    }

    public static boolean hasPaint(ItemStack stack) {
        return stack.getItem() instanceof PaintBrushItem && getRemainingPaintPercent(stack) > 0.0D;
    }

    public static boolean hasPaint(ItemStack stack, int slot) {
        return stack.getItem() instanceof PaintBrushItem && getRemainingPaintPercent(stack, slot) > 0.0D;
    }

    public static int chooseLoadSlot(ItemStack stack, int color) {
        if (!(stack.getItem() instanceof PaintBrushItem)) {
            return -1;
        }
        migrateLegacyPaint(stack);
        color &= 0xFFFFFF;
        for (int slot = 0; slot < COLOR_SLOTS; slot++) {
            if (getRemainingPaintPercent(stack, slot) <= 0.0D) {
                return slot;
            }
        }
        for (int slot = 0; slot < COLOR_SLOTS; slot++) {
            if (getPaintColor(stack, slot) == color && getRemainingPaintPercent(stack, slot) < 10.0D) {
                return slot;
            }
        }
        return -1;
    }

    public static void loadPaint(ItemStack stack, int color) {
        loadPaint(stack, getSelectedSlot(stack), color);
    }

    public static void loadPaint(ItemStack stack, int slot, int color) {
        if (!(stack.getItem() instanceof PaintBrushItem)) {
            return;
        }
        migrateLegacyPaint(stack);
        NbtCompound nbt = customData(stack);
        slot = clampSlot(slot);
        nbt.putInt(SLOT_COLOR_PREFIX + slot, color & 0xFFFFFF);
        nbt.putInt(SLOT_REMAINING_PREFIX + slot, MAX_PAINT_PIXELS);
        nbt.putDouble(SLOT_REMAINING_EXACT_PREFIX + slot, MAX_PAINT_PIXELS);
        nbt.putInt(SELECTED_SLOT_KEY, slot);
        nbt.remove(PAINT_COLOR_KEY);
        nbt.remove(PAINT_REMAINING_KEY);
        nbt.remove(PAINT_REMAINING_EXACT_KEY);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    public static int consumePaint(ItemStack stack, int amount) {
        return consumePaint(stack, getSelectedSlot(stack), amount);
    }

    public static int consumePaint(ItemStack stack, int slot, int amount) {
        return (int) Math.ceil(consumePaint(stack, slot, (double) amount));
    }

    public static double consumePaint(ItemStack stack, int slot, double amount) {
        if (!(stack.getItem() instanceof PaintBrushItem) || amount <= 0.0D) {
            return 0;
        }
        migrateLegacyPaint(stack);
        NbtCompound nbt = customData(stack);
        slot = clampSlot(slot);
        double remaining = getRemainingPaintPercent(stack, slot);
        double used = Math.min(remaining, amount);
        double next = Math.max(0.0D, remaining - used);
        if (next <= 0.000001D) {
            nbt.remove(SLOT_REMAINING_PREFIX + slot);
            nbt.remove(SLOT_REMAINING_EXACT_PREFIX + slot);
            nbt.remove(SLOT_COLOR_PREFIX + slot);
        } else {
            nbt.putInt(SLOT_REMAINING_PREFIX + slot, MathHelper.clamp((int) Math.ceil(next), 0, MAX_PAINT_PIXELS));
            nbt.putDouble(SLOT_REMAINING_EXACT_PREFIX + slot, next);
        }
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        return used;
    }

    public static int getSelectedSlot(ItemStack stack) {
        NbtCompound nbt = customData(stack);
        return clampSlot(nbt.getInt(SELECTED_SLOT_KEY, 0));
    }

    public static void setSelectedSlot(ItemStack stack, int slot) {
        if (!(stack.getItem() instanceof PaintBrushItem)) {
            return;
        }
        NbtCompound nbt = customData(stack);
        nbt.putInt(SELECTED_SLOT_KEY, clampSlot(slot));
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    public static SlotInfo slotInfo(ItemStack stack, int slot) {
        slot = clampSlot(slot);
        return new SlotInfo(slot, getPaintColor(stack, slot), getRemainingPaintPercent(stack, slot));
    }

    public record SlotInfo(int slot, int color, double remaining) {
    }

    private static int clampSlot(int slot) {
        return MathHelper.clamp(slot, 0, COLOR_SLOTS - 1);
    }

    private static boolean hasAnySlotData(NbtCompound nbt) {
        for (int slot = 0; slot < COLOR_SLOTS; slot++) {
            if (nbt.contains(SLOT_COLOR_PREFIX + slot) || nbt.contains(SLOT_REMAINING_PREFIX + slot)
                    || nbt.contains(SLOT_REMAINING_EXACT_PREFIX + slot)) {
                return true;
            }
        }
        return false;
    }

    private static void migrateLegacyPaint(ItemStack stack) {
        NbtCompound nbt = customData(stack);
        if (hasAnySlotData(nbt) || !nbt.contains(PAINT_REMAINING_KEY)) {
            return;
        }
        double remaining = nbt.contains(PAINT_REMAINING_EXACT_KEY)
                ? Math.clamp(nbt.getDouble(PAINT_REMAINING_EXACT_KEY, 0.0D), 0.0D, MAX_PAINT_PIXELS)
                : MathHelper.clamp(nbt.getInt(PAINT_REMAINING_KEY, 0), 0, MAX_PAINT_PIXELS);
        if (remaining > 0) {
            int slot = clampSlot(nbt.getInt(SELECTED_SLOT_KEY, 0));
            nbt.putInt(SLOT_COLOR_PREFIX + slot, nbt.getInt(PAINT_COLOR_KEY, 0xFFFFFF) & 0xFFFFFF);
            nbt.putInt(SLOT_REMAINING_PREFIX + slot, MathHelper.clamp((int) Math.ceil(remaining), 0, MAX_PAINT_PIXELS));
            nbt.putDouble(SLOT_REMAINING_EXACT_PREFIX + slot, remaining);
        }
        nbt.remove(PAINT_COLOR_KEY);
        nbt.remove(PAINT_REMAINING_KEY);
        nbt.remove(PAINT_REMAINING_EXACT_KEY);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    private static NbtCompound customData(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        return component == null ? new NbtCompound() : component.copyNbt();
    }
}
