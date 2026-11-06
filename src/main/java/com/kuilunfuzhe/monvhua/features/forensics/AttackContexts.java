package com.kuilunfuzhe.monvhua.features.forensics;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import java.util.*;

public final class AttackContexts {
    public record Attack(UUID actor, String id, ItemStack weapon, WeaponProfiles.Selection selection, ItemStack original) {}
    private static final ThreadLocal<Deque<Attack>> ACTIVE = ThreadLocal.withInitial(ArrayDeque::new);
    public static void push(PlayerEntity player) {
        ItemStack stack = player.getWeaponStack().copy();
        ACTIVE.get().push(new Attack(player.getUuid(), UUID.randomUUID().toString(), stack, WeaponProfiles.select(stack, "melee"), player.getWeaponStack()));
    }
    public static void pop() { var stack = ACTIVE.get(); stack.pop(); if (stack.isEmpty()) ACTIVE.remove(); }
    public static Attack current(UUID actor) {
        return ACTIVE.get().stream().filter(a -> a.actor.equals(actor)).findFirst().orElse(null);
    }
    public static boolean allowsSweep(PlayerEntity player) {
        Attack attack = current(player.getUuid());
        return !ForensicConfig.current.enabled || attack == null || attack.selection == null || attack.selection.mode().equals("slash") || attack.selection.mode().equals("default");
    }
}
