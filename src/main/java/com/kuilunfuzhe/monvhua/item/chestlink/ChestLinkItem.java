package com.kuilunfuzhe.monvhua.item.chestlink;

import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import com.kuilunfuzhe.monvhua.network.chestlink.ChestLinkStateS2CPacket;
import com.kuilunfuzhe.monvhua.features.chestlink.ChestLinkStore;
import com.kuilunfuzhe.monvhua.features.chestlink.ChestLinkServer;

/** Server-authoritative chest binding and remote chest access. */
public final class ChestLinkItem extends Item {
    private static final org.slf4j.Logger LOGGER = com.kuilunfuzhe.monvhua.MonvhuaMod.LOGGER;
    private static final java.util.Map<java.util.UUID, Long> LAST_INTERACTION = new java.util.HashMap<>();
    static final String DIMENSION = "monvhua_link_dimension";
    static final String X = "monvhua_link_x";
    static final String Y = "monvhua_link_y";
    static final String Z = "monvhua_link_z";
    static final String MAPPED_TARGET_DIMENSION = "monvhua_mapped_target_dimension";
    static final String MAPPED_TARGET_X = "monvhua_mapped_target_x";
    static final String MAPPED_TARGET_Y = "monvhua_mapped_target_y";
    static final String MAPPED_TARGET_Z = "monvhua_mapped_target_z";
    static final String MAPPED_SOURCE_DIMENSION = "monvhua_mapped_source_dimension";
    static final String MAPPED_SOURCE_X = "monvhua_mapped_source_x";
    static final String MAPPED_SOURCE_Y = "monvhua_mapped_source_y";
    static final String MAPPED_SOURCE_Z = "monvhua_mapped_source_z";
    static final String HIGHLIGHT = "monvhua_highlight";

    public ChestLinkItem(Settings settings) { super(settings); }

    public static void registerInteraction() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_INTERACTION.remove(handler.player.getUuid()));
    }

    @Override
    public ActionResult useOnBlock(net.minecraft.item.ItemUsageContext context) {
        return onUseBlock(context.getPlayer(), context.getWorld(), context.getHand(),
                new BlockHitResult(context.getHitPos(), context.getSide(), context.getBlockPos(), false));
    }

    @Override
    public ActionResult use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!world.isClient() && player.isSneaking()) {
            NbtCompound data = getData(stack);
            if (hasPending(data) || hasMapping(data)) {
                clearAllData(stack);
                player.sendMessage(Text.translatable("item.monvhua.chest_link.cleared"), true);
                LOGGER.info("[ChestLink] cleared pending and mapped bindings player={}", player.getName().getString());
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        }
        if (!world.isClient() && !player.isSneaking()) {
            toggleHighlight(stack);
            player.sendMessage(Text.translatable(isHighlightEnabled(stack)
                    ? "item.monvhua.chest_link.highlight_on"
                    : "item.monvhua.chest_link.highlight_off"), true);
            LOGGER.info("[ChestLink] highlight toggled player={} enabled={}", player.getName().getString(), isHighlightEnabled(stack));
            return ActionResult.SUCCESS;
        }
        return player.isSneaking() ? ActionResult.PASS : ActionResult.SUCCESS;
    }

    private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
        ItemStack stack = player.getStackInHand(hand);
        if (!(stack.getItem() instanceof ChestLinkItem)) return ActionResult.PASS;
        LOGGER.info("[ChestLink] use player={} side={} hand={} sneaking={} target={} itemData={}",
                player.getName().getString(), world.isClient() ? "client" : "server", hand, player.isSneaking(), hit.getBlockPos(), getData(stack));
        // Reserve sneaking chest clicks for binding. Ordinary chest clicks are consumed without opening vanilla UI.
        if (!world.getBlockState(hit.getBlockPos()).isOf(net.minecraft.block.Blocks.CHEST)) return ActionResult.PASS;
        if (world.isClient()) return ActionResult.SUCCESS;
        if (!(player instanceof ServerPlayerEntity serverPlayer) || !(world instanceof ServerWorld serverWorld)) return ActionResult.FAIL;
        if (!player.isSneaking()) {
            LOGGER.info("[ChestLink] chest click ignored player={} pos={} reason=not_sneaking", serverPlayer.getName().getString(), hit.getBlockPos());
            return ActionResult.SUCCESS;
        }
        long now = serverWorld.getTime();
        Long previous = LAST_INTERACTION.put(serverPlayer.getUuid(), now);
        if (previous != null && now - previous < 3L) return ActionResult.SUCCESS;
        BlockPos clicked = hit.getBlockPos().toImmutable();
        if (!(serverWorld.getBlockEntity(clicked) instanceof ChestBlockEntity)) return ActionResult.FAIL;
        NbtCompound data = getData(stack);
        ChestLinkStore store = ChestLinkStore.get(serverPlayer.getServer());
        if (migrateLegacyMapping(serverPlayer, stack, data, store)) ChestLinkServer.syncAll(serverPlayer.getServer());
        if (data.getInt(X).isEmpty()) {
            if (hasMapping(data)) {
                LOGGER.info("[ChestLink] replacing permanent mapping with new pending source player={} oldTarget={}",
                        serverPlayer.getName().getString(), mappingTargetPos(data));
                removeMapping(data);
            }
            data.putString(DIMENSION, world.getRegistryKey().getValue().toString());
            data.putInt(X, clicked.getX()); data.putInt(Y, clicked.getY()); data.putInt(Z, clicked.getZ());
            setData(stack, data);
            LOGGER.info("[ChestLink] bound source player={} dimension={} pos={}", serverPlayer.getName().getString(), dimensionName(world), clicked);
            serverPlayer.sendMessage(Text.translatable("item.monvhua.chest_link.first_bound"), true);
            return ActionResult.SUCCESS;
        }
        String dimension = data.getString(DIMENSION).orElse("");
        BlockPos sourcePos = new BlockPos(data.getInt(X).orElse(Integer.MIN_VALUE), data.getInt(Y).orElse(Integer.MIN_VALUE), data.getInt(Z).orElse(Integer.MIN_VALUE));
        LOGGER.info("[ChestLink] resolving source player={} sourceDimension={} sourcePos={} secondTarget={} currentDimension={}",
                serverPlayer.getName().getString(), dimension, sourcePos, clicked, dimensionName(world));
        if (!dimension.equals(world.getRegistryKey().getValue().toString())) {
            serverPlayer.sendMessage(Text.translatable("item.monvhua.chest_link.dimension_mismatch"), true);
            return ActionResult.SUCCESS;
        }
        // Force-load only the explicitly bound source chunk; do not load chunks during nearby chest highlighting.
        serverWorld.getChunk(sourcePos.getX() >> 4, sourcePos.getZ() >> 4);
        if (!(serverWorld.getBlockEntity(sourcePos) instanceof ChestBlockEntity source) || !source.hasWorld()) {
            LOGGER.warn("[ChestLink] source missing or invalid player={} sourcePos={} blockState={}", serverPlayer.getName().getString(), sourcePos, serverWorld.getBlockState(sourcePos));
            serverPlayer.sendMessage(Text.translatable("item.monvhua.chest_link.source_missing"), true);
            return ActionResult.SUCCESS;
        }
        data.remove(DIMENSION); data.remove(X); data.remove(Y); data.remove(Z);
        removeMapping(data);
        setData(stack, data);
        store.put(new ChestLinkStore.Endpoint(dimensionName(world), clicked), new ChestLinkStore.Endpoint(dimension, sourcePos));
        ChestLinkServer.syncAll(serverPlayer.getServer());
        LOGGER.info("[ChestLink] saved permanent mapping player={} target={} source={}", serverPlayer.getName().getString(), clicked, sourcePos);
        return openRemoteChest(serverPlayer, serverWorld, source, sourcePos, dimension);
    }

    public static ActionResult openSource(ServerPlayerEntity player, ChestLinkStore.Endpoint source) {
        if (player == null || source == null || player.getServer() == null) return ActionResult.FAIL;
        net.minecraft.registry.RegistryKey<World> key = net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, net.minecraft.util.Identifier.of(source.dimension()));
        ServerWorld world = player.getServer().getWorld(key);
        if (world == null) {
            player.sendMessage(Text.translatable("item.monvhua.chest_link.source_missing"), true);
            return ActionResult.SUCCESS;
        }
        world.getChunk(source.pos().getX() >> 4, source.pos().getZ() >> 4);
        if (!(world.getBlockEntity(source.pos()) instanceof ChestBlockEntity chest) || !chest.hasWorld()) {
            LOGGER.warn("[ChestLink] empty-hand mapped source missing player={} dimension={} source={}", player.getName().getString(), source.dimension(), source.pos());
            player.sendMessage(Text.translatable("item.monvhua.chest_link.source_missing"), true);
            return ActionResult.SUCCESS;
        }
        return openRemoteChest(player, world, chest, source.pos(), source.dimension());
    }

    private static ActionResult openRemoteChest(ServerPlayerEntity player, ServerWorld world, ChestBlockEntity source, BlockPos sourcePos, String dimension) {
        net.minecraft.util.math.ChunkPos sourceChunk = new net.minecraft.util.math.ChunkPos(sourcePos);
        world.getChunkManager().addTicket(ChunkTicketType.PORTAL, sourceChunk, 2);
        if (ServerPlayNetworking.canSend(player, ChestLinkStateS2CPacket.ID)) ServerPlayNetworking.send(player, new ChestLinkStateS2CPacket(true, dimension, sourcePos.getX(), sourcePos.getY(), sourcePos.getZ()));
        Inventory remoteInventory = new RemoteChestInventory(source);
        NamedScreenHandlerFactory factory = new NamedScreenHandlerFactory() {
            @Override public Text getDisplayName() { return Text.translatable("container.chest"); }
            @Override public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity openingPlayer) {
                int rows = Math.max(1, Math.min(6, remoteInventory.size() / 9));
                ScreenHandlerType<?> type = switch (rows) { case 1 -> ScreenHandlerType.GENERIC_9X1; case 2 -> ScreenHandlerType.GENERIC_9X2; case 4 -> ScreenHandlerType.GENERIC_9X4; case 5 -> ScreenHandlerType.GENERIC_9X5; case 6 -> ScreenHandlerType.GENERIC_9X6; default -> ScreenHandlerType.GENERIC_9X3; };
                return new net.minecraft.screen.GenericContainerScreenHandler(type, syncId, inventory, remoteInventory, rows) {
                    @Override public void onClosed(PlayerEntity closingPlayer) {
                        super.onClosed(closingPlayer); world.getChunkManager().removeTicket(ChunkTicketType.PORTAL, sourceChunk, 2);
                        if (closingPlayer instanceof ServerPlayerEntity closing && ServerPlayNetworking.canSend(closing, ChestLinkStateS2CPacket.ID)) ServerPlayNetworking.send(closing, new ChestLinkStateS2CPacket(false, dimension, sourcePos.getX(), sourcePos.getY(), sourcePos.getZ()));
                    }
                };
            }
        };
        LOGGER.info("[ChestLink] opening persistent mapped source player={} sourcePos={} sourceSize={}", player.getName().getString(), sourcePos, remoteInventory.size());
        player.openHandledScreen(factory);
        return ActionResult.SUCCESS;
    }

    private static String dimensionName(World world) { return world.getRegistryKey().getValue().toString(); }

    private static NbtCompound getData(ItemStack stack) {
        NbtComponent c = stack.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound data = c == null ? new NbtCompound() : c.copyNbt();
        return data;
    }
    private static void setData(ItemStack stack, NbtCompound nbt) { stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt)); }
    private static void clearAllData(ItemStack stack) {
        NbtCompound nbt = getData(stack);
        nbt.remove(DIMENSION); nbt.remove(X); nbt.remove(Y); nbt.remove(Z);
        removeMapping(nbt);
        setData(stack, nbt);
    }
    private static void removeMapping(NbtCompound nbt) {
        nbt.remove(MAPPED_TARGET_DIMENSION); nbt.remove(MAPPED_TARGET_X); nbt.remove(MAPPED_TARGET_Y); nbt.remove(MAPPED_TARGET_Z);
        nbt.remove(MAPPED_SOURCE_DIMENSION); nbt.remove(MAPPED_SOURCE_X); nbt.remove(MAPPED_SOURCE_Y); nbt.remove(MAPPED_SOURCE_Z);
    }
    public static void migrateLegacyMappings(ServerPlayerEntity player) {
        if (player == null || player.getServer() == null) return;
        ChestLinkStore store = ChestLinkStore.get(player.getServer());
        boolean changed = false;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (!(stack.getItem() instanceof ChestLinkItem)) continue;
            changed |= migrateLegacyMapping(player, stack, getData(stack), store);
        }
        if (changed) ChestLinkServer.syncAll(player.getServer());
    }

    private static boolean migrateLegacyMapping(ServerPlayerEntity player, ItemStack stack, NbtCompound data, ChestLinkStore store) {
        if (!hasMapping(data)) return false;
        ChestLinkStore.Endpoint entrance = new ChestLinkStore.Endpoint(
                data.getString(MAPPED_TARGET_DIMENSION).orElse(""), mappingTargetPos(data));
        ChestLinkStore.Endpoint source = new ChestLinkStore.Endpoint(
                data.getString(MAPPED_SOURCE_DIMENSION).orElse(""), mappedSourcePos(data));
        store.put(entrance, source);
        removeMapping(data);
        setData(stack, data);
        LOGGER.info("[ChestLink] migrated legacy item mapping player={} entrance={} source={}", player.getName().getString(), entrance, source);
        return true;
    }
    private static boolean hasPending(NbtCompound data) { return data.getInt(X).isPresent() && data.getInt(Y).isPresent() && data.getInt(Z).isPresent() && data.getString(DIMENSION).isPresent(); }
    private static boolean hasMapping(NbtCompound data) { return data.getInt(MAPPED_TARGET_X).isPresent() && data.getInt(MAPPED_TARGET_Y).isPresent() && data.getInt(MAPPED_TARGET_Z).isPresent() && data.getInt(MAPPED_SOURCE_X).isPresent() && data.getInt(MAPPED_SOURCE_Y).isPresent() && data.getInt(MAPPED_SOURCE_Z).isPresent() && data.getString(MAPPED_TARGET_DIMENSION).isPresent() && data.getString(MAPPED_SOURCE_DIMENSION).isPresent(); }
    private static BlockPos mappedSourcePos(NbtCompound data) {
        return new BlockPos(data.getInt(MAPPED_SOURCE_X).orElse(Integer.MIN_VALUE), data.getInt(MAPPED_SOURCE_Y).orElse(Integer.MIN_VALUE), data.getInt(MAPPED_SOURCE_Z).orElse(Integer.MIN_VALUE));
    }
    private static BlockPos mappingTargetPos(NbtCompound data) {
        return new BlockPos(data.getInt(MAPPED_TARGET_X).orElse(Integer.MIN_VALUE), data.getInt(MAPPED_TARGET_Y).orElse(Integer.MIN_VALUE), data.getInt(MAPPED_TARGET_Z).orElse(Integer.MIN_VALUE));
    }
    public static boolean isHighlightEnabled(ItemStack stack) { return getData(stack).getBoolean(HIGHLIGHT).orElse(false); }
    private static void toggleHighlight(ItemStack stack) {
        NbtCompound nbt = getData(stack);
        nbt.putBoolean(HIGHLIGHT, !nbt.getBoolean(HIGHLIGHT).orElse(false));
        setData(stack, nbt);
    }

    /** Keeps the real chest inventory but removes the vanilla distance check, since this is a remote view. */
    private record RemoteChestInventory(ChestBlockEntity chest) implements Inventory {
        @Override public int size() { return chest.size(); }
        @Override public boolean isEmpty() { return chest.isEmpty(); }
        @Override public ItemStack getStack(int slot) { return chest.getStack(slot); }
        @Override public ItemStack removeStack(int slot, int amount) { return chest.removeStack(slot, amount); }
        @Override public ItemStack removeStack(int slot) { return chest.removeStack(slot); }
        @Override public void setStack(int slot, ItemStack stack) { chest.setStack(slot, stack); }
        @Override public void markDirty() { chest.markDirty(); }
        @Override public boolean canPlayerUse(PlayerEntity player) { return true; }
        @Override public void clear() { chest.clear(); }
        @Override public boolean isValid(int slot, ItemStack stack) { return chest.isValid(slot, stack); }
        @Override public void onOpen(PlayerEntity player) { }
        @Override public void onClose(PlayerEntity player) { }
    }
}
