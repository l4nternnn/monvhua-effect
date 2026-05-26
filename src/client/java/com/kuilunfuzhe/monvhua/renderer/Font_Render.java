package com.kuilunfuzhe.monvhua.renderer;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.item.ModItemGroups;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemGroup;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class Font_Render {

    private static final int UPDATE_INTERVAL_TICKS = 20; // 每20 tick更新一次
    private static int lastUpdateTick = 0;

    /**
     * 应在客户端 tick 事件中调用
     */
    public static void tick(MinecraftClient client) {
        if (client.player == null) return;
        if (ModItemGroups.MAIN_GROUP == null) return;

        int currentTick = client.player.age;
        if (currentTick - lastUpdateTick >= UPDATE_INTERVAL_TICKS) {
            lastUpdateTick = currentTick;
            updateDisplayName(client);
        }
    }

    private static void updateDisplayName(MinecraftClient client) {
        // 根据当前时间计算一个动态颜色（彩虹渐变）
        long time = System.currentTimeMillis();
        float hue = (time / 50.0f) % 360.0f / 360.0f; // 色相随时间线性变化
        int color = MathHelper.hsvToRgb(hue, 1.0f, 1.0f);

        // 创建带颜色的新名称（保留原来的静态前缀部分）
        Text newName = Text.literal("魔法少女的溃论覆辙◆魔法重构")
                .styled(style -> style.withColor(color));

        setItemGroupDisplayName(ModItemGroups.MAIN_GROUP, newName);
    }

    private static void setItemGroupDisplayName(ItemGroup group, Text name) {
        try {
            Field field = ItemGroup.class.getDeclaredField("displayName");
            field.setAccessible(true);
            // 移除 final 修饰符（Java 反射允许修改 final 字段，但需要先修改 modifiers）
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
            field.set(group, name);
        } catch (Exception e) {
            MonvhuaMod.LOGGER.error("Failed to update item group display name", e);
        }
    }

}
