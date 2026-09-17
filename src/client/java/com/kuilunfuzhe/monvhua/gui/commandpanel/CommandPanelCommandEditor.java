package com.kuilunfuzhe.monvhua.gui.commandpanel;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.Screen;

import java.util.function.Consumer;

final class CommandPanelCommandEditor {
    private CommandPanelCommandEditor() {}

    static Screen open(Screen parent, String command, Consumer<String> saved) {
        if (FabricLoader.getInstance().isModLoaded("bettercommandblockui")) {
            try {
                return new BetterCommandPanelEditorScreen(parent, command, saved);
            } catch (LinkageError | RuntimeException ignored) {
                // A version-incompatible optional editor falls back to the vanilla widget.
            }
        }
        return new CommandPanelFallbackEditorScreen(parent, command, saved);
    }
}
