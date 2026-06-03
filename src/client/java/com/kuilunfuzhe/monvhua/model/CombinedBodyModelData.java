package com.kuilunfuzhe.monvhua.model;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModelPartNames;

/**
 * 聚合全身模型的模型数据，提供默认（4像素臂宽）和Slim（3像素臂宽）两种体型的TexturedModelData。
 * 用于方碑渲染时动态组装完整的玩家身体模型。
 */
public class CombinedBodyModelData {
    private CombinedBodyModelData() {
    }

    /** @return 默认体型（手臂4像素宽，Y轴偏移2.0）的模型数据 */
    public static TexturedModelData getDefaultTexturedModelData() {
        return getTexturedModelData(false);
    }

    /** @return Slim体型（手臂3像素宽，Y轴偏移2.5）的模型数据 */
    public static TexturedModelData getSlimTexturedModelData() {
        return getTexturedModelData(true);
    }

    /**
     * 构建全身模型数据，通过slim参数控制手臂宽度和位置偏移
     * @param slim true=Slim体型（3像素宽手臂），false=默认体型（4像素宽手臂）
     */
    private static TexturedModelData getTexturedModelData(boolean slim) {
        ModelData modelData = new ModelData();
        ModelPartData root = modelData.getRoot();

        // 头部：8x8x8立方体，原点居中
        ModelPartData head = root.addChild(EntityModelPartNames.HEAD,
                ModelPartBuilder.create().uv(0, 0).cuboid(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F),
                ModelTransform.origin(0.0F, 0.0F, 0.0F));
        head.addChild(EntityModelPartNames.HAT,
                ModelPartBuilder.create().uv(32, 0).cuboid(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new Dilation(0.5F)),
                ModelTransform.origin(0.0F, 0.0F, 0.0F));

        // 躯干：8x12x4立方体
        ModelPartData body = root.addChild(EntityModelPartNames.BODY,
                ModelPartBuilder.create().uv(16, 16).cuboid(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F),
                ModelTransform.origin(0.0F, 0.0F, 0.0F));
        body.addChild("jacket",
                ModelPartBuilder.create().uv(16, 32).cuboid(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, new Dilation(0.25F)),
                ModelTransform.origin(0.0F, 0.0F, 0.0F));

        // Slim体型与默认体型的区别：手臂宽度（3 vs 4像素）以及Y轴偏移（2.5 vs 2.0）
        // Slim体型与默认体型的区别：手臂宽度（3 vs 4像素）以及Y轴偏移（2.5 vs 2.0）
        if (slim) {
            // Slim右臂：3像素宽，Y偏移2.5（比默认体型略低）
            ModelPartData rightArm = root.addChild(EntityModelPartNames.RIGHT_ARM,
                    ModelPartBuilder.create().uv(40, 16).cuboid(-2.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F),
                    ModelTransform.origin(-5.0F, 2.5F, 0.0F));
            rightArm.addChild("right_sleeve",
                    ModelPartBuilder.create().uv(40, 32).cuboid(-2.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, new Dilation(0.25F)),
                    ModelTransform.origin(0.0F, 0.0F, 0.0F));

            // Slim左臂：3像素宽，Y偏移2.5
            ModelPartData leftArm = root.addChild(EntityModelPartNames.LEFT_ARM,
                    ModelPartBuilder.create().uv(32, 48).cuboid(-1.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F),
                    ModelTransform.origin(5.0F, 2.5F, 0.0F));
            leftArm.addChild("left_sleeve",
                    ModelPartBuilder.create().uv(48, 48).cuboid(-1.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, new Dilation(0.25F)),
                    ModelTransform.origin(0.0F, 0.0F, 0.0F));
        } else {
            // 默认右臂：4像素宽，Y偏移2.0
            ModelPartData rightArm = root.addChild(EntityModelPartNames.RIGHT_ARM,
                    ModelPartBuilder.create().uv(40, 16).cuboid(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                    ModelTransform.origin(-5.0F, 2.0F, 0.0F));
            rightArm.addChild("right_sleeve",
                    ModelPartBuilder.create().uv(40, 32).cuboid(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new Dilation(0.25F)),
                    ModelTransform.origin(0.0F, 0.0F, 0.0F));

            // 默认左臂：4像素宽，Y偏移2.0
            ModelPartData leftArm = root.addChild(EntityModelPartNames.LEFT_ARM,
                    ModelPartBuilder.create().uv(32, 48).cuboid(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                    ModelTransform.origin(5.0F, 2.0F, 0.0F));
            leftArm.addChild("left_sleeve",
                    ModelPartBuilder.create().uv(48, 48).cuboid(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new Dilation(0.25F)),
                    ModelTransform.origin(0.0F, 0.0F, 0.0F));
        }

        // 右腿：4x12x4立方体
        ModelPartData rightLeg = root.addChild(EntityModelPartNames.RIGHT_LEG,
                ModelPartBuilder.create().uv(0, 16).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                ModelTransform.origin(-1.9F, 12.0F, 0.0F));
        rightLeg.addChild("right_pants",
                ModelPartBuilder.create().uv(0, 32).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new Dilation(0.25F)),
                ModelTransform.origin(0.0F, 0.0F, 0.0F));

        // 左腿：4x12x4立方体
        ModelPartData leftLeg = root.addChild(EntityModelPartNames.LEFT_LEG,
                ModelPartBuilder.create().uv(16, 48).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                ModelTransform.origin(1.9F, 12.0F, 0.0F));
        leftLeg.addChild("left_pants",
                ModelPartBuilder.create().uv(0, 48).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new Dilation(0.25F)),
                ModelTransform.origin(0.0F, 0.0F, 0.0F));

        // 披风：背面薄片（仅Z轴1像素深），10x16
        root.addChild("cloak",
                ModelPartBuilder.create().uv(0, 0).cuboid(-5.0F, 0.0F, -1.0F, 10.0F, 16.0F, 1.0F),
                ModelTransform.origin(0.0F, 0.0F, 0.0F));

        return TexturedModelData.of(modelData, 64, 64);
    }
}
