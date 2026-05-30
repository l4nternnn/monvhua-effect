package com.kuilunfuzhe.monvhua.register;

import com.kuilunfuzhe.monvhua.entity.ModBlockEntities;
import com.kuilunfuzhe.monvhua.model.CombinedBodyModelData;
import com.kuilunfuzhe.monvhua.model.ModModelLayers;
import com.kuilunfuzhe.monvhua.model.arm.LeftArmModel;
import com.kuilunfuzhe.monvhua.model.arm.LeftArmSlimModel;
import com.kuilunfuzhe.monvhua.model.arm.RightArmModel;
import com.kuilunfuzhe.monvhua.model.arm.RightArmSlimModel;
import com.kuilunfuzhe.monvhua.model.head.HeadModel;
import com.kuilunfuzhe.monvhua.model.leg.LeftLegModel;
import com.kuilunfuzhe.monvhua.model.leg.RightLegModel;
import com.kuilunfuzhe.monvhua.model.torso.TorsoModel;
import com.kuilunfuzhe.monvhua.renderer.body.arm.LeftArmBlockEntityRenderer;
import com.kuilunfuzhe.monvhua.renderer.body.arm.LeftArmSpecialModelRenderer;
import com.kuilunfuzhe.monvhua.renderer.body.arm.RightArmBlockEntityRenderer;
import com.kuilunfuzhe.monvhua.renderer.body.arm.RightArmSpecialModelRenderer;
import com.kuilunfuzhe.monvhua.renderer.body.head.HeadBlockEntityRenderer;
import com.kuilunfuzhe.monvhua.renderer.body.head.HeadSpecialModelRenderer;
import com.kuilunfuzhe.monvhua.renderer.body.leg.LeftLegBlockEntityRenderer;
import com.kuilunfuzhe.monvhua.renderer.body.leg.LeftLegSpecialModelRenderer;
import com.kuilunfuzhe.monvhua.renderer.body.leg.RightLegBlockEntityRenderer;
import com.kuilunfuzhe.monvhua.renderer.body.leg.RightLegSpecialModelRenderer;
import com.kuilunfuzhe.monvhua.renderer.body.special.CombinedBodySpecialModelRenderer;
import com.kuilunfuzhe.monvhua.renderer.body.torso.TorsoBlockEntityRenderer;
import com.kuilunfuzhe.monvhua.renderer.body.torso.TorsoSpecialModelRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.item.model.special.SpecialModelTypes;
import net.minecraft.util.Identifier;

/**
 * 身体部件方块模型注册中心。
 * 注册所有肢体部件（躯干、左右臂/腿、头部）的 ModelLayer、BlockEntityRenderer 和物品栏 SpecialModelRenderer，
 * 包括普通和 Slim 两种手臂模型变体。
 */
public class BodyBlockModelRegister {
    /**
     * 注册所有身体部件相关的模型层、方块实体渲染器和物品特殊模型渲染器。
     */
    public static void register() {
        EntityModelLayerRegistry.registerModelLayer(ModModelLayers.COMBINED_BODY, CombinedBodyModelData::getDefaultTexturedModelData);
        EntityModelLayerRegistry.registerModelLayer(ModModelLayers.COMBINED_BODY_SLIM, CombinedBodyModelData::getSlimTexturedModelData);

        EntityModelLayerRegistry.registerModelLayer(ModModelLayers.TORSO, TorsoModel::getTexturedModelData);
        BlockEntityRendererFactories.register(
                ModBlockEntities.TORSO_BLOCK_ENTITY,
                TorsoBlockEntityRenderer::new
        );

        // Left arm
        EntityModelLayerRegistry.registerModelLayer(ModModelLayers.LEFT_ARM, LeftArmModel::getTexturedModelData);
        BlockEntityRendererFactories.register(
                ModBlockEntities.LEFT_ARM_BLOCK_ENTITY,
                LeftArmBlockEntityRenderer::new
        );

        // Right arm
        EntityModelLayerRegistry.registerModelLayer(ModModelLayers.RIGHT_ARM, RightArmModel::getTexturedModelData);
        BlockEntityRendererFactories.register(
                ModBlockEntities.RIGHT_ARM_BLOCK_ENTITY,
                RightArmBlockEntityRenderer::new
        );

        // Slim arm models
        EntityModelLayerRegistry.registerModelLayer(ModModelLayers.LEFT_ARM_SLIM, LeftArmSlimModel::getTexturedModelData);
        EntityModelLayerRegistry.registerModelLayer(ModModelLayers.RIGHT_ARM_SLIM, RightArmSlimModel::getTexturedModelData);

        // Left leg
        EntityModelLayerRegistry.registerModelLayer(ModModelLayers.LEFT_LEG, LeftLegModel::getTexturedModelData);
        BlockEntityRendererFactories.register(
                ModBlockEntities.LEFT_LEG_BLOCK_ENTITY,
                LeftLegBlockEntityRenderer::new
        );

        // Right leg
        EntityModelLayerRegistry.registerModelLayer(ModModelLayers.RIGHT_LEG, RightLegModel::getTexturedModelData);
        BlockEntityRendererFactories.register(
                ModBlockEntities.RIGHT_LEG_BLOCK_ENTITY,
                RightLegBlockEntityRenderer::new
        );

        // Head
        EntityModelLayerRegistry.registerModelLayer(ModModelLayers.HEAD, HeadModel::getTexturedModelData);
        BlockEntityRendererFactories.register(
                ModBlockEntities.HEAD_BLOCK_ENTITY,
                HeadBlockEntityRenderer::new
        );

        // Item inventory/hand special renderers
        SpecialModelTypes.ID_MAPPER.put(Identifier.of("monvhua", "torso"), TorsoSpecialModelRenderer.Unbaked.CODEC);
        SpecialModelTypes.ID_MAPPER.put(Identifier.of("monvhua", "left_arm"), LeftArmSpecialModelRenderer.Unbaked.CODEC);
        SpecialModelTypes.ID_MAPPER.put(Identifier.of("monvhua", "right_arm"), RightArmSpecialModelRenderer.Unbaked.CODEC);
        SpecialModelTypes.ID_MAPPER.put(Identifier.of("monvhua", "left_leg"), LeftLegSpecialModelRenderer.Unbaked.CODEC);
        SpecialModelTypes.ID_MAPPER.put(Identifier.of("monvhua", "right_leg"), RightLegSpecialModelRenderer.Unbaked.CODEC);
        SpecialModelTypes.ID_MAPPER.put(Identifier.of("monvhua", "head"), HeadSpecialModelRenderer.Unbaked.CODEC);
        SpecialModelTypes.ID_MAPPER.put(Identifier.of("monvhua", "combined_body"), CombinedBodySpecialModelRenderer.Unbaked.CODEC);
    }
}
