package com.kuilunfuzhe.monvhua.features.carryentity;

import com.kuilunfuzhe.monvhua.features.paint.PlayerSkinPaintManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.entity.model.LoadedEntityModels;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.util.Identifier;

public final class CarriedPlayerPoseRenderer {
	private static LoadedEntityModels cachedEntityModels;
	private static PlayerEntityModel cachedDefaultModel;
	private static PlayerEntityModel cachedSlimModel;

	private CarriedPlayerPoseRenderer() {
	}

	public static boolean render(Entity carried, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, boolean hideHead) {
		if (!(carried instanceof AbstractClientPlayerEntity player) || CarryPoseClientState.isDragCarried(carried.getId())) {
			return false;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		SkinTextures skinTextures = player.getSkinTextures();
		boolean slim = skinTextures.model() == SkinTextures.Model.SLIM;
		PlayerEntityModel model = getModel(client, slim);
		if (model == null) {
			return false;
		}

		PlayerEntityRenderState state = createState(player, skinTextures);
		resetModel(model);
		applySkinPartVisibility(model, player);
		float baseScale = player.getScale();

		matrices.push();
		try {
			CarryAttachedRenderMath.applyCarriedPlayerRenderModelTransform(matrices, baseScale);
			CarryAttachedRenderMath.applyCarriedModelHorizontalTransform(matrices, baseScale, false);
			CarryPoseModelApplier.beginRenderContext(model, state);
			try {
				CarryPoseModelApplier.apply(model, state);
				if (hideHead) {
					model.head.visible = false;
					model.hat.visible = false;
				}
				Identifier texture = getRenderTexture(player, skinTextures);
				VertexConsumer vertices = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
				model.render(matrices, vertices, light, OverlayTexture.DEFAULT_UV);
			} finally {
				CarryPoseModelApplier.endRenderContext(model, state);
			}
		} finally {
			matrices.pop();
		}
		return true;
	}

	private static PlayerEntityModel getModel(MinecraftClient client, boolean slim) {
		LoadedEntityModels loadedModels = client.getLoadedEntityModels();
		if (loadedModels == null) {
			return null;
		}
		if (loadedModels != cachedEntityModels) {
			cachedEntityModels = loadedModels;
			cachedDefaultModel = null;
			cachedSlimModel = null;
		}
		if (slim) {
			if (cachedSlimModel == null) {
				cachedSlimModel = new PlayerEntityModel(loadedModels.getModelPart(EntityModelLayers.PLAYER_SLIM), true);
			}
			return cachedSlimModel;
		}
		if (cachedDefaultModel == null) {
			cachedDefaultModel = new PlayerEntityModel(loadedModels.getModelPart(EntityModelLayers.PLAYER), false);
		}
		return cachedDefaultModel;
	}

	private static PlayerEntityRenderState createState(AbstractClientPlayerEntity player, SkinTextures skinTextures) {
		PlayerEntityRenderState state = new PlayerEntityRenderState();
		state.id = player.getId();
		state.name = player.getName().getString();
		state.skinTextures = skinTextures;
		return state;
	}

	private static void resetModel(PlayerEntityModel model) {
		for (ModelPart part : model.getRootPart().traverse()) {
			part.resetTransform();
			part.visible = true;
			part.hidden = false;
		}
	}

	private static void applySkinPartVisibility(PlayerEntityModel model, AbstractClientPlayerEntity player) {
		model.hat.visible = player.isPartVisible(PlayerModelPart.HAT);
		model.jacket.visible = player.isPartVisible(PlayerModelPart.JACKET);
		model.leftSleeve.visible = player.isPartVisible(PlayerModelPart.LEFT_SLEEVE);
		model.rightSleeve.visible = player.isPartVisible(PlayerModelPart.RIGHT_SLEEVE);
		model.leftPants.visible = player.isPartVisible(PlayerModelPart.LEFT_PANTS_LEG);
		model.rightPants.visible = player.isPartVisible(PlayerModelPart.RIGHT_PANTS_LEG);
	}

	private static Identifier getRenderTexture(AbstractClientPlayerEntity player, SkinTextures skinTextures) {
		Identifier paintedTexture = PlayerSkinPaintManager.getPaintedTexture(player.getUuid());
		return paintedTexture != null ? paintedTexture : skinTextures.texture();
	}
}
