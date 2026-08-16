package com.kuilunfuzhe.monvhua.features.activity.emotion;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screen.ChatScreen;

public final class EmotionPickerClient {
    private static boolean initialized;

    private EmotionPickerClient() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof ChatScreen chatScreen)) {
                return;
            }
            EmotionPickerOverlay overlay = new EmotionPickerOverlay(chatScreen);
            ScreenEvents.afterRender(screen).register((ignored, context, mouseX, mouseY, tickDelta) ->
                    overlay.render(context, mouseX, mouseY));
            ScreenMouseEvents.allowMouseClick(screen).register((ignored, mouseX, mouseY, button) ->
                    !overlay.mouseClicked(mouseX, mouseY, button));
            ScreenMouseEvents.allowMouseRelease(screen).register((ignored, mouseX, mouseY, button) ->
                    !overlay.mouseReleased(mouseX, mouseY, button));
            ScreenMouseEvents.allowMouseDrag(screen).register((ignored, mouseX, mouseY, button, dx, dy) ->
                    !overlay.mouseDragged(mouseX, mouseY, button));
            ScreenMouseEvents.allowMouseScroll(screen).register((ignored, mouseX, mouseY, horizontal, vertical) ->
                    !overlay.mouseScrolled(mouseX, mouseY, vertical));
        });
    }
}
