package com.kuilunfuzhe.monvhua.features.possession;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class PossessionFeature {
    public static final Identifier ITEM_ID = Identifier.of(MonvhuaMod.MOD_ID, "possession_wand");
    public static final PossessionItem POSSESSION_ITEM;

    private static boolean initialized = false;

    static {
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, ITEM_ID);
        POSSESSION_ITEM = new PossessionItem(new Item.Settings()
                .registryKey(key)
                .maxCount(1));
    }

    private PossessionFeature() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        Registry.register(Registries.ITEM, ITEM_ID, POSSESSION_ITEM);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(POSSESSION_ITEM));

        PossessionPackets.registerC2S();
        PossessionPackets.registerS2C();

        ServerPlayNetworking.registerGlobalReceiver(PossessionPackets.StopC2S.ID, (packet, context) ->
                context.server().execute(() -> PossessionManager.stopByController(context.player(), context.server())));
        ServerPlayNetworking.registerGlobalReceiver(PossessionPackets.InputC2S.ID, (packet, context) ->
                context.server().execute(() -> PossessionManager.applyInput(
                        context.player(), packet.input(), packet.yaw(), packet.pitch(), packet.selectedSlot())));
        ServerPlayNetworking.registerGlobalReceiver(PossessionPackets.ActionC2S.ID, (packet, context) ->
                context.server().execute(() -> PossessionManager.handleAction(context.player(), packet.action())));

        ServerTickEvents.END_SERVER_TICK.register(PossessionManager::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                PossessionManager.cleanupForDisconnect(handler.player, server));
    }
}
