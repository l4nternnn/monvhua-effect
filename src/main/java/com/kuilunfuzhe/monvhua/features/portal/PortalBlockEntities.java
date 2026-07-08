package com.kuilunfuzhe.monvhua.features.portal;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class PortalBlockEntities {
    public static final BlockEntityType<PortalBlockEntity> PORTAL_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(MonvhuaMod.MOD_ID, "light_portal"),
            FabricBlockEntityTypeBuilder.create(PortalBlockEntity::new, PortalItems.PORTAL_BLOCK).build()
    );

    private PortalBlockEntities() {
    }

    public static void initialize() {
    }
}
