package com.kuilunfuzhe.monvhua.renderer.commandpanel;

import com.kuilunfuzhe.monvhua.item.commandpanel.CommandPanelItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/** Item renderer with context-specific sizing and first-person floating placement. */
public final class CommandPanelItemGeoRenderer extends GeoItemRenderer<CommandPanelItem> {
    /** Vanilla map-style transition and placement constants. */
    private static final float VIEW_ANGLE = 90.0F;
    private static final float MAP_TILT_RANGE = 70.0F;
    public CommandPanelItemGeoRenderer(GeoModel<CommandPanelItem> model) {
        super(model);
    }

    @Override
    public void scaleModelForRender(GeoRenderState state, float width, float height,
                                    MatrixStack matrices, BakedGeoModel model, boolean reRender) {
        float scale = scaleFor(state);
        float previousWidth = this.scaleWidth;
        float previousHeight = this.scaleHeight;
        this.scaleWidth = scale;
        this.scaleHeight = scale;
        super.scaleModelForRender(state, width, height, matrices, model, reRender);
        this.scaleWidth = previousWidth;
        this.scaleHeight = previousHeight;
    }

    @Override
    public void adjustPositionForRender(GeoRenderState state, MatrixStack matrices,
                                        BakedGeoModel model, boolean reRender) {
        super.adjustPositionForRender(state, matrices, model, reRender);
        if (reRender) return;

        ItemDisplayContext context = perspective(state);
        if (context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) {
            applyMapTransform(matrices, state, 0.10D);
        } else if (context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
            applyMapTransform(matrices, state, -0.10D);
        } else {
            // Do not carry a first-person map pose into GUI, ground, or third-person renders.
        }
    }

    private static void applyMapTransform(MatrixStack matrices, GeoRenderState state, double handX) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        float partialTick = state.getOrDefaultGeckolibData(DataTickets.PARTIAL_TICK, 0.0F);
        // Match the vanilla map curve so a level view remains near the upright pose.
        float pitch = client.player.getLerpedPitch(partialTick);
        float progress = MathHelper.clamp(1.0F - pitch / 45.0F + 0.1F, 0.0F, 1.0F);
        float mapAngle = 0.5F - 0.5F * MathHelper.cos(progress * MathHelper.PI);
        float target = VIEW_ANGLE - MAP_TILT_RANGE * mapAngle;

        // The model's screen lies in its XZ plane: 0 degrees is flat and
        // 90 degrees faces the camera. The item JSON supplies no first-person
        // rotation, so this is the sole source of the held-panel pose.
        matrices.translate(handX, -0.18D, -0.28D);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(target));
    }

    private static float scaleFor(GeoRenderState state) {
        return switch (perspective(state)) {
            case FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND -> 0.18F;
            case GUI -> 0.13F;
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> 0.5F;
            case GROUND, FIXED -> 0.5F;
            default -> 0.10F;
        };
    }

    private static ItemDisplayContext perspective(GeoRenderState state) {
        return state.getOrDefaultGeckolibData(DataTickets.ITEM_RENDER_PERSPECTIVE, ItemDisplayContext.NONE);
    }
}
