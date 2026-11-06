package com.kuilunfuzhe.monvhua.mixin.forensics;
import com.kuilunfuzhe.monvhua.features.forensics.blood.BloodTransferService;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.*;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class BloodFurnaceMixin {
    @Inject(method = "canAcceptRecipeOutput", at = @At("HEAD"), cancellable = true)
    private static void monvhua$preserveResidue(DynamicRegistryManager registries, RecipeEntry<? extends AbstractCookingRecipe> recipe,
            SingleStackRecipeInput input, DefaultedList<ItemStack> inventory, int max, CallbackInfoReturnable<Boolean> cir) {
        if (!inventory.isEmpty() && BloodTransferService.stained(inventory.getFirst())) cir.setReturnValue(false);
    }
}
