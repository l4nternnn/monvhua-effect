package com.kuilunfuzhe.monvhua.features.carryentity;

public final class CarryAttachmentRenderState {
	private static int attachedRenderDepth;
	private static int firstPersonSelfCarriedRenderDepth;

	private CarryAttachmentRenderState() {
	}

	public static boolean isRenderingAttachedCarriedEntity() {
		return attachedRenderDepth > 0;
	}

	public static boolean isRenderingFirstPersonSelfCarriedEntity() {
		return firstPersonSelfCarriedRenderDepth > 0;
	}

	public static void beginAttachedCarriedEntityRender() {
		attachedRenderDepth++;
	}

	public static void beginFirstPersonSelfCarriedEntityRender() {
		beginAttachedCarriedEntityRender();
		firstPersonSelfCarriedRenderDepth++;
	}

	public static void endAttachedCarriedEntityRender() {
		if (attachedRenderDepth > 0) {
			attachedRenderDepth--;
		}
	}

	public static void endFirstPersonSelfCarriedEntityRender() {
		if (firstPersonSelfCarriedRenderDepth > 0) {
			firstPersonSelfCarriedRenderDepth--;
		}
		endAttachedCarriedEntityRender();
	}
}
