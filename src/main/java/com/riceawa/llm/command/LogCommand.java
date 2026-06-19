package com.riceawa.llm.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.riceawa.llm.compat.CommandSourceCompat;
import com.riceawa.llm.compat.PermissionCompat;
import com.riceawa.llm.config.LLMChatConfig;
import com.riceawa.llm.logging.LogConfig;
import com.riceawa.llm.logging.LogLevel;
import com.riceawa.llm.logging.LogManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * 日志管理命令
 */
public class LogCommand {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(Commands.literal("llmlog")
                .requires(PermissionCompat.requireGamemasters()) // 需要管理员权限
                .then(Commands.literal("level")
                        .then(Commands.argument("level", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    builder.suggest("DEBUG");
                                    builder.suggest("INFO");
                                    builder.suggest("WARN");
                                    builder.suggest("ERROR");
                                    return builder.buildFuture();
                                })
                                .executes(LogCommand::setLogLevel)))
                .then(Commands.literal("status")
                        .executes(LogCommand::showLogStatus))
                .then(Commands.literal("enable")
                        .then(Commands.argument("category", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    builder.suggest("system");
                                    builder.suggest("chat");
                                    builder.suggest("error");
                                    builder.suggest("performance");
                                    builder.suggest("audit");
                                    builder.suggest("file");
                                    builder.suggest("console");
                                    return builder.buildFuture();
                                })
                                .executes(LogCommand::enableCategory)))
                .then(Commands.literal("disable")
                        .then(Commands.argument("category", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    builder.suggest("system");
                                    builder.suggest("chat");
                                    builder.suggest("error");
                                    builder.suggest("performance");
                                    builder.suggest("audit");
                                    builder.suggest("file");
                                    builder.suggest("console");
                                    return builder.buildFuture();
                                })
                                .executes(LogCommand::disableCategory)))
                .then(Commands.literal("test")
                        .executes(LogCommand::testLogging))
        );
    }

    /**
     * 设置日志级别
     */
    private static int setLogLevel(CommandContext<CommandSourceStack> context) {
        String levelStr = StringArgumentType.getString(context, "level");
        LogLevel level = LogLevel.fromString(levelStr);
        
        LLMChatConfig config = LLMChatConfig.getInstance();
        LogConfig logConfig = config.getLogConfig();
        logConfig.setLogLevel(level);
        config.setLogConfig(logConfig);
        
        LogManager.getInstance().system("Log level changed to " + level.getName() + " by " + 
                context.getSource().getTextName());
        
        CommandSourceCompat.sendFeedback(context.getSource(), Component.literal("日志级别已设置为: " + level.getName()).withStyle(ChatFormatting.GREEN), true);
        
        return 1;
    }

    /**
     * 显示日志状态
     */
    private static int showLogStatus(CommandContext<CommandSourceStack> context) {
        LLMChatConfig config = LLMChatConfig.getInstance();
        LogConfig logConfig = config.getLogConfig();
        
        StringBuilder status = new StringBuilder();
        status.append("=== 日志系统状态 ===\n");
        status.append("日志级别: ").append(logConfig.getLogLevel().getName()).append("\n");
        status.append("文件日志: ").append(logConfig.isEnableFileLogging() ? "启用" : "禁用").append("\n");
        status.append("控制台日志: ").append(logConfig.isEnableConsoleLogging() ? "启用" : "禁用").append("\n");
        status.append("JSON格式: ").append(logConfig.isEnableJsonFormat() ? "启用" : "禁用").append("\n");
        status.append("异步日志: ").append(logConfig.isEnableAsyncLogging() ? "启用" : "禁用").append("\n");
        status.append("最大文件大小: ").append(logConfig.getMaxFileSize() / 1024 / 1024).append("MB\n");
        status.append("备份文件数: ").append(logConfig.getMaxBackupFiles()).append("\n");
        status.append("保留天数: ").append(logConfig.getRetentionDays()).append("\n");
        status.append("\n日志类别状态:\n");
        status.append("系统日志: ").append(logConfig.isEnableSystemLog() ? "启用" : "禁用").append("\n");
        status.append("聊天日志: ").append(logConfig.isEnableChatLog() ? "启用" : "禁用").append("\n");
        status.append("错误日志: ").append(logConfig.isEnableErrorLog() ? "启用" : "禁用").append("\n");
        status.append("性能日志: ").append(logConfig.isEnablePerformanceLog() ? "启用" : "禁用").append("\n");
        status.append("审计日志: ").append(logConfig.isEnableAuditLog() ? "启用" : "禁用").append("\n");
        
        CommandSourceCompat.sendFeedback(context.getSource(), Component.literal(status.toString()).withStyle(ChatFormatting.AQUA), false);
        
        return 1;
    }

    /**
     * 启用日志类别
     */
    private static int enableCategory(CommandContext<CommandSourceStack> context) {
        String category = StringArgumentType.getString(context, "category");
        
        LLMChatConfig config = LLMChatConfig.getInstance();
        LogConfig logConfig = config.getLogConfig();
        
        String message;

        switch (category.toLowerCase()) {
            case "system":
                logConfig.setEnableSystemLog(true);
                message = "系统日志已启用";
                break;
            case "chat":
                logConfig.setEnableChatLog(true);
                message = "聊天日志已启用";
                break;
            case "error":
                logConfig.setEnableErrorLog(true);
                message = "错误日志已启用";
                break;
            case "performance":
                logConfig.setEnablePerformanceLog(true);
                message = "性能日志已启用";
                break;
            case "audit":
                logConfig.setEnableAuditLog(true);
                message = "审计日志已启用";
                break;
            case "file":
                logConfig.setEnableFileLogging(true);
                message = "文件日志已启用";
                break;
            case "console":
                logConfig.setEnableConsoleLogging(true);
                message = "控制台日志已启用";
                break;
            default:
                CommandSourceCompat.sendFeedback(context.getSource(), Component.literal("未知的日志类别: " + category).withStyle(ChatFormatting.RED), false);
                return 0;
        }

        config.setLogConfig(logConfig);
        LogManager.getInstance().system("Log category " + category + " enabled by " +
                context.getSource().getTextName());

        final String finalMessage = message;
        CommandSourceCompat.sendFeedback(context.getSource(), Component.literal(finalMessage).withStyle(ChatFormatting.GREEN), true);
        
        return 1;
    }

    /**
     * 禁用日志类别
     */
    private static int disableCategory(CommandContext<CommandSourceStack> context) {
        String category = StringArgumentType.getString(context, "category");
        
        LLMChatConfig config = LLMChatConfig.getInstance();
        LogConfig logConfig = config.getLogConfig();
        
        String message;

        switch (category.toLowerCase()) {
            case "system":
                logConfig.setEnableSystemLog(false);
                message = "系统日志已禁用";
                break;
            case "chat":
                logConfig.setEnableChatLog(false);
                message = "聊天日志已禁用";
                break;
            case "error":
                logConfig.setEnableErrorLog(false);
                message = "错误日志已禁用";
                break;
            case "performance":
                logConfig.setEnablePerformanceLog(false);
                message = "性能日志已禁用";
                break;
            case "audit":
                logConfig.setEnableAuditLog(false);
                message = "审计日志已禁用";
                break;
            case "file":
                logConfig.setEnableFileLogging(false);
                message = "文件日志已禁用";
                break;
            case "console":
                logConfig.setEnableConsoleLogging(false);
                message = "控制台日志已禁用";
                break;
            default:
                CommandSourceCompat.sendFeedback(context.getSource(), Component.literal("未知的日志类别: " + category).withStyle(ChatFormatting.RED), false);
                return 0;
        }

        config.setLogConfig(logConfig);
        LogManager.getInstance().system("Log category " + category + " disabled by " +
                context.getSource().getTextName());

        final String finalMessage = message;
        CommandSourceCompat.sendFeedback(context.getSource(), Component.literal(finalMessage).withStyle(ChatFormatting.GREEN), true);
        
        return 1;
    }

    /**
     * 测试日志记录
     */
    private static int testLogging(CommandContext<CommandSourceStack> context) {
        LogManager logManager = LogManager.getInstance();
        String executor = context.getSource().getTextName();
        
        logManager.system("Test log message from " + executor + " - System");
        logManager.chat("Test log message from " + executor + " - Chat");
        logManager.error("Test log message from " + executor + " - Error");
        logManager.performance("Test log message from " + executor + " - Performance", null);
        logManager.audit("Test log message from " + executor + " - Audit", null);
        
        CommandSourceCompat.sendFeedback(context.getSource(), Component.literal("测试日志已记录，请检查日志文件").withStyle(ChatFormatting.GREEN), false);
        
        return 1;
    }
}
