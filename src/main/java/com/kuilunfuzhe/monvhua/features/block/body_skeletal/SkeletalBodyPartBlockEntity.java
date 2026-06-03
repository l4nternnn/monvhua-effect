package com.kuilunfuzhe.monvhua.features.block.body_skeletal;

import com.kuilunfuzhe.monvhua.features.block.body.BodyPartBlockEntity;
import com.mojang.serialization.Codec;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public class SkeletalBodyPartBlockEntity extends BodyPartBlockEntity implements GeoBlockEntity {
    private final SkeletalBodyPart part;
    private final AnimatableInstanceCache animatableCache = GeckoLibUtil.createInstanceCache(this);
    private float jointPitch;
    private float jointYaw;
    private float jointRoll;

    public SkeletalBodyPartBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, SkeletalBodyPart part) {
        super(type, pos, state);
        this.part = part;
    }

    public SkeletalBodyPart getPart() {
        return part;
    }

    public String getBoneId() {
        return part.id();
    }

    @Nullable
    public String getParentBoneId() {
        return part.parentId();
    }

    public float getJointPitch() {
        return jointPitch;
    }

    public float getJointYaw() {
        return jointYaw;
    }

    public float getJointRoll() {
        return jointRoll;
    }

    public void setJointPose(float pitch, float yaw, float roll) {
        this.jointPitch = pitch;
        this.jointYaw = yaw;
        this.jointRoll = roll;
        this.markDirty();
        if (this.world instanceof ServerWorld serverWorld) {
            serverWorld.getChunkManager().markForUpdate(this.pos);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animatableCache;
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }

    @Override
    protected void readData(ReadView view) {
        super.readData(view);
        this.jointPitch = view.read("joint_pitch", Codec.FLOAT).orElse(0.0F);
        this.jointYaw = view.read("joint_yaw", Codec.FLOAT).orElse(0.0F);
        this.jointRoll = view.read("joint_roll", Codec.FLOAT).orElse(0.0F);
    }

    @Override
    protected void writeData(WriteView view) {
        super.writeData(view);
        view.put("part", Codec.STRING, part.id());
        view.put("parent_part", Codec.STRING, part.parentId() == null ? "" : part.parentId());
        view.put("joint_pitch", Codec.FLOAT, jointPitch);
        view.put("joint_yaw", Codec.FLOAT, jointYaw);
        view.put("joint_roll", Codec.FLOAT, jointRoll);
    }
}
