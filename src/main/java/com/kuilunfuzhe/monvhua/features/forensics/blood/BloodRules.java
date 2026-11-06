package com.kuilunfuzhe.monvhua.features.forensics.blood;

import com.kuilunfuzhe.monvhua.features.forensics.*;
import net.minecraft.nbt.*;
import java.util.List;

/** Integer game budgets, never physiological volume. The ledger lives only on the server. */
public final class BloodRules {
    public static final List<String> PARTS = List.of("head", "torso", "left_arm", "right_arm", "left_leg", "right_leg");
    private BloodRules() {}
    public static boolean enabled() { return ForensicConfig.current.enabled && ForensicConfig.current.blood.enabled; }
    public static boolean generating() { return enabled() && ForensicConfig.current.blood.generate; }
    public static double factor(WeaponProfiles.Selection selection, String kinds) {
        if (selection != null) return selection.definition().externalBleeding() ? selection.definition().bleedingFactor() : 0;
        return kinds.equals("cut") || kinds.equals("puncture") ? 1 : 0;
    }
    public static int impactCost(double damage, double factor, int remaining) {
        if (!Double.isFinite(damage) || !Double.isFinite(factor) || damage <= 0 || factor <= 0) return 0;
        return Math.max(0, Math.min(remaining, Math.min(24, (int) Math.ceil(Math.min(12, damage) * 2 * factor))));
    }
    public static NbtCompound ledger(NbtCompound life) {
        if (!life.contains("blood")) {
            NbtCompound blood = new NbtCompound();
            blood.putInt("version", 1);
            blood.putInt("impact_remaining", ForensicConfig.current.blood.impactBudget);
            blood.put("recent", new NbtList());
            life.put("blood", blood);
        }
        return life.getCompoundOrEmpty("blood");
    }
    public static void remember(NbtCompound ledger, NbtCompound hit, long now) {
        NbtList recent = ledger.getListOrEmpty("recent");
        recent.removeIf(e -> !(e instanceof NbtCompound c) || now - c.getLong("time", 0) >= ForensicConfig.current.blood.recentTicks);
        NbtCompound entry = new NbtCompound();
        entry.putString("id", hit.getString("id", ""));
        entry.putString("part", hit.getString("part", "unknown"));
        entry.putLong("time", now);
        entry.putDouble("weight", hit.getFloat("amount", 0) * hit.getDouble("bleeding_factor", 0));
        recent.add(entry);
        while (recent.size() > 256) recent.remove(0);
        ledger.put("recent", recent);
    }
    public static void freeze(NbtCompound corpse, long now, boolean normalGravity) {
        // Missing ledger means a legacy corpse/life: never reconstruct from old wounds.
        if (!corpse.contains("blood")) return;
        NbtCompound ledger = corpse.getCompoundOrEmpty("blood");
        if (ledger.getBoolean("pool_initialized", false)) return;
        ledger.putBoolean("pool_initialized", true);
        ledger.putLong("pool_start", now);
        ledger.putBoolean("normal_gravity", normalGravity);
        double[] weights = new double[6];
        for (NbtElement element : ledger.getListOrEmpty("recent")) if (element instanceof NbtCompound hit) {
            double age = Math.max(0, now - hit.getLong("time", now));
            double contribution = Math.max(0, hit.getDouble("weight", 0)) * Math.max(0, 1 - age / ForensicConfig.current.blood.recentTicks);
            if (!Double.isFinite(contribution)) continue;
            int part = PARTS.indexOf(hit.getString("part", "unknown"));
            if (part >= 0) weights[part] += contribution;
            else for (int i = 0; i < 6; i++) weights[i] += contribution / 6;
        }
        double total = java.util.Arrays.stream(weights).sum();
        int budget = normalGravity ? (int) Math.ceil(Math.min(ForensicConfig.current.blood.poolBudget, total * 4)) : 0;
        NbtCompound balances = new NbtCompound();
        int assigned = 0;
        for (int i = 0; i < 6; i++) {
            int amount = total > 0 ? (int) Math.floor(budget * weights[i] / total) : 0;
            balances.putInt(PARTS.get(i), amount); assigned += amount;
        }
        for (int i = 0; assigned < budget; i = (i + 1) % 6) if (weights[i] > 0) {
            balances.putInt(PARTS.get(i), balances.getInt(PARTS.get(i), 0) + 1); assigned++;
        }
        ledger.putInt("pool_initial", budget);
        ledger.put("balances", balances);
    }
    public static int remaining(NbtCompound ledger, String part) {
        NbtCompound balances = ledger.getCompoundOrEmpty("balances");
        return part.equals("all") ? PARTS.stream().mapToInt(p -> Math.max(0, balances.getInt(p, 0))).sum()
                : PARTS.contains(part) ? Math.max(0, balances.getInt(part, 0)) : 0;
    }
    public static void consume(NbtCompound ledger, String part, int amount) {
        NbtCompound balances = ledger.getCompoundOrEmpty("balances");
        for (String p : part.equals("all") ? PARTS : List.of(part)) {
            int available = Math.max(0, balances.getInt(p, 0));
            int used = Math.min(available, Math.max(0, amount));
            balances.putInt(p, available - used); amount -= used;
        }
    }
    public static boolean active(NbtCompound ledger, long now) {
        return ledger.getBoolean("pool_initialized", false) && ledger.getBoolean("normal_gravity", false)
                && now >= ledger.getLong("pool_start", now) && now - ledger.getLong("pool_start", now) < ForensicConfig.current.blood.poolTicks;
    }
}
