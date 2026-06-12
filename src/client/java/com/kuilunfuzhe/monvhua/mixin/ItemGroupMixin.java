package com.kuilunfuzhe.monvhua.mixin;

import com.kuilunfuzhe.monvhua.item.ModItemGroups;
import com.kuilunfuzhe.monvhua.renderer.Font_Render;
import net.minecraft.item.ItemGroup;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemGroup.class)
public abstract class ItemGroupMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void monvhua$rainbowMainItemGroupName(CallbackInfoReturnable<Text> cir) {
        ItemGroup group = (ItemGroup) (Object) this;
        Identifier id = Registries.ITEM_GROUP.getId(group);
        if (ModItemGroups.MAIN_GROUP_KEY.getValue().equals(id)) {
            cir.setReturnValue(Font_Render.createMainGroupName());
        }
    }
}
