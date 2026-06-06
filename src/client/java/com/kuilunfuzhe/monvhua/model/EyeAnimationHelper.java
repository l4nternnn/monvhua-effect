//package com.kuilunfuzhe.monvhua.model;
//
//import net.minecraft.client.MinecraftClient;
//import net.minecraft.client.model.ModelPart;
//import net.minecraft.client.render.RenderLayer;
//import net.minecraft.client.render.VertexConsumer;
//import net.minecraft.client.render.VertexConsumerProvider;
//import net.minecraft.client.render.entity.model.PlayerEntityModel;
//import net.minecraft.client.util.math.MatrixStack;
//import net.minecraft.util.Identifier;
//
//public final class EyeAnimationHelper {
//    private static final float BLINK_PERIOD_TICKS = 80.0F;
//    private static final float BLINK_DURATION_TICKS = 6.0F;
//    private static final float MIN_VISIBLE_CLOSE_AMOUNT = 0.02F;
//    private static final float MASK_SLIDE_DISTANCE = 2.0F;
//    private static final int CLOSED_EYE_MASK_COLOR = 0xFFFFD0B0;
//    private static final Identifier WHITE_TEXTURE = Identifier.ofVanilla("textures/misc/white.png");
//    private static ClosedEyeMaskPosition maskPosition = ClosedEyeMaskPosition.LOW;
//
//    private EyeAnimationHelper() {
//    }
//
//    public enum ClosedEyeMaskPosition {
//        LOW(CombinedBodyModelData.EYELID_LOW_ORIGIN_Y),
//        HIGH(CombinedBodyModelData.EYELID_HIGH_ORIGIN_Y);
//
//        private final float originY;
//
//        ClosedEyeMaskPosition(float originY) {
//            this.originY = originY;
//        }
//    }
//
//    public static ClosedEyeMaskPosition getMaskPosition() {
//        return maskPosition;
//    }
//
//    public static void setMaskPosition(ClosedEyeMaskPosition position) {
//        maskPosition = position == null ? ClosedEyeMaskPosition.LOW : position;
//    }
//
//    public static ClosedEyeMaskPosition toggleMaskPosition() {
//        maskPosition = maskPosition == ClosedEyeMaskPosition.LOW ? ClosedEyeMaskPosition.HIGH : ClosedEyeMaskPosition.LOW;
//        return maskPosition;
//    }
//
//    public static void applyAutoBlink(ModelPart head) {
//        applyClosedAmount(head, getAutoBlinkAmount());
//    }
//
//    public static void applyClosedAmount(ModelPart head, float closedAmount) {
//        float amount = clamp01(closedAmount);
//        applyClosedEyeMask(head, CombinedBodyModelData.LEFT_EYELID, amount);
//        applyClosedEyeMask(head, CombinedBodyModelData.RIGHT_EYELID, amount);
//    }
//
//    private static void applyClosedEyeMask(ModelPart head, String childName, float amount) {
//        if (head == null || !head.hasChild(childName)) return;
//        ModelPart eyelid = head.getChild(childName);
//        boolean visible = amount > MIN_VISIBLE_CLOSE_AMOUNT;
//        eyelid.visible = false;
//        eyelid.hidden = !visible;
//        eyelid.originY = maskPosition.originY - MASK_SLIDE_DISTANCE * (1.0F - amount);
//        eyelid.xScale = 1.0F;
//        eyelid.yScale = 1.0F;
//        eyelid.zScale = 1.0F;
//    }
//
//    public static boolean renderClosedEyeMasks(PlayerEntityModel model, MatrixStack matrices,
//                                               VertexConsumerProvider vertexConsumers, int light, int overlay) {
//        if (model == null || model.head == null || !model.head.visible) return false;
//        matrices.push();
//        model.getRootPart().applyTransform(matrices);
//        boolean rendered = renderClosedEyeMasks(model.head, matrices, vertexConsumers, light, overlay);
//        matrices.pop();
//        return rendered;
//    }
//
//    public static boolean renderClosedEyeMasks(ModelPart head, MatrixStack matrices,
//                                               VertexConsumerProvider vertexConsumers, int light, int overlay) {
//        if (head == null || vertexConsumers == null) return false;
//        matrices.push();
//        head.applyTransform(matrices);
//        boolean rendered = renderClosedEyeMasksAtHead(head, matrices, vertexConsumers, light, overlay);
//        matrices.pop();
//        return rendered;
//    }
//
//    public static boolean renderClosedEyeMasksAtHead(ModelPart head, MatrixStack matrices,
//                                                     VertexConsumerProvider vertexConsumers, int light, int overlay) {
//        if (head == null || vertexConsumers == null) return false;
//        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(WHITE_TEXTURE));
//        boolean left = renderClosedEyeMask(head, CombinedBodyModelData.LEFT_EYELID, matrices, vertexConsumer, light, overlay);
//        boolean right = renderClosedEyeMask(head, CombinedBodyModelData.RIGHT_EYELID, matrices, vertexConsumer, light, overlay);
//        return left || right;
//    }
//
//    private static boolean renderClosedEyeMask(ModelPart head, String childName, MatrixStack matrices,
//                                               VertexConsumer vertexConsumer, int light, int overlay) {
//        if (!head.hasChild(childName)) return false;
//        ModelPart eyelid = head.getChild(childName);
//        if (eyelid.hidden) return false;
//
//        boolean oldVisible = eyelid.visible;
//        boolean oldHidden = eyelid.hidden;
//        eyelid.visible = true;
//        eyelid.hidden = false;
//        eyelid.render(matrices, vertexConsumer, light, overlay, CLOSED_EYE_MASK_COLOR);
//        eyelid.visible = oldVisible;
//        eyelid.hidden = oldHidden;
//        return true;
//    }
//
//    private static float getAutoBlinkAmount() {
//        MinecraftClient client = MinecraftClient.getInstance();
//        if (client == null || client.world == null) return 0.0F;
//        float tickProgress = client.getRenderTickCounter().getTickProgress(true);
//        float phase = (client.world.getTime() + tickProgress) % BLINK_PERIOD_TICKS;
//        if (phase >= BLINK_DURATION_TICKS) return 0.0F;
//        return (float) Math.sin((phase / BLINK_DURATION_TICKS) * Math.PI);
//    }
//
//    private static float clamp01(float value) {
//        return Math.max(0.0F, Math.min(1.0F, value));
//    }
//}
