package com.kuilunfuzhe.monvhua.model.arm;

import net.minecraft.client.model.*;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.entity.SkullBlockEntityModel;
import net.minecraft.client.render.entity.model.EntityModelPartNames;
import net.minecraft.client.util.math.MatrixStack;

/**
 * 右臂模型（默认体型，手臂4像素宽）。
 *
 * <p>继承{@link SkullBlockEntityModel}是为了复用头颅方碑的渲染管线——
 * 通过方块实体渲染器将各部分模型分别渲染到不同颜色通道上。</p>
 *
 * <p><b>设计决策：</b>虽然本模型实际代表右臂，但使用{@link EntityModelPartNames#HEAD}
 * 作为子节点名。这是因为{@link SkullBlockEntityModel#setHeadRotation}通过HEAD常量查找子节点，
 * 因此所有非头部部位（手臂、腿等）也统一使用HEAD作为节点名来复用父类的旋转逻辑。</p>
 */
public class RightArmModel extends SkullBlockEntityModel {
    private final ModelPart right_arm;

    public RightArmModel(ModelPart root) {
        super(root);
        // 用HEAD常量名查找实际代表右臂的模型部件，以复用父类SkullBlockEntityModel的旋转方法
        this.right_arm = root.getChild(EntityModelPartNames.HEAD);
    }

    /** @return 默认体型右臂（4像素宽）的TexturedModelData */
    public static TexturedModelData getTexturedModelData() {
        ModelData modelData = new ModelData();
        ModelPartData root = modelData.getRoot();

        // 子节点名使用HEAD（而非RIGHT_ARM），使父类setHeadRotation能通过HEAD查找到此部件并旋转
        ModelPartData rightArm = root.addChild(EntityModelPartNames.HEAD,
                ModelPartBuilder.create().uv(40, 16).cuboid(-4.0F, -12.0F, -4.0f, 4.0F, 12.0F, 4.0F),
                ModelTransform.origin(-0.0f, -0.0f, 0.0f));

        // 右臂衣袖（Dilation=0.25外层）
        ModelPartData sleeve = rightArm.addChild("right_sleeve",
                ModelPartBuilder.create().uv(40, 32).cuboid(-4.0F, -12.0F, -4.0F, 4.0F, 12.0F, 4.0F, new Dilation(0.25F)),
                ModelTransform.origin(-0.0f, -0.0f, 0.0f));


        return TexturedModelData.of(modelData, 64, 64);
    }

    /**
     * 设置右臂旋转角度，将角度值转换为弧度后应用到Yaw/Pitch。
     * @param animationProgress 动画进度（未使用，仅用于覆写父类签名）
     * @param yaw Y轴旋转角度（度）
     * @param pitch X轴旋转角度（度）
     */
    @Override
    public void setHeadRotation(float animationProgress, float yaw, float pitch) {
        // 角度转弧度：Math.PI / 180.0
        this.right_arm.yaw = yaw * (float)(Math.PI / 180.0);
        this.right_arm.pitch = pitch * (float)(Math.PI / 180.0);
    }

    public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha) {
        this.right_arm.render(matrices, vertices, light, overlay);
    }
}
