package com.riceawa.llm.compat;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
//? >=1.21.11 {
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
//?}

/**
 * 命令执行兼容层
 * 统一处理不同 Minecraft 版本的命令执行 API 差异
 * 
 * <p>在 1.21.11+ 中：
 * <ul>
 *   <li>新增 performPrefixedCommand() 方法用于执行命令</li>
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
    public static int executeCommand(MinecraftServer server, CommandSourceStack source, String command) {
        //? >=1.21.11 {
        AtomicBoolean completed = new AtomicBoolean();
        AtomicBoolean succeeded = new AtomicBoolean();
        AtomicInteger resultCode = new AtomicInteger();
        CommandSourceStack callbackSource = source.withCallback((success, result) -> {
            completed.set(true);
            succeeded.set(success);
            resultCode.set(result);
        });
        server.getCommands().performPrefixedCommand(callbackSource, command);
        return resultCodeForCallback(completed.get(), succeeded.get(), resultCode.get());
        //?} else {
        /*try {
            return server.getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            return 0;
        }
        *//*?}*/
    }

    //? >=1.21.11 {
    static int resultCodeForCallback(boolean completed, boolean succeeded, int resultCode) {
        return completed && succeeded ? resultCode : 0;
    }
    //?}
    
    /**
     * 执行命令并捕获输出
     * 使用自定义的 CommandSource 捕获命令输出
     * 
     * @param server Minecraft 服务器实例
     * @param outputCapture 输出捕获器
     * @param command 要执行的命令（不带斜杠）
     * @return 命令执行结果码
     */
    public static int executeCommandWithOutput(
            MinecraftServer server,
            CommandSource outputCapture,
            String command) {
        CommandSourceStack captureSource = server.createCommandSourceStack().withSource(outputCapture);

        //? >=1.21.11 {
        AtomicBoolean completed = new AtomicBoolean();
        AtomicBoolean succeeded = new AtomicBoolean();
        AtomicInteger resultCode = new AtomicInteger();
        CommandSourceStack callbackSource = captureSource.withCallback((success, result) -> {
            completed.set(true);
            succeeded.set(success);
            resultCode.set(result);
        });
        server.getCommands().performPrefixedCommand(callbackSource, command);
        return resultCodeForCallback(completed.get(), succeeded.get(), resultCode.get());
        //?} else {
        /*try {
            return server.getCommands().getDispatcher().execute(command, captureSource);
        } catch (CommandSyntaxException e) {
            return 0;
        }
        *//*?}*/
    }
}
