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
    private static final double COLLISION_EPSILON = 0.015;
    private static final TrackedData<Float> ANGLE = DataTracker.registerData(SwingEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> VELOCITY = DataTracker.registerData(SwingEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Boolean> Z_AXIS = DataTracker.registerData(SwingEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> INPUT_SIGN = DataTracker.registerData(SwingEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<NbtCompound> STRUCTURE = DataTracker.registerData(SwingEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
    private static final TrackedData<NbtCompound> SEATS = DataTracker.registerData(SwingEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
    private static final TrackedData<NbtCompound> MOTION = DataTracker.registerData(SwingEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
    private SwingStructure structure = new SwingStructure(java.util.List.of());
    private float previousAngle;
    private float renderAngle;
    private float renderPreviousAngle;
    private boolean renderAngleInitialized;
    private final SwingMotionTimeline motionTimeline = new SwingMotionTimeline();
    private final java.util.ArrayDeque<SwingMotionTimeline.Snapshot> interactionHistory = new java.util.ArrayDeque<>();
    private final Map<java.util.UUID, Integer> passengerSeats = new HashMap<>();
    private Entity platformPassenger;
    private SwingStructureSpace collisionPose;
    private java.util.List<Box> collisionBoxes = java.util.List.of();
    private long collisionBuilds;
    public long collisionBuilds() { return collisionBuilds; }
    public int cachedCollisionBoxCount() { return collisionBoxes.size(); }

    public SwingEntity(EntityType<? extends SwingEntity> type, World world) { super(type, world); noClip = true; }
    public SwingEntity(World world, Vec3d pivot, SwingStructure structure, boolean zAxis) {
        this(world, pivot, structure, zAxis, 1);
    }
    public SwingEntity(World world, Vec3d pivot, SwingStructure structure, boolean zAxis, int inputSign) {
        this(ModEntities.SWING, world); setPosition(pivot); this.structure = structure; dataTracker.set(Z_AXIS, zAxis); dataTracker.set(INPUT_SIGN, inputSign < 0 ? -1 : 1); syncStructure(); refreshStructureBounds();
    }
    @Override protected void initDataTracker(DataTracker.Builder b) { b.add(ANGLE, 0f); b.add(VELOCITY, 0f); b.add(Z_AXIS, false); b.add(INPUT_SIGN, 1); b.add(STRUCTURE, new NbtCompound()); b.add(SEATS, new NbtCompound()); b.add(MOTION, new NbtCompound()); }
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
        var n = new NbtCompound();
        n.putLongArray("P", structure.packedPositions().stream().mapToLong(Long::longValue).toArray());
        n.putIntArray("S", structure.stateIds().stream().mapToInt(Integer::intValue).toArray());
        dataTracker.set(STRUCTURE, n);
        SwingSpatialIndex.register(this);
    }
    @Override public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (data == MOTION && getWorld().isClient()) {
            var snapshot = dataTracker.get(MOTION);
            float a = snapshot.getFloat("Angle").orElse(0f);
            motionTimeline.accept(snapshot.getLong("Tick").orElse(0L), a,
                    snapshot.getFloat("Velocity").orElse(0f), age);
            if (Math.abs(a - renderAngle) > 1f) {
                renderAngle = a;
                renderPreviousAngle = a;
            }
        }
        if (data == SEATS && getWorld().isClient()) {
            passengerSeats.clear();
            var n = dataTracker.get(SEATS);
            for (String id : n.getKeys()) {
                try { passengerSeats.put(java.util.UUID.fromString(id), n.getInt(id).orElse(-1)); }
                catch (IllegalArgumentException ignored) { }
            }
        }
        if (data == STRUCTURE) {
            renderAngleInitialized = false;
            motionTimeline.clear();
            interactionHistory.clear();
            var n = dataTracker.get(STRUCTURE);
            structure = SwingStructure.fromPacked(java.util.Arrays.stream(n.getLongArray("P").orElse(new long[0])).boxed().toList(), java.util.Arrays.stream(n.getIntArray("S").orElse(new int[0])).boxed().toList());
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
    public SwingStructureSpace space(float tickDelta) {
        return new SwingStructureSpace(structure, getPos(), angle(tickDelta), zAxis());
    }
    public boolean ignoresCollision(Entity entity) { return entity.getVehicle() == this || entity == platformPassenger; }
    public java.util.List<VoxelShape> worldCollisionShapes() {
        return worldCollisionShapes(null);
    }
    public java.util.List<VoxelShape> worldCollisionShapes(Box query) {
        SwingStructureSpace pose = space(1);
        if (!pose.equals(collisionPose)) {
            collisionPose = pose;
            collisionBoxes = pose.collisionBoxes(null);
            collisionBuilds++;
        }
        return collisionBoxes.stream().filter(box -> query == null || box.intersects(query)).map(VoxelShapes::cuboid).toList();
    }
    public Optional<SwingStructureSpace.Hit> raycastStructure(Vec3d start, Vec3d end, float tickDelta) {
        return space(tickDelta).raycast(start, end);
    }
    private Optional<SwingStructureSpace.Hit> interactionHit(Vec3d start, Vec3d end) {
        var current = raycastStructure(start, end, 1);
        if (current.isPresent() || getWorld().isClient()) return current;
        // The client displays a buffered server pose. Recheck the same in-range ray against
        // recent authoritative poses; client-supplied positions never authorize a hit.
        var recent = interactionHistory.descendingIterator();
        while (recent.hasNext()) {
            var sample = recent.next();
            if (getWorld().getTime() - sample.tick() > 8) continue;
            var hit = new SwingStructureSpace(structure, getPos(), sample.angle(), zAxis()).raycast(start, end);
            if (hit.isPresent()) return hit;
        }
        return Optional.empty();
    }
    public Optional<Vec3d> raycastSeat(Vec3d start, Vec3d end, float tickDelta) {
        return raycastStructure(start, end, tickDelta).filter(hit -> hit.seat() >= 0).map(SwingStructureSpace.Hit::worldPoint);
    }
    @Override public boolean canHit() { return !isRemoved() && !structure.blocks().isEmpty(); }
    public boolean isSeatPoint(Vec3d worldPoint) {
        Vec3d local = SwingTransform.rotate(worldPoint.subtract(getPos()), -dataTracker.get(ANGLE), zAxis());
        return structure.seatShapes().stream().anyMatch(box -> box.contains(local));
    }
    private void refreshStructureBounds() {
        setBoundingBox(calculateStructureBounds());
    }
    public Box structureBounds() {
        return getWorld().isClient() ? space(0).bounds().union(space(1).bounds()) : getBoundingBox();
    }
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
        return space(1).bounds();
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
            float target = motionTimeline.sample(age, dataTracker.get(ANGLE));
            float previousTarget = motionTimeline.sample(age - 1, dataTracker.get(ANGLE));
            float predicted = MathHelper.clamp(renderAngle + target - previousTarget, -MAX_SWING_ANGLE, MAX_SWING_ANGLE);
            renderAngle = MathHelper.lerp(CLIENT_CORRECTION, predicted, target);
            carryStandingPlayers(space(0).bounds().union(space(1).bounds()), renderPreviousAngle, renderAngle);
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
            var snapshot = new NbtCompound();
            snapshot.putLong("Tick", getWorld().getTime());
            snapshot.putFloat("Angle", a);
            snapshot.putFloat("Velocity", v);
            dataTracker.set(MOTION, snapshot);
            interactionHistory.addLast(new SwingMotionTimeline.Snapshot(getWorld().getTime(), a, v));
            while (interactionHistory.size() > 9) interactionHistory.removeFirst();
            refreshStructureBounds();
            var supported = carryStandingPlayers(previousBounds.union(getBoundingBox()), previousAngle, a);
            pushIntersectingPlayers(previousBounds.union(getBoundingBox()), previousAngle, a, supported);
        }
        refreshStructureBounds();
        SwingSpatialIndex.register(this);
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
        Vec3d seat = space(1).toWorld(new Vec3d((bounds.minX + bounds.maxX) / 2, bounds.maxY + SEAT_HEIGHT_OFFSET, (bounds.minZ + bounds.maxZ) / 2));
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
            var candidate = swing.interactionHit(start, end);
            if (candidate.isPresent()) {
                Vec3d worldHit = candidate.get().worldPoint();
                double distance = start.squaredDistanceTo(worldHit);
                if (distance < best) {
                    best = distance;
                    selectedSwing = swing;
                    selectedSeat = candidate.get().seat();
                    selectedHit = worldHit;
                }
            }
        }
        if (selectedSwing == null || selectedHit == null || selectedSeat < 0) return false;
        var obstruction = observer.getWorld().raycast(new net.minecraft.world.RaycastContext(start, selectedHit,
                net.minecraft.world.RaycastContext.ShapeType.OUTLINE, net.minecraft.world.RaycastContext.FluidHandling.NONE, observer));
        if (obstruction.getType() != net.minecraft.util.hit.HitResult.Type.MISS
                && start.squaredDistanceTo(obstruction.getPos()) + 1.0e-6 < best) return false;
        return selectedSwing.mountAtSeat(passenger, selectedSeat);
    }

    private void pushIntersectingPlayers(Box sweptBounds, float fromAngle, float toAngle, java.util.Set<java.util.UUID> supported) {
        if (!(getWorld() instanceof ServerWorld world) || structure.blocks().isEmpty()) return;
        double radius = Math.max(1.0, -structure.localBounds().minY);
        int steps = MathHelper.clamp((int) Math.ceil(Math.abs(toAngle - fromAngle) * radius / .35), 1, 12);
        for (PlayerEntity player : world.getEntitiesByClass(PlayerEntity.class, sweptBounds.expand(.25),
                player -> player.isAlive() && player.getVehicle() != this && !player.isSpectator())) {
            Box playerBox = player.getBoundingBox();
            // A carried standing player is already at the final pose. Earlier sweep samples
            // must not push them backwards into the old platform position.
            for (int step = supported.contains(player.getUuid()) ? steps : 1; step <= steps; step++) {
                float angle = MathHelper.lerp(step / (float) steps, fromAngle, toAngle);
                for (Box shape : new SwingStructureSpace(structure, getPos(), angle, zAxis()).collisionBoxes(playerBox.expand(.5))) {
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

    private java.util.Set<java.util.UUID> carryStandingPlayers(Box sweptBounds, float fromAngle, float toAngle) {
        java.util.Set<java.util.UUID> carried = new java.util.HashSet<>();
        if (Math.abs(toAngle - fromAngle) < 1.0e-6) return carried;
        for (PlayerEntity player : getWorld().getEntitiesByClass(PlayerEntity.class, sweptBounds.expand(.25),
                player -> player.isAlive() && !player.hasVehicle() && !player.isSpectator()
                        && (!getWorld().isClient() || player.isMainPlayer()))) {
            if (player.getVelocity().y > .1) continue;
            Box playerBox = player.getBoundingBox();
            Vec3d feet = new Vec3d((playerBox.minX + playerBox.maxX) * .5, playerBox.minY,
                    (playerBox.minZ + playerBox.maxZ) * .5);
            boolean supported = false;
            for (Box oldShape : new SwingStructureSpace(structure, getPos(), fromAngle, zAxis()).collisionBoxes(playerBox.expand(.2))) {
                    if (feet.y < oldShape.maxY - .08 || feet.y > oldShape.maxY + .16) continue;
                    if (playerBox.maxX <= oldShape.minX || playerBox.minX >= oldShape.maxX
                            || playerBox.maxZ <= oldShape.minZ || playerBox.minZ >= oldShape.maxZ) continue;
                    supported = true;
                    break;
            }
            if (!supported) continue;
            Vec3d localFeet = SwingTransform.rotate(feet.subtract(getPos()), -fromAngle, zAxis());
            Vec3d movedFeet = SwingTransform.localToWorld(localFeet, getPos(), toAngle, zAxis());
            platformPassenger = player;
            try {
                player.move(MovementType.SHULKER_BOX, movedFeet.subtract(feet));
            } finally {
                platformPassenger = null;
            }
            player.fallDistance = 0;
            carried.add(player.getUuid());
        }
        return carried;
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
        var selection = interactionHit(start, end);
        if (selection.isEmpty() || selection.get().seat() < 0) return ActionResult.PASS;
        Vec3d localHit = selection.get().localPoint();
        int seat = selection.get().seat();
        var slots = structure.seatSlots();
        java.util.Optional<Vec3d> hit = java.util.Optional.of(selection.get().worldPoint());
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
        if (!canPlayerReachStructure(player)) return false;
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
    public boolean canPlayerReachStructure(PlayerEntity player) {
        if (player.isSpectator()) return false;
        Vec3d start = player.getEyePos();
        Vec3d end = start.add(player.getRotationVec(1).multiply(player.getEntityInteractionRange()));
        var hit = interactionHit(start, end);
        if (hit.isEmpty()) return false;
        var obstruction = getWorld().raycast(new net.minecraft.world.RaycastContext(start, hit.get().worldPoint(),
                net.minecraft.world.RaycastContext.ShapeType.OUTLINE, net.minecraft.world.RaycastContext.FluidHandling.NONE, player));
        return obstruction.getType() == net.minecraft.util.hit.HitResult.Type.MISS
                || start.squaredDistanceTo(obstruction.getPos()) + 1.0e-6 >= start.squaredDistanceTo(hit.get().worldPoint());
    }
    @Override public void remove(RemovalReason reason) {
        SwingSpatialIndex.unregister(this);
        super.remove(reason);
    }
}
