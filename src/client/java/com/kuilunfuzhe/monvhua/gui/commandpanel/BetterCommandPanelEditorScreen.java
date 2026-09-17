package com.kuilunfuzhe.monvhua.gui.commandpanel;

import bettercommandblockui.main.ui.screen.AbstractBetterCommandBlockScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.CommandBlockExecutor;

import java.util.function.Consumer;

final class BetterCommandPanelEditorScreen extends AbstractBetterCommandBlockScreen {
    private final Screen parent;
    private final Consumer<String> saved;

    BetterCommandPanelEditorScreen(Screen parent, String command, Consumer<String> saved) {
        this.parent = parent;
        this.saved = saved;
        this.commandExecutor = new LocalCommandExecutor(command);
    }

    @Override
    protected void syncSettingsToServer(CommandBlockExecutor executor) {
        String command = consoleCommandTextField.getText();
        executor.setCommand(command);
        saved.accept(command);
    }

    @Override
    protected void commitAndClose() {
        commit();
        if (client != null) client.setScreen(parent);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static final class LocalCommandExecutor extends CommandBlockExecutor {
        LocalCommandExecutor(String command) {
            setCommand(command == null ? "" : command);
            setTrackOutput(false);
        }

        @Override public ServerWorld getWorld() { return null; }
        @Override public void markDirty() {}
        @Override public Vec3d getPos() { return Vec3d.ZERO; }
        @Override public ServerCommandSource getSource() { return null; }
        @Override public boolean isEditable() { return true; }
    }
}
