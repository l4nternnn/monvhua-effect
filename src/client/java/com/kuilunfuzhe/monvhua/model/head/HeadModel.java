package com.kuilunfuzhe.monvhua.model.head;

import net.minecraft.client.model.*;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.entity.SkullBlockEntityModel;
import net.minecraft.client.render.entity.model.EntityModelPartNames;
import net.minecraft.client.util.math.MatrixStack;

/**
 * 头部模型，使用标准的{@link EntityModelPartNames#HEAD}子节点名。
 *
 * <p>继承{@link SkullBlockEntityModel}是为了复用头颅方碑的渲染管线——
 * 通过方块实体渲染器将各部分模型分别渲染到不同颜色通道上。</p>
 */
public class HeadModel extends SkullBlockEntityModel {
    private final ModelPart root;

    public HeadModel(ModelPart root) {
        super(root);
        this.root = root;
    }

    /** @return 头部（含帽子层）的TexturedModelData */
    public static TexturedModelData getTexturedModelData() {
        ModelData modelData = new ModelData();
        ModelPartData root = modelData.getRoot();

        // 头部主体：8x8x8立方体
        ModelPartData head = root.addChild(EntityModelPartNames.HEAD,
                ModelPartBuilder.create().uv(0, 0).cuboid(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F),
                ModelTransform.origin(0.0F, 0.0F, 0.0F));

        // 帽子层（Dilation=0.25外层，注意CombinedBodyModelData中帽子Dilation为0.5，此处更薄）
        ModelPartData hat = head.addChild(EntityModelPartNames.HAT,
                ModelPartBuilder.create().uv(32, 0).cuboid(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new Dilation(0.25F)),
                ModelTransform.origin(0.0F, 0.0F, 0.0F));


        return TexturedModelData.of(modelData, 64, 64);
    }

    /**
     * 设置头部旋转角度，将角度值转换为弧度后应用到Yaw/Pitch。
     * 这是头部模型，直接旋转root节点。
     * @param animationProgress 动画进度（未使用，仅用于覆写父类签名）
     * @param yaw Y轴旋转角度（度），控制左右摇头
     * @param pitch X轴旋转角度（度），控制上下点头
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
