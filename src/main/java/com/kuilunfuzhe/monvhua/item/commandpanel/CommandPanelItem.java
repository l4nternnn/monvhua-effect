package com.kuilunfuzhe.monvhua.item.commandpanel;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animatable.processing.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class CommandPanelItem extends Item implements GeoItem {
    private static volatile Object clientRenderProvider;
    private static volatile String requestedAnimation;
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public CommandPanelItem(Settings settings) { super(settings); }

    public static void requestAnimation(String animation) {
        requestedAnimation = animation;
    }

    private static String consumeAnimation() {
        String animation = requestedAnimation;
        requestedAnimation = null;
        return animation;
    }

    /** Installed from the client source set so dedicated servers never load renderer classes. */
    public static void setClientRenderProvider(Object provider) {
        clientRenderProvider = provider;
    }

    @Override
    public Object getRenderProvider() {
        return clientRenderProvider;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("command_panel_buttons", state -> {
                    String requested = consumeAnimation();
                    if (requested != null) {
                        // Apply the request directly in this controller callback. The
                        // trigger queue can otherwise be consumed by another render context.
                        state.controller().setAnimation(RawAnimation.begin().thenPlay(requested));
                    }
                    return software.bernie.geckolib.animation.PlayState.CONTINUE;
                })
                .triggerableAnim("left_switch", RawAnimation.begin().thenPlay("left_switch"))
                .triggerableAnim("right_switch", RawAnimation.begin().thenPlay("right_switch"))
                .triggerableAnim("press", RawAnimation.begin().thenPlay("press"))
                .receiveTriggeredAnimations());
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    @Override public ActionResult use(World world, PlayerEntity user, Hand hand) {
        return ActionResult.CONSUME;
    }
}
