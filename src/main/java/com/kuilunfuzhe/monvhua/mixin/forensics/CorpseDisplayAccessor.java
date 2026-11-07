package com.kuilunfuzhe.monvhua.mixin.forensics;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.util.math.AffineTransformation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(DisplayEntity.class)
public interface CorpseDisplayAccessor {
 @Invoker("getTransformation") static AffineTransformation monvhua$transform(DataTracker tracker){throw new AssertionError();}
}
