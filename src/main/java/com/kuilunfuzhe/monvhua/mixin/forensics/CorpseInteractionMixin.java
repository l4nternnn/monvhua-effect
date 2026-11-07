package com.kuilunfuzhe.monvhua.mixin.forensics;
import com.kuilunfuzhe.monvhua.features.forensics.CorpseBounds;
import net.minecraft.entity.decoration.InteractionEntity;
import net.minecraft.entity.data.*;
import net.minecraft.util.math.*;

import org.joml.Vector3f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(InteractionEntity.class)
public abstract class CorpseInteractionMixin implements CorpseBounds {
    @Unique private static final TrackedData<Vector3f> MIN=DataTracker.registerData(InteractionEntity.class,TrackedDataHandlerRegistry.VECTOR_3F);
    @Unique private static final TrackedData<Vector3f> MAX=DataTracker.registerData(InteractionEntity.class,TrackedDataHandlerRegistry.VECTOR_3F);
    @Inject(method="initDataTracker",at=@At("TAIL")) private void init(DataTracker.Builder builder,CallbackInfo ci){builder.add(MIN,new Vector3f());builder.add(MAX,new Vector3f());}
    public void monvhua$bounds(Box box){var self=(InteractionEntity)(Object)this;self.getDataTracker().set(MIN,new Vector3f((float)box.minX,(float)box.minY,(float)box.minZ));self.getDataTracker().set(MAX,new Vector3f((float)box.maxX,(float)box.maxY,(float)box.maxZ));self.setBoundingBox(box.offset(self.getPos()));}
    @Inject(method="calculateDefaultBoundingBox",at=@At("HEAD"),cancellable=true)
    private void box(Vec3d pos,CallbackInfoReturnable<Box> cir){var self=(InteractionEntity)(Object)this;var min=self.getDataTracker().get(MIN);var max=self.getDataTracker().get(MAX);if(max.y>min.y)cir.setReturnValue(new Box(min.x,min.y,min.z,max.x,max.y,max.z).offset(pos));}
    @Inject(method="onTrackedDataSet",at=@At("TAIL")) private void changed(TrackedData<?> key,CallbackInfo ci){if(key==MIN||key==MAX){var self=(InteractionEntity)(Object)this;var a=self.getDataTracker().get(MIN);var b=self.getDataTracker().get(MAX);if(b.y>a.y)self.setBoundingBox(new Box(a.x,a.y,a.z,b.x,b.y,b.z).offset(self.getPos()));}}
}
