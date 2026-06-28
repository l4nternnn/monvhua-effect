package com.kuilunfuzhe.monvhua.features.gravity;

import com.kuilunfuzhe.monvhua.entity.ModEntities;
import com.mojang.serialization.Codec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.Optional;
import java.util.UUID;

public class GravityBlockEntity extends Entity {
    private static final TrackedData<Integer> BLOCK_STATE_ID = DataTracker.registerData(GravityBlockEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> GRAVITY_Y = DataTracker.registerData(GravityBlockEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Vector3f> RENDER_GROUP_CENTER = DataTracker.registerData(GravityBlockEntity.class, TrackedDataHandlerRegistry.VECTOR_3F);
    private static final TrackedData<Float> RENDER_GROUP_RADIUS = DataTracker.registerData(GravityBlockEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> RENDER_GROUP_OWNER_ID = DataTracker.registerData(GravityBlockEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final int MAX_AGE = 600;
    private static final double SETTLE_SPEED = 0.08D;

    public float renderPitch;
    public float prevRenderPitch;
    public float renderYaw;
    public float prevRenderYaw;
    public float renderRoll;
    public float prevRenderRoll;
    private float pitchVelocity;
    private float yawVelocity;
    private float rollVelocity;
    private int age;
    private int maxAgeTicks = MAX_AGE;
    private double riseOriginY;
    private double maxRiseDistance;
    private boolean temporary;
    private boolean placeOrDropOnSettle = true;
    private UUID ownerUuid;
    private Vec3d extractStart;
    private Vec3d extractTarget;
    private int extractDelay;
    private int extractAge;
    private int extractTicks;

    public GravityBlockEntity(EntityType<? extends GravityBlockEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
        randomizeSpin(0.25F, 1.15F);
    }

    public GravityBlockEntity(World world, double x, double y, double z, BlockState state, Vec3d velocity, double gravity) {
        this(ModEntities.GRAVITY_BLOCK, world);
        this.setPosition(x, y, z);
        this.riseOriginY = y;
        this.setBlockState(state);
        this.setVelocity(velocity);
        this.setGravityAmount(gravity);
        randomizeSpin(4.0F, 14.0F);
    }

    private void randomizeSpin(float minDegreesPerTick, float maxDegreesPerTick) {
        float range = Math.max(0.0F, maxDegreesPerTick - minDegreesPerTick);
        this.pitchVelocity = signedSpin(minDegreesPerTick, range);
        this.yawVelocity = signedSpin(minDegreesPerTick, range);
        this.rollVelocity = signedSpin(minDegreesPerTick, range);
    }

    private float signedSpin(float minDegreesPerTick, float range) {
        float speed = minDegreesPerTick + this.getWorld().random.nextFloat() * range;
        return this.getWorld().random.nextBoolean() ? speed : -speed;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(BLOCK_STATE_ID, Block.getRawIdFromState(Blocks.STONE.getDefaultState()));
        builder.add(GRAVITY_Y, (float) -GravityMagic.WORLD_GRAVITY);
        builder.add(RENDER_GROUP_CENTER, new Vector3f());
        builder.add(RENDER_GROUP_RADIUS, 0.0F);
        builder.add(RENDER_GROUP_OWNER_ID, -1);
    }

    public BlockState getBlockState() {
        BlockState state = Block.getStateFromRawId(this.dataTracker.get(BLOCK_STATE_ID));
        return state == null ? Blocks.STONE.getDefaultState() : state;
    }

    public void setBlockState(BlockState state) {
        this.dataTracker.set(BLOCK_STATE_ID, Block.getRawIdFromState(state));
    }

    public double getGravityAmount() {
        return Math.abs(this.dataTracker.get(GRAVITY_Y));
    }

    public void setGravityAmount(double gravity) {
        setGravityY(-gravity);
    }

    public double getGravityY() {
        return this.dataTracker.get(GRAVITY_Y);
    }

    public void setGravityY(double gravityY) {
        double sign = gravityY < 0.0D ? -1.0D : 1.0D;
        double amount = Math.clamp(Math.abs(gravityY), 0.0D, 0.30D);
        this.dataTracker.set(GRAVITY_Y, (float) (sign * amount));
    }

    public void setRiseLimit(double maxRiseDistance) {
        this.riseOriginY = this.getY();
        this.maxRiseDistance = Math.max(0.0D, maxRiseDistance);
    }

    public void setTemporary(int maxAgeTicks) {
        this.temporary = true;
        this.placeOrDropOnSettle = false;
        this.maxAgeTicks = Math.max(1, maxAgeTicks);
    }

    public boolean isTemporary() {
        return temporary;
    }

    public void setPlaceOrDropOnSettle(boolean placeOrDropOnSettle) {
        this.placeOrDropOnSettle = placeOrDropOnSettle;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setRenderGroup(Vec3d center, double radius) {
        if (center == null || radius <= 0.0D) {
            clearRenderGroup();
            return;
        }
        this.dataTracker.set(RENDER_GROUP_CENTER, new Vector3f((float) center.x, (float) center.y, (float) center.z));
        this.dataTracker.set(RENDER_GROUP_RADIUS, (float) Math.min(radius, 1024.0D));
    }

    public void setRenderGroupOwnerId(int ownerId) {
        this.dataTracker.set(RENDER_GROUP_OWNER_ID, ownerId);
    }

    public void clearRenderGroup() {
        this.dataTracker.set(RENDER_GROUP_CENTER, new Vector3f());
        this.dataTracker.set(RENDER_GROUP_RADIUS, 0.0F);
        this.dataTracker.set(RENDER_GROUP_OWNER_ID, -1);
    }

    public boolean hasRenderGroup() {
        return this.dataTracker.get(RENDER_GROUP_RADIUS) > 0.0F;
    }

    public Vec3d getRenderGroupCenter() {
        Vector3f center = this.dataTracker.get(RENDER_GROUP_CENTER);
        return new Vec3d(center.x(), center.y(), center.z());
    }

    public double getRenderGroupRadius() {
        return this.dataTracker.get(RENDER_GROUP_RADIUS);
    }

    public int getRenderGroupOwnerId() {
        return this.dataTracker.get(RENDER_GROUP_OWNER_ID);
    }

    public void setExtractionTarget(Vec3d target, int ticks) {
        setExtractionTarget(target, 0, ticks);
    }

    public void setExtractionTarget(Vec3d target, int delayTicks, int ticks) {
        this.extractStart = this.getPos();
        this.extractTarget = target;
        this.extractDelay = Math.max(0, delayTicks);
        this.extractTicks = Math.max(1, ticks);
        this.extractAge = 0;
    }

    public boolean isExtracting() {
        return extractTarget != null && extractAge < extractDelay + extractTicks;
    }

    public void setMaxAgeTicks(int maxAgeTicks) {
        this.maxAgeTicks = Math.max(1, maxAgeTicks);
    }

    public void setSlowFreeSpin(float pitchVelocity, float yawVelocity, float rollVelocity) {
        this.pitchVelocity = pitchVelocity;
        this.yawVelocity = yawVelocity;
        this.rollVelocity = rollVelocity;
    }

    @Override
    public void tick() {
        super.tick();
        this.age++;

        this.prevRenderPitch = this.renderPitch;
        this.prevRenderYaw = this.renderYaw;
        this.prevRenderRoll = this.renderRoll;
        this.renderPitch += this.pitchVelocity;
        this.renderYaw += this.yawVelocity;
        this.renderRoll += this.rollVelocity;

        if (isExtracting()) {
            tickExtraction();
            return;
        }

        Vec3d velocity = this.getVelocity().add(0.0D, getGravityY(), 0.0D);
        this.setVelocity(velocity);
        this.move(MovementType.SELF, velocity);

        velocity = this.getVelocity();
        if (this.horizontalCollision) {
            velocity = new Vec3d(-velocity.x * 0.45D, velocity.y, -velocity.z * 0.45D);
            this.rollVelocity *= -0.65F;
        }
        if (this.verticalCollision) {
            velocity = new Vec3d(velocity.x * 0.82D, -velocity.y * 0.38D, velocity.z * 0.82D);
            this.pitchVelocity *= 0.55F;
            this.rollVelocity *= 0.55F;
        }

        velocity = velocity.multiply(0.985D);
        if (this.isOnGround()) {
            velocity = new Vec3d(velocity.x * 0.72D, velocity.y, velocity.z * 0.72D);
        }
        this.setVelocity(velocity);

        if (!this.getWorld().isClient && this.maxRiseDistance > 0.0D && getGravityY() > 0.0D && this.getY() >= this.riseOriginY + this.maxRiseDistance) {
            settleOrDrop();
            return;
        }

        if (!this.getWorld().isClient && (this.age > this.maxAgeTicks || ((this.isOnGround() || this.verticalCollision) && velocity.length() < SETTLE_SPEED))) {
            settleOrDrop();
        }
    }

    private void tickExtraction() {
        this.extractAge++;
        if (this.extractAge <= this.extractDelay) {
            this.setVelocity(Vec3d.ZERO);
            return;
        }

        Vec3d start = this.extractStart == null ? this.getPos() : this.extractStart;
        int flyAge = this.extractAge - this.extractDelay;
        double t = Math.clamp(flyAge / (double) this.extractTicks, 0.0D, 1.0D);
        double eased = 1.0D - Math.pow(1.0D - t, 3.0D);
        Vec3d current = this.getPos();
        Vec3d control = extractionBezierControl(start, extractTarget);
        Vec3d next = quadraticBezier(start, control, extractTarget, eased);
        Vec3d delta = next.subtract(current);
        this.setVelocity(delta);
        this.move(MovementType.SELF, delta);
        if (flyAge >= this.extractTicks || this.getPos().squaredDistanceTo(extractTarget) < 0.04D) {
            this.setPosition(extractTarget.x, extractTarget.y, extractTarget.z);
            this.setVelocity(Vec3d.ZERO);
            this.extractStart = null;
            this.extractTarget = null;
            this.extractDelay = 0;
        }
    }

    private static Vec3d extractionBezierControl(Vec3d start, Vec3d target) {
        double dx = target.x - start.x;
        double dz = target.z - start.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double heightOffset = 0.4D * horizontalDistance;
        double tangentOffset = 0.3D * heightOffset;
        Vec3d tangent = horizontalDistance < 1.0E-4D
                ? Vec3d.ZERO
                : new Vec3d(-dz / horizontalDistance, 0.0D, dx / horizontalDistance);
        return start.add(target).multiply(0.5D)
                .add(0.0D, heightOffset, 0.0D)
                .add(tangent.multiply(tangentOffset));
    }

    private static Vec3d quadraticBezier(Vec3d p0, Vec3d p1, Vec3d p2, double t) {
        double inv = 1.0D - t;
        return p0.multiply(inv * inv)
                .add(p1.multiply(2.0D * inv * t))
                .add(p2.multiply(t * t));
    }

    private void settleOrDrop() {
        if (!(this.getWorld() instanceof ServerWorld world)) {
            this.discard();
            return;
        }

        if (!placeOrDropOnSettle) {
            this.discard();
            return;
        }

        BlockPos pos = this.getBlockPos();
        BlockState state = getBlockState();
        if (world.getBlockState(pos).isReplaceable()) {
            world.setBlockState(pos, state, Block.NOTIFY_ALL);
        } else {
            Block.dropStacks(state, world, pos);
        }
        this.discard();
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        if (!temporary) {
            Block.dropStacks(getBlockState(), world, this.getBlockPos());
        }
        this.discard();
        return true;
    }

    @Override
    protected void readCustomData(ReadView view) {
        this.dataTracker.set(BLOCK_STATE_ID, view.read("BlockStateId", Codec.INT).orElse(Block.getRawIdFromState(Blocks.STONE.getDefaultState())));
        double gravityY = view.read("GravityY", Codec.DOUBLE).orElse(-view.read("Gravity", Codec.DOUBLE).orElse(GravityMagic.WORLD_GRAVITY));
        this.setGravityY(gravityY);
        this.age = view.read("Age", Codec.INT).orElse(0);
        this.maxAgeTicks = view.read("MaxAgeTicks", Codec.INT).orElse(MAX_AGE);
        this.riseOriginY = view.read("RiseOriginY", Codec.DOUBLE).orElse(this.getY());
        this.maxRiseDistance = view.read("MaxRiseDistance", Codec.DOUBLE).orElse(0.0D);
        this.temporary = view.read("Temporary", Codec.BOOL).orElse(false);
        this.placeOrDropOnSettle = view.read("PlaceOrDropOnSettle", Codec.BOOL).orElse(true);
        double groupRadius = view.read("RenderGroupRadius", Codec.DOUBLE).orElse(0.0D);
        if (groupRadius > 0.0D) {
            setRenderGroup(new Vec3d(
                    view.read("RenderGroupX", Codec.DOUBLE).orElse(0.0D),
                    view.read("RenderGroupY", Codec.DOUBLE).orElse(0.0D),
                    view.read("RenderGroupZ", Codec.DOUBLE).orElse(0.0D)
            ), groupRadius);
        } else {
            clearRenderGroup();
        }
        Optional<String> owner = view.read("OwnerUuid", Codec.STRING);
        owner.ifPresent(value -> {
            try {
                this.ownerUuid = UUID.fromString(value);
            } catch (IllegalArgumentException ignored) {
                this.ownerUuid = null;
            }
        });
    }

    @Override
    protected void writeCustomData(WriteView view) {
        view.put("BlockStateId", Codec.INT, this.dataTracker.get(BLOCK_STATE_ID));
        view.put("GravityY", Codec.DOUBLE, getGravityY());
        view.put("Age", Codec.INT, this.age);
        view.put("MaxAgeTicks", Codec.INT, this.maxAgeTicks);
        view.put("RiseOriginY", Codec.DOUBLE, this.riseOriginY);
        view.put("MaxRiseDistance", Codec.DOUBLE, this.maxRiseDistance);
        view.put("Temporary", Codec.BOOL, this.temporary);
        view.put("PlaceOrDropOnSettle", Codec.BOOL, this.placeOrDropOnSettle);
        if (hasRenderGroup()) {
            Vec3d center = getRenderGroupCenter();
            view.put("RenderGroupX", Codec.DOUBLE, center.x);
            view.put("RenderGroupY", Codec.DOUBLE, center.y);
            view.put("RenderGroupZ", Codec.DOUBLE, center.z);
            view.put("RenderGroupRadius", Codec.DOUBLE, getRenderGroupRadius());
        }
        if (ownerUuid != null) {
            view.put("OwnerUuid", Codec.STRING, ownerUuid.toString());
        }
    }
}
