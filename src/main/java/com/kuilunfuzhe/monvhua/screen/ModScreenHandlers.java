package com.kuilunfuzhe.monvhua.screen;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

/**
 * 模组ScreenHandler类型注册中心，负责注册所有自定义ScreenHandlerType到游戏注册表。
 */
public class ModScreenHandlers {
    /** 方块部件ScreenHandler类型，客户端构造器引用为{@code BodyPartScreenHandler::new} */
    public static final ScreenHandlerType<BodyPartScreenHandler> BODY_PART_SCREEN_HANDLER =
            new ScreenHandlerType<>(BodyPartScreenHandler::new, FeatureFlags.VANILLA_FEATURES);

    /**
     * 注册所有模组ScreenHandler类型到游戏注册表。在模组初始化时调用。
     */
    public static void initialize() {
        Registry.register(Registries.SCREEN_HANDLER,
                Identifier.of("monvhua", "body_part"),
                BODY_PART_SCREEN_HANDLER);
    }
}