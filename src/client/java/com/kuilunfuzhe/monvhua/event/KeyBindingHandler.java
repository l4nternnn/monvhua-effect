package com.kuilunfuzhe.monvhua.event;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class KeyBindingHandler {
    public static KeyBinding configKey;
    public static KeyBinding markKey;

    public static void register() {
        configKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.monvhua.config", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_U, "category.monvhua"));
        markKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.monvhua.mark", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.monvhua"));
    }
}
