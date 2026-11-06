package com.kuilunfuzhe.monvhua.features.forensics;

import net.minecraft.nbt.*;
import java.util.*;

public interface ProjectileEvidence {
    NbtCompound monvhua$getEvidence();
    default net.minecraft.util.math.Vec3d monvhua$impactVelocity() {
        return ((net.minecraft.entity.projectile.ProjectileEntity) this).getVelocity();
    }
    static WeaponProfiles.Selection selection(NbtCompound data) {
        if (!data.contains("profile")) return null;
        List<WeaponProfiles.Component> components = new ArrayList<>();
        for (NbtElement element : data.getListOrEmpty("components")) if (element instanceof NbtCompound c)
            components.add(new WeaponProfiles.Component(c.getString("kind", "unknown"), c.getDouble("weight", 1)));
        var defaults = new WeaponProfiles.Mode("projectile", List.copyOf(components));
        return new WeaponProfiles.Selection(data.getString("profile", ""), data.getString("mode", ""),
                new WeaponProfiles.Mode("projectile", List.copyOf(components),
                        data.getBoolean("external_bleeding", defaults.externalBleeding()),
                        data.getDouble("bleeding_factor", defaults.bleedingFactor())));
    }
}
