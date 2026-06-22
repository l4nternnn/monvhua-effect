package com.kuilunfuzhe.monvhua.features.carryentity;

public final class CarryAttachmentRenderState {
	private static int attachedRenderDepth;
	private static int firstPersonSelfCarriedRenderDepth;
	private static int attachedCarriedEntityId = -1;

	private CarryAttachmentRenderState() {
	}

	public static boolean isRenderingAttachedCarriedEntity() {
		return attachedRenderDepth > 0;
	}

	public static boolean isRenderingFirstPersonSelfCarriedEntity() {
		return firstPersonSelfCarriedRenderDepth > 0;
	}

	public static int getRenderingAttachedCarriedEntityId() {
		return attachedCarriedEntityId;
	}

	public static void beginAttachedCarriedEntityRender() {
		beginAttachedCarriedEntityRender(-1);
	}

	public static void beginAttachedCarriedEntityRender(int carriedEntityId) {
		if (attachedRenderDepth == 0) {
			attachedCarriedEntityId = carriedEntityId;
		}
		attachedRenderDepth++;
	}

	public static void beginFirstPersonSelfCarriedEntityRender() {
		beginFirstPersonSelfCarriedEntityRender(-1);
	}

	public static void beginFirstPersonSelfCarriedEntityRender(int carriedEntityId) {
		beginAttachedCarriedEntityRender(carriedEntityId);
		firstPersonSelfCarriedRenderDepth++;
	}

	public static void endAttachedCarriedEntityRender() {
		if (attachedRenderDepth > 0) {
			attachedRenderDepth--;
		}
		if (attachedRenderDepth == 0) {
			attachedCarriedEntityId = -1;
		}
	}

	public static void endFirstPersonSelfCarriedEntityRender() {
		if (firstPersonSelfCarriedRenderDepth > 0) {
			firstPersonSelfCarriedRenderDepth--;
		}
		endAttachedCarriedEntityRender();
	}
}
