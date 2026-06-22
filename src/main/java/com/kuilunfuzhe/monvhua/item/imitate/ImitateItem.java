package com.kuilunfuzhe.monvhua.item.imitate;

import com.kuilunfuzhe.monvhua.features.imitate.ImitateManager;
import com.kuilunfuzhe.monvhua.network.imitate.ImitateOpenUIPacket;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public class ImitateItem extends Item {

    public static final ImitateItem IMITATE_ITEM;

    static {
        Identifier id = Identifier.of("monvhua", "imitate_book");
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
        Item.Settings settings = new Item.Settings()
                .registryKey(key)
                .maxCount(1);
        IMITATE_ITEM = new ImitateItem(settings);
    }

    public ImitateItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient) {
            ServerPlayerEntity player = (ServerPlayerEntity) user;
            int witchScore = ImitateManager.getWitchScore(player);
            ServerPlayNetworking.send(player, new ImitateOpenUIPacket(witchScore));
        }
        return ActionResult.SUCCESS;
    }

    public static void initialize() {
        Registry.register(Registries.ITEM, Identifier.of("monvhua", "imitate_book"), IMITATE_ITEM);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS)
                .register(entries -> entries.add(IMITATE_ITEM));
    }
}
