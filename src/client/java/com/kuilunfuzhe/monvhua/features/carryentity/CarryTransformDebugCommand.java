package com.kuilunfuzhe.monvhua.features.carryentity;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.kuilunfuzhe.monvhua.network.SafeClientNetworking;
import com.kuilunfuzhe.monvhua.network.carryentity.CarryTransformPackets;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public final class CarryTransformDebugCommand {
	private static final Logger LOGGER = LoggerFactory.getLogger(CarryTransformDebugCommand.class);
	private static final String COMMAND_NAME = "monvhua_carry_debug_transform";
	private static final String POSE_TRANSFORM_COMMAND = "monvhua_carry_pose_transform";
	private static final String VIEW_TRANSFORM_COMMAND = "monvhua_carry_view_transform";

	private CarryTransformDebugCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register(CarryTransformDebugCommand::registerCommands);
	}

	private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, Object registryAccess) {
		dispatcher.register(ClientCommandManager.literal(COMMAND_NAME)
				.executes(context -> dumpCurrentTransform(context.getSource())));
		dispatcher.register(buildTransformCommand(POSE_TRANSFORM_COMMAND, CarryTransformPackets.TARGET_POSE));
		dispatcher.register(buildTransformCommand(VIEW_TRANSFORM_COMMAND, CarryTransformPackets.TARGET_VIEW));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> buildTransformCommand(String name, int target) {
		LiteralArgumentBuilder<FabricClientCommandSource> root = addTransformActions(
				ClientCommandManager.literal(name),
				CarryTransformPackets.POSE_PRINCESS,
				target
		);
		root.then(addTransformActions(ClientCommandManager.literal("princess"), CarryTransformPackets.POSE_PRINCESS, target));
		root.then(addTransformActions(ClientCommandManager.literal("drag"), CarryTransformPackets.POSE_DRAG, target));
		return root;
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> addTransformActions(
			LiteralArgumentBuilder<FabricClientCommandSource> root,
			int poseMode,
			int target
	) {
		return root
				.then(buildTransformAction("set", poseMode, target, CarryTransformPackets.ACTION_SET))
				.then(buildTransformAction("add", poseMode, target, CarryTransformPackets.ACTION_ADD))
				.then(ClientCommandManager.literal("reset")
						.executes(context -> sendTransformUpdate(
								context.getSource(),
								poseMode,
								target,
								CarryTransformPackets.ACTION_RESET,
								0.0F,
								0.0F,
								0.0F,
								0.0F,
								0.0F,
								0.0F
						)))
				.executes(context -> showTransform(context.getSource(), poseMode, target));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> buildTransformAction(String name, int poseMode, int target, int action) {
		return ClientCommandManager.literal(name)
				.then(ClientCommandManager.argument("x", FloatArgumentType.floatArg())
						.then(ClientCommandManager.argument("y", FloatArgumentType.floatArg())
								.then(ClientCommandManager.argument("z", FloatArgumentType.floatArg())
										.then(ClientCommandManager.argument("pitch", FloatArgumentType.floatArg())
												.then(ClientCommandManager.argument("yaw", FloatArgumentType.floatArg())
														.then(ClientCommandManager.argument("roll", FloatArgumentType.floatArg())
																.executes(context -> sendTransformUpdate(
																		context.getSource(),
																		poseMode,
																		target,
																		action,
																		FloatArgumentType.getFloat(context, "x"),
																		FloatArgumentType.getFloat(context, "y"),
																		FloatArgumentType.getFloat(context, "z"),
																		FloatArgumentType.getFloat(context, "pitch"),
																		FloatArgumentType.getFloat(context, "yaw"),
																		FloatArgumentType.getFloat(context, "roll")
																))))))));
	}

	private static int sendTransformUpdate(FabricClientCommandSource source, int poseMode, int target, int action, float x, float y, float z, float pitch, float yaw, float roll) {
		boolean sent = SafeClientNetworking.send(new CarryTransformPackets.UpdateC2S(poseMode, target, action, x, y, z, pitch, yaw, roll));
		if (!sent) {
			source.sendFeedback(Text.literal("Unable to send carry transform update to server."));
			return 0;
		}
		source.sendFeedback(Text.literal("Carry " + CarryTransformConfig.poseModeName(poseMode) + " " + transformTargetName(target) + " transform update sent to server."));
		return 1;
	}

	private static int showTransform(FabricClientCommandSource source, int poseMode, int target) {
		boolean dragPose = CarryTransformConfig.sanitizePoseMode(poseMode) == CarryTransformPackets.POSE_DRAG;
		source.sendFeedback(Text.literal("Carry " + CarryTransformConfig.poseModeName(poseMode) + " " + transformTargetName(target) + " transform: " + formatCurrentTransform(dragPose, target)));
		return 1;
	}

	private static String formatCurrentTransform(boolean dragPose, int target) {
		if (target == CarryTransformPackets.TARGET_VIEW) {
			return "enabled=" + CarryPoseTuning.isDefaultViewTransformEnabled(dragPose) + " " + formatSix(
					CarryPoseTuning.getDefaultViewOffsetX(dragPose),
					CarryPoseTuning.getDefaultViewOffsetY(dragPose),
					CarryPoseTuning.getDefaultViewOffsetZ(dragPose),
					CarryPoseTuning.getDefaultViewPitchDegrees(dragPose),
					CarryPoseTuning.getDefaultViewYawDegrees(dragPose),
					CarryPoseTuning.getDefaultViewRollDegrees(dragPose)
			);
		}
		return formatSix(
				CarryPoseTuning.getCarriedPoseOffsetX(dragPose),
				CarryPoseTuning.getCarriedPoseOffsetY(dragPose),
				CarryPoseTuning.getCarriedPoseOffsetZ(dragPose),
				CarryPoseTuning.getCarriedPosePitchDegrees(dragPose),
				CarryPoseTuning.getCarriedPoseYawDegrees(dragPose),
				CarryPoseTuning.getCarriedPoseRollDegrees(dragPose)
		);
	}

	private static String transformTargetName(int target) {
		return target == CarryTransformPackets.TARGET_VIEW ? "view" : "pose";
	}

	private static int dumpCurrentTransform(FabricClientCommandSource source) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null) {
			source.sendFeedback(Text.literal("§c当前没有客户端世界或玩家。"));
			return 0;
		}

		Entity carried = client.player;
		if (!CarryPoseClientState.isCarried(carried.getId())) {
			source.sendFeedback(Text.literal("§c当前玩家不是被抱状态。"));
			return 0;
		}

		int carrierId = CarryPoseClientState.getPartnerId(carried.getId());
		Entity carrier = carrierId >= 0 ? client.world.getEntityById(carrierId) : null;
		if (carrier == null) {
			source.sendFeedback(Text.literal("§c找不到抱人者实体。carrierId=" + carrierId));
			return 0;
		}

		float tickProgress = client.getRenderTickCounter().getTickProgress(true);
		float localYaw = CarriedPlayerViewState.getLocalViewYawDegrees();
		float localPitch = CarriedPlayerViewState.getLocalViewPitchDegrees();

		CarryAttachedRenderMath.DebugTransform baseHeadTransform = CarryAttachedRenderMath.getCarriedBaseHeadDebugTransform(
				carrier,
				carried,
				tickProgress
		);
		CarryAttachedRenderMath.DebugTransform localViewTransform = CarryAttachedRenderMath.getCarriedLocalViewDebugTransform(
				carrier,
				carried,
				tickProgress,
				localYaw,
				localPitch
		);
		CarryAttachedRenderMath.CarriedCameraOrientation expectedOrientation = CarryAttachedRenderMath.getCarriedLocalViewCameraOrientation(
				carrier,
				carried,
				tickProgress,
				localYaw,
				localPitch
		);
		Vec3d expectedCameraPos = CarryAttachedRenderMath.getCarriedCameraHeadWorldPos(carrier, carried, tickProgress);

		Camera camera = client.gameRenderer.getCamera();
		String output = formatDebugOutput(
				client,
				carried,
				carrier,
				tickProgress,
				localYaw,
				localPitch,
				baseHeadTransform,
				localViewTransform,
				expectedOrientation,
				expectedCameraPos,
				camera
		);

		LOGGER.info("{}", output);
		copyToClipboard(client, output);
		source.sendFeedback(Text.literal("§a已输出被抱视角/头部 transform 信息到 latest.log，并已复制到剪贴板。"));
		source.sendFeedback(Text.literal("§7请把剪贴板内容或 latest.log 中 [MONVHUA_CARRY_TRANSFORM_DEBUG] 这一段发给我。"));
		return 1;
	}

	private static String formatDebugOutput(
			MinecraftClient client,
			Entity carried,
			Entity carrier,
			float tickProgress,
			float localYaw,
			float localPitch,
			CarryAttachedRenderMath.DebugTransform baseHeadTransform,
			CarryAttachedRenderMath.DebugTransform localViewTransform,
			CarryAttachedRenderMath.CarriedCameraOrientation expectedOrientation,
			Vec3d expectedCameraPos,
			Camera camera
	) {
		StringBuilder builder = new StringBuilder();
		builder.append("\n[MONVHUA_CARRY_TRANSFORM_DEBUG]\n");
		builder.append("tickProgress=").append(f(tickProgress)).append('\n');
		builder.append("carried.id=").append(carried.getId()).append(" carried.yaw=").append(f(carried.getYaw())).append(" carried.pitch=").append(f(carried.getPitch())).append(" carried.height=").append(f(carried.getHeight())).append('\n');
		builder.append("carrier.id=").append(carrier.getId()).append(" carrier.yaw=").append(f(carrier.getYaw())).append(" carrier.pitch=").append(f(carrier.getPitch())).append(" carrier.height=").append(f(carrier.getHeight())).append('\n');
		builder.append("localViewYawDegrees=").append(f(localYaw)).append(" localViewPitchDegrees=").append(f(localPitch)).append('\n');
		builder.append('\n');
		builder.append("tuning.attached=(baseX=").append(f(CarryAttachedRenderMath.ATTACHED_CARRIED_X))
				.append(", scaledX=").append(f(CarryAttachedRenderMath.getAttachedCarriedX(carrier, carried)))
				.append(", baseY=").append(f(CarryAttachedRenderMath.ATTACHED_CARRIED_Y))
				.append(", scaledY=").append(f(CarryAttachedRenderMath.getAttachedCarriedY(carrier, carried)))
				.append(", z=").append(f(CarryAttachedRenderMath.ATTACHED_CARRIED_Z))
				.append(", yawDeg=").append(f(CarryAttachedRenderMath.ATTACHED_CARRIED_YAW_DEGREES)).append(")\n");
		builder.append("tuning.scaleCompensation=(referencePlayerHeight=").append(f(CarryAttachedRenderMath.REFERENCE_PLAYER_HEIGHT))
				.append(", carrierX=").append(f(CarryAttachedRenderMath.CARRIER_SCALE_SIDE_COMPENSATION))
				.append(", carriedX=").append(f(CarryAttachedRenderMath.CARRIED_SCALE_SIDE_COMPENSATION))
				.append(", carrierY=").append(f(CarryAttachedRenderMath.CARRIER_SCALE_HEIGHT_COMPENSATION))
				.append(", carriedY=").append(f(CarryAttachedRenderMath.CARRIED_SCALE_HEIGHT_COMPENSATION)).append(")\n");
		builder.append("tuning.modelRotation=(xDeg=").append(f(CarryAttachedRenderMath.CARRIED_MODEL_ROTATION_X_DEGREES))
				.append(", yDeg=").append(f(CarryAttachedRenderMath.CARRIED_MODEL_ROTATION_Y_DEGREES))
				.append(", zDeg=").append(f(CarryAttachedRenderMath.CARRIED_MODEL_ROTATION_Z_DEGREES)).append(")\n");
		builder.append("tuning.poseTransform=").append(formatSix(
				CarryPoseTuning.CARRIED_POSE_OFFSET_X,
				CarryPoseTuning.CARRIED_POSE_OFFSET_Y,
				CarryPoseTuning.CARRIED_POSE_OFFSET_Z,
				CarryPoseTuning.CARRIED_POSE_PITCH_DEGREES,
				CarryPoseTuning.CARRIED_POSE_YAW_DEGREES,
				CarryPoseTuning.CARRIED_POSE_ROLL_DEGREES)).append('\n');
		builder.append("tuning.cameraLocal=(x=").append(f(CarryAttachedRenderMath.CARRIED_CAMERA_HEAD_LOCAL_X))
				.append(", y=").append(f(CarryAttachedRenderMath.CARRIED_CAMERA_HEAD_LOCAL_Y))
				.append(", z=").append(f(CarryAttachedRenderMath.CARRIED_CAMERA_HEAD_LOCAL_Z)).append(")\n");
		builder.append("tuning.defaultViewTransform=(enabled=").append(CarryPoseTuning.CARRIED_DEFAULT_VIEW_TRANSFORM_ENABLED)
				.append(", ").append(formatSix(
						CarryPoseTuning.CARRIED_DEFAULT_VIEW_OFFSET_X,
						CarryPoseTuning.CARRIED_DEFAULT_VIEW_OFFSET_Y,
						CarryPoseTuning.CARRIED_DEFAULT_VIEW_OFFSET_Z,
						CarryPoseTuning.CARRIED_DEFAULT_VIEW_PITCH_DEGREES,
						CarryPoseTuning.CARRIED_DEFAULT_VIEW_YAW_DEGREES,
						CarryPoseTuning.CARRIED_DEFAULT_VIEW_ROLL_DEGREES)).append(")\n");
		builder.append("tuning.headBaseRadians=(yaw=").append(f(CarryPoseTuning.HEAD_YAW + CarryPoseTuning.CUSTOM_HEAD_YAW))
				.append(", pitch=").append(f(CarryPoseTuning.HEAD_PITCH + CarryPoseTuning.CUSTOM_HEAD_PITCH))
				.append(", roll=").append(f(CarryPoseTuning.HEAD_ROLL + CarryPoseTuning.CUSTOM_HEAD_ROLL)).append(")\n");
		builder.append("tuning.viewCenterDegrees=(yaw=").append(f(CarryPoseTuning.CARRIED_VIEW_CENTER_YAW_OFFSET_DEGREES))
				.append(", pitch=").append(f(CarryPoseTuning.CARRIED_VIEW_CENTER_PITCH_OFFSET_DEGREES)).append(")\n");
		builder.append('\n');
		appendDebugTransform(builder, "baseHeadTransform", baseHeadTransform);
		appendDebugTransform(builder, "localViewTransform", localViewTransform);
		builder.append("expectedCameraPos=").append(v(expectedCameraPos)).append('\n');
		builder.append("expectedCameraYawPitch=(yaw=").append(f(expectedOrientation.yawDegrees()))
				.append(", pitch=").append(f(expectedOrientation.pitchDegrees())).append(")\n");
		builder.append("expectedCameraQuaternion=").append(q(expectedOrientation.rotation())).append('\n');
		builder.append("expectedCameraAxes=(horizontal/forward=").append(v(expectedOrientation.horizontalPlane()))
				.append(", vertical/up=").append(v(expectedOrientation.verticalPlane()))
				.append(", diagonal/left=").append(v(expectedOrientation.diagonalPlane())).append(")\n");
		builder.append('\n');
		builder.append("actualCameraPos=").append(v(camera.getPos())).append('\n');
		builder.append("actualCameraYawPitch=(yaw=").append(f(camera.getYaw())).append(", pitch=").append(f(camera.getPitch())).append(")\n");
		builder.append("windowSize=").append(client.getWindow().getWidth()).append('x').append(client.getWindow().getHeight()).append('\n');
		builder.append("[/MONVHUA_CARRY_TRANSFORM_DEBUG]");
		return builder.toString();
	}

	private static void appendDebugTransform(StringBuilder builder, String label, CarryAttachedRenderMath.DebugTransform transform) {
		builder.append(label).append(".pos=").append(v(transform.position())).append('\n');
		builder.append(label).append(".axes=(right=").append(v(transform.right()))
				.append(", up=").append(v(transform.up()))
				.append(", forward=").append(v(transform.forward())).append(")\n");
		builder.append(label).append(".quaternion=").append(q(transform.rotation())).append('\n');
		builder.append(label).append(".matrix=").append(m(transform.matrix())).append('\n');
	}

	private static void copyToClipboard(MinecraftClient client, String text) {
		GLFW.glfwSetClipboardString(client.getWindow().getHandle(), text);
	}

	private static String formatSix(float x, float y, float z, float pitch, float yaw, float roll) {
		return String.format(Locale.ROOT, "offset=(%.4f, %.4f, %.4f), rotation=(pitch=%.4f, yaw=%.4f, roll=%.4f)", x, y, z, pitch, yaw, roll);
	}

	private static String f(float value) {
		return String.format(Locale.ROOT, "%.6f", value);
	}

	private static String f(double value) {
		return String.format(Locale.ROOT, "%.6f", value);
	}

	private static String v(Vec3d value) {
		return String.format(Locale.ROOT, "(%.6f, %.6f, %.6f)", value.x, value.y, value.z);
	}

	private static String v(Vector3f value) {
		return String.format(Locale.ROOT, "(%.6f, %.6f, %.6f)", value.x, value.y, value.z);
	}

	private static String q(Quaternionf value) {
		return String.format(Locale.ROOT, "(x=%.6f, y=%.6f, z=%.6f, w=%.6f)", value.x, value.y, value.z, value.w);
	}

	private static String m(Matrix4f matrix) {
		return String.format(
				Locale.ROOT,
				"[[%.6f, %.6f, %.6f, %.6f], [%.6f, %.6f, %.6f, %.6f], [%.6f, %.6f, %.6f, %.6f], [%.6f, %.6f, %.6f, %.6f]]",
				matrix.m00(), matrix.m10(), matrix.m20(), matrix.m30(),
				matrix.m01(), matrix.m11(), matrix.m21(), matrix.m31(),
				matrix.m02(), matrix.m12(), matrix.m22(), matrix.m32(),
				matrix.m03(), matrix.m13(), matrix.m23(), matrix.m33()
		);
	}
}
