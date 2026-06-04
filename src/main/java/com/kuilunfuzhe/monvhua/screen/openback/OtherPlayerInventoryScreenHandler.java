package com.kuilunfuzhe.monvhua.screen.openback;

import com.kuilunfuzhe.monvhua.MonvhuaMod;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

/**
 * 查看其他玩家物品栏的屏幕处理器。
 * 显示目标玩家完整41格物品栏（快捷栏9格 + 背包27格 + 盔甲4格 + 副手1格），
 * 底部同时显示查看者自己的快捷栏。快速移动功能被禁用。
 */
public class OtherPlayerInventoryScreenHandler extends ScreenHandler {
    private static final int TARGET_INVENTORY_START = 0;
    private static final int TARGET_INVENTORY_END = 37;
    private static final int VIEWER_INVENTORY_START = TARGET_INVENTORY_END;
    private static final int VIEWER_INVENTORY_END = VIEWER_INVENTORY_START + 36;

    /** 目标玩家的物品栏（服务端为真实PlayerInventory引用，客户端为SimpleInventory占位） */
    private final Inventory otherInventory;
    /** 查看者（正在查看其他玩家背包的玩家） */
    private final PlayerEntity viewer;

    /**
     * 客户端构造器，使用41格SimpleInventory占位（与服务端目标玩家物品栏数量一致，确保槽位索引正确）。
     * @param syncId 同步ID
     * @param viewerInv 查看者的物品栏
     */
    public OtherPlayerInventoryScreenHandler(int syncId, PlayerInventory viewerInv) {
        this(syncId, viewerInv, new SimpleInventory(41)); // 41 个槽位，与服务端目标玩家物品栏数量一致
    }

    /**
     * 服务端构造器，绑定目标玩家的真实物品栏。
     * 布局：顶部为目标玩家快捷栏 → 目标背包 → 查看者快捷栏（底部），盔甲栏与副手栏目前被注释禁用。
     * @param syncId 同步ID
     * @param viewerInv 查看者的物品栏
     * @param targetInventory 目标玩家的物品栏
     */
    public OtherPlayerInventoryScreenHandler(int syncId, PlayerInventory viewerInv, Inventory targetInventory) {
        super(MonvhuaMod.OTHER_INVENTORY_HANDLER, syncId);
        this.otherInventory = targetInventory;
        this.viewer = viewerInv.player;
        targetInventory.onOpen(viewerInv.player);


        // 快捷栏 9 格 (索引 0-8)
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(targetInventory, col, 8 + col * 18, 81-23));
        }

        // 目标玩家的背包槽位 (41 格)
        // 主背包 27 格 (索引 9-35)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(targetInventory, col + row * 9 + 9, 8 + col * 18, 78 + row * 19));
            }
        }
        // 盔甲栏 4 格 (索引 36-39)
//        for (int i = 0; i < 4; ++i) {
//            final EquipmentSlot slotType = EquipmentSlot.values()[2 + i];
//            final int slotIndex = 36 + i;
//            this.addSlot(new Slot(targetInventory, slotIndex, 8, 8 + i * 18) {
//                @Override
//                public boolean canInsert(ItemStack stack) {
//                    EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
//                    return equippable != null && equippable.slot() == slotType;
//                }
//                @Override
//                public int getMaxItemCount() {
//                    return 1;
//                }
//            });
//        }
//         副手 (索引 40)
        this.addSlot(new Slot(targetInventory, 40, 8, 38));

        // 查看者自己的背包 (36 格)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(viewerInv, col + row * 9 + 9, 8 + col * 18, 152 + row * 19));
            }
        }

        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(viewerInv, col, 8 + col * 18, 176+38));
        }
    }

    /**
     * 禁止快速移动——查看他人背包时不允许Shift+点击转移物品，防止物品丢失或复制。
     * @return 始终返回EMPTY
     */
    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        if (slot < 0 || slot >= this.slots.size()) {
            return ItemStack.EMPTY;
        }

        Slot slotObj = this.slots.get(slot);
        if (slotObj == null || !slotObj.hasStack()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slotObj.getStack();
        ItemStack original = stack.copy();

        if (slot >= TARGET_INVENTORY_START && slot < TARGET_INVENTORY_END) {
            if (!this.insertItem(stack, VIEWER_INVENTORY_START, VIEWER_INVENTORY_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (slot >= VIEWER_INVENTORY_START && slot < VIEWER_INVENTORY_END) {
            if (!this.insertItem(stack, TARGET_INVENTORY_START, TARGET_INVENTORY_END, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slotObj.setStack(ItemStack.EMPTY);
        } else {
            slotObj.markDirty();
        }

        return original;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return otherInventory.canPlayerUse(player);
    }

    /**
     * 界面关闭时通知目标物品栏，触发onClose回调以正确清理状态。
     */
    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        otherInventory.onClose(player);
    }
}
