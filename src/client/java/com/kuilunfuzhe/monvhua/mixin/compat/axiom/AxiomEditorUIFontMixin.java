package com.kuilunfuzhe.monvhua.mixin.compat.axiom;

import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "com.moulberry.axiom.editor.EditorUI", remap = false)
public class AxiomEditorUIFontMixin {
    @ModifyVariable(
            method = "initFonts(Ljava/lang/String;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0,
            remap = false
    )
    @Dynamic("Optional Axiom compatibility target; method exists in Axiom 5.4.1 but is not on the normal compile classpath.")
    private static String monvhua$useCompactCjkFontAtlas(String languageCode) {
        return languageCode;
    }
}
