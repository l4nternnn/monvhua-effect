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

public class GravityBlockEntity extends Entity {
    private static final TrackedData<Integer> BLOCK_STATE_ID = DataTracker.registerData(GravityBlockEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> GRAVITY_Y = DataTracker.registerData(GravityBlockEntity.class, TrackedDataHandlerRegistry.FLOAT);
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
    }
}
