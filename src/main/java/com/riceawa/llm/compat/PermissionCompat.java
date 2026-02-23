package com.riceawa.llm.compat;

import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
//? >=1.21.11 {
import net.minecraft.command.permission.PermissionCheck;
//?}

import java.util.function.Predicate;

/**
 * 权限检查兼容层
 * 统一处理不同 Minecraft 版本的权限检查 API 差异
 * 
 * <p>在 1.21.11+ 中，权限检查使用新的 PermissionPredicate API：
 * <ul>
 *   <li>CommandManager.requirePermissionLevel(PermissionCheck)</li>
 *   <li>返回 Predicate&lt;ServerCommandSource&gt;</li>
 * </ul>
 * 
 * <p>在旧版本中，使用传统方法：
 * <ul>
 *   <li>source.hasPermissionLevel(int)</li>
 * </ul>
 */
public final class PermissionCompat {
    
    private PermissionCompat() {}
    
    /**
     * 检查命令源是否有管理员权限（权限等级2，对应 GAMEMASTERS_CHECK）
     * 用于 /gamemode、/give 等命令的权限级别
     * 
     * @param source 命令源
     * @return 是否有管理员权限
     */
    public static boolean hasGamemastersPermission(ServerCommandSource source) {
        //? >=1.21.11 {
        return CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK).test(source);
        //?} else {
        /*return source.hasPermissionLevel(2);
        *//*?}*/
    }
    
    /**
     * 检查命令源是否有指定权限等级
     * 
     * @param source 命令源
     * @param level 权限等级 (1-4)
     * @return 是否有指定权限
     */
    public static boolean hasPermissionLevel(ServerCommandSource source, int level) {
        //? >=1.21.11 {
        // Map permission levels to the new PermissionCheck constants
        PermissionCheck check = switch (level) {
            case 1 -> CommandManager.MODERATORS_CHECK;
            case 2 -> CommandManager.GAMEMASTERS_CHECK;
            case 3 -> CommandManager.ADMINS_CHECK;
            case 4 -> CommandManager.OWNERS_CHECK;
            default -> CommandManager.GAMEMASTERS_CHECK;
        };
        return CommandManager.requirePermissionLevel(check).test(source);
        //?} else {
        /*// In older versions, use the simple permission level check
        return source.hasPermissionLevel(level);
        *//*?}*/
    }
    
    /**
     * 创建管理员权限要求的 predicate（用于命令注册）
     * 权限等级 2，对应游戏管理员级别
     * 
     * @return 权限检查 Predicate
     */
    public static Predicate<ServerCommandSource> requireGamemasters() {
        //? >=1.21.11 {
        return CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK);
        //?} else {
        /*return source -> source.hasPermissionLevel(2);
        *//*?}*/
    }
    
    /**
     * 创建指定权限等级要求的 predicate（用于命令注册）
     * 
     * @param level 权限等级 (1-4)
     * @return 权限检查 Predicate
     */
    public static Predicate<ServerCommandSource> requirePermissionLevel(int level) {
        //? >=1.21.11 {
        PermissionCheck check = switch (level) {
            case 1 -> CommandManager.MODERATORS_CHECK;
            case 2 -> CommandManager.GAMEMASTERS_CHECK;
            case 3 -> CommandManager.ADMINS_CHECK;
            case 4 -> CommandManager.OWNERS_CHECK;
            default -> CommandManager.GAMEMASTERS_CHECK;
        };
        return CommandManager.requirePermissionLevel(check);
        //?} else {
        /*return source -> source.hasPermissionLevel(level);
        *//*?}*/
    }
}
