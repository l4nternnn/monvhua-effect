package com.kuilunfuzhe.monvhua.model;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModelPartNames;

public class CombinedBodyModelData {
    public static final String WAIST = "waist";
    public static final String LEFT_FOREARM = "left_forearm";
    public static final String RIGHT_FOREARM = "right_forearm";
    public static final String LEFT_LOWER_LEG = "left_lower_leg";
    public static final String RIGHT_LOWER_LEG = "right_lower_leg";
    public static final String WAIST_BLEND = "waist_blend";
    public static final String LEFT_ELBOW_BLEND = "left_elbow_blend";
    public static final String RIGHT_ELBOW_BLEND = "right_elbow_blend";
    public static final String LEFT_KNEE_BLEND = "left_knee_blend";
    public static final String RIGHT_KNEE_BLEND = "right_knee_blend";
    public static final String WAIST_JACKET = "waist_jacket";
    public static final String LEFT_FOREARM_SLEEVE = "left_forearm_sleeve";
    public static final String RIGHT_FOREARM_SLEEVE = "right_forearm_sleeve";
    public static final String LEFT_LOWER_PANTS = "left_lower_pants";
    public static final String RIGHT_LOWER_PANTS = "right_lower_pants";

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
                ModelPartBuilder.create().uv(16, 16).cuboid(-4.0F, 0.0F, -2.0F, 8.0F, 6.0F, 4.0F),
                ModelTransform.origin(0.0F, 0.0F, 0.0F));
        body.addChild(EntityModelPartNames.JACKET,
                ModelPartBuilder.create().uv(16, 32).cuboid(-4.0F, 0.0F, -2.0F, 8.0F, 6.0F, 4.0F, new Dilation(0.25F)),
                ModelTransform.origin(0.0F, 0.0F, 0.0F));
        ModelPartData waist = body.addChild(WAIST,
                ModelPartBuilder.create().uv(16, 22).cuboid(-4.0F, 0.0F, -2.0F, 8.0F, 6.0F, 4.0F),
                ModelTransform.origin(0.0F, 6.0F, 0.0F));
        body.addChild(WAIST_BLEND,
                ModelPartBuilder.create().uv(16, 21).cuboid(-4.0F, -1.0F, -2.0F, 8.0F, 2.0F, 4.0F, new Dilation(0.01F)),
                ModelTransform.origin(0.0F, 6.0F, 0.0F));
        waist.addChild(WAIST_JACKET,
                ModelPartBuilder.create().uv(16, 38).cuboid(-4.0F, 0.0F, -2.0F, 8.0F, 6.0F, 4.0F, new Dilation(0.25F)),
                ModelTransform.origin(0.0F, 0.0F, 0.0F));

        if (slim) {
            addArm(root, EntityModelPartNames.RIGHT_ARM, RIGHT_FOREARM, RIGHT_ELBOW_BLEND, "right_sleeve", RIGHT_FOREARM_SLEEVE,
                    -5.0F, 2.5F, -2.0F, 3.0F, 40, 16, 40, 32);
            addArm(root, EntityModelPartNames.LEFT_ARM, LEFT_FOREARM, LEFT_ELBOW_BLEND, "left_sleeve", LEFT_FOREARM_SLEEVE,
                    5.0F, 2.5F, -1.0F, 3.0F, 32, 48, 48, 48);
        } else {
            addArm(root, EntityModelPartNames.RIGHT_ARM, RIGHT_FOREARM, RIGHT_ELBOW_BLEND, "right_sleeve", RIGHT_FOREARM_SLEEVE,
                    -5.0F, 2.0F, -3.0F, 4.0F, 40, 16, 40, 32);
            addArm(root, EntityModelPartNames.LEFT_ARM, LEFT_FOREARM, LEFT_ELBOW_BLEND, "left_sleeve", LEFT_FOREARM_SLEEVE,
                    5.0F, 2.0F, -1.0F, 4.0F, 32, 48, 48, 48);
        }

        addLeg(root, EntityModelPartNames.RIGHT_LEG, RIGHT_LOWER_LEG, RIGHT_KNEE_BLEND, "right_pants", RIGHT_LOWER_PANTS,
                -1.9F, 0, 16, 0, 32);
        addLeg(root, EntityModelPartNames.LEFT_LEG, LEFT_LOWER_LEG, LEFT_KNEE_BLEND, "left_pants", LEFT_LOWER_PANTS,
                1.9F, 16, 48, 0, 48);

        root.addChild("cloak", ModelPartBuilder.create(), ModelTransform.origin(0.0F, 0.0F, 0.0F));

        return TexturedModelData.of(modelData, 64, 64);
    }

    private static void addArm(ModelPartData root, String armName, String forearmName, String elbowBlendName,
                               String sleeveName, String forearmSleeveName,
                               float originX, float originY, float cubeMinX, float width,
                               int skinU, int skinV, int sleeveU, int sleeveV) {
        ModelPartData arm = root.addChild(armName,
                ModelPartBuilder.create().uv(skinU, skinV).cuboid(cubeMinX, -2.0F, -2.0F, width, 6.0F, 4.0F),
                ModelTransform.origin(originX, originY, 0.0F));
        arm.addChild(sleeveName,
                ModelPartBuilder.create().uv(sleeveU, sleeveV).cuboid(cubeMinX, -2.0F, -2.0F, width, 6.0F, 4.0F, new Dilation(0.25F)),
                ModelTransform.origin(0.0F, 0.0F, 0.0F));
        ModelPartData forearm = arm.addChild(forearmName,
                ModelPartBuilder.create().uv(skinU, skinV + 6).cuboid(cubeMinX, 0.0F, -2.0F, width, 6.0F, 4.0F),
                ModelTransform.origin(0.0F, 4.0F, 0.0F));
        arm.addChild(elbowBlendName,
                ModelPartBuilder.create().uv(skinU, skinV + 5).cuboid(cubeMinX, -1.0F, -2.0F, width, 2.0F, 4.0F, new Dilation(0.01F)),
                ModelTransform.origin(0.0F, 4.0F, 0.0F));
        forearm.addChild(forearmSleeveName,
                ModelPartBuilder.create().uv(sleeveU, sleeveV + 6).cuboid(cubeMinX, 0.0F, -2.0F, width, 6.0F, 4.0F, new Dilation(0.25F)),
                ModelTransform.origin(0.0F, 0.0F, 0.0F));
    }

    private static void addLeg(ModelPartData root, String legName, String lowerLegName, String kneeBlendName,
                               String pantsName, String lowerPantsName,
                               float originX, int skinU, int skinV, int pantsU, int pantsV) {
        ModelPartData leg = root.addChild(legName,
                ModelPartBuilder.create().uv(skinU, skinV).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F),
                ModelTransform.origin(originX, 12.0F, 0.0F));
        leg.addChild(pantsName,
                ModelPartBuilder.create().uv(pantsU, pantsV).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new Dilation(0.25F)),
                ModelTransform.origin(0.0F, 0.0F, 0.0F));
        ModelPartData lowerLeg = leg.addChild(lowerLegName,
                ModelPartBuilder.create().uv(skinU, skinV + 6).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F),
                ModelTransform.origin(0.0F, 6.0F, 0.0F));
        leg.addChild(kneeBlendName,
                ModelPartBuilder.create().uv(skinU, skinV + 5).cuboid(-2.0F, -1.0F, -2.0F, 4.0F, 2.0F, 4.0F, new Dilation(0.01F)),
                ModelTransform.origin(0.0F, 6.0F, 0.0F));
        lowerLeg.addChild(lowerPantsName,
                ModelPartBuilder.create().uv(pantsU, pantsV + 6).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new Dilation(0.25F)),
                ModelTransform.origin(0.0F, 0.0F, 0.0F));
    }
}
