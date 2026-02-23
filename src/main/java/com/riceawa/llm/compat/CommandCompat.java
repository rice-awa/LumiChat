package com.riceawa.llm.compat;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;

/**
 * 命令执行兼容层
 * 统一处理不同 Minecraft 版本的命令执行 API 差异
 * 
 * <p>在 1.21.11+ 中：
 * <ul>
 *   <li>新增 parseAndExecute() 方法用于执行命令</li>
 * </ul>
 * 
 * <p>在旧版本中：
 * <ul>
 *   <li>使用 getDispatcher().execute() 执行命令</li>
 * </ul>
 */
public final class CommandCompat {
    
    private CommandCompat() {}
    
    /**
     * 执行命令并返回结果码
     * 
     * @param server Minecraft 服务器实例
     * @param source 命令源
     * @param command 要执行的命令（不带斜杠）
     * @return 命令执行结果码，0 表示失败，正数表示成功
     */
    public static int executeCommand(MinecraftServer server, ServerCommandSource source, String command) {
        //? >=1.21.11 {
        // 优先尝试使用 parseAndExecute
        try {
            server.getCommandManager().parseAndExecute(source, command);
            return 1; // parseAndExecute 没有返回值，成功则返回 1
        } catch (Exception e) {
            // 如果 parseAndExecute 失败，回退到 dispatcher.execute
            try {
                return server.getCommandManager().getDispatcher().execute(command, source);
            } catch (CommandSyntaxException ex) {
                return 0;
            }
        }
        //?} else {
        /*try {
            return server.getCommandManager().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            return 0;
        }
        *//*?}*/
    }
    
    /**
     * 执行命令并捕获输出
     * 使用自定义的 CommandOutput 捕获命令输出
     * 
     * @param server Minecraft 服务器实例
     * @param outputCapture 输出捕获器
     * @param command 要执行的命令（不带斜杠）
     * @return 命令执行结果码
     */
    public static int executeCommandWithOutput(
            MinecraftServer server, 
            CommandOutput outputCapture, 
            String command) {
        ServerCommandSource captureSource = server.getCommandSource().withOutput(outputCapture);
        
        //? >=1.21.11 {
        try {
            // 首先尝试使用 dispatcher.execute 获取返回值
            return server.getCommandManager().getDispatcher().execute(command, captureSource);
        } catch (CommandSyntaxException e) {
            return 0;
        } catch (Exception e) {
            // 如果失败，尝试使用 parseAndExecute
            try {
                server.getCommandManager().parseAndExecute(captureSource, command);
                return 1;
            } catch (Exception ex) {
                return 0;
            }
        }
        //?} else {
        /*try {
            return server.getCommandManager().getDispatcher().execute(command, captureSource);
        } catch (CommandSyntaxException e) {
            return 0;
        }
        *//*?}*/
    }
}
