package com.kuilunfuzhe.monvhua.model.leg;

import net.minecraft.client.model.*;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.entity.SkullBlockEntityModel;
import net.minecraft.client.render.entity.model.EntityModelPartNames;
import net.minecraft.client.util.math.MatrixStack;

public class LeftLegModel extends SkullBlockEntityModel {
    private final ModelPart left_leg;

    public LeftLegModel(ModelPart root) {
        super(root);
        this.left_leg = root.getChild(EntityModelPartNames.HEAD);
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData modelData = new ModelData();
        ModelPartData root = modelData.getRoot();

        ModelPartData leftLeg = root.addChild(EntityModelPartNames.HEAD,
                ModelPartBuilder.create().uv(16, 48).cuboid(-4.0F, -12.0F, -4.0f, 4.0F, 12.0F, 4.0F),
                ModelTransform.origin(0.5f, -6.0f, 0.0f));

        ModelPartData pants = leftLeg.addChild("left_pants",
                ModelPartBuilder.create().uv(0, 32).cuboid(-4.0F, -12.0F, -4.0F, 4.0F, 12.0F, 4.0F, new Dilation(0.25F)),
                ModelTransform.origin(0.0f, -0.0f, 0.0f));

        pants.addChild("edge_front",
                ModelPartBuilder.create().uv(0, 32).cuboid(-4.0F, -12.0F, 0.0F, 4.0F, 12.0F, 0.25F),
                ModelTransform.origin(0.0f, 0.0f, 0.0f));
        pants.addChild("edge_back",
                ModelPartBuilder.create().uv(0, 32).cuboid(-4.0F, -12.0F, -4.25F, 4.0F, 12.0F, 0.25F),
                ModelTransform.origin(0.0f, 0.0f, 0.0f));
        pants.addChild("edge_left",
                ModelPartBuilder.create().uv(0, 32).cuboid(-4.25F, -12.0F, -4.0F, 0.25F, 12.0F, 4.0F),
                ModelTransform.origin(0.0f, 0.0f, 0.0f));
        pants.addChild("edge_right",
                ModelPartBuilder.create().uv(0, 32).cuboid(0.0F, -12.0F, -4.0F, 0.25F, 12.0F, 4.0F),
                ModelTransform.origin(0.0f, 0.0f, 0.0f));
        pants.addChild("edge_top",
                ModelPartBuilder.create().uv(0, 32).cuboid(-4.0F, -12.25F, -4.0F, 4.0F, 0.25F, 4.0F),
                ModelTransform.origin(0.0f, 0.0f, 0.0f));
        pants.addChild("edge_bottom",
                ModelPartBuilder.create().uv(0, 32).cuboid(-4.0F, 0.0F, -4.0F, 4.0F, 0.25F, 4.0F),
                ModelTransform.origin(0.0f, 0.0f, 0.0f));

        return TexturedModelData.of(modelData, 64, 64);
    }

    @Override
    public void setHeadRotation(float animationProgress, float yaw, float pitch) {
        this.left_leg.yaw = yaw * (float)(Math.PI / 180.0);
        this.left_leg.pitch = pitch * (float)(Math.PI / 180.0);
    }

    public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha) {
        this.left_leg.render(matrices, vertices, light, overlay);
    }
}
