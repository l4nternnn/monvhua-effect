package com.kuilunfuzhe.monvhua.features.activity;

import com.mojang.serialization.Codec;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

/** Persisted server-wide style choice, shared by every activity bubble. */
public final class UiActivityBubbleStyleStore extends PersistentState {
    public static final PersistentStateType<UiActivityBubbleStyleStore> TYPE = new PersistentStateType<>(
            "monvhua_bubble_style",
            UiActivityBubbleStyleStore::new,
            Codec.INT.xmap(UiActivityBubbleStyleStore::new, store -> store.style.ordinal()),
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private UiActivityBubbleStyle style;

    public UiActivityBubbleStyleStore() {
        this(UiActivityBubbleStyle.DEFAULT);
    }

    private UiActivityBubbleStyleStore(int styleId) {
        this(UiActivityBubbleStyle.fromId(styleId));
    }

    private UiActivityBubbleStyleStore(UiActivityBubbleStyle style) {
        this.style = style;
    }

    public static UiActivityBubbleStyleStore get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
    }

    public UiActivityBubbleStyle style() {
        return style;
    }

    public void setStyle(UiActivityBubbleStyle style) {
        UiActivityBubbleStyle resolved = style == null ? UiActivityBubbleStyle.DEFAULT : style;
        if (this.style != resolved) {
            this.style = resolved;
            markDirty();
        }
    }
}
