package com.kuilunfuzhe.monvhua.renderer.commandpanel;

import com.kuilunfuzhe.monvhua.item.commandpanel.CommandPanelItem;
import com.kuilunfuzhe.monvhua.item.commandpanel.CommandPanelItems;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/** Client-only registration for the GeckoLib special item renderer. */
public final class CommandPanelItemRenderer {
    private CommandPanelItemRenderer() {}

    public static void register() {
        PanelUiTexture.initialize();
        CommandPanelItem item = (CommandPanelItem) CommandPanelItems.COMMAND_PANEL;
        item.setClientRenderProvider(new GeoRenderProvider() {
            private GeoItemRenderer<CommandPanelItem> renderer;

            @Override
            public GeoItemRenderer<?> getGeoItemRenderer() {
                if (renderer == null) {
                    // GeckoLib handles pixel-to-model conversion internally.
                    renderer = new CommandPanelItemGeoRenderer(new CommandPanelGeoModel());
                    renderer.addRenderLayer(new CommandPanelUiLayer(renderer));
                }
                return renderer;
            }
        });
    }
}
