package com.kuilunfuzhe.monvhua.features.cosmic_box;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.item.cosmic_box.CosmicBoxItems;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class CosmicBoxBlockEntities {
    public static final BlockEntityType<CosmicBoxBlockEntity> COSMIC_BOX_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(MonvhuaMod.MOD_ID, "cosmic_box"),
            FabricBlockEntityTypeBuilder.create(
                    CosmicBoxBlockEntity::new,
                    CosmicBoxItems.COSMIC_BOX_BLOCK,
                    CosmicBoxItems.COSMIC_BOX_RENDER_BLOCK
            ).build()
    );

    private CosmicBoxBlockEntities() {
    }

    public static void initialize() {
    }
}
