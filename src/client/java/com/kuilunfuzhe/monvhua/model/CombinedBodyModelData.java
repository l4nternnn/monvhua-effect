package com.kuilunfuzhe.monvhua.model;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModelPartNames;

public class CombinedBodyModelData {
    private CombinedBodyModelData() {
    }

    public static TexturedModelData getDefaultTexturedModelData() {
        return getTexturedModelData(false);
    }

    public static TexturedModelData getSlimTexturedModelData() {
        return getTexturedModelData(true);
    }

    private static TexturedModelData getTexturedModelData(boolean slim) {
        ModelData modelData = new ModelData();
        ModelPartData root = modelData.getRoot();

        ModelPartData head = root.addChild(EntityModelPartNames.HEAD,
                ModelPartBuilder.create().uv(0, 0).cuboid(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F),
                ModelTransform.origin(0.0F, 0.0F, 0.0F));
        head.addChild(EntityModelPartNames.HAT,
                ModelPartBuilder.create().uv(32, 0).cuboid(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new Dilation(0.5F)),
                ModelTransform.origin(0.0F, 0.0F, 0.0F));

        ModelPartData body = root.addChild(EntityModelPartNames.BODY,
                ModelPartBuilder.create().uv(16, 16).cuboid(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F),
                ModelTransform.origin(0.0F, 0.0F, 0.0F));
        body.addChild("jacket",
                ModelPartBuilder.create().uv(16, 32).cuboid(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, new Dilation(0.25F)),
                ModelTransform.origin(0.0F, 0.0F, 0.0F));

        if (slim) {
            ModelPartData rightArm = root.addChild(EntityModelPartNames.RIGHT_ARM,
                    ModelPartBuilder.create().uv(40, 16).cuboid(-2.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F),
                    ModelTransform.origin(-5.0F, 2.5F, 0.0F));
            rightArm.addChild("right_sleeve",
                    ModelPartBuilder.create().uv(40, 32).cuboid(-2.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, new Dilation(0.25F)),
                    ModelTransform.origin(0.0F, 0.0F, 0.0F));

            ModelPartData leftArm = root.addChild(EntityModelPartNames.LEFT_ARM,
                    ModelPartBuilder.create().uv(32, 48).cuboid(-1.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F),
                    ModelTransform.origin(5.0F, 2.5F, 0.0F));
            leftArm.addChild("left_sleeve",
                    ModelPartBuilder.create().uv(48, 48).cuboid(-1.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, new Dilation(0.25F)),
                    ModelTransform.origin(0.0F, 0.0F, 0.0F));
        } else {
            ModelPartData rightArm = root.addChild(EntityModelPartNames.RIGHT_ARM,
                    ModelPartBuilder.create().uv(40, 16).cuboid(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                    ModelTransform.origin(-5.0F, 2.0F, 0.0F));
            rightArm.addChild("right_sleeve",
                    ModelPartBuilder.create().uv(40, 32).cuboid(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new Dilation(0.25F)),
                    ModelTransform.origin(0.0F, 0.0F, 0.0F));

            ModelPartData leftArm = root.addChild(EntityModelPartNames.LEFT_ARM,
                    ModelPartBuilder.create().uv(32, 48).cuboid(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                    ModelTransform.origin(5.0F, 2.0F, 0.0F));
            leftArm.addChild("left_sleeve",
                    ModelPartBuilder.create().uv(48, 48).cuboid(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new Dilation(0.25F)),
                    ModelTransform.origin(0.0F, 0.0F, 0.0F));
        }

        ModelPartData rightLeg = root.addChild(EntityModelPartNames.RIGHT_LEG,
                ModelPartBuilder.create().uv(0, 16).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                ModelTransform.origin(-1.9F, 12.0F, 0.0F));
        rightLeg.addChild("right_pants",
                ModelPartBuilder.create().uv(0, 32).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new Dilation(0.25F)),
                ModelTransform.origin(0.0F, 0.0F, 0.0F));

        ModelPartData leftLeg = root.addChild(EntityModelPartNames.LEFT_LEG,
                ModelPartBuilder.create().uv(16, 48).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                ModelTransform.origin(1.9F, 12.0F, 0.0F));
        leftLeg.addChild("left_pants",
                ModelPartBuilder.create().uv(0, 48).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new Dilation(0.25F)),
                ModelTransform.origin(0.0F, 0.0F, 0.0F));

        root.addChild("cloak",
                ModelPartBuilder.create().uv(0, 0).cuboid(-5.0F, 0.0F, -1.0F, 10.0F, 16.0F, 1.0F),
                ModelTransform.origin(0.0F, 0.0F, 0.0F));

        return TexturedModelData.of(modelData, 64, 64);
    }
}
