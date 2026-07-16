package com.kuilunfuzhe.monvhua.features.possession;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;

public final class PossessionHotbarHud {
    private static final int SLOT_SIZE = 20;
    private static final int ITEM_OFFSET = 2;
    private static boolean registered = false;

    private PossessionHotbarHud() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        HudRenderCallback.EVENT.register(PossessionHotbarHud::render);
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!PossessionClient.isActive()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) {
            return;
        }

        int startX = client.getWindow().getScaledWidth() / 2 - 90;
        int y = client.getWindow().getScaledHeight() - 52;
        int selectedSlot = PossessionClient.getTargetSelectedSlot();

        context.fill(startX - 3, y - 3, startX + 9 * SLOT_SIZE + 3, y + SLOT_SIZE + 3, 0x88000000);
        for (int i = 0; i < 9; i++) {
            int x = startX + i * SLOT_SIZE;
            drawSlotFrame(context, x, y, i == selectedSlot);
            ItemStack stack = PossessionClient.getTargetHotbarStack(i);
            if (!stack.isEmpty()) {
                context.drawItem(stack, x + ITEM_OFFSET, y + ITEM_OFFSET);
                context.drawStackOverlay(client.textRenderer, stack, x + ITEM_OFFSET, y + ITEM_OFFSET);
            }
        }
    }

    private static void drawSlotFrame(DrawContext context, int x, int y, boolean selected) {
        int border = selected ? 0xFFFF55FF : 0xFF555555;
        int fill = selected ? 0x553C003C : 0x33000000;
        context.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, border);
        context.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, fill);
    }
}
