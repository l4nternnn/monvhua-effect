package com.kuilunfuzhe.monvhua.network.portal;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import com.kuilunfuzhe.monvhua.features.portal.PortalManager;
import com.kuilunfuzhe.monvhua.features.portal.PortalViewConfig;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

public final class PortalPackets {
    private static final int MAX_GROUPS = 64;
    private static final int MAX_GROUP_NAME = 32;

    private PortalPackets() {
    }

    public static void registerS2C() {
        OpenEditorS2C.register();
        RemoteViewStateS2C.register();
        RemoteHorizonS2C.register();
        RemoteChunkS2C.register();
    }

    public static void registerC2S() {
        BindGroupC2S.register();
        DeleteGroupC2S.register();
        RequestRemoteViewC2S.register();
    }

    public static void registerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(BindGroupC2S.ID, (packet, context) ->
                context.server().execute(() -> PortalManager.bindToGroup(context.player(), packet.pos(), packet.groupId())));
        ServerPlayNetworking.registerGlobalReceiver(DeleteGroupC2S.ID, (packet, context) ->
                context.server().execute(() -> PortalManager.deleteGroup(context.player(), packet.groupId())));
        ServerPlayNetworking.registerGlobalReceiver(RequestRemoteViewC2S.ID, (packet, context) ->
                context.server().execute(() -> PortalManager.requestRemoteView(
                        context.player(),
                        packet.sourcePos(),
                        packet.viewCenter()
                )));
    }

    public record RemoteViewStateS2C(boolean active, BlockPos sourcePos, BlockPos targetPos, BlockPos viewCenter,
                                     int radius, long generation)
            implements CustomPayload {
        public static final Id<RemoteViewStateS2C> ID =
                new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "portal_remote_view_state"));
        public static final PacketCodec<RegistryByteBuf, RemoteViewStateS2C> CODEC =
                PacketCodec.of(RemoteViewStateS2C::write, RemoteViewStateS2C::new);
        private static boolean registered;

        public RemoteViewStateS2C {
            sourcePos = sourcePos == null ? BlockPos.ORIGIN : sourcePos.toImmutable();
            targetPos = targetPos == null ? BlockPos.ORIGIN : targetPos.toImmutable();
            viewCenter = viewCenter == null ? targetPos : viewCenter.toImmutable();
            radius = PortalViewConfig.clampRemoteRadius(radius);
            generation = Math.max(0L, generation);
        }

        private RemoteViewStateS2C(RegistryByteBuf buf) {
            this(buf.readBoolean(), buf.readBlockPos(), buf.readBlockPos(), buf.readBlockPos(),
                    buf.readVarInt(), buf.readVarLong());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeBoolean(active);
            buf.writeBlockPos(sourcePos);
            buf.writeBlockPos(targetPos);
            buf.writeBlockPos(viewCenter);
            buf.writeVarInt(radius);
            buf.writeVarLong(generation);
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

    public record RemoteHorizonS2C(BlockPos sourcePos, long generation, BlockPos center, int stepBlocks, int gridRadius,
                                   int minY, int maxY, int skyColor, int fogColor,
                                   int[] heights, int[] colors)
            implements CustomPayload {
        public static final Id<RemoteHorizonS2C> ID =
                new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "portal_remote_horizon"));
        public static final PacketCodec<RegistryByteBuf, RemoteHorizonS2C> CODEC =
                PacketCodec.of(RemoteHorizonS2C::write, RemoteHorizonS2C::new);
        private static boolean registered;

        public RemoteHorizonS2C {
            sourcePos = sourcePos == null ? BlockPos.ORIGIN : sourcePos.toImmutable();
            generation = Math.max(0L, generation);
            center = center == null ? BlockPos.ORIGIN : center.toImmutable();
            stepBlocks = Math.max(1, stepBlocks);
            gridRadius = Math.max(0, gridRadius);
            int expected = Math.min(
                    (gridRadius * 2 + 1) * (gridRadius * 2 + 1),
                    PortalViewConfig.PORTAL_HORIZON_MAX_SAMPLES
            );
            heights = sanitizeArray(heights, expected);
            colors = sanitizeArray(colors, expected);
        }

        private RemoteHorizonS2C(RegistryByteBuf buf) {
            this(
                    buf.readBlockPos(),
                    buf.readVarLong(),
                    buf.readBlockPos(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readInt(),
                    buf.readInt(),
                    readIntArray(buf),
                    readIntArray(buf)
            );
        }

        private void write(RegistryByteBuf buf) {
            buf.writeBlockPos(sourcePos);
            buf.writeVarLong(generation);
            buf.writeBlockPos(center);
            buf.writeVarInt(stepBlocks);
            buf.writeVarInt(gridRadius);
            buf.writeVarInt(minY);
            buf.writeVarInt(maxY);
            buf.writeInt(skyColor);
            buf.writeInt(fogColor);
            writeIntArray(buf, heights);
            writeIntArray(buf, colors);
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

    public static final class RemoteChunkS2C implements CustomPayload {
        public static final Id<RemoteChunkS2C> ID =
                new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "portal_remote_chunk"));
        public static final PacketCodec<RegistryByteBuf, RemoteChunkS2C> CODEC =
                PacketCodec.of(RemoteChunkS2C::write, RemoteChunkS2C::new);
        private static boolean registered;

        private final BlockPos sourcePos;
        private final long generation;
        private final int chunkX;
        private final int chunkZ;
        private final ChunkData chunkData;

        public RemoteChunkS2C(BlockPos sourcePos, long generation, WorldChunk chunk) {
            this(
                    sourcePos,
                    Math.max(0L, generation),
                    chunk.getPos().x,
                    chunk.getPos().z,
                    new ChunkData(chunk)
            );
        }

        private RemoteChunkS2C(BlockPos sourcePos, long generation, int chunkX, int chunkZ, ChunkData chunkData) {
            this.sourcePos = sourcePos == null ? BlockPos.ORIGIN : sourcePos.toImmutable();
            this.generation = Math.max(0L, generation);
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.chunkData = chunkData;
        }

        private RemoteChunkS2C(RegistryByteBuf buf) {
            this.sourcePos = buf.readBlockPos().toImmutable();
            this.generation = Math.max(0L, buf.readVarLong());
            this.chunkX = buf.readVarInt();
            this.chunkZ = buf.readVarInt();
            this.chunkData = new ChunkData(buf, this.chunkX, this.chunkZ);
        }

        private void write(RegistryByteBuf buf) {
            buf.writeBlockPos(sourcePos);
            buf.writeVarLong(generation);
            buf.writeVarInt(chunkX);
            buf.writeVarInt(chunkZ);
            chunkData.write(buf);
        }

        public BlockPos sourcePos() {
            return sourcePos;
        }

        public long generation() {
            return generation;
        }

        public int chunkX() {
            return chunkX;
        }

        public int chunkZ() {
            return chunkZ;
        }

        public ChunkData chunkData() {
            return chunkData;
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

    public record OpenEditorS2C(BlockPos pos, String selectedGroup, String[] groups, int[] endpointCounts) implements CustomPayload {
        public static final Id<OpenEditorS2C> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "portal_editor_open"));
        public static final PacketCodec<RegistryByteBuf, OpenEditorS2C> CODEC = PacketCodec.of(OpenEditorS2C::write, OpenEditorS2C::new);
        private static boolean registered;

        public OpenEditorS2C {
            pos = pos.toImmutable();
            selectedGroup = sanitize(selectedGroup);
            groups = sanitizeGroups(groups);
            endpointCounts = sanitizeCounts(groups, endpointCounts);
        }

        private OpenEditorS2C(RegistryByteBuf buf) {
            this(buf.readBlockPos(), buf.readString(MAX_GROUP_NAME), readGroups(buf), readCounts(buf));
        }

        private void write(RegistryByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeString(selectedGroup, MAX_GROUP_NAME);
            buf.writeVarInt(groups.length);
            for (String group : groups) {
                buf.writeString(group, MAX_GROUP_NAME);
            }
            buf.writeVarInt(endpointCounts.length);
            for (int count : endpointCounts) {
                buf.writeVarInt(count);
            }
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

    public record BindGroupC2S(BlockPos pos, String groupId) implements CustomPayload {
        public static final Id<BindGroupC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "portal_bind_group"));
        public static final PacketCodec<RegistryByteBuf, BindGroupC2S> CODEC = PacketCodec.of(BindGroupC2S::write, BindGroupC2S::new);
        private static boolean registered;

        public BindGroupC2S {
            pos = pos.toImmutable();
            groupId = sanitize(groupId);
        }

        private BindGroupC2S(RegistryByteBuf buf) {
            this(buf.readBlockPos(), buf.readString(MAX_GROUP_NAME));
        }

        private void write(RegistryByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeString(groupId, MAX_GROUP_NAME);
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

    public record DeleteGroupC2S(String groupId) implements CustomPayload {
        public static final Id<DeleteGroupC2S> ID = new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "portal_delete_group"));
        public static final PacketCodec<RegistryByteBuf, DeleteGroupC2S> CODEC = PacketCodec.of(DeleteGroupC2S::write, DeleteGroupC2S::new);
        private static boolean registered;

        public DeleteGroupC2S {
            groupId = sanitize(groupId);
        }

        private DeleteGroupC2S(RegistryByteBuf buf) {
            this(buf.readString(MAX_GROUP_NAME));
        }

        private void write(RegistryByteBuf buf) {
            buf.writeString(groupId, MAX_GROUP_NAME);
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

    public record RequestRemoteViewC2S(BlockPos sourcePos, BlockPos viewCenter) implements CustomPayload {
        public static final Id<RequestRemoteViewC2S> ID =
                new Id<>(Identifier.of(MonvhuaMod.MOD_ID, "portal_remote_view_request"));
        public static final PacketCodec<RegistryByteBuf, RequestRemoteViewC2S> CODEC =
                PacketCodec.of(RequestRemoteViewC2S::write, RequestRemoteViewC2S::new);
        private static boolean registered;

        public RequestRemoteViewC2S {
            sourcePos = sourcePos.toImmutable();
            viewCenter = viewCenter == null ? sourcePos : viewCenter.toImmutable();
        }

        private RequestRemoteViewC2S(RegistryByteBuf buf) {
            this(buf.readBlockPos(), buf.readBlockPos());
        }

        private void write(RegistryByteBuf buf) {
            buf.writeBlockPos(sourcePos);
            buf.writeBlockPos(viewCenter);
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

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > MAX_GROUP_NAME ? trimmed.substring(0, MAX_GROUP_NAME) : trimmed;
    }

    private static String[] readGroups(RegistryByteBuf buf) {
        int count = Math.min(buf.readVarInt(), MAX_GROUPS);
        String[] groups = new String[count];
        for (int i = 0; i < count; i++) {
            groups[i] = sanitize(buf.readString(MAX_GROUP_NAME));
        }
        return groups;
    }

    private static int[] readCounts(RegistryByteBuf buf) {
        int count = Math.min(buf.readVarInt(), MAX_GROUPS);
        int[] counts = new int[count];
        for (int i = 0; i < count; i++) {
            counts[i] = Math.max(0, Math.min(2, buf.readVarInt()));
        }
        return counts;
    }

    private static int[] readIntArray(RegistryByteBuf buf) {
        int count = Math.min(buf.readVarInt(), PortalViewConfig.PORTAL_HORIZON_MAX_SAMPLES);
        int[] values = new int[count];
        for (int i = 0; i < count; i++) {
            values[i] = buf.readInt();
        }
        return values;
    }

    private static void writeIntArray(RegistryByteBuf buf, int[] values) {
        int count = Math.min(values == null ? 0 : values.length, PortalViewConfig.PORTAL_HORIZON_MAX_SAMPLES);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeInt(values[i]);
        }
    }

    private static int[] sanitizeArray(int[] values, int expected) {
        int length = Math.min(expected, PortalViewConfig.PORTAL_HORIZON_MAX_SAMPLES);
        int[] copy = new int[length];
        if (values != null) {
            System.arraycopy(values, 0, copy, 0, Math.min(length, values.length));
        }
        return copy;
    }

    private static String[] sanitizeGroups(String[] groups) {
        if (groups == null) {
            return new String[0];
        }
        int length = Math.min(groups.length, MAX_GROUPS);
        String[] copy = new String[length];
        for (int i = 0; i < length; i++) {
            copy[i] = sanitize(groups[i]);
        }
        return copy;
    }

    private static int[] sanitizeCounts(String[] groups, int[] counts) {
        int length = groups == null ? 0 : groups.length;
        int[] copy = new int[length];
        if (counts != null) {
            for (int i = 0; i < Math.min(length, counts.length); i++) {
                copy[i] = Math.max(0, Math.min(2, counts[i]));
            }
        }
        return copy;
    }
}
