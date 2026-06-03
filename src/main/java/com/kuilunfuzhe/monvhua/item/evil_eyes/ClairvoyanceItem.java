package com.kuilunfuzhe.monvhua.item.evil_eyes;

import com.kuilunfuzhe.monvhua.network.evil_eyes.OpenUIPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * 天眼物品，右键使用时检查玩家是否带有Silenced标签，
 * 若被沉默则发送提示消息并阻止使用，否则向客户端发送OpenUIPacket以打开天眼UI。
 */
public class ClairvoyanceItem extends Item {
    /** 用于判断玩家是否被沉默的指令标签，有此标签时无法使用天眼 */
    private static final String SILENCED_TAG = "Silenced";

    public ClairvoyanceItem(Settings settings) {
        super(settings);
    }

    /**
     * 玩家右键使用天眼物品。
     * 服务端检查Silenced标签：被沉默则提示"你难以集中精神"并返回失败，
     * 否则发送OpenUIPacket数据包打开天眼界面。
     * @return 客户端始终返回SUCCESS，服务端根据检查结果返回SUCCESS或FAIL
     */
    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient) {
            ServerPlayerEntity player = (ServerPlayerEntity) user;
            if (player.getCommandTags().contains(SILENCED_TAG)) {
                player.sendMessage(Text.literal("§c你难以集中精神"), true);
                return ActionResult.FAIL;
            }
            ServerPlayNetworking.send(player, new OpenUIPacket());
        }
        return ActionResult.SUCCESS;
    }
}