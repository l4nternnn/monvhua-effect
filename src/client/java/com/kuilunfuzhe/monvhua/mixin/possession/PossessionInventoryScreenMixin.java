package com.kuilunfuzhe.monvhua.mixin.possession;

import com.kuilunfuzhe.monvhua.features.possession.PossessionClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class PossessionInventoryScreenMixin {
    @Shadow protected int x;
    @Shadow protected int y;
    @Shadow protected ScreenHandler handler;

    @Inject(method = "render", at = @At("TAIL"))
    private void monvhua$renderTargetInventory(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!PossessionClient.isActive() || client.player == null || !((Object) this instanceof InventoryScreen)) {
            return;
        }

        ItemStack hovered = ItemStack.EMPTY;
        for (Slot slot : handler.slots) {
            if (slot.inventory != client.player.getInventory() || slot.getIndex() < 0) {
                continue;
            }
            int slotX = x + slot.x;
            int slotY = y + slot.y;
            ItemStack stack = PossessionClient.getTargetInventoryStack(slot.getIndex());
            context.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, 0xFF1F1F25);
            if (!stack.isEmpty()) {
                context.drawItem(stack, slotX, slotY);
                context.drawStackOverlay(client.textRenderer, stack, slotX, slotY);
            }
            if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                hovered = stack;
            }
        }
        if (!hovered.isEmpty()) {
            context.drawItemTooltip(client.textRenderer, hovered, mouseX, mouseY);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void monvhua$protectControllerInventory(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!PossessionClient.isActive() || client.player == null || !((Object) this instanceof InventoryScreen)) {
            return;
        }
        for (Slot slot : handler.slots) {
            if (slot.inventory != client.player.getInventory()
                    || mouseX < x + slot.x || mouseX >= x + slot.x + 16
                    || mouseY < y + slot.y || mouseY >= y + slot.y + 16) {
                continue;
            }
            if (slot.getIndex() >= 0 && slot.getIndex() < 9) {
                PossessionClient.requestTargetSlot(slot.getIndex());
            }
            cir.setReturnValue(true);
            return;
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void monvhua$redirectPossessionHotbarKeys(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!PossessionClient.isActive() || !((Object) this instanceof InventoryScreen)) {
            return;
        }
        for (int slot = 0; slot < client.options.hotbarKeys.length; slot++) {
            if (client.options.hotbarKeys[slot].matchesKey(keyCode, scanCode)) {
                PossessionClient.requestTargetSlot(slot);
                cir.setReturnValue(true);
                return;
            }
        }
    }
}
