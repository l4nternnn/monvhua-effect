package com.kuilunfuzhe.monvhua.model.torso;

import net.minecraft.client.model.*;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.entity.SkullBlockEntityModel;
import net.minecraft.client.render.entity.model.EntityModelPartNames;
import net.minecraft.client.util.math.MatrixStack;

public class TorsoModel extends SkullBlockEntityModel {
    private final ModelPart root;

    public TorsoModel(ModelPart root) {
        super(root);
        this.root = root;
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData modelData = new ModelData();
        ModelPartData root = modelData.getRoot();

        ModelPartData torso = root.addChild(EntityModelPartNames.HEAD,
                ModelPartBuilder.create().uv(16,16).cuboid(-4.0F, -12.0F, -2.0F, 8.0F, 12.0F, 4.0F),
                ModelTransform.origin(0.0F, 1.0f, 0.0F));

        ModelPartData jacket = torso.addChild(EntityModelPartNames.JACKET,
                ModelPartBuilder.create().uv(16, 32).cuboid(-4.0F, -12.0F, -2.0F, 8.0F, 12.0F, 4.0F, new Dilation(0.25F)),
                ModelTransform.origin(0.0F, 1.0F, 0.0F));

        jacket.addChild("edge_front",
                ModelPartBuilder.create().uv(16, 32).cuboid(-4.0F, -12.0F, 2.0F, 8.0F, 12.0F, 0.25F),
                ModelTransform.origin(0.0F, 0.0F, 0.0F));
        jacket.addChild("edge_back",
                ModelPartBuilder.create().uv(16, 32).cuboid(-4.0F, -12.0F, -2.25F, 8.0F, 12.0F, 0.25F),
                ModelTransform.origin(0.0F, 0.0F, 0.0F));
        jacket.addChild("edge_left",
                ModelPartBuilder.create().uv(16, 32).cuboid(-4.25F, -12.0F, -2.0F, 0.25F, 12.0F, 4.0F),
                ModelTransform.origin(0.0F, 0.0F, 0.0F));
        jacket.addChild("edge_right",
                ModelPartBuilder.create().uv(16, 32).cuboid(4.0F, -12.0F, -2.0F, 0.25F, 12.0F, 4.0F),
                ModelTransform.origin(0.0F, 0.0F, 0.0F));
        jacket.addChild("edge_top",
                ModelPartBuilder.create().uv(16, 32).cuboid(-4.0F, -12.25F, -2.0F, 8.0F, 0.25F, 4.0F),
                ModelTransform.origin(0.0F, 0.0F, 0.0F));
        jacket.addChild("edge_bottom",
                ModelPartBuilder.create().uv(16, 32).cuboid(-4.0F, 0.0F, -2.0F, 8.0F, 0.25F, 4.0F),
                ModelTransform.origin(0.0F, 0.0F, 0.0F));

        return TexturedModelData.of(modelData, 64, 64);
    }

    @Override
    public void setHeadRotation(float animationProgress, float yaw, float pitch) {
        this.root.yaw = yaw * (float)(Math.PI / 180.0);
        this.root.pitch = pitch * (float)(Math.PI / 180.0);
    }

    public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha) {
        this.root.render(matrices, vertices, light, overlay);
    }
}
