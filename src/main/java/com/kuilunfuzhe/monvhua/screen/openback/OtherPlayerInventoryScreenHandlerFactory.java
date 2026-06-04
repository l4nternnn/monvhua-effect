package com.kuilunfuzhe.monvhua.screen.openback;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * 其他玩家物品栏屏幕处理器工厂，实现{@link NamedScreenHandlerFactory}以支持服务端通过{@code player.openHandledScreen()}打开界面。
 * 直接持有目标玩家PlayerInventory的引用（非拷贝），实现物品栏内容的实时同步显示。
 */
public class OtherPlayerInventoryScreenHandlerFactory implements NamedScreenHandlerFactory {
    /** 目标玩家的物品栏引用（非拷贝，修改会实时反映到界面） */
    private final PlayerInventory targetInventory;

    /**
     * @param targetPlayer 目标玩家，其物品栏将被查看
     */
    public OtherPlayerInventoryScreenHandlerFactory(ServerPlayerEntity targetPlayer) {
        // 直接使用引用而非拷贝，实现实时同步
        this.targetInventory = targetPlayer.getInventory();
    }

    @Override
    public Text getDisplayName() {
        return Text.literal("目标玩家的背包");
    }

    /**
     * 创建{@link OtherPlayerInventoryScreenHandler}实例，传入目标玩家物品栏引用以实现实时同步。
     */
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
        return new OtherPlayerInventoryScreenHandler(syncId, inv, targetInventory);
    }
}