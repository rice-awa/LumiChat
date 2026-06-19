package com.riceawa.llm.compat;

//? >=1.21.11 {
import net.minecraft.world.level.gamerules.GameRules;
//?} else {
/*import net.minecraft.world.level.GameRules;
*//*?}*/
import net.minecraft.server.level.ServerLevel;

/**
 * GameRules 兼容层
 * 统一处理不同 Minecraft 版本的 GameRules API 差异
 * 
 * <p>在 1.21.11+ 中：
 * <ul>
 *   <li>GameRules 类位于 net.minecraft.world.level.gamerules.GameRules</li>
 *   <li>使用 get(GameRules.Key) 获取布尔值</li>
 * </ul>
 * 
 * <p>在旧版本中：
 * <ul>
 *   <li>GameRules 类位于 net.minecraft.world.level.gamerules.GameRules</li>
 *   <li>使用 getBoolean(GameRules.Key) 获取布尔值</li>
 * </ul>
 */
public final class GameRulesCompat {
    
    private GameRulesCompat() {}
    
    /**
     * 获取世界的 PvP 规则值
     * 
     * @param level 服务器世界
     * @return PvP 是否启用
     */
    public static boolean isPvpEnabled(ServerLevel level) {
        // `pvp` 在旧版本中由服务器级配置控制，不是 GameRules 常量。
        //? >=1.21.11 {
        return level.getGameRules().get(GameRules.PVP);
        //?} else {
        /*return level.getServer().isPvpAllowed();
        *//*?}*/
    }
    
    /**
     * 获取世界的命令方块输出规则值
     * 
     * @param level 服务器世界
     * @return 命令方块是否输出到控制台
     */
    public static boolean isCommandBlockOutputEnabled(ServerLevel level) {
        //? >=1.21.11 {
        return level.getGameRules().get(GameRules.COMMAND_BLOCK_OUTPUT);
        //?} else {
        /*return level.getGameRules().getBoolean(GameRules.RULE_COMMANDBLOCKOUTPUT);
        *//*?}*/
    }
    
    /**
     * 获取世界的命令反馈规则值
     * 
     * @param level 服务器世界
     * @return 是否发送命令反馈
     */
    public static boolean isSendCommandFeedbackEnabled(ServerLevel level) {
        //? >=1.21.11 {
        return level.getGameRules().get(GameRules.SEND_COMMAND_FEEDBACK);
        //?} else {
        /*return level.getGameRules().getBoolean(GameRules.RULE_SENDCOMMANDFEEDBACK);
        *//*?}*/
    }
    
    /**
     * 获取世界的保持库存规则值
     * 
     * @param level 服务器世界
     * @return 死亡是否保留库存
     */
    public static boolean isKeepInventoryEnabled(ServerLevel level) {
        //? >=1.21.11 {
        return level.getGameRules().get(GameRules.KEEP_INVENTORY);
        //?} else {
        /*return level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
        *//*?}*/
    }
}
