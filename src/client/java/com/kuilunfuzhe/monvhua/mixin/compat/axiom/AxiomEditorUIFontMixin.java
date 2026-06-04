package com.kuilunfuzhe.monvhua.mixin.compat.axiom;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Locale;

@Pseudo
@Mixin(targets = "com.moulberry.axiom.editor.EditorUI", remap = false)
public class AxiomEditorUIFontMixin {
    @ModifyVariable(
            method = "initFonts",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false
    )
    private static String monvhua$useCompactCjkFontAtlas(String languageCode) {
        if (languageCode != null && languageCode.toLowerCase(Locale.ROOT).startsWith("zh")) {
            return "ja_jp";
        }
        return languageCode;
    }
}
