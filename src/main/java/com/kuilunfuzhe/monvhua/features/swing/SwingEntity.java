package com.kuilunfuzhe.monvhua.features.swing;

import com.kuilunfuzhe.monvhua.entity.ModEntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import com.mojang.serialization.Codec;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Comparator;
import com.kuilunfuzhe.monvhua.item.swing.SwingAssemblyItems;

public class SwingEntity extends Entity {
    private static final double SEAT_HEIGHT_OFFSET = -0.45;
    private static final float MAX_SWING_ANGLE = MathHelper.RADIANS_PER_DEGREE * 75f;
    private static final float GRAVITY_ACCELERATION = 0.0075f;
    private static final float AIR_DAMPING = 0.992f;
    private static final float INPUT_ACCELERATION = 0.0045f;
    private static final float CLIENT_CORRECTION = 0.2f;
    private static final int CLIENT_PREDICTION_TICKS = 5;
    private static final double COLLISION_EPSILON = 0.015;
    private static final double COLLISION_CELL_SIZE = 0.25;
    private static final TrackedData<Float> ANGLE = DataTracker.registerData(SwingEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> VELOCITY = DataTracker.registerData(SwingEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Boolean> Z_AXIS = DataTracker.registerData(SwingEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> INPUT_SIGN = DataTracker.registerData(SwingEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<NbtCompound> STRUCTURE = DataTracker.registerData(SwingEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
    private static final TrackedData<NbtCompound> SEATS = DataTracker.registerData(SwingEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
    private SwingStructure structure = new SwingStructure(java.util.List.of());
    private float previousAngle;
    private float renderAngle;
    private float renderPreviousAngle;
    private boolean renderAngleInitialized;
    private int clientSnapshotAge;
    private final Map<java.util.UUID, Integer> passengerSeats = new HashMap<>();
    private java.util.List<Box> collisionShapes = java.util.List.of();

    public SwingEntity(EntityType<? extends SwingEntity> type, World world) { super(type, world); noClip = true; }
    public SwingEntity(World world, Vec3d pivot, SwingStructure structure, boolean zAxis) {
        this(world, pivot, structure, zAxis, 1);
    }
    public SwingEntity(World world, Vec3d pivot, SwingStructure structure, boolean zAxis, int inputSign) {
        this(ModEntities.SWING, world); setPosition(pivot); this.structure = structure; dataTracker.set(Z_AXIS, zAxis); dataTracker.set(INPUT_SIGN, inputSign < 0 ? -1 : 1); syncStructure(); refreshStructureBounds();
    }
    @Override protected void initDataTracker(DataTracker.Builder b) { b.add(ANGLE, 0f); b.add(VELOCITY, 0f); b.add(Z_AXIS, false); b.add(INPUT_SIGN, 1); b.add(STRUCTURE, new NbtCompound()); b.add(SEATS, new NbtCompound()); }
    private void syncSeats() {
        var n = new NbtCompound();
        passengerSeats.forEach((id, seat) -> n.putInt(id.toString(), seat));
        dataTracker.set(SEATS, n);
    }
    private void pruneSeats() {
        if (!getWorld().isClient() && passengerSeats.entrySet().removeIf(e ->
                getPassengerList().stream().noneMatch(p -> p.getUuid().equals(e.getKey())))) syncSeats();
    }
    public SwingStructure structure() { return structure; }
    private void syncStructure() {
        collisionShapes = structure.collisionShapes();
        var n = new NbtCompound();
        n.putLongArray("P", structure.packedPositions().stream().mapToLong(Long::longValue).toArray());
        n.putIntArray("S", structure.stateIds().stream().mapToInt(Integer::intValue).toArray());
        dataTracker.set(STRUCTURE, n);
        SwingSpatialIndex.register(this);
    }
    @Override public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if ((data == ANGLE || data == VELOCITY) && getWorld().isClient()) clientSnapshotAge = 0;
        if (data == SEATS && getWorld().isClient()) {
            passengerSeats.clear();
            var n = dataTracker.get(SEATS);
            for (String id : n.getKeys()) {
                try { passengerSeats.put(java.util.UUID.fromString(id), n.getInt(id).orElse(-1)); }
                catch (IllegalArgumentException ignored) { }
            }
        }
        if (data == STRUCTURE) {
            var n = dataTracker.get(STRUCTURE);
            structure = SwingStructure.fromPacked(java.util.Arrays.stream(n.getLongArray("P").orElse(new long[0])).boxed().toList(), java.util.Arrays.stream(n.getIntArray("S").orElse(new int[0])).boxed().toList());
            collisionShapes = structure.collisionShapes();
            refreshStructureBounds();
            SwingSpatialIndex.register(this);
            com.kuilunfuzhe.monvhua.MonvhuaMod.LOGGER.info("[SwingDiag] STRUCTURE_RECEIVED side={} uuid={} blocks={} pos={} bounds={}",
                    getWorld().isClient() ? "CLIENT" : "SERVER", getUuid(), structure.blocks().size(), getPos(), getBoundingBox());
        }
        if (data == ANGLE || data == Z_AXIS) refreshStructureBounds();
    }
    public float angle(float tickDelta) {
        if (!getWorld().isClient()) return dataTracker.get(ANGLE);
        if (!renderAngleInitialized) {
            renderAngle = dataTracker.get(ANGLE);
            renderPreviousAngle = renderAngle;
            renderAngleInitialized = true;
        }
        return MathHelper.lerp(tickDelta, renderPreviousAngle, renderAngle);
    }
    public boolean zAxis() { return dataTracker.get(Z_AXIS); }
    public java.util.List<VoxelShape> worldCollisionShapes() {
        return worldCollisionShapes(null);
    }
    public java.util.List<VoxelShape> worldCollisionShapes(Box query) {
        float angle = getWorld().isClient() ? angle(1.0f) : dataTracker.get(ANGLE);
        java.util.ArrayList<VoxelShape> result = new java.util.ArrayList<>();
        for (Box local : collisionShapes) {
            for (Box cell : collisionCells(local)) {
                Box world = transformedBounds(cell, angle);
                if (query == null || world.intersects(query)) result.add(VoxelShapes.cuboid(world));
            }
        }
        return result;
    }
    public Optional<Vec3d> raycastSeat(Vec3d start, Vec3d end, float tickDelta) {
        float angle = getWorld().isClient() ? angle(tickDelta) : dataTracker.get(ANGLE);
        Vec3d localStart = SwingTransform.rotate(start.subtract(getPos()), -angle, zAxis());
        Vec3d localEnd = SwingTransform.rotate(end.subtract(getPos()), -angle, zAxis());
        return structure.seatSlots().stream()
                .flatMap(box -> box.raycast(localStart, localEnd).stream())
                .min(Comparator.comparingDouble(localStart::squaredDistanceTo))
                .map(hit -> SwingTransform.localToWorld(hit, getPos(), angle, zAxis()));
    }
    @Override public boolean canHit() { return !isRemoved() && !structure.blocks().isEmpty(); }
    public boolean isSeatPoint(Vec3d worldPoint) {
        Vec3d local = SwingTransform.rotate(worldPoint.subtract(getPos()), -dataTracker.get(ANGLE), zAxis());
        return structure.seatShapes().stream().anyMatch(box -> box.contains(local));
    }
    private void refreshStructureBounds() {
        setBoundingBox(calculateStructureBounds());
    }
    public Box structureBounds() { return getBoundingBox(); }
    @Override public void setPosition(double x, double y, double z) {
        super.setPosition(x, y, z);
        refreshStructureBounds();
    }
    @Override public void calculateDimensions() {
        super.calculateDimensions();
        refreshStructureBounds();
    }
    private Box calculateStructureBounds() {
        // Entity's constructor calls this before our fields and tracked data exist.
        if (structure == null || structure.blocks().isEmpty()) return new Box(getX(), getY(), getZ(), getX(), getY(), getZ());
        Box b = structure.localBounds();
        Vec3d[] corners = new Vec3d[8]; int i=0;
        for (double x : new double[]{b.minX,b.maxX}) for (double y : new double[]{b.minY,b.maxY}) for (double z : new double[]{b.minZ,b.maxZ}) corners[i++]=SwingTransform.rotate(new Vec3d(x,y,z), dataTracker.get(ANGLE), zAxis());
        double minX=Double.POSITIVE_INFINITY,minY=Double.POSITIVE_INFINITY,minZ=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY,maxZ=Double.NEGATIVE_INFINITY;
        for (Vec3d p: corners) { minX=Math.min(minX,p.x); minY=Math.min(minY,p.y); minZ=Math.min(minZ,p.z); maxX=Math.max(maxX,p.x); maxY=Math.max(maxY,p.y); maxZ=Math.max(maxZ,p.z); }
        return new Box(getX()+minX,getY()+minY,getZ()+minZ,getX()+maxX,getY()+maxY,getZ()+maxZ);
    }

    public void addImpulse(float amount) { dataTracker.set(VELOCITY, MathHelper.clamp(dataTracker.get(VELOCITY) + amount, -.12f, .12f)); }
    @Override public void tick() {
        super.tick(); previousAngle = dataTracker.get(ANGLE);
        pruneSeats();
        if (getWorld().isClient()) {
            if (!renderAngleInitialized) {
                renderAngle = previousAngle;
                renderPreviousAngle = previousAngle;
                renderAngleInitialized = true;
            }
            renderPreviousAngle = renderAngle;
            int predictionTicks = Math.min(++clientSnapshotAge, CLIENT_PREDICTION_TICKS);
            float target = MathHelper.clamp(dataTracker.get(ANGLE) + dataTracker.get(VELOCITY) * predictionTicks,
                    -MAX_SWING_ANGLE, MAX_SWING_ANGLE);
            float predicted = MathHelper.clamp(renderAngle + dataTracker.get(VELOCITY), -MAX_SWING_ANGLE, MAX_SWING_ANGLE);
            // Continue locally between server snapshots, then converge gently when a snapshot arrives.
            renderAngle = MathHelper.lerp(CLIENT_CORRECTION, predicted, target);
        }
        if (!getWorld().isClient) {
            Box previousBounds = getBoundingBox();
            float a = dataTracker.get(ANGLE), v = dataTracker.get(VELOCITY);
            float input = 0f;
            for (Entity p : getPassengerList()) if (p instanceof ServerPlayerEntity player) {
                input += (player.getPlayerInput().forward() ? 1f : 0f)
                        - (player.getPlayerInput().backward() ? 1f : 0f);
            }
            input = MathHelper.clamp(input, -1f, 1f);
            // Pendulum recovery. A one-block horizontal displacement is the
            // sustained-input target; alternating input can continue building speed.
            v -= MathHelper.sin(a) * GRAVITY_ACCELERATION;
            v *= AIR_DAMPING;
            float ropeLength = Math.max(1f, structure.seatBlocks().stream()
                    .mapToInt(b -> -b.localPos().getY()).max().orElse(2));
            float sustained = (float) Math.asin(Math.min(1.0, 1.0 / ropeLength));
            if (input != 0f) {
                float target = input * dataTracker.get(INPUT_SIGN) * sustained;
                float error = target - a;
                v += MathHelper.clamp(error * INPUT_ACCELERATION, -INPUT_ACCELERATION, INPUT_ACCELERATION);
            }
            a += v;
            if (a > MAX_SWING_ANGLE || a < -MAX_SWING_ANGLE) {
                a = MathHelper.clamp(a, -MAX_SWING_ANGLE, MAX_SWING_ANGLE);
                v *= -.25f;
            }
            dataTracker.set(ANGLE, a); dataTracker.set(VELOCITY, v);
            refreshStructureBounds();
            carryStandingPlayers(previousBounds.union(getBoundingBox()), previousAngle, a);
            pushIntersectingPlayers(previousBounds.union(getBoundingBox()), previousAngle, a);
        }
        refreshStructureBounds();
    }
    @Override protected void updatePassengerPosition(Entity passenger, PositionUpdater updater) {
        var slots = structure.seatSlots();
        if (slots.isEmpty()) return;
        int index = passengerSeats.getOrDefault(passenger.getUuid(), -1);
        if (index < 0 || index >= slots.size()) {
            // Passenger and tracked-data packets can arrive separately. Never guess a client seat.
            if (getWorld().isClient()) return;
            index = firstFreeSeat();
            if (index < 0) return;
            passengerSeats.put(passenger.getUuid(), index);
            syncSeats();
        }
        Box bounds = slots.get(index);
        float seatAngle = getWorld().isClient() ? angle(1f) : dataTracker.get(ANGLE);
        Vec3d seat = SwingTransform.localToWorld(new Vec3d((bounds.minX + bounds.maxX) / 2, bounds.maxY + SEAT_HEIGHT_OFFSET, (bounds.minZ + bounds.maxZ) / 2), getPos(), seatAngle, zAxis());
        updater.accept(passenger, seat.x, seat.y, seat.z);
    }
    @Override public Vec3d updatePassengerForDismount(net.minecraft.entity.LivingEntity passenger) {
        // Our entity origin is the suspension pivot, several blocks above the seat.
        // Vanilla's origin-based fallback teleports riders there on dismount. Keep
        // the actual rider position instead, including the current swing rotation.
        // This also works after the passenger relationship/seat assignment is removed.
        return passenger.getPos();
    }
    private int firstFreeSeat() {
        boolean[] used = new boolean[structure.seatSlots().size()];
        for (int i : passengerSeats.values()) if (i >= 0 && i < used.length) used[i] = true;
        for (int i=0; i<used.length; i++) if (!used[i]) return i;
        return -1;
    }
    private boolean mountAtSeat(PlayerEntity passenger, int seat) {
        if (getWorld().isClient() || seat < 0 || seat >= structure.seatSlots().size()) return false;
        pruneSeats();
        if (passenger.hasVehicle() || passengerSeats.containsValue(seat)) return false;
        passengerSeats.put(passenger.getUuid(), seat);
        boolean mounted = passenger.startRiding(this);
        if (!mounted) passengerSeats.remove(passenger.getUuid());
        syncSeats();
        return mounted;
    }

    public static boolean trySeatCarried(ServerPlayerEntity observer, ServerPlayerEntity passenger) {
        Vec3d start = observer.getEyePos();
        Vec3d end = start.add(observer.getRotationVec(1.0f).multiply(observer.getEntityInteractionRange()));
        SwingEntity selectedSwing = null;
        int selectedSeat = -1;
        Vec3d selectedHit = null;
        double best = Double.POSITIVE_INFINITY;
        for (SwingEntity swing : SwingSpatialIndex.find(observer.getWorld(), new Box(start, end).expand(1.0))) {
            Vec3d localStart = SwingTransform.rotate(start.subtract(swing.getPos()), -swing.dataTracker.get(ANGLE), swing.zAxis());
            Vec3d localEnd = SwingTransform.rotate(end.subtract(swing.getPos()), -swing.dataTracker.get(ANGLE), swing.zAxis());
            var slots = swing.structure.seatSlots();
            for (int i = 0; i < slots.size(); i++) {
                Optional<Vec3d> candidate = slots.get(i).raycast(localStart, localEnd);
                if (candidate.isEmpty()) continue;
                Vec3d worldHit = SwingTransform.localToWorld(candidate.get(), swing.getPos(), swing.dataTracker.get(ANGLE), swing.zAxis());
                double distance = start.squaredDistanceTo(worldHit);
                if (distance < best) {
                    best = distance;
                    selectedSwing = swing;
                    selectedSeat = i;
                    selectedHit = worldHit;
                }
            }
        }
        if (selectedSwing == null || selectedHit == null) return false;
        var obstruction = observer.getWorld().raycast(new net.minecraft.world.RaycastContext(start, selectedHit,
                net.minecraft.world.RaycastContext.ShapeType.OUTLINE, net.minecraft.world.RaycastContext.FluidHandling.NONE, observer));
        if (obstruction.getType() != net.minecraft.util.hit.HitResult.Type.MISS
                && start.squaredDistanceTo(obstruction.getPos()) + 1.0e-6 < best) return false;
        return selectedSwing.mountAtSeat(passenger, selectedSeat);
    }

    private void pushIntersectingPlayers(Box sweptBounds, float fromAngle, float toAngle) {
        if (!(getWorld() instanceof ServerWorld world) || structure.blocks().isEmpty()) return;
        double radius = Math.max(1.0, -structure.localBounds().minY);
        int steps = MathHelper.clamp((int) Math.ceil(Math.abs(toAngle - fromAngle) * radius / .35), 1, 12);
        for (PlayerEntity player : world.getEntitiesByClass(PlayerEntity.class, sweptBounds.expand(.25),
                player -> player.isAlive() && player.getVehicle() != this && !player.isSpectator())) {
            Box playerBox = player.getBoundingBox();
            for (int step = 1; step <= steps; step++) {
                float angle = MathHelper.lerp(step / (float) steps, fromAngle, toAngle);
                for (Box local : collisionShapes) {
                    for (Box cell : collisionCells(local)) {
                        Box shape = transformedBounds(cell, angle);
                        if (!shape.intersects(playerBox)) continue;
                        for (Vec3d correction : separations(playerBox, shape)) {
                            Box moved = playerBox.offset(correction);
                            if (!world.isSpaceEmpty(player, moved.contract(1.0e-7))) continue;
                            player.setPosition(player.getPos().add(correction));
                            Vec3d velocity = player.getVelocity();
                            player.setVelocity(correction.x == 0 ? velocity.x : 0,
                                    correction.y == 0 ? velocity.y : Math.max(0, velocity.y),
                                    correction.z == 0 ? velocity.z : 0);
                            playerBox = moved;
                            break;
                        }
                    }
                }
            }
        }
    }

    private void carryStandingPlayers(Box sweptBounds, float fromAngle, float toAngle) {
        if (!(getWorld() instanceof ServerWorld world) || Math.abs(toAngle - fromAngle) < 1.0e-6) return;
        for (PlayerEntity player : world.getEntitiesByClass(PlayerEntity.class, sweptBounds.expand(.25),
                player -> player.isAlive() && player.getVehicle() != this && !player.isSpectator())) {
            Box playerBox = player.getBoundingBox();
            Vec3d feet = new Vec3d((playerBox.minX + playerBox.maxX) * .5, playerBox.minY,
                    (playerBox.minZ + playerBox.maxZ) * .5);
            boolean supported = false;
            outer:
            for (Box local : collisionShapes) {
                for (Box cell : collisionCells(local)) {
                    Box oldShape = transformedBounds(cell, fromAngle);
                    if (feet.y < oldShape.maxY - .08 || feet.y > oldShape.maxY + .16) continue;
                    if (playerBox.maxX <= oldShape.minX || playerBox.minX >= oldShape.maxX
                            || playerBox.maxZ <= oldShape.minZ || playerBox.minZ >= oldShape.maxZ) continue;
                    supported = true;
                    break outer;
                }
            }
            if (!supported) continue;
            Vec3d localFeet = SwingTransform.rotate(feet.subtract(getPos()), -fromAngle, zAxis());
            Vec3d movedFeet = SwingTransform.localToWorld(localFeet, getPos(), toAngle, zAxis());
            player.move(MovementType.SHULKER_BOX, movedFeet.subtract(feet));
            player.fallDistance = 0;
        }
    }

    private java.util.List<Box> collisionCells(Box box) {
        int xParts = zAxis() ? parts(box.getLengthX()) : 1;
        int yParts = parts(box.getLengthY());
        int zParts = zAxis() ? 1 : parts(box.getLengthZ());
        if (xParts == 1 && yParts == 1 && zParts == 1) return java.util.List.of(box);
        java.util.ArrayList<Box> cells = new java.util.ArrayList<>(xParts * yParts * zParts);
        for (int x = 0; x < xParts; x++) for (int y = 0; y < yParts; y++) for (int z = 0; z < zParts; z++) {
            cells.add(new Box(
                    MathHelper.lerp(x / (double) xParts, box.minX, box.maxX),
                    MathHelper.lerp(y / (double) yParts, box.minY, box.maxY),
                    MathHelper.lerp(z / (double) zParts, box.minZ, box.maxZ),
                    MathHelper.lerp((x + 1) / (double) xParts, box.minX, box.maxX),
                    MathHelper.lerp((y + 1) / (double) yParts, box.minY, box.maxY),
                    MathHelper.lerp((z + 1) / (double) zParts, box.minZ, box.maxZ)));
        }
        return cells;
    }

    private static int parts(double length) {
        return Math.max(1, (int) Math.ceil(length / COLLISION_CELL_SIZE));
    }

    private Box transformedBounds(Box local, float angle) {
        double minX=Double.POSITIVE_INFINITY,minY=Double.POSITIVE_INFINITY,minZ=Double.POSITIVE_INFINITY;
        double maxX=Double.NEGATIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY,maxZ=Double.NEGATIVE_INFINITY;
        for (double x : new double[]{local.minX, local.maxX}) for (double y : new double[]{local.minY, local.maxY}) for (double z : new double[]{local.minZ, local.maxZ}) {
            Vec3d p = SwingTransform.localToWorld(new Vec3d(x, y, z), getPos(), angle, zAxis());
            minX=Math.min(minX,p.x); minY=Math.min(minY,p.y); minZ=Math.min(minZ,p.z);
            maxX=Math.max(maxX,p.x); maxY=Math.max(maxY,p.y); maxZ=Math.max(maxZ,p.z);
        }
        return new Box(minX,minY,minZ,maxX,maxY,maxZ);
    }

    private static java.util.List<Vec3d> separations(Box entity, Box obstacle) {
        double west = obstacle.minX - entity.maxX - COLLISION_EPSILON;
        double east = obstacle.maxX - entity.minX + COLLISION_EPSILON;
        double down = obstacle.minY - entity.maxY - COLLISION_EPSILON;
        double up = obstacle.maxY - entity.minY + COLLISION_EPSILON;
        double north = obstacle.minZ - entity.maxZ - COLLISION_EPSILON;
        double south = obstacle.maxZ - entity.minZ + COLLISION_EPSILON;
        java.util.ArrayList<Vec3d> candidates = new java.util.ArrayList<>(java.util.List.of(
                new Vec3d(west,0,0), new Vec3d(east,0,0), new Vec3d(0,down,0),
                new Vec3d(0,up,0), new Vec3d(0,0,north), new Vec3d(0,0,south)));
        candidates.sort(Comparator.comparingDouble(Vec3d::lengthSquared));
        return candidates;
    }
    @Override public boolean canAddPassenger(Entity passenger) {
        if (!(passenger instanceof PlayerEntity)) return false;
        Integer assigned = passengerSeats.get(passenger.getUuid());
        return assigned != null && assigned >= 0 && assigned < structure.seatSlots().size()
                || firstFreeSeat() >= 0;
    }
    @Override public ActionResult interact(PlayerEntity player, Hand hand) {
        com.kuilunfuzhe.monvhua.MonvhuaMod.LOGGER.info("[SwingDiag] ENTITY_INTERACT side={} uuid={} player={} hand={} blocks={} canHit={} bounds={} seatLocal={}",
                getWorld().isClient() ? "CLIENT" : "SERVER", getUuid(), player.getName().getString(), hand, structure.blocks().size(), canHit(), getBoundingBox(), structure.seatBounds());
        if (hand != Hand.MAIN_HAND || player.isSpectator()) return ActionResult.PASS;
        Vec3d start = player.getEyePos();
        Vec3d end = start.add(player.getRotationVec(1.0f).multiply(player.getEntityInteractionRange()));
        Vec3d localStart = SwingTransform.rotate(start.subtract(getPos()), -dataTracker.get(ANGLE), zAxis());
        Vec3d localEnd = SwingTransform.rotate(end.subtract(getPos()), -dataTracker.get(ANGLE), zAxis());
        Vec3d localHit = null;
        int seat = -1;
        double bestDistance = Double.POSITIVE_INFINITY;
        var slots = structure.seatSlots();
        for (int i = 0; i < slots.size(); i++) {
            var candidate = slots.get(i).raycast(localStart, localEnd);
            if (candidate.isPresent()) {
                double distance = localStart.squaredDistanceTo(candidate.get());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    localHit = candidate.get();
                    seat = i;
                }
            }
        }
        java.util.Optional<Vec3d> hit = localHit == null ? java.util.Optional.empty()
                : java.util.Optional.of(SwingTransform.localToWorld(localHit, getPos(), dataTracker.get(ANGLE), zAxis()));
        com.kuilunfuzhe.monvhua.MonvhuaMod.LOGGER.info("[SwingDiag] SEAT_RAY uuid={} eye={} end={} hit={} accepted={} angle={}",
                getUuid(), start, end, hit, hit.isPresent(), dataTracker.get(ANGLE));
        if (hit.isEmpty()) return ActionResult.PASS;
        if (seat < 0) return ActionResult.PASS;
        com.kuilunfuzhe.monvhua.MonvhuaMod.LOGGER.info("[SwingDiag] SEAT_SELECTED uuid={} seat={} hitLocal={} slots={}",
                getUuid(), seat, localHit, slots.size());
        pruneSeats();
        if (passengerSeats.containsValue(seat)) {
            com.kuilunfuzhe.monvhua.MonvhuaMod.LOGGER.info("[SwingDiag] SEAT_OCCUPIED uuid={} seat={}", getUuid(), seat);
            return ActionResult.PASS;
        }
        var obstruction = getWorld().raycast(new net.minecraft.world.RaycastContext(start, hit.get(),
                net.minecraft.world.RaycastContext.ShapeType.OUTLINE, net.minecraft.world.RaycastContext.FluidHandling.NONE, player));
        if (obstruction.getType() != net.minecraft.util.hit.HitResult.Type.MISS
                && start.squaredDistanceTo(obstruction.getPos()) + 1.0e-6 < start.squaredDistanceTo(hit.get())) return ActionResult.PASS;
        if (passengerSeats.containsKey(player.getUuid())) return ActionResult.PASS;
        if (!canAddPassenger(player)) return ActionResult.PASS;
        if (getWorld().isClient()) return ActionResult.SUCCESS;
        boolean mounted = mountAtSeat(player, seat);
        com.kuilunfuzhe.monvhua.MonvhuaMod.LOGGER.info("[SwingDiag] MOUNT_RESULT uuid={} player={} seat={} mounted={} passengers={}",
                getUuid(), player.getName().getString(), seat, mounted, getPassengerList().size());
        return mounted ? ActionResult.SUCCESS : ActionResult.PASS;
    }
    @Override protected void readCustomData(ReadView view) {
        dataTracker.set(ANGLE, view.read("Angle", Codec.FLOAT).orElse(0f));
        dataTracker.set(VELOCITY, view.read("Velocity", Codec.FLOAT).orElse(0f));
        dataTracker.set(Z_AXIS, view.read("ZAxis", Codec.BOOL).orElse(false));
        dataTracker.set(INPUT_SIGN, view.read("InputSign", Codec.INT).orElse(1) < 0 ? -1 : 1);
        var positions = view.read("Positions", Codec.LONG.listOf()).orElse(java.util.List.of());
        var ids = view.read("StateIds", Codec.INT.listOf()).orElse(java.util.List.of());
        structure = SwingStructure.fromPacked(positions, ids);
        syncStructure();
        passengerSeats.clear();
        view.read("SeatAssignments", Codec.unboundedMap(Codec.STRING, Codec.INT)).orElse(Map.of()).forEach((id, seat) -> {
            if (seat >= 0 && seat < structure.seatSlots().size() && !passengerSeats.containsValue(seat)) {
                try { passengerSeats.put(java.util.UUID.fromString(id), seat); }
                catch (IllegalArgumentException ignored) { }
            }
        });
        syncSeats();
    }
    @Override protected void writeCustomData(WriteView view) {
        view.put("Angle", Codec.FLOAT, dataTracker.get(ANGLE));
        view.put("Velocity", Codec.FLOAT, dataTracker.get(VELOCITY));
        view.put("ZAxis", Codec.BOOL, dataTracker.get(Z_AXIS));
        view.put("InputSign", Codec.INT, dataTracker.get(INPUT_SIGN));
        view.put("Positions", Codec.LONG.listOf(), structure.packedPositions());
        view.put("StateIds", Codec.INT.listOf(), structure.stateIds());
        Map<String, Integer> seats = new HashMap<>();
        passengerSeats.forEach((id, seat) -> seats.put(id.toString(), seat));
        view.put("SeatAssignments", Codec.unboundedMap(Codec.STRING, Codec.INT), seats);
    }
    public void restoreStructure() {
        if (!(getWorld() instanceof ServerWorld world)) return;
        for (SwingBlock block : structure.blocks()) {
            var pos = getBlockPos().add(block.localPos());
            if (world.getBlockState(pos).isReplaceable()) world.setBlockState(pos, block.state(), net.minecraft.block.Block.NOTIFY_ALL);
            else net.minecraft.block.Block.dropStacks(block.state(), world, pos);
        }
        structure = new SwingStructure(java.util.List.of());
    }
    @Override public boolean damage(ServerWorld world, net.minecraft.entity.damage.DamageSource source, float amount) {
        Entity attacker = source.getAttacker();
        if (!(attacker instanceof PlayerEntity player) || !player.getMainHandStack().isOf(SwingAssemblyItems.ASSEMBLE_STICK)) return false;
        removeAllPassengers(); restoreStructure(); discard(); return true;
    }
    @Override public ActionResult interactAt(PlayerEntity player, Vec3d hitPos, Hand hand) {
        return interact(player, hand);
    }
    public boolean canPlayerReachSeat(PlayerEntity player) {
        Vec3d start = player.getEyePos();
        Vec3d end = start.add(player.getRotationVec(1.0f).multiply(player.getEntityInteractionRange()));
        return raycastSeat(start, end, 1.0f).isPresent();
    }
    @Override public void remove(RemovalReason reason) {
        SwingSpatialIndex.unregister(this);
        super.remove(reason);
    }
}
