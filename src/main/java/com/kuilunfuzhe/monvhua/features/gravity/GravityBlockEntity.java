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
    private static final TrackedData<Float> GRAVITY = DataTracker.registerData(GravityBlockEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final int MAX_AGE = 600;
    private static final double SETTLE_SPEED = 0.08D;

    public float renderPitch;
    public float prevRenderPitch;
    public float renderRoll;
    public float prevRenderRoll;
    private float pitchVelocity;
    private float rollVelocity;
    private int age;

    public GravityBlockEntity(EntityType<? extends GravityBlockEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
    }

    public GravityBlockEntity(World world, double x, double y, double z, BlockState state, Vec3d velocity, double gravity) {
        this(ModEntities.GRAVITY_BLOCK, world);
        this.setPosition(x, y, z);
        this.setBlockState(state);
        this.setVelocity(velocity);
        this.setGravityAmount(gravity);
        this.pitchVelocity = (float) (world.random.nextDouble() * 10.0D + 4.0D);
        this.rollVelocity = (float) (world.random.nextDouble() * 10.0D + 4.0D);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(BLOCK_STATE_ID, Block.getRawIdFromState(Blocks.STONE.getDefaultState()));
        builder.add(GRAVITY, 0.04F);
    }

    public BlockState getBlockState() {
        BlockState state = Block.getStateFromRawId(this.dataTracker.get(BLOCK_STATE_ID));
        return state == null ? Blocks.STONE.getDefaultState() : state;
    }

    public void setBlockState(BlockState state) {
        this.dataTracker.set(BLOCK_STATE_ID, Block.getRawIdFromState(state));
    }

    public double getGravityAmount() {
        return this.dataTracker.get(GRAVITY);
    }

    public void setGravityAmount(double gravity) {
        this.dataTracker.set(GRAVITY, (float) Math.clamp(gravity, 0.0D, 0.30D));
    }

    @Override
    public void tick() {
        super.tick();
        this.age++;

        this.prevRenderPitch = this.renderPitch;
        this.prevRenderRoll = this.renderRoll;
        this.renderPitch += this.pitchVelocity;
        this.renderRoll += this.rollVelocity;

        Vec3d velocity = this.getVelocity().add(0.0D, -getGravityAmount(), 0.0D);
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

        if (!this.getWorld().isClient && (this.age > MAX_AGE || (this.isOnGround() && velocity.length() < SETTLE_SPEED))) {
            settleOrDrop();
        }
    }

    private void settleOrDrop() {
        if (!(this.getWorld() instanceof ServerWorld world)) {
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
        Block.dropStacks(getBlockState(), world, this.getBlockPos());
        this.discard();
        return true;
    }

    @Override
    protected void readCustomData(ReadView view) {
        this.dataTracker.set(BLOCK_STATE_ID, view.read("BlockStateId", Codec.INT).orElse(Block.getRawIdFromState(Blocks.STONE.getDefaultState())));
        this.setGravityAmount(view.read("Gravity", Codec.DOUBLE).orElse(0.04D));
        this.age = view.read("Age", Codec.INT).orElse(0);
    }

    @Override
    protected void writeCustomData(WriteView view) {
        view.put("BlockStateId", Codec.INT, this.dataTracker.get(BLOCK_STATE_ID));
        view.put("Gravity", Codec.DOUBLE, getGravityAmount());
        view.put("Age", Codec.INT, this.age);
    }
}
