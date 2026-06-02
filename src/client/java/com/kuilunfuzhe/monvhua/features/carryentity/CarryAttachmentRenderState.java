package com.kuilunfuzhe.monvhua.features.carryentity;

public final class CarryAttachmentRenderState {
	private static int attachedRenderDepth;

	private CarryAttachmentRenderState() {
	}

	public static boolean isRenderingAttachedCarriedEntity() {
		return attachedRenderDepth > 0;
	}

	public static void beginAttachedCarriedEntityRender() {
		attachedRenderDepth++;
	}

	public static void endAttachedCarriedEntityRender() {
		if (attachedRenderDepth > 0) {
			attachedRenderDepth--;
		}
	}
}
