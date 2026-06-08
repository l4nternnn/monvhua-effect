package com.kuilunfuzhe.monvhua.item.paint;

import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;

public class PaintEraserItem extends Item {
    public PaintEraserItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        return ActionResult.FAIL;
    }
}
