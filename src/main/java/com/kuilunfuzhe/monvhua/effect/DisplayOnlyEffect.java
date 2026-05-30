package com.kuilunfuzhe.monvhua.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/**
 * 纯显示药水效果，无任何 tick 逻辑（不覆写 {@code applyUpdateEffect}）。
 * 仅用于在客户端 HUD 显示魔女化状态的图标和名称，
 * 所有状态管理由服务端 {@code MonvhuaMod} 的 tick 循环负责。
 */
public class DisplayOnlyEffect extends StatusEffect {
    /**
     * @param category 效果分类（增益/中性/负面），影响图标边框颜色
     * @param color    效果粒子颜色或图标颜色（RGB十六进制）
     */
    public DisplayOnlyEffect(StatusEffectCategory category, int color) {
        super(category, color);
    }
}
