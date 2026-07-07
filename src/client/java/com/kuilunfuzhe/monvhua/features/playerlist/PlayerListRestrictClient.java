package com.kuilunfuzhe.monvhua.features.playerlist;

/**
 * 玩家列表限制客户端状态管理器。
 * 存储服务端同步的限制开关状态，供客户端 mixin 查询。
 */
public final class PlayerListRestrictClient {

    /** 当前是否启用了玩家列表限制 */
    private static boolean restricted = false;

    /**
     * 查询当前玩家列表限制是否启用。
     * @return true 表示限制已启用
     */
    public static boolean isRestricted() {
        return restricted;
    }

    /**
     * 设置玩家列表限制状态（由服务端数据包调用）。
     * @param value true 表示启用限制
     */
    public static void setRestricted(boolean value) {
        restricted = value;
    }

    private PlayerListRestrictClient() {
    }
}
