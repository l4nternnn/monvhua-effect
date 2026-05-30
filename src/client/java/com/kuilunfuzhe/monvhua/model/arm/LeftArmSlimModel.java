package com.kuilunfuzhe.monvhua.model.arm;

import net.minecraft.client.model.*;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.entity.SkullBlockEntityModel;
import net.minecraft.client.render.entity.model.EntityModelPartNames;
import net.minecraft.client.util.math.MatrixStack;

/**
 * 左臂Slim模型（手臂3像素宽），与{@link LeftArmModel}的区别仅在于手臂宽度（3 vs 4像素）。
 *
 * <p>继承{@link SkullBlockEntityModel}是为了复用头颅方碑的渲染管线——
 * 通过方块实体渲染器将各部分模型分别渲染到不同颜色通道上。</p>
 */
public class LeftArmSlimModel extends SkullBlockEntityModel {
    private final ModelPart left_arm;

    public LeftArmSlimModel(ModelPart root) {
        super(root);
        this.left_arm = root.getChild(EntityModelPartNames.LEFT_ARM);
    }

    /** @return Slim体型左臂（3像素宽）的TexturedModelData */
    public static TexturedModelData getTexturedModelData() {
        ModelData modelData = new ModelData();
        ModelPartData root = modelData.getRoot();

        // Slim左臂主体：3x12x4立方体（比默认体型的4像素宽窄1像素），UV偏移(32,48)
        ModelPartData arm = root.addChild(EntityModelPartNames.LEFT_ARM,
                ModelPartBuilder.create().uv(32, 48).cuboid(-4.0F, -12.0F, -4.0f, 3.0F, 12.0F, 4.0F),
                ModelTransform.origin(0.0f, 0.0f, 0.0f));

        // Slim左臂衣袖（Dilation=0.25外层）
        ModelPartData sleeve = arm.addChild("left_sleeve",
                ModelPartBuilder.create().uv(48, 48).cuboid(-4.0F, -12.0F, -4.0F, 3.0F, 12.0F, 4.0F, new Dilation(0.25F)),
                ModelTransform.origin(0.0f, 0.0f, 0.0f));


        return TexturedModelData.of(modelData, 64, 64);
    }

    /**
     * 设置左臂旋转角度，将角度值转换为弧度后应用到Yaw/Pitch。
     * @param animationProgress 动画进度（未使用，仅用于覆写父类签名）
     * @param yaw Y轴旋转角度（度）
     * @param pitch X轴旋转角度（度）
     */
    @Override
    public void setHeadRotation(float animationProgress, float yaw, float pitch) {
        // 角度转弧度：Math.PI / 180.0
        this.left_arm.yaw = yaw * (float)(Math.PI / 180.0);
        this.left_arm.pitch = pitch * (float)(Math.PI / 180.0);
    }

    public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha) {
        this.left_arm.render(matrices, vertices, light, overlay);
    }
}
