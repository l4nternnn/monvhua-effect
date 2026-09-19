package com.kuilunfuzhe.monvhua.client.commandpanel;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.PlayerEntity;
import com.kuilunfuzhe.monvhua.item.commandpanel.CommandPanelItems;
import com.kuilunfuzhe.monvhua.item.commandpanel.CommandPanelItem;

/** Per-client state for the UI plane attached to the switch model. */
public final class CommandPanelItemUiState {
    private static boolean visible;
    private static int selected;

    private CommandPanelItemUiState() {}

    public static boolean isVisible() {
        return visible;
    }

    public static int selectedIndex() {
        return selected;
    }

    public static void toggle() {
        visible = !visible;
    }

    public static void selectPrevious() {
        selected--;
    }

    public static void selectNext() {
        selected++;
    }

    public static void trigger(PlayerEntity player, String animation) {
        ItemStack stack = player.getMainHandStack();
        if (!stack.isOf(CommandPanelItems.COMMAND_PANEL)) return;
        CommandPanelItem.requestAnimation(animation);
    }

    public static void tick(MinecraftClient client) {
        if (client.player == null || !client.player.getMainHandStack().isOf(CommandPanelItems.COMMAND_PANEL)) {
            visible = false;
            selected = 0;
        }
    }

    public static boolean shouldRender(ItemStack stack, boolean firstPerson) {
        return visible && firstPerson && stack.isOf(CommandPanelItems.COMMAND_PANEL);
    }
}
