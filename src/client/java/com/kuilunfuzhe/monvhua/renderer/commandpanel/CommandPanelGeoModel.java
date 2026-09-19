package com.kuilunfuzhe.monvhua.renderer.commandpanel;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.item.commandpanel.CommandPanelItem;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/** GeckoLib model for the converted Blockbench switch. */
public final class CommandPanelGeoModel extends GeoModel<CommandPanelItem> {
    private static final Identifier MODEL = Identifier.of(MonvhuaMod.MOD_ID, "geckolib/models/switch.geo.json");
    private static final Identifier TEXTURE = Identifier.of(MonvhuaMod.MOD_ID, "textures/item/switch.png");
    private static final Identifier ANIMATIONS = Identifier.of(MonvhuaMod.MOD_ID, "geckolib/animations/command_panel/switch.animation.json");

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(CommandPanelItem animatable) {
        return ANIMATIONS;
    }
}
