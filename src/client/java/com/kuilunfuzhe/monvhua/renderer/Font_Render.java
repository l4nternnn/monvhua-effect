package com.kuilunfuzhe.monvhua.renderer;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.item.ModItemGroups;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemGroup;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import sun.misc.Unsafe;
import java.lang.reflect.Field;

/**
 * 物品栏标题彩虹色渲染器。
 * 通过Unsafe修改ItemGroup的displayName final字段来实现动态颜色变化，
 * 每20 tick更新一次标题颜色，色相随时间匀速变化形成彩虹渐变效果。
 */
public class Font_Render {

    private static final int UPDATE_INTERVAL_TICKS = 20; // 每20 tick更新一次（约1秒）
    /** 上次更新的tick计数，用于控制更新频率 */
    private static int lastUpdateTick = 0;

    /**
     * 客户端tick回调，按固定间隔触发标题颜色更新。
     * 应在ClientTickEvents.END_CLIENT_TICK事件中调用。
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

    /** 根据当前时间计算HSV色相，生成彩虹渐变颜色并更新物品组标题 */
    private static void updateDisplayName(MinecraftClient client) {
        // 色相随时间匀速变化（周期约360*50ms ≈ 18秒循环一圈），形成彩虹渐变
        long time = System.currentTimeMillis();
        float hue = (time / 50.0f) % 360.0f / 360.0f; // 50ms步进使色相平滑变化
        int color = MathHelper.hsvToRgb(hue, 1.0f, 1.0f);

        // 创建带颜色的新名称（保留原来的静态前缀部分）
        Text newName = Text.literal("魔法少女的溃论覆辙◆魔法重构")
                .styled(style -> style.withColor(color));

        setItemGroupDisplayName(ModItemGroups.MAIN_GROUP, newName);
    }

    /** 通过反射获取的Unsafe实例，用于绕过访问控制修改final字段 */
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

    /** ItemGroup.displayName字段的反射引用，配合Unsafe修改其final值 */
    private static final Field DISPLAY_NAME_FIELD;

    static {
        Field f = null;
        try {
            f = ItemGroup.class.getDeclaredField("displayName");
        } catch (Exception ignored) {}
        DISPLAY_NAME_FIELD = f;
    }

    /**
     * 使用Unsafe绕过final修饰符，直接修改ItemGroup的displayName字段。
     * 正常API无法修改final字段，故通过Unsafe.objectFieldOffset获取字段偏移量后写入。
     */
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
