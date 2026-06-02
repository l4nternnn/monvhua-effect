package com.kuilunfuzhe.monvhua.event;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * 按键绑定处理器，负责注册本模组的自定义快捷键。
 * U键打开配置面板，V键执行标记/交互操作。
 */
public class KeyBindingHandler {
    /** U键 - 打开模组配置面板（仅创造模式可用） */
    public static KeyBinding configKey;
    /** V键 - 标记/交互按键，根据手持物品执行不同操作 */
    public static KeyBinding markKey;
    public static KeyBinding bodyPoseEditorKey;
    public static KeyBinding actionEditorKey;

    /**
     * 注册按键绑定到Minecraft键位系统。
     * 两个按键均归入"monvhua"按键分类，可在原版 controls 界面中重新绑定。
     */
    public static void register() {
        configKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.monvhua.config", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_U, "category.monvhua"));
        markKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.monvhua.mark", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.monvhua"));
        bodyPoseEditorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.monvhua.body_pose_editor", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, "category.monvhua"));
        actionEditorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.monvhua.action_editor", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, "category.monvhua"));
    }
}
