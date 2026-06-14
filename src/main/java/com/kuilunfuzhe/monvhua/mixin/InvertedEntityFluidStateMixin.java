package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.gravity.GravityMagic;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class InvertedEntityFluidStateMixin {
    @Shadow
    protected Object2DoubleMap<TagKey<Fluid>> fluidHeight;

    @Shadow
    public abstract World getWorld();

    @Shadow
    public abstract Box getBoundingBox();

    @Shadow
    public abstract boolean isRegionUnloaded();

    @Shadow
    public abstract boolean isPushedByFluids();

    @Shadow
    public abstract Vec3d getVelocity();

    @Shadow
    public abstract void setVelocity(Vec3d velocity);

    @Shadow
    public abstract boolean isSwimming();

    @Shadow
    public abstract boolean isSprinting();

    @Shadow
    public abstract boolean hasVehicle();

    @Shadow
    public abstract void setSwimming(boolean swimming);

    @Inject(method = "updateMovementInFluid", at = @At("HEAD"), cancellable = true)
    private void monvhua$updateInvertedMovementInFluid(TagKey<Fluid> tag, double speed, CallbackInfoReturnable<Boolean> cir) {
        Entity entity = (Entity) (Object) this;
        if (!GravityMagic.isInInvertedArea(entity)) {
            return;
        }

        if (this.isRegionUnloaded()) {
            cir.setReturnValue(false);
            return;
        }

        Box box = this.getBoundingBox().contract(0.001D);
        int minX = MathHelper.floor(box.minX);
        int maxX = MathHelper.ceil(box.maxX);
        int minY = MathHelper.floor(box.minY);
        int maxY = MathHelper.ceil(box.maxY);
        int minZ = MathHelper.floor(box.minZ);
        int maxZ = MathHelper.ceil(box.maxZ);

        double fluidDepth = 0.0D;
        boolean touching = false;
        boolean pushedByFluids = this.isPushedByFluids();
        Vec3d flow = Vec3d.ZERO;
        int flowSamples = 0;
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    mutable.set(x, y, z);
                    FluidState state = this.getWorld().getFluidState(mutable);
                    if (!state.isIn(tag)) {
                        continue;
                    }

                    double height = Math.clamp(state.getHeight(this.getWorld(), mutable), 0.0F, 1.0F);
                    double fluidTop = y + 1.0D;
                    double fluidBottom = fluidTop - height;
                    if (fluidTop < box.minY || fluidBottom > box.maxY) {
                        continue;
                    }

                    touching = true;
                    fluidDepth = Math.max(fluidDepth, box.maxY - fluidBottom);
                    if (!pushedByFluids) {
                        continue;
                    }

                    Vec3d velocity = state.getVelocity(this.getWorld(), mutable);
                    if (fluidDepth < 0.4D) {
                        velocity = velocity.multiply(fluidDepth);
                    }
                    flow = flow.add(velocity);
                    flowSamples++;
                }
            }
        }

        if (flow.length() > 0.0D) {
            if (flowSamples > 0) {
                flow = flow.multiply(1.0D / flowSamples);
            }
            if (!(((Object) this) instanceof net.minecraft.entity.player.PlayerEntity)) {
                flow = flow.normalize();
            }

            Vec3d current = this.getVelocity();
            flow = flow.multiply(speed);
            if (Math.abs(current.x) < 0.003D
                    && Math.abs(current.z) < 0.003D
                    && flow.length() < 0.0045000000000000005D) {
                flow = flow.normalize().multiply(0.0045000000000000005D);
            }
            this.setVelocity(this.getVelocity().add(flow));
        }

        this.fluidHeight.put(tag, fluidDepth);
        cir.setReturnValue(touching);
    }

    @Inject(method = "updateSwimming", at = @At("HEAD"), cancellable = true)
    private void monvhua$updateInvertedSwimming(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (!GravityMagic.isInInvertedArea(entity)) {
            return;
        }

        this.setSwimming(this.isSprinting() && monvhua$isInvertedSubmergedInWater(boxForSwimming(entity)) && !this.hasVehicle());
        ci.cancel();
    }

    private boolean monvhua$isInvertedSubmergedInWater(Box box) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        int minX = MathHelper.floor(box.minX + 0.001D);
        int maxX = MathHelper.ceil(box.maxX - 0.001D);
        int minY = MathHelper.floor(box.minY + 0.001D);
        int maxY = MathHelper.ceil(box.maxY - 0.001D);
        int minZ = MathHelper.floor(box.minZ + 0.001D);
        int maxZ = MathHelper.ceil(box.maxZ - 0.001D);

        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    mutable.set(x, y, z);
                    FluidState state = this.getWorld().getFluidState(mutable);
                    if (!state.isIn(FluidTags.WATER)) {
                        continue;
                    }

                    double height = Math.clamp(state.getHeight(this.getWorld(), mutable), 0.0F, 1.0F);
                    double fluidTop = y + 1.0D;
                    double fluidBottom = fluidTop - height;
                    if (fluidTop >= box.maxY && fluidBottom <= box.minY) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static Box boxForSwimming(Entity entity) {
        Box box = entity.getBoundingBox();
        double insetX = Math.min(0.05D, Math.max(0.0D, (box.maxX - box.minX) * 0.25D));
        double insetZ = Math.min(0.05D, Math.max(0.0D, (box.maxZ - box.minZ) * 0.25D));
        return new Box(
                box.minX + insetX,
                box.maxY - 0.4D,
                box.minZ + insetZ,
                box.maxX - insetX,
                box.maxY,
                box.maxZ - insetZ
        );
    }
}
