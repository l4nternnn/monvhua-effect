package com.kuilunfuzhe.monvhua.item.paint;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;

import java.util.List;

public class PaintBucketItem extends BlockItem {
    public static final int DEFAULT_COLOR = 0xFFFFFF;

    public PaintBucketItem(Block block, Settings settings) {
        super(block, settings);
    }

    public static int getColor(ItemStack stack) {
        CustomModelDataComponent data = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
        Integer color = data == null ? null : data.getColor(0);
        return color == null ? DEFAULT_COLOR : color & 0xFFFFFF;
    }

    public static boolean isFilled(ItemStack stack) {
        CustomModelDataComponent data = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
        return data != null && data.getColor(0) != null;
    }

    public static void setColor(ItemStack stack, int color) {
        stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(
                List.of(),
                List.of(),
                List.of(),
                List.of(color & 0xFFFFFF)
        ));
    }
}
