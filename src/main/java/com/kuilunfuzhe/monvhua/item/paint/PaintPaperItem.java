package com.kuilunfuzhe.monvhua.item.paint;

import com.kuilunfuzhe.monvhua.features.paint.PaintOverlayFeature;
import com.kuilunfuzhe.monvhua.features.paint.PaintOverlayStore;
import com.kuilunfuzhe.monvhua.features.paint.PaintPaperStore;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PaintPaperItem extends Item {
    private static final String DATA_ID = "paint_paper_id";
    private static final String DATA_SIZE = "paint_paper_size";

    public PaintPaperItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World rawWorld = context.getWorld();
        if (!(rawWorld instanceof ServerWorld world) || !(context.getPlayer() instanceof ServerPlayerEntity player)) {
            return ActionResult.SUCCESS;
        }

        ItemStack stack = context.getStack();
        UUID existingId = getPaperId(stack);
        if (existingId != null && !player.isSneaking()) {
            paste(world, player, stack, existingId, context.getBlockPos(), context.getSide());
            return ActionResult.SUCCESS;
        }

        save(world, player, stack, context.getBlockPos(), context.getSide());
        return ActionResult.SUCCESS;
    }

    public static int getStoredSize(ItemStack stack) {
        NbtCompound data = getData(stack);
        return data == null ? 0 : data.getInt(DATA_SIZE, 0);
    }

    public static boolean hasSavedGraffiti(ItemStack stack) {
        return getPaperId(stack) != null;
    }

    public static ItemStack createSavedPaper(ServerWorld world, String displayName, int size, List<PaintPaperStore.Cell> cells) {
        UUID id = UUID.randomUUID();
        PaintPaperStore.get(world).put(new PaintPaperStore.PaperData(
                id,
                size,
                cells
        ));
        ItemStack stack = new ItemStack(PaintItems.PAINT_PAPER);
        writePaperData(stack, id, size);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(displayName));
        return stack;
    }

    public static void useFromEditor(ServerWorld world, ServerPlayerEntity player, ItemStack stack, BlockPos origin, Direction face, boolean save) {
        UUID existingId = getPaperId(stack);
        if (!save && existingId != null) {
            paste(world, player, stack, existingId, origin, face);
            return;
        }
        save(world, player, stack, origin, face);
    }

    private static void save(ServerWorld world, ServerPlayerEntity player, ItemStack stack, BlockPos origin, Direction face) {
        int size = PaintOverlayFeature.getPaperSize(player);
        UUID id = getPaperId(stack);
        if (id == null) {
            id = UUID.randomUUID();
        }

        PaintOverlayStore overlayStore = PaintOverlayStore.get(world);
        List<PaintPaperStore.Cell> cells = new ArrayList<>();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int[] pixels = overlayStore.getPixels(areaPos(origin, face, x, y), face);
                if (hasPixels(pixels)) {
                    cells.add(new PaintPaperStore.Cell(x, y, pixels));
                }
            }
        }

        if (cells.isEmpty()) {
            player.sendMessage(Text.literal("画纸: 当前区域没有可保存的涂鸦"), true);
            return;
        }

        PaintPaperStore.get(world).put(new PaintPaperStore.PaperData(id, size, cells));
        writePaperData(stack, id, size);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("画纸 " + size + "x" + size));
        player.sendMessage(Text.literal("画纸: 已保存 " + size + "x" + size), true);
    }

    private static void paste(ServerWorld world, ServerPlayerEntity player, ItemStack stack, UUID id, BlockPos origin, Direction face) {
        PaintPaperStore.PaperData data = PaintPaperStore.get(world).get(id);
        if (data == null) {
            player.sendMessage(Text.literal("画纸: 保存数据不存在，请潜行右键重新保存"), true);
            return;
        }

        int changed = 0;
        for (PaintPaperStore.Cell cell : data.cells()) {
            BlockPos target = areaPos(origin, face, cell.x(), cell.y());
            if (world.isAir(target)) {
                continue;
            }
            if (PaintOverlayFeature.setFacePixels(world, target, face, cell.pixels())) {
                changed++;
            }
        }

        writePaperData(stack, id, data.size());
        player.sendMessage(Text.literal(changed == 0 ? "画纸: 没有可释放的目标平面" : "画纸: 已释放 " + data.size() + "x" + data.size()), true);
    }

    private static BlockPos areaPos(BlockPos origin, Direction face, int x, int y) {
        BlockPos right = rightOffset(face);
        BlockPos down = downOffset(face);
        return origin.add(right.getX() * x + down.getX() * y, right.getY() * x + down.getY() * y, right.getZ() * x + down.getZ() * y);
    }

    private static BlockPos rightOffset(Direction face) {
        return switch (face) {
            case NORTH -> new BlockPos(-1, 0, 0);
            case EAST -> new BlockPos(0, 0, -1);
            case WEST -> new BlockPos(0, 0, 1);
            default -> new BlockPos(1, 0, 0);
        };
    }

    private static BlockPos downOffset(Direction face) {
        return switch (face) {
            case UP -> new BlockPos(0, 0, 1);
            case DOWN -> new BlockPos(0, 0, -1);
            default -> new BlockPos(0, -1, 0);
        };
    }

    private static UUID getPaperId(ItemStack stack) {
        NbtCompound data = getData(stack);
        if (data == null) {
            return null;
        }
        String id = data.getString(DATA_ID, "");
        if (id.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static NbtCompound getData(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        return component == null ? null : component.copyNbt();
    }

    private static void writePaperData(ItemStack stack, UUID id, int size) {
        NbtCompound data = getData(stack);
        if (data == null) {
            data = new NbtCompound();
        }
        data.putString(DATA_ID, id.toString());
        data.putInt(DATA_SIZE, size);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
    }

    private static boolean hasPixels(int[] pixels) {
        for (int pixel : pixels) {
            if (pixel != 0) {
                return true;
            }
        }
        return false;
    }
}
