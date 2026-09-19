package com.kuilunfuzhe.monvhua.renderer.commandpanel;

import com.kuilunfuzhe.monvhua.item.commandpanel.CommandPanelItem;
import com.kuilunfuzhe.monvhua.item.commandpanel.CommandPanelItems;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/** Client-only registration for the GeckoLib special item renderer. */
public final class CommandPanelItemRenderer {
    private CommandPanelItemRenderer() {}

    public static void register() {
        CommandPanelItem item = (CommandPanelItem) CommandPanelItems.COMMAND_PANEL;
        item.setClientRenderProvider(new GeoRenderProvider() {
            private GeoItemRenderer<CommandPanelItem> renderer;

            @Override
            public GeoItemRenderer<?> getGeoItemRenderer() {
                if (renderer == null) {
                    // Blockbench exported pixel coordinates; GeckoLib's renderer uses model units.
                    // Convert the 0..16 pixel range back to one Minecraft block.
                    renderer = new CommandPanelItemGeoRenderer(new CommandPanelGeoModel());
                    renderer.addRenderLayer(new CommandPanelUiLayer(renderer));
                }
                return renderer;
            }
        });
    }
}
