package com.kuilunfuzhe.monvhua.mixin.forensics;

import com.kuilunfuzhe.monvhua.features.forensics.*;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ProjectileEntity.class)
public abstract class ForensicProjectileMixin implements ProjectileEvidence {
    @Unique private NbtCompound monvhua$evidence = new NbtCompound();
    @Unique private net.minecraft.util.math.Vec3d monvhua$collisionVelocity;
    @Inject(method = "onCollision", at = @At("HEAD"))
    private void monvhua$beforeCollision(net.minecraft.util.hit.HitResult hit, CallbackInfo ci) {
        monvhua$collisionVelocity = ((ProjectileEntity) (Object) this).getVelocity();
    }
    @Inject(method = "onCollision", at = @At("RETURN"))
    private void monvhua$afterCollision(net.minecraft.util.hit.HitResult hit, CallbackInfo ci) { monvhua$collisionVelocity = null; }
    @Override public net.minecraft.util.math.Vec3d monvhua$impactVelocity() {
        return monvhua$collisionVelocity == null ? ((ProjectileEntity) (Object) this).getVelocity() : monvhua$collisionVelocity;
    }
    @Inject(method = "tick", at = @At("HEAD"))
    private void monvhua$capture(CallbackInfo ci) { monvhua$getEvidence(); }
    @Override public NbtCompound monvhua$getEvidence() {
        if (!monvhua$evidence.isEmpty()) return monvhua$evidence;
        ProjectileEntity self = (ProjectileEntity) (Object) this;
        if (self.getWorld().isClient) return monvhua$evidence;
        monvhua$evidence.putString("attack_id", self.getUuid().toString());
        if (self instanceof PersistentProjectileEntity arrow && arrow.getWeaponStack() != null) {
            var stack = arrow.getWeaponStack();
            monvhua$evidence.putString("weapon", Registries.ITEM.getId(stack.getItem()).toString());
            var selected = WeaponProfiles.select(stack, "projectile");
            if (selected != null) {
                monvhua$evidence.putString("profile", selected.profile()); monvhua$evidence.putString("mode", selected.mode());
                NbtList list = new NbtList();
                for (var component : selected.definition().components()) {
                    NbtCompound c = new NbtCompound(); c.putString("kind", component.kind()); c.putDouble("weight", component.weight()); list.add(c);
                }
                monvhua$evidence.put("components", list);
                monvhua$evidence.putBoolean("external_bleeding", selected.definition().externalBleeding());
                monvhua$evidence.putDouble("bleeding_factor", selected.definition().bleedingFactor());
            }
        }
        return monvhua$evidence;
    }
    @Inject(method = "writeCustomData", at = @At("TAIL"))
    private void monvhua$write(WriteView view, CallbackInfo ci) { view.put("monvhua_forensics", NbtCompound.CODEC, monvhua$getEvidence()); }
    @Inject(method = "readCustomData", at = @At("TAIL"))
    private void monvhua$read(ReadView view, CallbackInfo ci) { monvhua$evidence = view.read("monvhua_forensics", NbtCompound.CODEC).orElseGet(NbtCompound::new); }
}
