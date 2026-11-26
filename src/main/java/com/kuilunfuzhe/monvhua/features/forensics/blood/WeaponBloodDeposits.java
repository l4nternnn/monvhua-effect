package com.kuilunfuzhe.monvhua.features.forensics.blood;

import com.kuilunfuzhe.monvhua.features.forensics.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.server.network.ServerPlayerEntity;

/** Shared, finite melee contact. Hidden receipts live with the source ledger, never on the weapon. */
public final class WeaponBloodDeposits {
    public static final int MAX_EVENTS = 256;
    private WeaponBloodDeposits() {}

    public static int contact(ServerPlayerEntity actor, ItemStack original, NbtCompound sourceRecord,
                              NbtCompound hit, boolean postmortem) {
        if (!actor.getServer().isOnThread() || !BloodTransferService.generating()
                || !ForensicConfig.current.transfer.weaponStains) return 0;
        // Do not stain a replacement item, restore a broken item or mutate an attack snapshot.
        if (original.isEmpty() ? !actor.getMainHandStack().isEmpty()
                : original != actor.getMainHandStack() && original != actor.getOffHandStack()) return 0;
        var ledger = sourceRecord.getCompoundOrEmpty("blood");
        long now = BloodTraceService.now(actor.getServer());
        if (!eligible(ledger, hit, postmortem, now)) return 0;
        String holder = original.isEmpty() ? BloodTransferService.holder(actor, "hands")
                : BloodTransferService.prepareWeapon(actor, original);
        return deposit(BloodTransferStore.get(actor.getServer()), ledger, hit, holder,
                sourceRecord.getString("id", "unknown"), postmortem, now);
    }

    private static boolean eligible(NbtCompound ledger, NbtCompound hit, boolean postmortem, long now) {
        if (hit.getString("id", "").isEmpty() || !Float.isFinite(hit.getFloat("amount", 0))
                || hit.getFloat("amount", 0) <= 0) return false;
        var events = ledger.getListOrEmpty("weapon_events");
        if (events.size() >= MAX_EVENTS || events.contains(NbtString.of(hit.getString("id", "")))) return false;
        if (!postmortem) return ledger.getInt("impact_remaining", 0) > 0;
        return BloodRules.active(ledger, now) && BloodRules.remaining(ledger, hit.getString("part", "unknown")) > 0;
    }

    /** Data-layer transaction; caller validates actual melee contact, weapon and bleeding profile. */
    public static int deposit(BloodTransferStore store, NbtCompound ledger, NbtCompound hit,
                              String holder, String source, boolean postmortem, long now) {
        if (holder.isEmpty() || !eligible(ledger, hit, postmortem, now) || store.visible(holder) >= 16) return 0;
        long until = now + ForensicConfig.current.transfer.wetTicks;
        if (postmortem) until = Math.min(until, ledger.getLong("pool_start", now) + ForensicConfig.current.blood.poolTicks);
        if (!store.add(holder, source.isEmpty() ? "unknown" : source, 1, until, now)) return 0;
        if (postmortem) BloodRules.consume(ledger, hit.getString("part", "unknown"), 1);
        else ledger.putInt("impact_remaining", ledger.getInt("impact_remaining", 0) - 1);
        NbtList events = ledger.getListOrEmpty("weapon_events");
        events.add(NbtString.of(hit.getString("id", ""))); ledger.put("weapon_events", events);
        return 1;
    }
}
