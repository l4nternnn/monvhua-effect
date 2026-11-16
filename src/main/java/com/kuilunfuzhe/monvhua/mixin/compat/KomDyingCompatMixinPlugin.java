package com.kuilunfuzhe.monvhua.mixin.compat;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
import java.util.*;

/** Deliberately version gated: unknown third-party lifecycles keep their original behavior. */
public final class KomDyingCompatMixinPlugin implements IMixinConfigPlugin {
    public void onLoad(String p) {}
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String target, String mixin) {
        return FabricLoader.getInstance().getModContainer("kom_dying")
                .map(m -> m.getMetadata().getVersion().getFriendlyString().equals("1.2.2")).orElse(false);
    }
    public void acceptTargets(Set<String> a, Set<String> b) {}
    public List<String> getMixins() { return null; }
    public void preApply(String t, ClassNode c, String m, IMixinInfo i) {}
    public void postApply(String t, ClassNode c, String m, IMixinInfo i) {}
}
