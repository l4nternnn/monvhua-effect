package com.kuilunfuzhe.monvhua.compat;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.features.carryentity.CarryPoseClientState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.model.ModelPart;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * Entity Model Features compatibility detection.
 */
public final class EmfCompat {
	private static boolean loaded;

	private static Method pauseCustomAnimationsForPartsMethod;
	private static Method resumeAllCustomAnimationsMethod;
	private static Class<?> emfEntityClass;
	private static Method emfEntityEntityMethod;
	private static Field emfEntityEntityField;
	private static final Map<PlayerEntity, Object> EMF_ENTITIES_BY_PLAYER = new WeakHashMap<>();
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
		PlayerEntity player = extractPlayer(emfEntity);
		cacheEmfEntity(player, emfEntity);
		// 被抱者需要整个人采用 vanilla 模型，避免 EMF/ETF 的自定义模型绕开我们的横抱姿势。
		// 抱人者不能强制 vanilla，也不能调用 EMF pause；抱人者只在 EMF 自定义手臂部件渲染时做临时矩阵覆盖。
		return player != null && CarryPoseClientState.isCarried(player.getId());
	}

	public static void pauseCarrierUpperBodyAnimations(PlayerEntityModelParts parts, int entityId) {
		if (!loaded || parts == null) {
			return;
		}
		resumeCarrierAnimations(parts.player());
	}

	public static void resumeCarrierAnimations(PlayerEntity player) {
		if (!loaded || player == null) {
			return;
		}

		try {
			ensureAnimationControlMethods();
			Object emfEntity = asEmfEntity(player);
			if (resumeAllCustomAnimationsMethod != null && emfEntity != null) {
				resumeAllCustomAnimationsMethod.invoke(null, emfEntity);
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
		cacheEmfEntityAccessors();
		if (!loggedUpperBodyPauseAvailable) {
			MonvhuaMod.LOGGER.info("[Monvhua] EMF carrier upper-body animation pause API is available");
			loggedUpperBodyPauseAvailable = true;
		}
	}

	private static Object asEmfEntity(PlayerEntity player) {
		if (emfEntityClass == null || player == null) {
			return null;
		}
		if (emfEntityClass.isInstance(player)) {
			return player;
		}
		return EMF_ENTITIES_BY_PLAYER.get(player);
	}

	private static ModelPart[] collectCarrierArmParts(PlayerEntityModelParts parts) {
		List<ModelPart> modelParts = new ArrayList<>();
		addPartAndEmfCustomChildren(modelParts, parts.rightArm());
		addPartAndEmfCustomChildren(modelParts, parts.leftArm());
		return modelParts.toArray(ModelPart[]::new);
	}

	private static void addPartAndEmfCustomChildren(List<ModelPart> modelParts, ModelPart part) {
		if (part == null || modelParts.contains(part)) {
			return;
		}
		modelParts.add(part);
		try {
			Method getAllEMFCustomChildren = part.getClass().getMethod("getAllEMFCustomChildren");
			Object children = getAllEMFCustomChildren.invoke(part);
			if (children instanceof ModelPart[] childParts) {
				for (ModelPart child : childParts) {
					addPartAndEmfCustomChildren(modelParts, child);
				}
			}
		} catch (NoSuchMethodException ignored) {
			// Vanilla ModelPart has no EMF custom children.
		} catch (Throwable throwable) {
			MonvhuaMod.LOGGER.debug("[Monvhua] Failed to collect EMF custom arm children", throwable);
		}
	}

	private static void cacheEmfEntity(PlayerEntity player, Object emfEntity) {
		if (player == null || emfEntityClass == null || !emfEntityClass.isInstance(emfEntity)) {
			return;
		}
		EMF_ENTITIES_BY_PLAYER.put(player, emfEntity);
	}

	private static PlayerEntity extractPlayer(Object emfEntity) {
		if (emfEntity instanceof PlayerEntity player) {
			return player;
		}
		Entity entity = extractEntity(emfEntity);
		return entity instanceof PlayerEntity player ? player : null;
	}

	private static Entity extractEntity(Object emfEntity) {
		if (emfEntity == null) {
			return null;
		}
		try {
			ensureEmfEntityAccessorsFor(emfEntity);
			if (emfEntityEntityMethod != null) {
				Object entity = emfEntityEntityMethod.invoke(emfEntity);
				return entity instanceof Entity minecraftEntity ? minecraftEntity : null;
			}
			if (emfEntityEntityField != null) {
				Object entity = emfEntityEntityField.get(emfEntity);
				return entity instanceof Entity minecraftEntity ? minecraftEntity : null;
			}
		} catch (Throwable ignored) {
			return null;
		}
		return null;
	}

	private static void ensureEmfEntityAccessorsFor(Object emfEntity) throws ClassNotFoundException {
		if (emfEntityClass == null) {
			emfEntityClass = Class.forName("traben.entity_model_features.utils.EMFEntity");
		}
		if (!emfEntityClass.isInstance(emfEntity)) {
			return;
		}
		cacheEmfEntityAccessors();
	}

	private static void cacheEmfEntityAccessors() {
		if (emfEntityClass == null || emfEntityEntityMethod != null || emfEntityEntityField != null) {
			return;
		}
		emfEntityEntityMethod = findEntityGetter(emfEntityClass);
		if (emfEntityEntityMethod != null) {
			return;
		}
		emfEntityEntityField = findEntityField(emfEntityClass);
	}

	private static Method findEntityGetter(Class<?> emfEntityType) {
		for (Method method : emfEntityType.getMethods()) {
			if (method.getParameterCount() == 0 && Entity.class.isAssignableFrom(method.getReturnType())) {
				method.setAccessible(true);
				return method;
			}
		}
		return null;
	}

	private static Field findEntityField(Class<?> emfEntityType) {
		Class<?> current = emfEntityType;
		while (current != null) {
			for (Field field : current.getDeclaredFields()) {
				if (Entity.class.isAssignableFrom(field.getType())) {
					field.setAccessible(true);
					return field;
				}
			}
			current = current.getSuperclass();
		}
		return null;
	}

	public record PlayerEntityModelParts(PlayerEntity player, ModelPart body, ModelPart rightArm, ModelPart leftArm) {
	}
}
