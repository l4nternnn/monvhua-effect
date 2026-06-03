package com.kuilunfuzhe.monvhua.item.gazeguidance;

import net.minecraft.item.Item;

/**
 * 诱导法杖物品，用于引导视线/指示目标。
 * 当前无特殊逻辑，仅作为基础物品存在。
 * 下方注释掉的useOnEntity代码为旧版实现（发光效果+粒子特效），暂时保留以备后续参考。
 */
public class MagicStickItem extends Item {
    public MagicStickItem(Settings settings) {
        super(settings);
    }

    // ===== 以下为注释掉的旧版useOnEntity实现，必须保留 =====
    // 原功能：对实体右键施加发光效果（100 tick）并在目标位置生成粒子特效。
//    @Override
//    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
//        if (!user.getWorld().isClient()) {
//            double maxRange = 200.0;
//            if (user.squaredDistanceTo(entity) <= maxRange * maxRange) {
//                // 添加发光效果，持续5秒（100 tick）
////                entity.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 100, 0, false, false), user);
//                // 生成粒子效果
//                if (user.getWorld() instanceof ServerWorld serverWorld) {
////                    serverWorld.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
////                            entity.getX(), entity.getY() + entity.getHeight() / 2, entity.getZ(),
////                            10, 0.5, 0.5, 0.5, 0.1);
//                }
//            }
//        }
//        // 返回 SUCCESS 不会造成伤害或消耗
//        return ActionResult.SUCCESS;
//    }
}