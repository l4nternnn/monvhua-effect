package com.kuilunfuzhe.monvhua.features.dissolve;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class DissolveParticleTypes {
    public static final SimpleParticleType DISSOLVE_PIXEL = FabricParticleTypes.simple(false);

    private static boolean registered;

    private DissolveParticleTypes() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        Registry.register(
                Registries.PARTICLE_TYPE,
                Identifier.of(MonvhuaMod.MOD_ID, "dissolve_pixel"),
                DISSOLVE_PIXEL
        );
    }
}
