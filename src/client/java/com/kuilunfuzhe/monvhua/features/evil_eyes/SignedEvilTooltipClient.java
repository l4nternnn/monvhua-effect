package com.kuilunfuzhe.monvhua.features.evil_eyes;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

public final class SignedEvilTooltipClient {
    private static final String SIGNED_EVIL_KEY = "signed_evil";

    private SignedEvilTooltipClient() {
    }

    public static void initialize() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
            if (customData == null) {
                return;
            }
            NbtCompound nbt = customData.copyNbt();
            if (nbt.contains(SIGNED_EVIL_KEY)) {
                lines.add(Text.literal("\u00A77\u8FD9\u4EF6\u7269\u54C1\u5E26\u7740\u4E00\u679A\u6D1E\u5BDF\u5370\u8BB0\u3002"));
            }
        });
    }
}
