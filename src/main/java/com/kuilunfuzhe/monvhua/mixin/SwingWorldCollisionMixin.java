package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.features.swing.SwingEntity;
import com.kuilunfuzhe.monvhua.features.swing.SwingSpatialIndex;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

@Mixin(Entity.class)
public abstract class SwingWorldCollisionMixin {
    @ModifyVariable(
            method = "adjustMovementForCollisions(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;",
            at = @At(value = "STORE"),
            ordinal = 0
    )
    private List<VoxelShape> monvhua$addSwingCollision(List<VoxelShape> original, Vec3d movement) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof SwingEntity || entity.isSpectator()) return original;
        Box query = entity.getBoundingBox().stretch(movement).expand(1.0e-4, entity.getStepHeight() + 1.0e-4, 1.0e-4);
        List<SwingEntity> swings = SwingSpatialIndex.find(entity.getWorld(), query);
        if (swings.isEmpty()) return original;
        ArrayList<VoxelShape> collisions = new ArrayList<>(original);
        for (SwingEntity swing : swings) if (!swing.ignoresCollision(entity)) collisions.addAll(swing.worldCollisionShapes(query));
        return collisions;
    }
}
