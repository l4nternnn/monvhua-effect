package com.kuilunfuzhe.monvhua.features.possession;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public class PossessionInventoryScreen extends Screen {
    private static final int WIDTH = 208;
    private static final int HEIGHT = 132;
    private static final int SLOT = 18;
    private static final int ITEM_OFFSET = 1;

    protected PossessionInventoryScreen() {
        super(Text.literal("Possessed Inventory"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        super.render(context, mouseX, mouseY, deltaTicks);

        MinecraftClient client = MinecraftClient.getInstance();
        int left = (this.width - WIDTH) / 2;
        int top = (this.height - HEIGHT) / 2;

        context.fill(left, top, left + WIDTH, top + HEIGHT, 0xDD101018);
        context.fill(left + 1, top + 1, left + WIDTH - 1, top + HEIGHT - 1, 0xCC20202A);
        context.drawText(this.textRenderer, this.title, left + 8, top + 7, 0xFFE6D7FF, false);
        context.drawText(this.textRenderer, Text.literal("read-only"), left + WIDTH - 58, top + 7, 0xFFAAAAAA, false);

        ItemStack hovered = ItemStack.EMPTY;
        hovered = drawEquipmentColumn(context, mouseX, mouseY, left + 10, top + 25, hovered);
        hovered = drawMainInventory(context, mouseX, mouseY, left + 44, top + 25, hovered);
        hovered = drawHotbar(context, mouseX, mouseY, left + 44, top + 88, hovered);
        hovered = drawExtraEquipment(context, mouseX, mouseY, left + 10, top + 106, hovered);

        if (!hovered.isEmpty()) {
            context.drawItemTooltip(client.textRenderer, hovered, mouseX, mouseY);
        }
    }

    private ItemStack drawEquipmentColumn(DrawContext context, int mouseX, int mouseY, int x, int y, ItemStack hovered) {
        int[] slots = {39, 38, 37, 36};
        for (int i = 0; i < slots.length; i++) {
            hovered = drawSlot(context, mouseX, mouseY, x, y + i * SLOT, slots[i], false, hovered);
        }
        return hovered;
    }

    private ItemStack drawMainInventory(DrawContext context, int mouseX, int mouseY, int x, int y, ItemStack hovered) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = 9 + row * 9 + col;
                hovered = drawSlot(context, mouseX, mouseY, x + col * SLOT, y + row * SLOT, slot, false, hovered);
            }
        }
        return hovered;
    }

    private ItemStack drawHotbar(DrawContext context, int mouseX, int mouseY, int x, int y, ItemStack hovered) {
        int selectedSlot = PossessionClient.getTargetSelectedSlot();
        for (int col = 0; col < 9; col++) {
            hovered = drawSlot(context, mouseX, mouseY, x + col * SLOT, y, col, col == selectedSlot, hovered);
        }
        return hovered;
    }

    private ItemStack drawExtraEquipment(DrawContext context, int mouseX, int mouseY, int x, int y, ItemStack hovered) {
        hovered = drawSlot(context, mouseX, mouseY, x, y, 40, false, hovered);
        hovered = drawSlot(context, mouseX, mouseY, x + SLOT, y, 41, false, hovered);
        return drawSlot(context, mouseX, mouseY, x + SLOT * 2, y, 42, false, hovered);
    }

    private ItemStack drawSlot(DrawContext context, int mouseX, int mouseY, int x, int y, int slot, boolean selected, ItemStack hovered) {
        int border = selected ? 0xFFFF55FF : 0xFF6A6A78;
        context.fill(x, y, x + SLOT, y + SLOT, border);
        context.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0xFF2B2B35);

        ItemStack stack = PossessionClient.getTargetInventoryStack(slot);
        if (!stack.isEmpty()) {
            context.drawItem(stack, x + ITEM_OFFSET, y + ITEM_OFFSET);
            context.drawStackOverlay(this.textRenderer, stack, x + ITEM_OFFSET, y + ITEM_OFFSET);
        }

        if (mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT && !stack.isEmpty()) {
            context.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0x55FFFFFF);
            return stack;
        }
        return hovered;
    }
}
