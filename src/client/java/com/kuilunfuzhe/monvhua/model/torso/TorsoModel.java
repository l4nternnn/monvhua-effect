package com.kuilunfuzhe.monvhua.model.torso;

import net.minecraft.client.model.*;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.entity.SkullBlockEntityModel;
import net.minecraft.client.render.entity.model.EntityModelPartNames;
import net.minecraft.client.util.math.MatrixStack;

/**
 * 躯干模型。
 *
 * <p>继承{@link SkullBlockEntityModel}是为了复用头颅方碑的渲染管线——
 * 通过方块实体渲染器将各部分模型分别渲染到不同颜色通道上。</p>
 *
 * <p><b>设计决策：</b>虽然本模型实际代表躯干，但使用{@link EntityModelPartNames#HEAD}
 * 作为子节点名。这是因为{@link SkullBlockEntityModel#setHeadRotation}通过HEAD常量查找子节点，
 * 因此所有非头部部位也统一使用HEAD作为节点名来复用父类的旋转逻辑。</p>
 */
public class TorsoModel extends SkullBlockEntityModel {
    private final ModelPart root;

    public TorsoModel(ModelPart root) {
        super(root);
        this.root = root;
    }

    /** @return 躯干（含夹克层）的TexturedModelData */
    public static TexturedModelData getTexturedModelData() {
        ModelData modelData = new ModelData();
        ModelPartData root = modelData.getRoot();

        // 子节点名使用HEAD（而非BODY），使父类setHeadRotation能通过HEAD查找到此部件并旋转
        // 躯干主体：8x12x4立方体，Y偏移+1（比CombinedBodyModelData中躯干高1像素）
        ModelPartData torso = root.addChild(EntityModelPartNames.HEAD,
                ModelPartBuilder.create().uv(16,16).cuboid(-4.0F, -12.0F, -2.0F, 8.0F, 12.0F, 4.0F),
                ModelTransform.origin(0.0F, 1.0f, 0.0F));

        // 夹克层（Dilation=0.25外层）
        ModelPartData jacket = torso.addChild(EntityModelPartNames.JACKET,
                ModelPartBuilder.create().uv(16, 32).cuboid(-4.0F, -12.0F, -2.0F, 8.0F, 12.0F, 4.0F, new Dilation(0.25F)),
                ModelTransform.origin(0.0F, 1.0F, 0.0F));


        return TexturedModelData.of(modelData, 64, 64);
    }

    /**
     * 设置躯干旋转角度，将角度值转换为弧度后应用到Yaw/Pitch。
     * 躯干通过旋转root节点实现整体的倾斜/旋转效果。
     * @param animationProgress 动画进度（未使用，仅用于覆写父类签名）
     * @param yaw Y轴旋转角度（度）
     * @param pitch X轴旋转角度（度）
     */
    @Override
    public void setHeadRotation(float animationProgress, float yaw, float pitch) {
        // 角度转弧度：Math.PI / 180.0
        this.root.yaw = yaw * (float)(Math.PI / 180.0);
        this.root.pitch = pitch * (float)(Math.PI / 180.0);
    }

    public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha) {
        this.root.render(matrices, vertices, light, overlay);
    }
}
