package com.kuilunfuzhe.monvhua.compat;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryPoseClientState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.lang.reflect.Method;
import java.util.function.Function;

/**
 * Entity Model Features compatibility detection.
 */
public final class EmfCompat {
	private static boolean loaded;

	private EmfCompat() {
	}

	public static void init() {
		loaded = FabricLoader.getInstance().isModLoaded("entity_model_features");
		if (loaded) {
			MonvhuaMod.LOGGER.info("[Monvhua] Entity Model Features detected, enabling carry pose render-priority compatibility layer");
			registerVanillaModelCondition();
		}
	}

	public static boolean isLoaded() {
		return loaded;
	}

	@SuppressWarnings("unchecked")
	private static void registerVanillaModelCondition() {
		try {
			Class<?> apiClass = Class.forName("traben.entity_model_features.EMFAnimationApi");
			Method registerVanillaModelCondition = apiClass.getMethod("registerVanillaModelCondition", Function.class);
			Object result = registerVanillaModelCondition.invoke(null, (Function<Object, Boolean>) EmfCompat::shouldForceVanillaPlayerModel);
			if (Boolean.TRUE.equals(result)) {
				MonvhuaMod.LOGGER.info("[Monvhua] Registered EMF vanilla player model condition for active carry poses");
			} else {
				MonvhuaMod.LOGGER.warn("[Monvhua] EMF vanilla player model condition was not accepted");
			}
		} catch (Throwable throwable) {
			MonvhuaMod.LOGGER.warn("[Monvhua] Failed to register EMF vanilla player model condition", throwable);
		}
	}

	private static Boolean shouldForceVanillaPlayerModel(Object emfEntity) {
		if (!(emfEntity instanceof PlayerEntity player)) {
			return false;
		}

		int entityId = player.getId();
		return CarryPoseClientState.isCarrier(entityId) || CarryPoseClientState.isCarried(entityId);
	}
}
