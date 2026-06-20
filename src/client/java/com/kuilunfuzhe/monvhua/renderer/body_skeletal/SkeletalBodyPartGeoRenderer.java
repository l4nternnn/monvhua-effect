package com.kuilunfuzhe.monvhua.renderer.body_skeletal;

import com.kuilunfuzhe.monvhua.features.block.body_skeletal.SkeletalBodyPartBlockEntity;
import net.minecraft.client.util.math.MatrixStack;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public class SkeletalBodyPartGeoRenderer<T extends SkeletalBodyPartBlockEntity> extends GeoBlockRenderer<T> {
    public SkeletalBodyPartGeoRenderer() {
        super(new SkeletalBodyPartGeoModel<>());
        this.addRenderLayer(new SkeletalOuterLayerVoxelRenderLayer<>(this));
    }

    @Override
    public GeoRenderState captureDefaultRenderState(T animatable, Void relatedObject, GeoRenderState renderState, float partialTick) {
        GeoRenderState state = super.captureDefaultRenderState(animatable, relatedObject, renderState, partialTick);
        state.addGeckolibData(SkeletalBodyPartGeoModel.BLOCK_ENTITY, animatable);
        return state;
    }

    @Override
    public void adjustPositionForRender(GeoRenderState renderState, MatrixStack poseStack, BakedGeoModel model, boolean isReRender) {
        super.adjustPositionForRender(renderState, poseStack, model, isReRender);
        if (!isReRender) {
            poseStack.translate(0.0D, -0.5D, 0.0D);
        }
    }
}
