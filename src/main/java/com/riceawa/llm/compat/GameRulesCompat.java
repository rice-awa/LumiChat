package com.riceawa.llm.compat;

//? >=1.21.11 {
import net.minecraft.world.rule.GameRules;
//?} else {
/*import net.minecraft.world.GameRules;
*//*?}*/
import net.minecraft.server.world.ServerWorld;

/**
 * GameRules 兼容层
 * 统一处理不同 Minecraft 版本的 GameRules API 差异
 * 
 * <p>在 1.21.11+ 中：
 * <ul>
 *   <li>GameRules 类移动到 net.minecraft.world.rule.GameRules</li>
 *   <li>使用 getValue(GameRules.Key) 获取布尔值</li>
 * </ul>
 * 
 * <p>在旧版本中：
 * <ul>
 *   <li>GameRules 类位于 net.minecraft.world.GameRules</li>
 *   <li>使用 getBoolean(GameRules.Key) 获取布尔值</li>
 * </ul>
 */
public final class GameRulesCompat {
    
    private GameRulesCompat() {}
    
    /**
     * 获取世界的 PvP 规则值
     * 
     * @param world 服务器世界
     * @return PvP 是否启用
     */
    public static boolean isPvpEnabled(ServerWorld world) {
        //? >=1.21.11 {
        return world.getGameRules().getValue(GameRules.PVP);
        //?} else {
        /*return world.getGameRules().getBoolean(GameRules.PVP);
        *//*?}*/
    }
    
    /**
     * 获取世界的命令方块输出规则值
     * 
     * @param world 服务器世界
     * @return 命令方块是否输出到控制台
     */
    public static boolean isCommandBlockOutputEnabled(ServerWorld world) {
        //? >=1.21.11 {
        return world.getGameRules().getValue(GameRules.COMMAND_BLOCK_OUTPUT);
        //?} else {
        /*return world.getGameRules().getBoolean(GameRules.COMMAND_BLOCK_OUTPUT);
        *//*?}*/
    }
    
    /**
     * 获取世界的命令反馈规则值
     * 
     * @param world 服务器世界
     * @return 是否发送命令反馈
     */
    public static boolean isSendCommandFeedbackEnabled(ServerWorld world) {
        //? >=1.21.11 {
        return world.getGameRules().getValue(GameRules.SEND_COMMAND_FEEDBACK);
        //?} else {
        /*return world.getGameRules().getBoolean(GameRules.SEND_COMMAND_FEEDBACK);
        *//*?}*/
    }
    
    /**
     * 获取世界的保持库存规则值
     * 
     * @param world 服务器世界
     * @return 死亡是否保留库存
     */
    public static boolean isKeepInventoryEnabled(ServerWorld world) {
        //? >=1.21.11 {
        return world.getGameRules().getValue(GameRules.KEEP_INVENTORY);
        //?} else {
        /*return world.getGameRules().getBoolean(GameRules.KEEP_INVENTORY);
        *//*?}*/
    }
}
