package com.kuilunfuzhe.monvhua.features.gravity;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.TrapdoorBlock;
import com.kuilunfuzhe.monvhua.item.gravity.GravityItems;
import com.kuilunfuzhe.monvhua.mixin.gravity.SurfaceGravityEntityAccessor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import java.util.ArrayList;
import java.util.List;

public final class SurfaceGravityEngine {
    private static final double SUPPORT_EPSILON = 0.035D;
    private static final double SUPPORT_TARGET_GAP = 0.002D;
    private static final double SUPPORT_CORRECTION_DEADBAND = 0.006D;
    private static final double SUPPORT_PROBE = 0.12D;
    private static final double GRAVITY = 0.08D;
    private static final double MAX_FALL_SPEED = 3.92D;
    private static final double JUMP_SPEED = 0.42D;
    private static final double AIR_AXIS_DRAG = 0.98D;
    private static final double AIR_TANGENT_DRAG = 0.91D;
    private static final double GROUND_DRAG = 0.546D;
    private static final double WALK_ACCELERATION = 0.10D;
    private static final double SPRINT_ACCELERATION = 0.13D;
    private static final double SNEAK_ACCELERATION = 0.03D;
    private static final double AIR_ACCELERATION = 0.02D;
    private static final double STEP_HEIGHT = 0.6D;
    private static final double CLIMB_SPEED = 0.20D;
    private static final int JUMP_DETACH_TICKS = 8;
    private static final int EDGE_TRANSFER_COOLDOWN_TICKS = 6;
    private static final int EDGE_TRANSFER_GRACE_TICKS = 5;
    private static final double EDGE_TRANSFER_REACH = 1.10D;
    private static final double EDGE_TRANSFER_FORWARD_REACH = 1.20D;
    private static final double EDGE_TRANSFER_SAMPLE_STEP = 0.20D;
    private static final double EDGE_TRANSFER_LATERAL_REACH = 0.20D;
    private static final double EDGE_TRANSFER_MIN_MOVEMENT = 1.0E-4D;
    private static final double EDGE_TRANSFER_OUTER_DOT_MIN = 0.20D;
    private static final int EDGE_TRANSFER_PATH_REQUIRED_BLOCKS = 3;
    private static final double EDGE_TRANSFER_PATH_STEP = 1.0D;
    private static final double EDGE_TRANSFER_PATH_CLEARANCE_HEIGHT = 2.0D;
    private static final double EDGE_TRANSFER_PATH_PLANE_TOLERANCE = 0.12D;

    private SurfaceGravityEngine() {
    }

    public static boolean tick(Entity entity, PlayerInput input, SurfaceState state) {
        if (entity == null || input == null || state == null || state.downDirection == null) {
            return false;
        }
        if (shouldUseVanilla(entity)) {
            clearEntitySurface(entity);
            return false;
        }

        entity.setNoGravity(true);
        entity.fallDistance = 0.0F;

        if (state.detachTicks > 0) {
            state.detachTicks--;
        }
        if (state.edgeTransferCooldown > 0) {
            state.edgeTransferCooldown--;
        }
        if (state.edgeTransferGraceTicks > 0) {
            state.edgeTransferGraceTicks--;
        }

        Direction downDirection = state.downDirection;
        if (downDirection != Direction.DOWN) {
            state.updateCrouch(input.sneak() || entity.isInPose(EntityPose.CROUCHING), entity.age);
        }
        Vec3d down = SurfaceGravityBasis.directionVector(downDirection);
        Vec3d up = down.multiply(-1.0D);
        Vec3d velocity = entity.getVelocity();
        double downSpeed = velocity.dotProduct(down);
        SurfaceSupport support = state.detachTicks <= 0 && downSpeed >= -0.02D
                ? findSupport(entity, downDirection, SUPPORT_PROBE)
                : null;
        boolean climbable = isClimbable(entity, downDirection);
        boolean canAutoTransfer = isMainHandHoldingGravityWand(entity);

        if (support != null) {
            state.attached = true;
            state.edgeTransferGraceTicks = canAutoTransfer ? EDGE_TRANSFER_GRACE_TICKS : 0;
            Vec3d acceleration = inputAcceleration(input, downDirection, state.localYaw, groundAcceleration(input));
            Vec3d tangential = SurfaceGravityBasis.reject(velocity, down).add(acceleration);
            if (input.sneak()) {
                tangential = tangential.multiply(0.35D);
            }
            double correction = support.gap() - SUPPORT_TARGET_GAP;
            double correctionSpeed = Math.abs(correction) <= SUPPORT_CORRECTION_DEADBAND
                    ? 0.0D
                    : Math.clamp(correction * 0.35D, -0.04D, 0.04D);
            Vec3d moveVelocity = tangential.add(down.multiply(correctionSpeed));

            if (input.jump()) {
                state.detachTicks = JUMP_DETACH_TICKS;
                state.attached = false;
                Vec3d jumpVelocity = tangential.add(up.multiply(JUMP_SPEED));
                Vec3d moved = move(entity, jumpVelocity, downDirection);
                velocity = applyDrag(moved, down, AIR_TANGENT_DRAG);
                setSurfaceGrounded(entity, false);
            } else {
                moveAttached(entity, moveVelocity, downDirection);
                snapToSupport(entity, downDirection);
                velocity = applyDrag(tangential, down, GROUND_DRAG);
                setSurfaceGrounded(entity, true);
            }
        } else if (canAutoTransfer && tryEdgeTransfer(entity, input, state, downDirection, velocity)) {
            Direction nextDown = state.downDirection;
            Vec3d nextDownVec = SurfaceGravityBasis.directionVector(nextDown);
            velocity = SurfaceGravityBasis.reject(entity.getVelocity(), nextDownVec).multiply(GROUND_DRAG);
            setSurfaceGrounded(entity, true);
        } else if (canAutoTransfer && holdEdgeTransferGrace(entity, input, state, downDirection, velocity)) {
            velocity = entity.getVelocity();
        } else if (climbable && (input.forward() || input.jump())) {
            state.attached = false;
            Vec3d acceleration = inputAcceleration(input, downDirection, state.localYaw, SNEAK_ACCELERATION);
            Vec3d climbVelocity = SurfaceGravityBasis.reject(velocity, down).add(acceleration).add(up.multiply(CLIMB_SPEED));
            Vec3d moved = move(entity, climbVelocity, downDirection);
            velocity = applyDrag(moved, down, AIR_TANGENT_DRAG);
            setSurfaceGrounded(entity, false);
        } else {
            state.attached = false;
            Vec3d acceleration = inputAcceleration(input, downDirection, state.localYaw, AIR_ACCELERATION);
            double nextDownSpeed = Math.min(downSpeed + GRAVITY, MAX_FALL_SPEED);
            Vec3d tangential = SurfaceGravityBasis.reject(velocity, down).add(acceleration);
            Vec3d moveVelocity = tangential.add(down.multiply(nextDownSpeed));
            Vec3d moved = move(entity, moveVelocity, downDirection);
            velocity = applyDrag(moved, down, AIR_TANGENT_DRAG);
            setSurfaceGrounded(entity, false);
        }

        entity.setVelocity(velocity);
        updateBodyYaw(state);
        return true;
    }

    private static boolean shouldUseVanilla(Entity entity) {
        return entity.isTouchingWater() || entity.isInLava() || entity.hasVehicle()
                || entity instanceof PlayerEntity player && player.getAbilities().flying;
    }

    private static void clearEntitySurface(Entity entity) {
        entity.setNoGravity(false);
        entity.fallDistance = 0.0F;
        if (entity instanceof ServerPlayerEntity player) {
            GravityMagic.clearSurfaceGravity(player);
        } else {
            GravityMagic.clearClientSurfaceGravity(entity);
        }
    }

    private static double groundAcceleration(PlayerInput input) {
        if (input.sneak()) {
            return SNEAK_ACCELERATION;
        }
        if (input.sprint()) {
            return SPRINT_ACCELERATION;
        }
        return WALK_ACCELERATION;
    }

    private static Vec3d inputAcceleration(PlayerInput input, Direction downDirection, float localYaw, double acceleration) {
        return inputMovement(input, downDirection, localYaw).multiply(acceleration);
    }

    private static Vec3d inputMovement(PlayerInput input, Direction downDirection, float localYaw) {
        double forwardAmount = (input.forward() ? 1.0D : 0.0D) - (input.backward() ? 1.0D : 0.0D);
        double sideAmount = (input.right() ? 1.0D : 0.0D) - (input.left() ? 1.0D : 0.0D);
        if (forwardAmount == 0.0D && sideAmount == 0.0D) {
            return Vec3d.ZERO;
        }

        Vec3d forward = SurfaceGravityBasis.movementForward(downDirection, localYaw);
        Vec3d right = SurfaceGravityBasis.movementRight(downDirection, localYaw);
        Vec3d movement = forward.multiply(forwardAmount).add(right.multiply(sideAmount));
        if (movement.lengthSquared() > 1.0D) {
            movement = movement.normalize();
        }
        return movement;
    }

    private static boolean isMainHandHoldingGravityWand(Entity entity) {
        return entity instanceof PlayerEntity player
                && player.getMainHandStack().getItem() == GravityItems.GRAVITY_WAND;
    }

    private static Vec3d applyDrag(Vec3d velocity, Vec3d down, double tangentDrag) {
        double axisSpeed = velocity.dotProduct(down) * AIR_AXIS_DRAG;
        Vec3d tangential = SurfaceGravityBasis.reject(velocity, down).multiply(tangentDrag);
        return tangential.add(down.multiply(axisSpeed));
    }

    private static void moveAttached(Entity entity, Vec3d moveVelocity, Direction downDirection) {
        Vec3d down = SurfaceGravityBasis.directionVector(downDirection);
        double requestedSq = SurfaceGravityBasis.reject(moveVelocity, down).lengthSquared();
        if (requestedSq <= 1.0E-8D) {
            move(entity, moveVelocity, downDirection);
            return;
        }

        double beforeX = entity.getX();
        double beforeY = entity.getY();
        double beforeZ = entity.getZ();
        Vec3d moved = move(entity, moveVelocity, downDirection);

        double normalX = entity.getX();
        double normalY = entity.getY();
        double normalZ = entity.getZ();
        double movedSq = SurfaceGravityBasis.reject(moved, down).lengthSquared();
        if (movedSq >= requestedSq * 0.35D) {
            return;
        }

        SurfaceGravityCollision.setAnchorAndRefreshBox(entity, downDirection, new Vec3d(beforeX, beforeY, beforeZ));
        Vec3d up = down.multiply(-STEP_HEIGHT);
        Vec3d stepped = move(entity, moveVelocity.add(up), downDirection);
        double steppedSq = SurfaceGravityBasis.reject(stepped, down).lengthSquared();
        SurfaceSupport steppedSupport = findSupport(entity, downDirection, STEP_HEIGHT + SUPPORT_PROBE);
        if (steppedSq < Math.max(movedSq, requestedSq * 0.35D)
                || steppedSupport == null
                || steppedSupport.gap() > STEP_HEIGHT * 0.5D) {
            SurfaceGravityCollision.setAnchorAndRefreshBox(entity, downDirection, new Vec3d(normalX, normalY, normalZ));
        }
    }

    private static Vec3d move(Entity entity, Vec3d movement, Direction downDirection) {
        Vec3d before = entity.getPos();
        entity.move(MovementType.SELF, movement);
        SurfaceGravityCollision.refreshBox(entity, downDirection);
        Vec3d adjusted = entity.getPos().subtract(before);
        updateCollisionFlags(entity, movement, adjusted, downDirection);
        return adjusted;
    }

    private static boolean tryEdgeTransfer(Entity entity, PlayerInput input, SurfaceState state, Direction oldDown, Vec3d velocity) {
        if ((!state.attached && state.edgeTransferGraceTicks <= 0)
                || state.edgeTransferCooldown > 0
                || state.detachTicks > 0
                || input.sneak()
                || input.jump()) {
            return false;
        }

        Vec3d movement = inputMovement(input, oldDown, state.localYaw);
        if (movement.lengthSquared() <= EDGE_TRANSFER_MIN_MOVEMENT) {
            movement = SurfaceGravityBasis.reject(velocity, SurfaceGravityBasis.directionVector(oldDown));
        }
        if (movement.lengthSquared() <= EDGE_TRANSFER_MIN_MOVEMENT) {
            return false;
        }

        Vec3d anchor = entity.getPos();
        List<Vec3d> probeAnchors = edgeTransferProbeAnchors(anchor, oldDown, movement);
        Vec3d worldLook = SurfaceGravityBasis.look(oldDown, state.localYaw, state.localPitch);
        for (Direction candidate : edgeTransferCandidates(oldDown, movement)) {
            for (Vec3d probeAnchor : probeAnchors) {
                Box candidateBox = SurfaceGravityCollision.boxAt(entity, candidate, probeAnchor);
                if (!entity.getWorld().isSpaceEmpty(entity, candidateBox.contract(1.0E-7D))) {
                    continue;
                }
                SurfaceSupport support = findSupport(entity, candidate, EDGE_TRANSFER_REACH, candidateBox);
                if (support == null) {
                    continue;
                }
                if (!hasEdgeTransferPath(entity, oldDown, candidate, probeAnchor, support)) {
                    continue;
                }

                SurfaceGravityBasis.LocalView localView = SurfaceGravityBasis.localView(candidate, worldLook);
                state.setLook(localView.yaw(), localView.pitch());
                state.setDownDirection(candidate);
                state.edgeTransferCooldown = EDGE_TRANSFER_COOLDOWN_TICKS;
                state.attached = true;
                SurfaceGravityCollision.setAnchorAndRefreshBox(entity, candidate, probeAnchor);
                snapToSupport(entity, candidate);
                Vec3d nextDown = SurfaceGravityBasis.directionVector(candidate);
                entity.setVelocity(SurfaceGravityBasis.reject(velocity, nextDown));
                return true;
            }
        }
        return false;
    }

    private static List<Vec3d> edgeTransferProbeAnchors(Vec3d anchor, Direction oldDown, Vec3d movement) {
        Vec3d direction = movement.lengthSquared() <= EDGE_TRANSFER_MIN_MOVEMENT ? Vec3d.ZERO : movement.normalize();
        Vec3d lateral = SurfaceGravityBasis.directionVector(oldDown).crossProduct(direction);
        if (lateral.lengthSquared() > EDGE_TRANSFER_MIN_MOVEMENT) {
            lateral = lateral.normalize();
        }
        ArrayList<Vec3d> anchors = new ArrayList<>();
        for (double distance = 0.0D; distance <= EDGE_TRANSFER_FORWARD_REACH + 1.0E-6D; distance += EDGE_TRANSFER_SAMPLE_STEP) {
            Vec3d center = anchor.add(direction.multiply(distance));
            anchors.add(center);
            if (lateral.lengthSquared() > EDGE_TRANSFER_MIN_MOVEMENT) {
                anchors.add(center.add(lateral.multiply(EDGE_TRANSFER_LATERAL_REACH)));
                anchors.add(center.subtract(lateral.multiply(EDGE_TRANSFER_LATERAL_REACH)));
            }
        }
        return anchors;
    }

    private static boolean hasEdgeTransferPath(
            Entity entity,
            Direction oldDown,
            Direction candidateDown,
            Vec3d anchor,
            SurfaceSupport initialSupport
    ) {
        Vec3d path = SurfaceGravityBasis.directionVector(oldDown);
        if (path.lengthSquared() <= EDGE_TRANSFER_MIN_MOVEMENT || initialSupport == null) {
            return false;
        }

        double referenceGap = initialSupport.gap();
        double lastRequiredGap = referenceGap;
        for (int i = 0; i < EDGE_TRANSFER_PATH_REQUIRED_BLOCKS; i++) {
            Vec3d sampleAnchor = anchor.add(path.multiply(i * EDGE_TRANSFER_PATH_STEP));
            Box clearanceBox = edgeTransferClearanceBox(entity, candidateDown, sampleAnchor);
            if (!entity.getWorld().isSpaceEmpty(entity, clearanceBox.contract(1.0E-7D))) {
                return false;
            }
            SurfaceSupport support = findSupport(entity, candidateDown, EDGE_TRANSFER_REACH, clearanceBox);
            if (support == null || Math.abs(support.gap() - referenceGap) > EDGE_TRANSFER_PATH_PLANE_TOLERANCE) {
                return false;
            }
            lastRequiredGap = support.gap();
        }

        Vec3d optionalAnchor = anchor.add(path.multiply(EDGE_TRANSFER_PATH_REQUIRED_BLOCKS * EDGE_TRANSFER_PATH_STEP));
        SurfaceSupport optionalSupport = findSupport(
                entity,
                candidateDown,
                EDGE_TRANSFER_REACH,
                edgeTransferClearanceBox(entity, candidateDown, optionalAnchor)
        );
        return optionalSupport == null
                || Math.abs(optionalSupport.gap() - lastRequiredGap) <= EDGE_TRANSFER_PATH_PLANE_TOLERANCE;
    }

    private static Box edgeTransferClearanceBox(Entity entity, Direction downDirection, Vec3d anchor) {
        double halfWidth = entity.getDimensions(entity.getPose()).width() * 0.5D;
        double height = EDGE_TRANSFER_PATH_CLEARANCE_HEIGHT;
        return switch (downDirection) {
            case DOWN -> new Box(anchor.x - halfWidth, anchor.y, anchor.z - halfWidth,
                    anchor.x + halfWidth, anchor.y + height, anchor.z + halfWidth);
            case UP -> new Box(anchor.x - halfWidth, anchor.y - height, anchor.z - halfWidth,
                    anchor.x + halfWidth, anchor.y, anchor.z + halfWidth);
            case NORTH -> new Box(anchor.x - halfWidth, anchor.y - halfWidth, anchor.z,
                    anchor.x + halfWidth, anchor.y + halfWidth, anchor.z + height);
            case SOUTH -> new Box(anchor.x - halfWidth, anchor.y - halfWidth, anchor.z - height,
                    anchor.x + halfWidth, anchor.y + halfWidth, anchor.z);
            case WEST -> new Box(anchor.x, anchor.y - halfWidth, anchor.z - halfWidth,
                    anchor.x + height, anchor.y + halfWidth, anchor.z + halfWidth);
            case EAST -> new Box(anchor.x - height, anchor.y - halfWidth, anchor.z - halfWidth,
                    anchor.x, anchor.y + halfWidth, anchor.z + halfWidth);
        };
    }

    private static boolean holdEdgeTransferGrace(
            Entity entity,
            PlayerInput input,
            SurfaceState state,
            Direction downDirection,
            Vec3d velocity
    ) {
        if (state.edgeTransferGraceTicks <= 0 || state.detachTicks > 0 || input.sneak() || input.jump()) {
            return false;
        }
        Vec3d down = SurfaceGravityBasis.directionVector(downDirection);
        Vec3d movement = inputMovement(input, downDirection, state.localYaw);
        Vec3d tangentialVelocity = SurfaceGravityBasis.reject(velocity, down);
        if (movement.lengthSquared() <= EDGE_TRANSFER_MIN_MOVEMENT
                && tangentialVelocity.lengthSquared() <= EDGE_TRANSFER_MIN_MOVEMENT) {
            return false;
        }
        Vec3d moveVelocity = tangentialVelocity.add(movement.multiply(AIR_ACCELERATION));
        Vec3d moved = move(entity, moveVelocity, downDirection);
        entity.setVelocity(applyDrag(SurfaceGravityBasis.reject(moved, down), down, AIR_TANGENT_DRAG));
        state.attached = false;
        setSurfaceGrounded(entity, false);
        return true;
    }

    private static List<Direction> edgeTransferCandidates(Direction oldDown, Vec3d movement) {
        Vec3d preferred = movement.normalize().multiply(-1.0D);
        ArrayList<Direction> candidates = new ArrayList<>(4);
        for (Direction direction : Direction.values()) {
            if (isPerpendicular(oldDown, direction)
                    && SurfaceGravityBasis.directionVector(direction).dotProduct(preferred) >= EDGE_TRANSFER_OUTER_DOT_MIN) {
                candidates.add(direction);
            }
        }
        candidates.sort((a, b) -> Double.compare(
                SurfaceGravityBasis.directionVector(b).dotProduct(preferred),
                SurfaceGravityBasis.directionVector(a).dotProduct(preferred)
        ));
        return candidates;
    }

    private static boolean isPerpendicular(Direction a, Direction b) {
        return a != null && b != null && a.getAxis() != b.getAxis();
    }

    public static BlockPos getSurfaceLandingPos(Entity entity, Direction downDirection, float yOffset) {
        if (entity == null || downDirection == null) {
            return null;
        }
        Vec3d anchor = SurfaceGravityCollision.anchorFromBox(downDirection, entity.getBoundingBox());
        Vec3d down = SurfaceGravityBasis.directionVector(downDirection);
        return BlockPos.ofFloored(anchor.add(down.multiply(yOffset)));
    }

    private static void updateCollisionFlags(Entity entity, Vec3d requested, Vec3d adjusted, Direction downDirection) {
        Vec3d down = SurfaceGravityBasis.directionVector(downDirection);
        Vec3d requestedTangent = SurfaceGravityBasis.reject(requested, down);
        Vec3d adjustedTangent = SurfaceGravityBasis.reject(adjusted, down);
        double requestedDown = requested.dotProduct(down);
        double adjustedDown = adjusted.dotProduct(down);
        boolean tangentBlocked = requestedTangent.lengthSquared() > adjustedTangent.lengthSquared() + 1.0E-7D;
        boolean normalBlocked = Math.abs(requestedDown - adjustedDown) > 1.0E-7D;
        boolean hitSurface = normalBlocked && requestedDown > adjustedDown;

        entity.horizontalCollision = tangentBlocked;
        entity.verticalCollision = normalBlocked;
        entity.groundCollision = hitSurface;
        ((SurfaceGravityEntityAccessor) entity).monvhua$setOnGround(hitSurface);
    }

    private static void setSurfaceGrounded(Entity entity, boolean grounded) {
        entity.groundCollision = grounded;
        entity.verticalCollision = grounded || entity.verticalCollision;
        ((SurfaceGravityEntityAccessor) entity).monvhua$setOnGround(grounded);
    }

    private static void updateBodyYaw(SurfaceState state) {
        // Velocity-based vanilla body tracking turns wall strafing into an
        // unwanted world-up/world-down tilt. Track the local view instead.
        state.updateBodyYaw(state.localYaw);
    }

    private static void snapToSupport(Entity entity, Direction downDirection) {
        SurfaceSupport support = findSupport(entity, downDirection, SUPPORT_PROBE);
        if (support == null) {
            return;
        }
        double correction = support.gap() - SUPPORT_TARGET_GAP;
        if (Math.abs(correction) <= SUPPORT_CORRECTION_DEADBAND) {
            return;
        }
        Vec3d down = SurfaceGravityBasis.directionVector(downDirection);
        entity.setBoundingBox(entity.getBoundingBox().offset(down.multiply(correction)));
        SurfaceGravityCollision.syncPositionToBox(entity, downDirection);
    }

    public static SurfaceSupport findSupport(Entity entity, Direction downDirection, double reach) {
        return findSupport(entity, downDirection, reach, entity.getBoundingBox());
    }

    private static SurfaceSupport findSupport(Entity entity, Direction downDirection, double reach, Box box) {
        Vec3d down = SurfaceGravityBasis.directionVector(downDirection);
        Box probe = box.offset(down.multiply(reach + SUPPORT_EPSILON));
        Box swept = box.union(probe).expand(0.01D);
        SurfaceSupport closest = null;
        double closestGap = Double.MAX_VALUE;

        int minX = (int) Math.floor(swept.minX) - 1;
        int minY = (int) Math.floor(swept.minY) - 1;
        int minZ = (int) Math.floor(swept.minZ) - 1;
        int maxX = (int) Math.floor(swept.maxX) + 1;
        int maxY = (int) Math.floor(swept.maxY) + 1;
        int maxZ = (int) Math.floor(swept.maxZ) + 1;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = entity.getWorld().getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    VoxelShape shape = state.getCollisionShape(entity.getWorld(), pos, ShapeContext.of(entity));
                    if (shape.isEmpty()) {
                        continue;
                    }
                    for (Box localBounds : shape.getBoundingBoxes()) {
                        Box bounds = localBounds.offset(pos);
                        Double gap = surfaceGap(box, bounds, downDirection);
                        if (gap == null || gap < -SUPPORT_EPSILON || gap > reach + SUPPORT_EPSILON) {
                            continue;
                        }
                        if (gap < closestGap) {
                            closest = new SurfaceSupport(gap);
                            closestGap = gap;
                        }
                    }
                }
            }
        }
        return closest;
    }

    private static boolean isClimbable(Entity entity, Direction downDirection) {
        Box box = entity.getBoundingBox().expand(0.08D);
        int minX = (int) Math.floor(box.minX);
        int minY = (int) Math.floor(box.minY);
        int minZ = (int) Math.floor(box.minZ);
        int maxX = (int) Math.floor(box.maxX);
        int maxY = (int) Math.floor(box.maxY);
        int maxZ = (int) Math.floor(box.maxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockState state = entity.getWorld().getBlockState(new BlockPos(x, y, z));
                    if (state.isIn(BlockTags.CLIMBABLE) || state.getBlock() instanceof TrapdoorBlock) {
                        return true;
                    }
                }
            }
        }
        return findSupport(entity, downDirection.getOpposite(), 0.16D) != null;
    }

    private static Double surfaceGap(Box entityBox, Box blockBox, Direction downDirection) {
        if (!overlapsOnSurfaceAxes(entityBox, blockBox, downDirection)) {
            return null;
        }
        return switch (downDirection) {
            case DOWN -> entityBox.minY - blockBox.maxY;
            case UP -> blockBox.minY - entityBox.maxY;
            case NORTH -> entityBox.minZ - blockBox.maxZ;
            case SOUTH -> blockBox.minZ - entityBox.maxZ;
            case WEST -> entityBox.minX - blockBox.maxX;
            case EAST -> blockBox.minX - entityBox.maxX;
        };
    }

    private static boolean overlapsOnSurfaceAxes(Box a, Box b, Direction downDirection) {
        return switch (downDirection.getAxis()) {
            case X -> rangesOverlap(a.minY, a.maxY, b.minY, b.maxY) && rangesOverlap(a.minZ, a.maxZ, b.minZ, b.maxZ);
            case Y -> rangesOverlap(a.minX, a.maxX, b.minX, b.maxX) && rangesOverlap(a.minZ, a.maxZ, b.minZ, b.maxZ);
            case Z -> rangesOverlap(a.minX, a.maxX, b.minX, b.maxX) && rangesOverlap(a.minY, a.maxY, b.minY, b.maxY);
        };
    }

    private static boolean rangesOverlap(double minA, double maxA, double minB, double maxB) {
        return maxA > minB + 1.0E-4D && maxB > minA + 1.0E-4D;
    }

    public static final class SurfaceState {
        private Direction downDirection;
        private float localYaw;
        private float localPitch;
        private float localBodyYaw;
        private float lastLocalBodyYaw;
        private float crouchProgress;
        private float lastCrouchProgress;
        private int lastCrouchUpdateAge = Integer.MIN_VALUE;
        private int detachTicks;
        private int edgeTransferCooldown;
        private int edgeTransferGraceTicks;
        private boolean attached;

        public SurfaceState(Direction downDirection, float localYaw, float localPitch) {
            this.downDirection = downDirection;
            this.localYaw = localYaw;
            this.localPitch = localPitch;
            this.localBodyYaw = localYaw;
            this.lastLocalBodyYaw = localYaw;
        }

        public Direction downDirection() {
            return downDirection;
        }

        public void setDownDirection(Direction downDirection) {
            Direction next = downDirection == null ? Direction.DOWN : downDirection;
            if (this.downDirection != next) {
                this.downDirection = next;
                resetBodyYaw();
            } else {
                this.downDirection = next;
            }
        }

        public float localYaw() {
            return localYaw;
        }

        public float localPitch() {
            return localPitch;
        }

        public float localBodyYaw() {
            return localBodyYaw;
        }

        public float lastLocalBodyYaw() {
            return lastLocalBodyYaw;
        }

        public void setLook(float localYaw, float localPitch) {
            this.localYaw = localYaw;
            this.localPitch = Math.clamp(localPitch, -89.0F, 89.0F);
        }

        public void updateCrouch(boolean crouching, int age) {
            if (this.lastCrouchUpdateAge == age) {
                return;
            }
            int ticks = this.lastCrouchUpdateAge == Integer.MIN_VALUE
                    ? 1
                    : Math.clamp(age - this.lastCrouchUpdateAge, 1, 4);
            this.lastCrouchUpdateAge = age;
            for (int i = 0; i < ticks; i++) {
                this.lastCrouchProgress = this.crouchProgress;
                float target = crouching ? 1.0F : 0.0F;
                this.crouchProgress += (target - this.crouchProgress) * 0.5F;
                if (Math.abs(target - this.crouchProgress) < 0.01F) {
                    this.crouchProgress = target;
                }
            }
        }

        public float crouchProgress(float tickProgress) {
            return Math.clamp(MathHelper.lerp(tickProgress, this.lastCrouchProgress, this.crouchProgress), 0.0F, 1.0F);
        }

        private void resetBodyYaw() {
            this.localBodyYaw = this.localYaw;
            this.lastLocalBodyYaw = this.localYaw;
        }

        public void snapBodyYawToLook() {
            resetBodyYaw();
        }

        private void updateBodyYaw(float targetYaw) {
            this.lastLocalBodyYaw = this.localBodyYaw;
            this.localBodyYaw += MathHelper.wrapDegrees(targetYaw - this.localBodyYaw) * 0.3F;
            float headDelta = MathHelper.wrapDegrees(this.localYaw - this.localBodyYaw);
            float maxHeadDelta = 50.0F;
            if (Math.abs(headDelta) > maxHeadDelta) {
                this.localBodyYaw += headDelta - Math.signum(headDelta) * maxHeadDelta;
            }
        }
    }

    public record SurfaceSupport(double gap) {
    }
}
