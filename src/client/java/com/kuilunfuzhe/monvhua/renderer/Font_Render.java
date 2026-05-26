package com.kuilunfuzhe.monvhua.renderer;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.item.ModItemGroups;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemGroup;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import sun.misc.Unsafe;
import java.lang.reflect.Field;

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

    private static final Unsafe UNSAFE;

    static {
        Unsafe u = null;
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            u = (Unsafe) f.get(null);
        } catch (Exception ignored) {}
        UNSAFE = u;
    }

    private static final Field DISPLAY_NAME_FIELD;

    static {
        Field f = null;
        try {
            f = ItemGroup.class.getDeclaredField("displayName");
        } catch (Exception ignored) {}
        DISPLAY_NAME_FIELD = f;
    }

    private static void setItemGroupDisplayName(ItemGroup group, Text name) {
        try {
            if (UNSAFE == null || DISPLAY_NAME_FIELD == null) return;
            long offset = UNSAFE.objectFieldOffset(DISPLAY_NAME_FIELD);
            UNSAFE.putObject(group, offset, name);
        } catch (Exception e) {
            MonvhuaMod.LOGGER.error("Failed to update item group display name", e);
        }
    }

}
