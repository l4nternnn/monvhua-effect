package com.kuilunfuzhe.monvhua.features.carryentity;

import com.mojang.brigadier.CommandDispatcher;
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

	private CarryTransformDebugCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register(CarryTransformDebugCommand::registerCommands);
	}

	private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, Object registryAccess) {
		dispatcher.register(ClientCommandManager.literal(COMMAND_NAME)
				.executes(context -> dumpCurrentTransform(context.getSource())));
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
		builder.append("tuning.cameraLocal=(x=").append(f(CarryAttachedRenderMath.CARRIED_CAMERA_HEAD_LOCAL_X))
				.append(", y=").append(f(CarryAttachedRenderMath.CARRIED_CAMERA_HEAD_LOCAL_Y))
				.append(", z=").append(f(CarryAttachedRenderMath.CARRIED_CAMERA_HEAD_LOCAL_Z)).append(")\n");
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
