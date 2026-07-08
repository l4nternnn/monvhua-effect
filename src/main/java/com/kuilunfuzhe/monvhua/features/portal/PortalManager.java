package com.kuilunfuzhe.monvhua.features.portal;

import com.kuilunfuzhe.monvhua.network.portal.PortalPackets;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PortalManager {
    public static final int MAX_PORTAL_SIZE = 100;
    private static final double PLANE_EPSILON = 0.08D;
    private static final int COOLDOWN_TICKS = 10;
    private static final int ACTIVATION_DELAY_TICKS = 60;
    private static final int MAX_FLOOD_CELLS = MAX_PORTAL_SIZE * MAX_PORTAL_SIZE;

    private static final Set<BlockPos> PORTALS = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<UUID, Vec3d> LAST_POSITIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Integer> COOLDOWNS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, PortalGroup> GROUPS = new ConcurrentHashMap<>();
    private static final Set<BlockPos> CLEARING_PORTALS = ConcurrentHashMap.newKeySet();
    private static boolean initialized;

    private PortalManager() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        PortalPackets.registerReceivers();
        ServerTickEvents.END_WORLD_TICK.register(PortalManager::tickWorld);
    }

    public static void onPortalPlaced(World world, BlockPos pos, PortalBlockEntity portal) {
        if (portal.isController()) {
            PORTALS.add(pos.toImmutable());
        }
    }

    public static void onPortalRemoved(World world, BlockPos pos) {
        PORTALS.remove(pos);
        removeEndpoint(pos);
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }
        for (BlockPos knownPos : new ArrayList<>(PORTALS)) {
            BlockEntity blockEntity = serverWorld.getBlockEntity(knownPos);
            if (blockEntity instanceof PortalBlockEntity portal) {
                PortalLinkData link = portal.getLinkData();
                if (link != null && link.targetPos().equals(pos)) {
                    portal.clearTarget();
                }
            }
        }
    }

    public static void onPortalBlockRemoved(ServerWorld world, BlockPos pos, BlockState removedState) {
        if (CLEARING_PORTALS.contains(pos)) {
            return;
        }
        BlockPos controllerPos = pos;
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof PortalBlockEntity portal) {
            controllerPos = portal.isController() ? portal.getPos() : portal.getOrigin();
        } else if (!removedState.get(PortalBlock.CONTROLLER)) {
            return;
        }
        PortalBlockEntity controller = getController(world, controllerPos);
        if (controller != null) {
            clearPortalSurface(world, controller);
        } else {
            onPortalRemoved(world, controllerPos);
        }
    }

    public static void onFrameRemoved(ServerWorld world, BlockPos framePos) {
        Set<BlockPos> checkedControllers = new HashSet<>();
        for (BlockPos checkPos : BlockPos.iterate(framePos.add(-1, -1, -1), framePos.add(1, 1, 1))) {
            BlockEntity blockEntity = world.getBlockEntity(checkPos);
            if (!(blockEntity instanceof PortalBlockEntity portal)) {
                continue;
            }
            BlockPos controllerPos = portal.isController() ? portal.getPos() : portal.getOrigin();
            if (!checkedControllers.add(controllerPos.toImmutable())) {
                continue;
            }
            PortalBlockEntity controller = getController(world, controllerPos);
            if (controller != null && !isStructureStillValid(world, controller)) {
                clearPortalSurface(world, controller);
            }
        }
    }

    public static boolean tryCreateAt(ServerWorld world, BlockPos framePos, PlayerEntity player) {
        for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
            Structure structure = detectStructure(world, axis, framePos);
            if (structure != null) {
                Direction facing = facingFor(player, axis, structure.fixed());
                createSurface(world, structure, facing);
                if (player instanceof ServerPlayerEntity serverPlayer) {
                    openEditor(world, structure.origin(), serverPlayer);
                }
                return true;
            }
        }
        if (player != null) {
            player.sendMessage(Text.literal("Portal frame: no closed rectangular frame found"), true);
        }
        return false;
    }

    public static void openEditor(ServerWorld world, BlockPos controllerPos, PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)
                || !(world.getBlockEntity(controllerPos) instanceof PortalBlockEntity portal)
                || !portal.isController()) {
            return;
        }
        registerExistingEndpoint(portal);
        sendEditor(serverPlayer, portal);
    }

    public static void bindToGroup(ServerPlayerEntity player, BlockPos controllerPos, String groupId) {
        if (!(player.getWorld() instanceof ServerWorld world)
                || !canEdit(player, controllerPos)
                || !(world.getBlockEntity(controllerPos) instanceof PortalBlockEntity portal)
                || !portal.isController()) {
            return;
        }
        String clean = sanitizeGroup(groupId);
        if (clean.isEmpty()) {
            player.sendMessage(Text.literal("Portal group name is empty"), true);
            return;
        }
        removeEndpoint(controllerPos);
        PortalGroup group = GROUPS.computeIfAbsent(clean, PortalGroup::new);
        if (!group.add(controllerPos)) {
            player.sendMessage(Text.literal("Portal group already has two endpoints"), true);
            sendEditor(player, portal);
            return;
        }
        portal.setGroupId(clean);
        relinkGroup(world, group);
        sendEditor(player, portal);
    }

    public static void deleteGroup(ServerPlayerEntity player, String groupId) {
        String clean = sanitizeGroup(groupId);
        if (clean.isEmpty()) {
            return;
        }
        PortalGroup group = GROUPS.remove(clean);
        if (group == null) {
            return;
        }
        clearEndpoint(player.getWorld(), group.first);
        clearEndpoint(player.getWorld(), group.second);
    }

    private static void sendEditor(ServerPlayerEntity player, PortalBlockEntity portal) {
        List<String> names = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (PortalGroup group : GROUPS.values()) {
            names.add(group.id);
            counts.add(group.count());
        }
        ServerPlayNetworking.send(player, new PortalPackets.OpenEditorS2C(
                portal.getPos(),
                portal.getGroupId(),
                names.toArray(String[]::new),
                counts.stream().mapToInt(Integer::intValue).toArray()
        ));
    }

    private static boolean canEdit(ServerPlayerEntity player, BlockPos pos) {
        return Vec3d.ofCenter(pos).squaredDistanceTo(player.getEyePos()) <= 64.0D;
    }

    private static void registerExistingEndpoint(PortalBlockEntity portal) {
        if (!portal.getGroupId().isEmpty()) {
            GROUPS.computeIfAbsent(portal.getGroupId(), PortalGroup::new).add(portal.getPos());
        }
    }

    private static void removeEndpoint(BlockPos pos) {
        for (PortalGroup group : GROUPS.values()) {
            group.remove(pos);
        }
    }

    private static void clearEndpoint(World world, BlockPos pos) {
        if (pos != null && world.getBlockEntity(pos) instanceof PortalBlockEntity portal) {
            portal.setGroupId("");
            portal.clearTarget();
        }
    }

    private static void relinkGroup(ServerWorld world, PortalGroup group) {
        PortalBlockEntity first = group.first == null ? null : getController(world, group.first);
        PortalBlockEntity second = group.second == null ? null : getController(world, group.second);
        if (first == null || second == null) {
            if (first != null) {
                first.clearTarget();
            }
            if (second != null) {
                second.clearTarget();
            }
            return;
        }
        long activeTick = world.getTime() + ACTIVATION_DELAY_TICKS;
        first.setTarget(second.getPos(), second.getFacing(), activeTick);
        second.setTarget(first.getPos(), first.getFacing(), activeTick);
    }

    private static PortalBlockEntity getController(ServerWorld world, BlockPos pos) {
        return world.getBlockEntity(pos) instanceof PortalBlockEntity portal && portal.isController() ? portal : null;
    }

    private static String sanitizeGroup(String groupId) {
        if (groupId == null) {
            return "";
        }
        String trimmed = groupId.trim();
        if (trimmed.length() > 32) {
            trimmed = trimmed.substring(0, 32);
        }
        return trimmed;
    }

    private static Structure detectStructure(ServerWorld world, Direction.Axis axis, BlockPos framePos) {
        int fixed = fixed(axis, framePos);
        int h = horizontal(axis, framePos);
        int y = framePos.getY();
        int[][] adjacent = {{h + 1, y}, {h - 1, y}, {h, y + 1}, {h, y - 1}};
        for (int[] seed : adjacent) {
            Structure structure = floodInterior(world, axis, fixed, seed[0], seed[1]);
            if (structure != null) {
                return structure;
            }
        }
        return null;
    }

    private static Structure floodInterior(ServerWorld world, Direction.Axis axis, int fixed, int seedH, int seedY) {
        BlockPos seedPos = pos(axis, fixed, seedH, seedY);
        if (!isInterior(world.getBlockState(seedPos))) {
            return null;
        }
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        Set<Cell> visited = ConcurrentHashMap.newKeySet();
        Cell seed = new Cell(seedH, seedY);
        queue.add(seed);
        visited.add(seed);
        int minH = seedH;
        int maxH = seedH;
        int minY = seedY;
        int maxY = seedY;
        while (!queue.isEmpty()) {
            Cell cell = queue.removeFirst();
            minH = Math.min(minH, cell.h);
            maxH = Math.max(maxH, cell.h);
            minY = Math.min(minY, cell.y);
            maxY = Math.max(maxY, cell.y);
            if (maxH - minH + 1 > MAX_PORTAL_SIZE || maxY - minY + 1 > MAX_PORTAL_SIZE || visited.size() > MAX_FLOOD_CELLS) {
                return null;
            }
            addInteriorNeighbor(world, axis, fixed, cell.h + 1, cell.y, visited, queue);
            addInteriorNeighbor(world, axis, fixed, cell.h - 1, cell.y, visited, queue);
            addInteriorNeighbor(world, axis, fixed, cell.h, cell.y + 1, visited, queue);
            addInteriorNeighbor(world, axis, fixed, cell.h, cell.y - 1, visited, queue);
        }
        int width = maxH - minH + 1;
        int height = maxY - minY + 1;
        if (width < 1 || height < 1 || width > MAX_PORTAL_SIZE || height > MAX_PORTAL_SIZE) {
            return null;
        }
        for (int yy = minY; yy <= maxY; yy++) {
            for (int hh = minH; hh <= maxH; hh++) {
                if (!isInterior(world.getBlockState(pos(axis, fixed, hh, yy)))) {
                    return null;
                }
            }
        }
        for (int hh = minH - 1; hh <= maxH + 1; hh++) {
            if (!isFrame(world.getBlockState(pos(axis, fixed, hh, minY - 1)))
                    || !isFrame(world.getBlockState(pos(axis, fixed, hh, maxY + 1)))) {
                return null;
            }
        }
        for (int yy = minY; yy <= maxY; yy++) {
            if (!isFrame(world.getBlockState(pos(axis, fixed, minH - 1, yy)))
                    || !isFrame(world.getBlockState(pos(axis, fixed, maxH + 1, yy)))) {
                return null;
            }
        }
        return new Structure(axis, fixed, minH, minY, width, height, pos(axis, fixed, minH, minY));
    }

    private static void addInteriorNeighbor(ServerWorld world, Direction.Axis axis, int fixed, int h, int y,
                                            Set<Cell> visited, ArrayDeque<Cell> queue) {
        Cell cell = new Cell(h, y);
        if (!visited.contains(cell) && isInterior(world.getBlockState(pos(axis, fixed, h, y)))) {
            visited.add(cell);
            queue.addLast(cell);
        }
    }

    private static boolean isFrame(BlockState state) {
        return state.isOf(PortalItems.FRAME_BLOCK);
    }

    private static boolean isInterior(BlockState state) {
        return state.isAir()
                || state.isReplaceable()
                || state.isOf(Blocks.FIRE)
                || state.isOf(PortalItems.PORTAL_BLOCK);
    }

    private static void createSurface(ServerWorld world, Structure structure, Direction facing) {
        BlockPos origin = structure.origin();
        for (int y = 0; y < structure.height(); y++) {
            for (int h = 0; h < structure.width(); h++) {
                BlockPos pos = pos(structure.axis(), structure.fixed(), structure.minH() + h, structure.minY() + y);
                boolean controller = pos.equals(origin);
                BlockState state = PortalItems.PORTAL_BLOCK.getDefaultState()
                        .with(PortalBlock.FACING, facing)
                        .with(PortalBlock.CONTROLLER, controller);
                world.setBlockState(pos, state, Block.NOTIFY_ALL);
                if (world.getBlockEntity(pos) instanceof PortalBlockEntity portal) {
                    portal.setStructure(origin, structure.width(), structure.height());
                    if (controller) {
                        PORTALS.add(pos.toImmutable());
                    }
                }
            }
        }
    }

    private static Direction facingFor(PlayerEntity player, Direction.Axis axis, int fixed) {
        if (axis == Direction.Axis.X) {
            return player.getX() >= fixed + 0.5D ? Direction.EAST : Direction.WEST;
        }
        return player.getZ() >= fixed + 0.5D ? Direction.SOUTH : Direction.NORTH;
    }

    private static int fixed(Direction.Axis axis, BlockPos pos) {
        return axis == Direction.Axis.X ? pos.getX() : pos.getZ();
    }

    private static int horizontal(Direction.Axis axis, BlockPos pos) {
        return axis == Direction.Axis.X ? pos.getZ() : pos.getX();
    }

    private static BlockPos pos(Direction.Axis axis, int fixed, int h, int y) {
        return axis == Direction.Axis.X ? new BlockPos(fixed, y, h) : new BlockPos(h, y, fixed);
    }

    private static void tickWorld(ServerWorld world) {
        pruneCooldowns();
        tickPortalActivation(world);
        for (ServerPlayerEntity player : world.getPlayers()) {
            UUID playerId = player.getUuid();
            Vec3d currentPos = player.getPos();
            Vec3d previousPos = LAST_POSITIONS.put(playerId, currentPos);
            if (previousPos == null) {
                continue;
            }
            int cooldown = COOLDOWNS.getOrDefault(playerId, 0);
            if (cooldown > 0) {
                COOLDOWNS.put(playerId, cooldown - 1);
                continue;
            }
            PortalBlockEntity portal = findNearbyPortal(world, currentPos);
            if (portal == null || !portal.isActive()) {
                continue;
            }
            PortalLinkData link = portal.getLinkData();
            if (link == null) {
                continue;
            }
            if (crossedPortalPlane(portal, previousPos, currentPos)) {
                teleportPlayer(world, player, portal, link);
                COOLDOWNS.put(playerId, COOLDOWN_TICKS);
                LAST_POSITIONS.put(playerId, player.getPos());
            }
        }
    }

    private static void tickPortalActivation(ServerWorld world) {
        for (BlockPos knownPos : new ArrayList<>(PORTALS)) {
            if (world.getBlockEntity(knownPos) instanceof PortalBlockEntity portal) {
                portal.tickActivation(world);
            } else {
                PORTALS.remove(knownPos);
            }
        }
    }

    private static void pruneCooldowns() {
        Iterator<UUID> iterator = COOLDOWNS.keySet().iterator();
        while (iterator.hasNext()) {
            UUID id = iterator.next();
            if (COOLDOWNS.getOrDefault(id, 0) <= 0) {
                iterator.remove();
            }
        }
    }

    private static PortalBlockEntity findNearbyPortal(ServerWorld world, Vec3d pos) {
        BlockPos base = BlockPos.ofFloored(pos);
        for (BlockPos checkPos : BlockPos.iterate(base.add(-2, -2, -2), base.add(2, 3, 2))) {
            BlockEntity blockEntity = world.getBlockEntity(checkPos);
            if (blockEntity instanceof PortalBlockEntity portal) {
                BlockPos controllerPos = portal.isController() ? portal.getPos() : portal.getOrigin();
                if (world.getBlockEntity(controllerPos) instanceof PortalBlockEntity controller
                        && controller.isController()
                        && isInsidePortalRect(controller, pos)) {
                    PORTALS.add(controllerPos.toImmutable());
                    return controller;
                }
            }
        }
        return null;
    }

    private static boolean crossedPortalPlane(PortalBlockEntity portal, Vec3d previousPos, Vec3d currentPos) {
        Vec3d center = portal.getPortalCenter();
        Vec3d normal = normal(portal.getFacing());
        double previousSide = previousPos.subtract(center).dotProduct(normal);
        double currentSide = currentPos.subtract(center).dotProduct(normal);
        return (previousSide > PLANE_EPSILON && currentSide <= PLANE_EPSILON)
                || (previousSide < -PLANE_EPSILON && currentSide >= -PLANE_EPSILON);
    }

    private static boolean isInsidePortalRect(PortalBlockEntity portal, Vec3d pos) {
        Vec3d local = pos.subtract(Vec3d.ofCenter(portal.getOrigin()));
        Direction facing = portal.getFacing();
        double horizontal = facing.getAxis() == Direction.Axis.X ? local.z : local.x;
        double vertical = local.y + 0.5D;
        return horizontal >= -0.1D
                && horizontal <= portal.getPortalWidth() + 0.1D
                && vertical >= -0.1D
                && vertical <= portal.getPortalHeight() + 0.1D;
    }

    private static void teleportPlayer(ServerWorld world, ServerPlayerEntity player, PortalBlockEntity portal, PortalLinkData link) {
        BlockEntity targetBlockEntity = world.getBlockEntity(link.targetPos());
        if (!(targetBlockEntity instanceof PortalBlockEntity targetPortal) || !targetPortal.isController()) {
            portal.clearTarget();
            return;
        }
        Vec3d sourceCenter = portal.getPortalCenter();
        Vec3d targetCenter = targetPortal.getPortalCenter();
        Vec3d relative = player.getPos().subtract(sourceCenter);
        Vec3d rotated = rotateHorizontal(relative, portal.getFacing(), targetPortal.getFacing().getOpposite());
        Vec3d exitNormal = normal(targetPortal.getFacing()).multiply(0.75D);
        Vec3d targetPos = targetCenter.add(rotated).add(exitNormal);
        float yawDelta = targetPortal.getFacing().getPositiveHorizontalDegrees()
                - portal.getFacing().getPositiveHorizontalDegrees()
                + 180.0F;
        float yaw = MathHelper.wrapDegrees(player.getYaw() + yawDelta);

        player.teleport(world, targetPos.x, targetPos.y, targetPos.z, java.util.Set.of(), yaw, player.getPitch(), false);
        player.setVelocity(rotateHorizontal(player.getVelocity(), portal.getFacing(), targetPortal.getFacing().getOpposite()));
        player.velocityModified = true;
    }

    private static boolean isStructureStillValid(ServerWorld world, PortalBlockEntity portal) {
        BlockPos origin = portal.getOrigin();
        Direction.Axis axis = portal.getFacing().getAxis();
        int fixed = fixed(axis, origin);
        int minH = horizontal(axis, origin);
        int minY = origin.getY();
        int width = portal.getPortalWidth();
        int height = portal.getPortalHeight();
        for (int y = 0; y < height; y++) {
            for (int h = 0; h < width; h++) {
                if (!world.getBlockState(pos(axis, fixed, minH + h, minY + y)).isOf(PortalItems.PORTAL_BLOCK)) {
                    return false;
                }
            }
        }
        for (int h = minH - 1; h <= minH + width; h++) {
            if (!isFrame(world.getBlockState(pos(axis, fixed, h, minY - 1)))
                    || !isFrame(world.getBlockState(pos(axis, fixed, h, minY + height)))) {
                return false;
            }
        }
        for (int y = minY; y < minY + height; y++) {
            if (!isFrame(world.getBlockState(pos(axis, fixed, minH - 1, y)))
                    || !isFrame(world.getBlockState(pos(axis, fixed, minH + width, y)))) {
                return false;
            }
        }
        return true;
    }

    private static void clearPortalSurface(ServerWorld world, PortalBlockEntity controller) {
        BlockPos origin = controller.getOrigin();
        Direction.Axis axis = controller.getFacing().getAxis();
        int fixed = fixed(axis, origin);
        int minH = horizontal(axis, origin);
        int minY = origin.getY();
        int width = controller.getPortalWidth();
        int height = controller.getPortalHeight();
        onPortalRemoved(world, controller.getPos());
        for (int y = 0; y < height; y++) {
            for (int h = 0; h < width; h++) {
                BlockPos surfacePos = pos(axis, fixed, minH + h, minY + y);
                if (!world.getBlockState(surfacePos).isOf(PortalItems.PORTAL_BLOCK)) {
                    continue;
                }
                BlockPos immutable = surfacePos.toImmutable();
                CLEARING_PORTALS.add(immutable);
                world.setBlockState(surfacePos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                CLEARING_PORTALS.remove(immutable);
            }
        }
    }

    private static Vec3d rotateHorizontal(Vec3d vector, Direction from, Direction to) {
        int steps = Math.floorMod(to.getHorizontalQuarterTurns() - from.getHorizontalQuarterTurns(), 4);
        return switch (steps) {
            case 1 -> new Vec3d(-vector.z, vector.y, vector.x);
            case 2 -> new Vec3d(-vector.x, vector.y, -vector.z);
            case 3 -> new Vec3d(vector.z, vector.y, -vector.x);
            default -> vector;
        };
    }

    private static Vec3d normal(Direction direction) {
        return new Vec3d(direction.getOffsetX(), direction.getOffsetY(), direction.getOffsetZ());
    }

    private record Cell(int h, int y) {
    }

    private record Structure(Direction.Axis axis, int fixed, int minH, int minY, int width, int height, BlockPos origin) {
    }

    private static final class PortalGroup {
        private final String id;
        private BlockPos first;
        private BlockPos second;

        private PortalGroup(String id) {
            this.id = id;
        }

        private boolean add(BlockPos pos) {
            BlockPos immutable = pos.toImmutable();
            if (immutable.equals(first) || immutable.equals(second)) {
                return true;
            }
            if (first == null) {
                first = immutable;
                return true;
            }
            if (second == null) {
                second = immutable;
                return true;
            }
            return false;
        }

        private void remove(BlockPos pos) {
            if (pos.equals(first)) {
                first = null;
            }
            if (pos.equals(second)) {
                second = null;
            }
        }

        private int count() {
            return (first == null ? 0 : 1) + (second == null ? 0 : 1);
        }
    }
}
