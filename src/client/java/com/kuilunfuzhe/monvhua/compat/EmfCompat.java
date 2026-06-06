package com.kuilunfuzhe.monvhua.compat;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryPoseClientState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.model.ModelPart;
import net.minecraft.entity.player.PlayerEntity;

import java.lang.reflect.Method;
import java.util.function.Function;

/**
 * Entity Model Features compatibility detection.
 */
public final class EmfCompat {
	private static boolean loaded;

	private static Method pauseCustomAnimationsForPartsMethod;
	private static Method resumeAllCustomAnimationsMethod;
	private static Class<?> emfEntityClass;
	private static boolean loggedUpperBodyPauseAvailable;

	private EmfCompat() {
	}

	public static void init() {
		loaded = FabricLoader.getInstance().isModLoaded("entity_model_features");
		if (loaded) {
			MonvhuaMod.LOGGER.info("[Monvhua] Entity Model Features detected, enabling carry pose render-priority compatibility layer");
			registerVanillaModelCondition();
		}
	}

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

		return CarryPoseClientState.isCarried(player.getId());
	}

	public static void pauseCarrierUpperBodyAnimations(PlayerEntityModelParts parts, int entityId) {
		if (!loaded || !CarryPoseClientState.isCarrier(entityId)) {
			return;
		}

		try {
			ensureAnimationControlMethods();
			if (pauseCustomAnimationsForPartsMethod == null || emfEntityClass == null || !emfEntityClass.isInstance(parts.player())) {
				return;
			}
			pauseCustomAnimationsForPartsMethod.invoke(null, parts.player(), new ModelPart[]{parts.body(), parts.rightArm(), parts.leftArm()});

        } catch (Throwable throwable) {
			MonvhuaMod.LOGGER.warn("[Monvhua] Failed to pause EMF carrier upper-body animations", throwable);
		}
	}

	public static void resumeCarrierAnimations(PlayerEntity player) {
		if (!loaded || player == null) {
			return;
		}

		try {
			ensureAnimationControlMethods();
			if (resumeAllCustomAnimationsMethod != null && emfEntityClass != null && emfEntityClass.isInstance(player)) {
				resumeAllCustomAnimationsMethod.invoke(null, player);
			}
		} catch (Throwable throwable) {
			MonvhuaMod.LOGGER.warn("[Monvhua] Failed to resume EMF carrier animations", throwable);
		}
	}

	private static void ensureAnimationControlMethods() throws ClassNotFoundException, NoSuchMethodException {
		if (pauseCustomAnimationsForPartsMethod != null && resumeAllCustomAnimationsMethod != null && emfEntityClass != null) {
			return;
		}

		Class<?> apiClass = Class.forName("traben.entity_model_features.EMFAnimationApi");
		emfEntityClass = Class.forName("traben.entity_model_features.utils.EMFEntity");
		pauseCustomAnimationsForPartsMethod = apiClass.getMethod("pauseCustomAnimationsForThesePartsOfEntity", emfEntityClass, ModelPart[].class);
		resumeAllCustomAnimationsMethod = apiClass.getMethod("resumeAllCustomAnimationsForEntity", emfEntityClass);
		if (!loggedUpperBodyPauseAvailable) {
			MonvhuaMod.LOGGER.info("[Monvhua] EMF carrier upper-body animation pause API is available");
			loggedUpperBodyPauseAvailable = true;
		}
	}

	public record PlayerEntityModelParts(PlayerEntity player, ModelPart body, ModelPart rightArm, ModelPart leftArm) {
	}
}
