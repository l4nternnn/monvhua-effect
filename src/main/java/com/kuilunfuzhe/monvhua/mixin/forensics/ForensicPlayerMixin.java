package com.kuilunfuzhe.monvhua.mixin.forensics;

import com.kuilunfuzhe.monvhua.features.forensics.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerEntity.class)
public abstract class ForensicPlayerMixin {
    @WrapMethod(method = "attack")
    private void monvhua$attack(Entity target, Operation<Void> original) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (player.getWorld().isClient) { original.call(target); return; }
        AttackContexts.push(player);
        try { original.call(target); } finally { AttackContexts.pop(); }
    }

    @WrapOperation(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;isIn(Lnet/minecraft/registry/tag/TagKey;)Z"))
    private boolean monvhua$sweep(ItemStack stack, TagKey<Item> tag, Operation<Boolean> original) {
        return original.call(stack, tag) && AttackContexts.allowsSweep((PlayerEntity) (Object) this);
    }

    // PlayerEntity overrides LivingEntity.applyDamage. This call occurs AFTER armor/absorption
    // and BEFORE setHealth/onDeath, so lethal hits need no AFTER_DAMAGE fallback or duplication.
    @WrapOperation(method = "applyDamage", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/damage/DamageTracker;onDamage(Lnet/minecraft/entity/damage/DamageSource;F)V"))
    private void monvhua$record(DamageTracker tracker, DamageSource source, float amount, Operation<Void> original) {
        original.call(tracker, source, amount);
        if ((Object) this instanceof ServerPlayerEntity player) Forensics.record(player, source, amount);
    }
}
