package com.kuilunfuzhe.monvhua.network.carryentity;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class CarryTransformPackets {
	public static final int POSE_PRINCESS = 0;
	public static final int POSE_DRAG = 1;
	public static final int TARGET_POSE = 0;
	public static final int TARGET_VIEW = 1;
	public static final int ACTION_SET = 0;
	public static final int ACTION_ADD = 1;
	public static final int ACTION_RESET = 2;
	private static final int MAX_JSON_LENGTH = 8192;

	private CarryTransformPackets() {
	}

	public static void registerS2C() {
		ConfigS2C.register();
	}

	public static void registerC2S() {
		RequestConfigC2S.register();
		UpdateC2S.register();
	}

	public record ConfigS2C(String json) implements CustomPayload {
		public static final Id<ConfigS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "carry_transform_config"));
		public static final PacketCodec<RegistryByteBuf, ConfigS2C> CODEC = PacketCodec.of(ConfigS2C::write, ConfigS2C::new);
		private static boolean registered = false;

		private ConfigS2C(RegistryByteBuf buf) {
			this(buf.readString(MAX_JSON_LENGTH));
		}

		private void write(RegistryByteBuf buf) {
			buf.writeString(json, MAX_JSON_LENGTH);
		}

		public static void register() {
			if (!registered) {
				PayloadTypeRegistry.playS2C().register(ID, CODEC);
				registered = true;
			}
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record RequestConfigC2S() implements CustomPayload {
		public static final Id<RequestConfigC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "request_carry_transform_config"));
		public static final PacketCodec<RegistryByteBuf, RequestConfigC2S> CODEC = PacketCodec.unit(new RequestConfigC2S());
		private static boolean registered = false;

		public static void register() {
			if (!registered) {
				PayloadTypeRegistry.playC2S().register(ID, CODEC);
				registered = true;
			}
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public record UpdateC2S(int poseMode, int target, int action, float x, float y, float z, float pitch, float yaw, float roll) implements CustomPayload {
		public static final Id<UpdateC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "update_carry_transform"));
		public static final PacketCodec<RegistryByteBuf, UpdateC2S> CODEC = PacketCodec.of(UpdateC2S::write, UpdateC2S::new);
		private static boolean registered = false;

		private UpdateC2S(RegistryByteBuf buf) {
			this(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
		}

		private void write(RegistryByteBuf buf) {
			buf.writeVarInt(poseMode);
			buf.writeVarInt(target);
			buf.writeVarInt(action);
			buf.writeFloat(x);
			buf.writeFloat(y);
			buf.writeFloat(z);
			buf.writeFloat(pitch);
			buf.writeFloat(yaw);
			buf.writeFloat(roll);
		}

		public static void register() {
			if (!registered) {
				PayloadTypeRegistry.playC2S().register(ID, CODEC);
				registered = true;
			}
		}

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}
}
