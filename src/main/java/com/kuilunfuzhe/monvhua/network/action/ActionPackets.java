package com.kuilunfuzhe.monvhua.network.action;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class ActionPackets {
    private ActionPackets() {
    }

    public static void registerC2S() {
        RequestActionsConfigC2S.register();
        UpdateActionsConfigC2S.register();
        PreviewActionC2S.register();
        PreviewTimelineC2S.register();
        TimelineControlC2S.register();
        ListActionFilesC2S.register();
        LoadActionFileC2S.register();
    }

    public static void registerS2C() {
        ActionsConfigS2C.register();
        ActionFilesListS2C.register();
        PreviewResultS2C.register();
        PreviewTimelineResultS2C.register();
        TimelineStateS2C.register();
        ActionPoseS2C.register();
    }

    public record ActionFilesListS2C(List<String> files) implements CustomPayload {
        public static final Id<ActionFilesListS2C> ID = new Id<>(Identifier.of("monvhua", "action_files_list"));
        public static final PacketCodec<RegistryByteBuf, ActionFilesListS2C> CODEC = PacketCodec.of(ActionFilesListS2C::write, ActionFilesListS2C::new);
        private static boolean registered = false;

        private ActionFilesListS2C(RegistryByteBuf buf) {
            this(new ArrayList<>());
            int count = buf.readVarInt();
            for (int i = 0; i < count; i++) files.add(buf.readString());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeVarInt(files.size());
            for (String f : files) buf.writeString(f);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playS2C().register(ID, CODEC);
                registered = true;
            }
        }
    }

    public record ActionPoseS2C(int entityId, float[] poseValues, int durationTicks) implements CustomPayload {
        public static final Id<ActionPoseS2C> ID = new Id<>(Identifier.of("monvhua", "action_pose"));
        public static final PacketCodec<RegistryByteBuf, ActionPoseS2C> CODEC = PacketCodec.of(
                ActionPoseS2C::write, ActionPoseS2C::new);
        private static boolean registered = false;

        public ActionPoseS2C {
            poseValues = normalize(poseValues);
        }

        private ActionPoseS2C(RegistryByteBuf buf) {
            this(buf.readVarInt(), readPose(buf), buf.readVarInt());
        }

        private static float[] readPose(RegistryByteBuf buf) {
            float[] values = new float[18];
            for (int i = 0; i < values.length; i++) {
                values[i] = buf.readFloat();
            }
            return values;
        }

        private void write(RegistryByteBuf buf) {
            buf.writeVarInt(entityId);
            for (float value : poseValues) {
                buf.writeFloat(value);
            }
            buf.writeVarInt(durationTicks);
        }

        private static float[] normalize(float[] values) {
            float[] normalized = new float[18];
            if (values != null) {
                System.arraycopy(values, 0, normalized, 0, Math.min(values.length, normalized.length));
            }
            return normalized;
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playS2C().register(ID, CODEC);
                registered = true;
            }
        }
    }

    public record ActionsConfigS2C(String json) implements CustomPayload {
        public static final Id<ActionsConfigS2C> ID = new Id<>(Identifier.of("monvhua", "actions_config_sync"));
        public static final PacketCodec<RegistryByteBuf, ActionsConfigS2C> CODEC = PacketCodec.of(ActionsConfigS2C::write, ActionsConfigS2C::new);
        private static boolean registered = false;

        private ActionsConfigS2C(RegistryByteBuf buf) {
            this(buf.readString());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeString(json);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playS2C().register(ID, CODEC);
                registered = true;
            }
        }
    }

    public record ListActionFilesC2S() implements CustomPayload {
        public static final Id<ListActionFilesC2S> ID = new Id<>(Identifier.of("monvhua", "list_action_files"));
        public static final PacketCodec<RegistryByteBuf, ListActionFilesC2S> CODEC = PacketCodec.unit(new ListActionFilesC2S());
        private static boolean registered = false;

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }
    }

    public record LoadActionFileC2S(String filename) implements CustomPayload {
        public static final Id<LoadActionFileC2S> ID = new Id<>(Identifier.of("monvhua", "load_action_file"));
        public static final PacketCodec<RegistryByteBuf, LoadActionFileC2S> CODEC = PacketCodec.of(LoadActionFileC2S::write, LoadActionFileC2S::new);
        private static boolean registered = false;

        private LoadActionFileC2S(RegistryByteBuf buf) {
            this(buf.readString());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeString(filename);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }
    }

    public record PreviewActionC2S(String actionJson) implements CustomPayload {
        public static final Id<PreviewActionC2S> ID = new Id<>(Identifier.of("monvhua", "preview_action"));
        public static final PacketCodec<RegistryByteBuf, PreviewActionC2S> CODEC = PacketCodec.of(PreviewActionC2S::write, PreviewActionC2S::new);
        private static boolean registered = false;

        private PreviewActionC2S(RegistryByteBuf buf) {
            this(buf.readString());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeString(actionJson);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }
    }

    public record PreviewResultS2C(String actionId, String previewText) implements CustomPayload {
        public static final Id<PreviewResultS2C> ID = new Id<>(Identifier.of("monvhua", "preview_result"));
        public static final PacketCodec<RegistryByteBuf, PreviewResultS2C> CODEC = PacketCodec.of(PreviewResultS2C::write, PreviewResultS2C::new);
        private static boolean registered = false;

        private PreviewResultS2C(RegistryByteBuf buf) {
            this(buf.readString(), buf.readString());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeString(actionId);
            buf.writeString(previewText);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playS2C().register(ID, CODEC);
                registered = true;
            }
        }
    }

    public record PreviewTimelineC2S() implements CustomPayload {
        public static final Id<PreviewTimelineC2S> ID = new Id<>(Identifier.of("monvhua", "preview_timeline"));
        public static final PacketCodec<RegistryByteBuf, PreviewTimelineC2S> CODEC = PacketCodec.unit(new PreviewTimelineC2S());
        private static boolean registered = false;

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }
    }

    public record PreviewTimelineResultS2C(List<PreviewEntry> entries) implements CustomPayload {
        public record PreviewEntry(int second, String actionId, String previewText, String actionType) {
        }

        public static final Id<PreviewTimelineResultS2C> ID = new Id<>(Identifier.of("monvhua", "preview_timeline_result"));
        public static final PacketCodec<RegistryByteBuf, PreviewTimelineResultS2C> CODEC = PacketCodec.of(
                PreviewTimelineResultS2C::write, PreviewTimelineResultS2C::new);
        private static boolean registered = false;

        private PreviewTimelineResultS2C(RegistryByteBuf buf) {
            this(readEntries(buf));
        }

        private static List<PreviewEntry> readEntries(RegistryByteBuf buf) {
            int size = buf.readVarInt();
            List<PreviewEntry> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                list.add(new PreviewEntry(buf.readVarInt(), buf.readString(), buf.readString(), buf.readString()));
            }
            return list;
        }

        private void write(RegistryByteBuf buf) {
            buf.writeVarInt(entries.size());
            for (PreviewEntry e : entries) {
                buf.writeVarInt(e.second);
                buf.writeString(e.actionId);
                buf.writeString(e.previewText);
                buf.writeString(e.actionType);
            }
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playS2C().register(ID, CODEC);
                registered = true;
            }
        }
    }

    public record RequestActionsConfigC2S() implements CustomPayload {
        public static final Id<RequestActionsConfigC2S> ID = new Id<>(Identifier.of("monvhua", "request_actions_config"));
        public static final PacketCodec<RegistryByteBuf, RequestActionsConfigC2S> CODEC = PacketCodec.unit(new RequestActionsConfigC2S());
        private static boolean registered = false;

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }
    }

    public record TimelineControlC2S(String action, int second, String actionId) implements CustomPayload {
        public static final Id<TimelineControlC2S> ID = new Id<>(Identifier.of("monvhua", "timeline_control"));
        public static final PacketCodec<RegistryByteBuf, TimelineControlC2S> CODEC = PacketCodec.of(
                TimelineControlC2S::write, TimelineControlC2S::new);
        private static boolean registered = false;

        private TimelineControlC2S(RegistryByteBuf buf) {
            this(buf.readString(), buf.readVarInt(), buf.readString());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeString(action);
            buf.writeVarInt(second);
            buf.writeString(actionId != null ? actionId : "");
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }
    }

    public record TimelineStateS2C(int currentSecond, boolean running, boolean paused, boolean loop, int totalSeconds) implements CustomPayload {
        public static final Id<TimelineStateS2C> ID = new Id<>(Identifier.of("monvhua", "timeline_state"));
        public static final PacketCodec<RegistryByteBuf, TimelineStateS2C> CODEC = PacketCodec.of(
                TimelineStateS2C::write, TimelineStateS2C::new);
        private static boolean registered = false;

        private TimelineStateS2C(RegistryByteBuf buf) {
            this(buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeVarInt(currentSecond);
            buf.writeBoolean(running);
            buf.writeBoolean(paused);
            buf.writeBoolean(loop);
            buf.writeVarInt(totalSeconds);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playS2C().register(ID, CODEC);
                registered = true;
            }
        }
    }

    public record UpdateActionsConfigC2S(String json) implements CustomPayload {
        public static final Id<UpdateActionsConfigC2S> ID = new Id<>(Identifier.of("monvhua", "update_actions_config"));
        public static final PacketCodec<RegistryByteBuf, UpdateActionsConfigC2S> CODEC = PacketCodec.of(UpdateActionsConfigC2S::write, UpdateActionsConfigC2S::new);
        private static boolean registered = false;

        private UpdateActionsConfigC2S(RegistryByteBuf buf) {
            this(buf.readString());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeString(json);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        public static void register() {
            if (!registered) {
                PayloadTypeRegistry.playC2S().register(ID, CODEC);
                registered = true;
            }
        }
    }
}
