package com.riceawa.llm.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 传送 API 兼容层
 * 统一处理不同 Minecraft 版本的玩家传送 API 差异
 */
public final class TeleportCompat {

    private TeleportCompat() {}

    /**
     * 传送玩家到指定世界和坐标
     *
     * @param player 目标玩家
     * @param level 目标世界
     * @param x X 坐标
     * @param y Y 坐标
     * @param z Z 坐标
     * @param yaw 水平旋转角度
     * @param pitch 垂直旋转角度
     */
    public static void teleport(ServerPlayer player, ServerLevel level, double x, double y, double z, float yaw, float pitch) {
        //? >=1.21.2 {
        player.teleportTo(level, x, y, z, java.util.Set.of(), yaw, pitch, true);
        //?} else {
        /*player.teleportTo(level, x, y, z, yaw, pitch);
        *//*?}*/
    }
}
