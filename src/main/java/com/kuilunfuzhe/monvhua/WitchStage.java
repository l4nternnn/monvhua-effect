package com.kuilunfuzhe.monvhua;

import net.minecraft.ChatFormatting;
import net.minecraft.world.effect.MobEffectCategory;

public enum WitchStage {
    SANE(0, "sane", "神智清醒",
            "初来乍到，对未来还有着希望，对魔法的了解仅限于知道如何释放。理性没有受到影响，仅能在日常探索中使用魔法辅助。",
            0x55FF55, MobEffectCategory.BENEFICIAL, ChatFormatting.GREEN),
    TAINTED(10, "tainted", "略染污浊",
            "逐渐熟悉这里，也感觉到了魔女因子对你的影响，一些古怪的念头冒出。",
            0x00AA00, MobEffectCategory.BENEFICIAL, ChatFormatting.DARK_GREEN),
    LIGHT(25, "light", "轻度魔女化",
            "意外频发，十分不安。理性逐渐开始受到侵扰，魔法开始波动。",
            0xFFFF55, MobEffectCategory.NEUTRAL, ChatFormatting.YELLOW),
    MEDIUM(45, "medium", "中度魔女化",
            "思考不断受影响，纷乱的念头不断涌现。魔法可在任何地点或时间中使用。开始互相猜忌、怀疑，不能再轻易信任彼此。",
            0xFFAA00, MobEffectCategory.NEUTRAL, ChatFormatting.GOLD),
    HIGH(60, "high", "高度魔女化",
            "亲眼见证血腥、暴力、背叛与冷漠之后，逐渐不受控制，难以从可怕的想法中挣扎。魔法获得强化，但代价是什么？",
            0xFF5555, MobEffectCategory.HARMFUL, ChatFormatting.RED),
    SEVERE(70, "severe", "重度魔女化",
            "难以继续保持理性，黑暗想法几乎要控制你。状态开始波动，对躯体的掌控力下降，但魔法得到大幅加强。",
            0xAA0000, MobEffectCategory.HARMFUL, ChatFormatting.DARK_RED),
    PROTO_WITCH(80, "proto_witch", "准魔女",
            "理性所剩无几，杀人欲望强烈无比，是一座即将爆发的火山。杀人后会暂时冷静下来，但欲望终将再次降临。此阶段仍须遵守杀人限制，凶杀会大幅减少魔女化进度。",
            0xFF55FF, MobEffectCategory.HARMFUL, ChatFormatting.LIGHT_PURPLE),
    WITCH(90, "witch", "魔女",
            "最后的理智也即将丢失。没有救赎，没有希望，强烈的杀人欲望充斥脑海。并非完全失去意识，而是在极强杀人欲望驱使之下保留最后一点意志——需要进行RP演绎，而非无脑清图行为。杀完一个人后也会暂时冷静。被看守抓捕，也许是你不再伤害他人最后的方式。作为怪物，你已经没有回头路了。",
            0xAA00AA, MobEffectCategory.HARMFUL, ChatFormatting.DARK_PURPLE);

    public final int threshold;
    public final String id;
    public final String displayName;
    public final String description;
    public final int color;
    public final MobEffectCategory category;
    public final ChatFormatting chatColor;

    WitchStage(int threshold, String id, String displayName, String description,
               int color, MobEffectCategory category, ChatFormatting chatColor) {
        this.threshold = threshold;
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.color = color;
        this.category = category;
        this.chatColor = chatColor;
    }

    public static WitchStage fromScore(int score) {
        WitchStage result = SANE;
        for (WitchStage stage : values()) {
            if (score >= stage.threshold) {
                result = stage;
            } else {
                break;
            }
        }
        return result;
    }
}
