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
    private static final float MAP_TILT_RANGE = 75.0F;
    /** Static screen orientation, independent of the player's pitch. */
    private static final float MODEL_SCREEN_HEADING = 180.0F;
    private static final float FIRST_PERSON_SCALE = 1.00F;
    private static final float PIVOT_FROM_BOTTOM = 0.25F;
    private static final float MODEL_TOP_Z = -4.0F;
    private static final float MODEL_BOTTOM_Z = 4.05F;
    private static final double FIRST_PERSON_X_OFFSET = 0.575D;
    private static final double FOLDED_Y_OFFSET = -0.02D;
    private static final double VIEW_Y_OFFSET = 0.32D;
    private static final double FIRST_PERSON_Z_OFFSET = -0.28D;
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
            applyMapTransform(matrices, state, -FIRST_PERSON_X_OFFSET);
        } else if (context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
            applyMapTransform(matrices, state, FIRST_PERSON_X_OFFSET);
        }
    }

    private static void applyMapTransform(MatrixStack matrices, GeoRenderState state, double handX) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        float partialTick = state.getOrDefaultGeckolibData(DataTickets.PARTIAL_TICK, 0.0F);
        // Vanilla map curve: level/upward views use the flat end; looking down
        // reaches the viewing end between player pitches 4.5 and 49.5 degrees.
        float pitch = client.player.getLerpedPitch(partialTick);
        float progress = MathHelper.clamp(1.0F - pitch / 45.0F + 0.1F, 0.0F, 1.0F);
        float mapAngle = 0.5F - 0.5F * MathHelper.cos(progress * MathHelper.PI);
        float target = VIEW_ANGLE - MAP_TILT_RANGE * mapAngle;
        float viewProgress = 1.0F - mapAngle;
        double yOffset = MathHelper.lerp(viewProgress, FOLDED_Y_OFFSET, VIEW_Y_OFFSET);

        // MatrixStack post-multiplies: local screen orientation is applied to
        // vertices before the dynamic tilt, without rotating the held position.
        matrices.translate(handX, yOffset, FIRST_PERSON_Z_OFFSET);
        double pivotZ = firstPersonPivotZ();
        matrices.translate(0.0D, 0.0D, pivotZ);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(target));
        matrices.translate(0.0D, 0.0D, -pivotZ);
        applyModelScreenOrientation(matrices);
    }

    private static double firstPersonPivotZ() {
        float modelPivotZ = MODEL_BOTTOM_Z
                - (MODEL_BOTTOM_Z - MODEL_TOP_Z) * PIVOT_FROM_BOTTOM;
        return modelPivotZ / 16.0D * FIRST_PERSON_SCALE;
    }

    private static void applyModelScreenOrientation(MatrixStack matrices) {
        // The top marker is on +Z; GeckoLib bakes the right controls onto -X.
        // A local Y half-turn sends these to -Z/+X while preserving the +Y
        // screen normal. Positive X tilt then puts the marker above the screen.
        // Keep this in the shared model matrix so the attached UI follows it.
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(MODEL_SCREEN_HEADING));
    }

    private static float scaleFor(GeoRenderState state) {
        return switch (perspective(state)) {
            case FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND -> FIRST_PERSON_SCALE;
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
