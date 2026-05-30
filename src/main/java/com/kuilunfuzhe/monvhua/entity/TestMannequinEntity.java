package com.kuilunfuzhe.monvhua.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.util.Arm;
import net.minecraft.world.World;

/**
 * 测试假人实体——用于调试和展示的无敌、无重力活体实体。
 * 继承 LivingEntity，不产生掉落物，不可移动，默认右手为主手。
 */
public class TestMannequinEntity extends LivingEntity {
    public TestMannequinEntity(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
        // 防止被推动或攻击消失（测试用）
        this.setNoGravity(true);
        this.setInvulnerable(true);
    }

    /**
     * 返回空盔甲物品列表——假人不穿戴任何盔甲。
     * @return 空迭代器
     */
//    @Override
    public Iterable<net.minecraft.item.ItemStack> getArmorItems() {
        return java.util.Collections.emptyList();
    }

    /**
     * 返回空物品堆——假人不装备任何物品。
     * @param slot 装备槽位
     * @return 空 ItemStack
     */
    @Override
    public net.minecraft.item.ItemStack getEquippedStack(net.minecraft.entity.EquipmentSlot slot) {
        return net.minecraft.item.ItemStack.EMPTY;
    }

    /**
     * 空实现——假人不接受任何装备。
     * @param slot 装备槽位
     * @param stack 物品堆
     */
    @Override
    public void equipStack(net.minecraft.entity.EquipmentSlot slot, net.minecraft.item.ItemStack stack) {
    }

    /**
     * 死亡处理——仅调用父类逻辑，不产生掉落物。
     * @param source 伤害来源
     */
    @Override
    public void onDeath(DamageSource source) {
        // 测试实体不产生掉落物
        super.onDeath(source);
    }

    // ========== LivingEntity 中还有一个抽象方法 getMainArm() ==========
    /**
     * 返回假人的主手。
     * @return 默认右手
     */
    @Override
    public Arm getMainArm() {
        // 假人默认右手为主手（不影响显示）
        return Arm.RIGHT;
    }
    // ========== 可选：覆写属性注册，使假人拥有基础的移动速度（虽然我们设为 noGravity） ==========
    /**
     * 创建假人的默认属性——最大生命值20，移动速度为0以确保不移动。
     * @return 属性构建器
     */
    public static DefaultAttributeContainer.Builder createMobAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(EntityAttributes.MAX_HEALTH, 20.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.0); // 速度0，确保不会移动
    }
}