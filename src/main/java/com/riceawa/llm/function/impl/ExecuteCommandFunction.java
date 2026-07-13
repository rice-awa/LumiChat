package com.riceawa.llm.function.impl;

import com.google.gson.JsonObject;
import com.riceawa.llm.compat.CommandCompat;
import com.riceawa.llm.config.LLMChatConfig;
import com.riceawa.llm.function.CommandExecutionPolicy;
import com.riceawa.llm.function.LLMFunction;
import com.riceawa.llm.function.PermissionHelper;
import com.riceawa.llm.logging.LogManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Executes explicitly allowlisted server commands as the initiating player.
 */
public class ExecuteCommandFunction implements LLMFunction {

    @Override
    public String getName() {
        return "execute_command";
    }

    @Override
    public String getDescription() {
        return "执行已由服务器管理员显式允许的Minecraft指令。仅OP可用，并以发起玩家身份执行。";
    }

    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();
        JsonObject commandParam = new JsonObject();
        commandParam.addProperty("type", "string");
        commandParam.addProperty("description", "要执行的已允许Minecraft指令（可省略开头的斜杠）。");
        properties.add("command", commandParam);
        schema.add("properties", properties);

        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
        required.add("command");
        schema.add("required", required);
        return schema;
    }

    @Override
    public FunctionResult execute(Player player, MinecraftServer server, JsonObject arguments) {
        long startTime = System.currentTimeMillis();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return FunctionResult.error("命令执行只能由服务器玩家发起");
        }
        if (arguments == null || !arguments.has("command") || arguments.get("command").isJsonNull()) {
            audit(serverPlayer, "", "", 0, startTime, false);
            return FunctionResult.error("缺少必需参数: command");
        }

        String requestedCommand;
        try {
            requestedCommand = arguments.get("command").getAsString();
        } catch (RuntimeException exception) {
            audit(serverPlayer, "", "", 0, startTime, false);
            return FunctionResult.error("command 必须是字符串");
        }

        LLMChatConfig config = LLMChatConfig.getInstance();
        CommandExecutionPolicy.Decision decision = CommandExecutionPolicy.evaluate(
                requestedCommand,
                PermissionHelper.isOperator(serverPlayer),
                config.isEnableExecuteCommand(),
                config.getExecuteCommandAllowlist(),
                config.getExecuteCommandMaxLength());
        String commandHash = sha256(requestedCommand);
        if (!decision.allowed()) {
            audit(serverPlayer, decision.commandRoot(), commandHash, 0, startTime, false);
            return FunctionResult.error(getPolicyErrorMessage(decision.reason()));
        }

        String command = CommandExecutionPolicy.normalizeCommand(requestedCommand);
        List<String> outputMessages = new ArrayList<>();
        CommandOutputCapture outputCapture = new CommandOutputCapture(outputMessages);
        CommandSourceStack source = serverPlayer.createCommandSourceStack().withSource(outputCapture);

        int resultCode = 0;
        boolean success = false;
        String error = null;
        try {
            resultCode = CommandCompat.executeCommand(server, source, command);
            success = resultCode > 0;
            if (!success) {
                error = "命令执行失败";
            }
        } catch (RuntimeException exception) {
            error = "命令执行失败";
        } finally {
            audit(serverPlayer, decision.commandRoot(), commandHash, resultCode, startTime, success);
        }

        long durationMs = System.currentTimeMillis() - startTime;
        if (!success) {
            return FunctionResult.error(error == null ? "命令执行失败" : error);
        }

        JsonObject responseData = new JsonObject();
        responseData.addProperty("success", true);
        responseData.addProperty("command_root", decision.commandRoot());
        responseData.addProperty("result_code", resultCode);
        responseData.addProperty("execution_time_ms", durationMs);
        if (!outputMessages.isEmpty()) {
            responseData.addProperty("output", String.join("\n", outputMessages));
        }
        return FunctionResult.success(createResultMessage(decision.commandRoot(), resultCode,
                outputMessages), responseData);
    }

    private static String createResultMessage(String commandRoot, int resultCode,
                                              List<String> outputMessages) {
        StringBuilder result = new StringBuilder("命令执行成功: ")
                .append(commandRoot).append(" (返回码: ").append(resultCode).append(")");
        if (!outputMessages.isEmpty()) {
            result.append("\n").append(String.join("\n", outputMessages));
        }
        return result.toString();
    }

    private static String getPolicyErrorMessage(String reason) {
        return switch (reason) {
            case "disabled" -> "命令执行功能已禁用";
            case "not_operator" -> "没有权限执行命令（需要OP权限）";
            case "allowlist_empty", "not_allowlisted" -> "该命令未在允许列表中";
            case "too_long" -> "命令长度超过允许的最大值";
            default -> "命令格式无效";
        };
    }

    private static void audit(ServerPlayer player, String commandRoot, String commandHash,
                              int resultCode, long startTime, boolean success) {
        long durationMs = System.currentTimeMillis() - startTime;
        LogManager.getInstance().audit("execute_command", Map.of(
                "actor_uuid", player.getUUID().toString(),
                "command_root", commandRoot,
                "command_sha256", commandHash,
                "result_code", resultCode,
                "duration_ms", durationMs,
                "success", success));
    }

    private static String sha256(String command) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(command.getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hash.append(Character.forDigit((value >>> 4) & 0xF, 16));
                hash.append(Character.forDigit(value & 0xF, 16));
            }
            return hash.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    @Override
    public boolean hasPermission(Player player) {
        return PermissionHelper.isOperator(player);
    }

    @Override
    public boolean isEnabled() {
        return LLMChatConfig.getInstance().isEnableExecuteCommand();
    }

    @Override
    public String getCategory() {
        return "admin";
    }

    private static class CommandOutputCapture implements net.minecraft.commands.CommandSource {
        private final List<String> outputMessages;

        private CommandOutputCapture(List<String> outputMessages) {
            this.outputMessages = outputMessages;
        }

        @Override
        public void sendSystemMessage(Component message) {
            outputMessages.add(message.getString());
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }
}
