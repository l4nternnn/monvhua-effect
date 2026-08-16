package com.kuilunfuzhe.monvhua.features.activity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

public final class UiActivityBubbleSizeStore extends PersistentState {
    public static final Codec<UiActivityBubbleSizeStore> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("multiplier", UiActivityBubbleSize.DEFAULT_MULTIPLIER)
                    .forGetter(UiActivityBubbleSizeStore::multiplier)
    ).apply(instance, UiActivityBubbleSizeStore::new));

    public static final PersistentStateType<UiActivityBubbleSizeStore> TYPE = new PersistentStateType<>(
            "monvhua_bubble_size",
            UiActivityBubbleSizeStore::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private float multiplier = UiActivityBubbleSize.DEFAULT_MULTIPLIER;

    public UiActivityBubbleSizeStore() {
    }

    private UiActivityBubbleSizeStore(float multiplier) {
        this.multiplier = UiActivityBubbleSize.sanitize(multiplier);
        if (Float.compare(this.multiplier, multiplier) != 0) {
            markDirty();
        }
    }

    public static UiActivityBubbleSizeStore get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE);
    }

    public float multiplier() {
        return multiplier;
    }

    public void setMultiplier(float multiplier) {
        float sanitized = UiActivityBubbleSize.sanitize(multiplier);
        if (Float.compare(this.multiplier, sanitized) != 0) {
            this.multiplier = sanitized;
            markDirty();
        }
    }
}
