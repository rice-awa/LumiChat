package com.riceawa.llm.compat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * 玩家 API 兼容层
 * 统一处理不同 Minecraft 版本的玩家查找与状态查询 API 差异
 */
public final class PlayerCompat {

    private PlayerCompat() {}

    /**
     * 根据玩家名称查找在线玩家
     *
     * @param server Minecraft 服务器实例
     * @param name 玩家名称
     * @return 找到的 ServerPlayer，如果未找到则返回 null
     */
    @Nullable
    public static ServerPlayer getPlayerByName(MinecraftServer server, String name) {
        //? >=1.21.11 {
        return server.getPlayerList().getPlayer(name);
        //?} else {
        /*return server.getPlayerList().getPlayerByName(name);
        *//*?}*/
    }

    /**
     * 检查玩家是否在地面上
     *
     * @param player 目标玩家
     * @return 如果玩家在地面上返回 true
     */
    public static boolean isOnGround(ServerPlayer player) {
        //? >=1.20 {
        return player.onGround();
        //?} else {
        /*return player.isOnGround();
        *//*?}*/
    }
}
