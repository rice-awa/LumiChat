package com.riceawa.llm.function;

import com.riceawa.llm.util.EntityHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 权限管理工具类，统一处理LLM函数的权限检查。
 */
public final class PermissionHelper {

    private PermissionHelper() {
    }

    /**
     * 检查玩家是否为OP。
     */
    public static boolean isOperator(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            CommandSourceStack source = serverPlayer.createCommandSourceStack();
            return EntityHelper.hasPermissionLevel(source, 2);
        }
        return false;
    }

    /**
     * 检查玩家是否有指定级别的命令权限。
     */
    public static boolean hasCommandPermission(Player player, int level) {
        if (player instanceof ServerPlayer serverPlayer) {
            CommandSourceStack source = serverPlayer.createCommandSourceStack();
            return EntityHelper.hasPermissionLevel(source, level);
        }
        return false;
    }

    /**
     * 检查玩家是否可以修改世界。
     */
    public static boolean canModifyWorld(Player player) {
        return isOperator(player);
    }

    /**
     * 检查玩家是否可以查看其他玩家的信息。
     */
    public static boolean canViewOtherPlayerInfo(Player requester, Player target) {
        if (requester.equals(target)) {
            return true;
        }
        return isOperator(requester);
    }

    /**
     * 检查玩家是否可以对其他玩家执行操作。
     */
    public static boolean canOperateOnOtherPlayer(Player requester, Player target) {
        if (requester.equals(target)) {
            return false;
        }
        return isOperator(requester);
    }

    /**
     * 检查玩家是否可以发送广播消息。
     */
    public static boolean canSendBroadcast(Player player) {
        return isOperator(player);
    }

    /**
     * 检查玩家是否可以控制服务器环境（天气、时间等）。
     */
    public static boolean canControlEnvironment(Player player) {
        return isOperator(player);
    }

    /**
     * 检查玩家是否可以生成实体。
     */
    public static boolean canSummonEntity(Player player) {
        return isOperator(player);
    }

    /**
     * 检查玩家是否可以传送其他玩家。
     */
    public static boolean canTeleportOthers(Player player) {
        return isOperator(player);
    }

    /**
     * 获取权限错误消息。
     */
    public static String getPermissionErrorMessage(String action) {
        return "没有权限执行操作: " + action + "（需要OP权限）";
    }
}
