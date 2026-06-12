package com.kuilunfuzhe.monvhua.renderer;

import com.kuilunfuzhe.monvhua.item.ModItemGroups;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public final class Font_Render {
    private Font_Render() {
    }

    public static Text createMainGroupName() {
        float hue = (System.currentTimeMillis() % 36000L) / 36000.0F;
        int color = MathHelper.hsvToRgb(hue, 1.0F, 1.0F);
        return Text.literal(ModItemGroups.MAIN_GROUP_TITLE).styled(style -> style.withColor(color));
    }
}
