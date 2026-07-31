package com.riceawa.llm.function.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.riceawa.llm.compat.MessageCompat;
import com.riceawa.llm.compat.PlayerCompat;
import com.riceawa.llm.function.LLMFunction;
import com.riceawa.llm.function.PermissionHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.ChatFormatting;

/**
 * 发送消息给其他玩家的函数
 */
public class SendMessageFunction implements LLMFunction {
    
    @Override
    public String getName() {
        return "send_message";
    }
    
    @Override
    public String getDescription() {
        return "向指定玩家或所有玩家发送消息";
    }
    
    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        JsonObject required = new JsonObject();
        
        // 必需参数：消息内容
        JsonObject message = new JsonObject();
        message.addProperty("type", "string");
        message.addProperty("description", "要发送的消息内容");
        message.addProperty("minLength", 1);
        message.addProperty("maxLength", 512);
        properties.add("message", message);
        
        // 可选参数：目标玩家
        JsonObject target = new JsonObject();
        target.addProperty("type", "string");
        target.addProperty("description", "目标玩家名称；不填则发送给自己，all表示向所有玩家广播（需要OP权限）");
        properties.add("target", target);
        
        // 可选参数：消息类型
        JsonObject messageType = new JsonObject();
        messageType.addProperty("type", "string");
        messageType.addProperty("description", "消息类型：chat(聊天), system(系统消息), actionbar(动作栏)");
        JsonArray messageTypes = new JsonArray();
        messageTypes.add("chat");
        messageTypes.add("system");
        messageTypes.add("actionbar");
        messageType.add("enum", messageTypes);
        messageType.addProperty("default", "chat");
        properties.add("message_type", messageType);
        
        schema.add("properties", properties);
        schema.addProperty("additionalProperties", false);
        schema.add("required", new com.google.gson.JsonArray());
        schema.getAsJsonArray("required").add("message");
        
        return schema;
    }
    
    @Override
    public FunctionResult execute(Player player, MinecraftServer server, JsonObject arguments) {
        try {
            // 获取必需参数
            if (!arguments.has("message")) {
                return FunctionResult.error("缺少必需参数: message");
            }
            
            String messageContent = arguments.get("message").getAsString();
            if (!isValidMessageContent(messageContent)) {
                return FunctionResult.error("消息长度必须在1到512个字符之间");
            }
            
            String target = arguments.has("target") ? 
                arguments.get("target").getAsString() : null;
            String messageType = arguments.has("message_type") ?
                arguments.get("message_type").getAsString() : "chat";
            if (!isSupportedMessageType(messageType)) {
                return FunctionResult.error("不支持的消息类型");
            }
            
            // 构建消息
            Component messageComponent = buildMessage(player, messageContent, messageType);
            
            if ("all".equals(target)) {
                // 向所有在线玩家发送消息 - 需要OP权限
                if (!PermissionHelper.canSendBroadcast(player)) {
                    return FunctionResult.error(PermissionHelper.getPermissionErrorMessage("向所有玩家发送消息"));
                }

                for (ServerPlayer onlinePlayer : server.getPlayerList().getPlayers()) {
                    sendMessageToPlayer(onlinePlayer, messageComponent, messageType);
                }

                return FunctionResult.success("消息已发送给所有在线玩家 (" +
                    server.getPlayerCount() + " 人)");

            } else if (target == null || target.trim().isEmpty()) {
                sendMessageToPlayer((ServerPlayer) player, messageComponent, messageType);
                return FunctionResult.success("消息已发送给自己");

            } else {
                // 发送给指定玩家
                ServerPlayer targetPlayer = PlayerCompat.getPlayerByName(server, target);
                if (targetPlayer == null) {
                    return FunctionResult.error("目标玩家不可用");
                }
                if (!canSendToTarget(PermissionHelper.isOperator(player), targetPlayer.equals(player))) {
                    return FunctionResult.error(PermissionHelper.getPermissionErrorMessage("向其他玩家发送消息"));
                }

                sendMessageToPlayer(targetPlayer, messageComponent, messageType);
                return FunctionResult.success("消息已发送给 " + target);
            }
            
        } catch (Exception e) {
            return FunctionResult.error("发送消息失败: " + e.getMessage());
        }
    }
    
    public static boolean isValidMessageContent(String messageContent) {
        return messageContent != null && messageContent.length() >= 1 && messageContent.length() <= 512;
    }

    public static boolean isSupportedMessageType(String messageType) {
        return "chat".equals(messageType) || "system".equals(messageType)
                || "actionbar".equals(messageType);
    }

    public static boolean canSendToTarget(boolean operator, boolean targetIsSender) {
        return operator || targetIsSender;
    }

    private Component buildMessage(Player sender, String content, String messageType) {
        String senderName = sender.getName().getString();
        
        switch (messageType.toLowerCase()) {
            case "system":
                return Component.literal("[系统] " + content).withStyle(ChatFormatting.YELLOW);
            case "actionbar":
                return Component.literal(content).withStyle(ChatFormatting.AQUA);
            case "chat":
            default:
                return Component.literal("[" + senderName + " 通过AI] " + content).withStyle(ChatFormatting.GREEN);
        }
    }
    
    private void sendMessageToPlayer(ServerPlayer player, Component message, String messageType) {
        switch (messageType.toLowerCase()) {
            case "actionbar":
                MessageCompat.displayClientMessage(player, message, true); // true = actionbar
                break;
            case "system":
            case "chat":
            default:
                MessageCompat.displayClientMessage(player, message, false); // chat/system
                break;
        }
    }
    
    @Override
    public boolean hasPermission(Player player) {
        // 所有玩家都可以使用此功能，但向所有玩家发送消息需要OP权限
        return true;
    }
    
    @Override
    public boolean isEnabled() {
        return true;
    }
    
    @Override
    public String getCategory() {
        return "interaction";
    }
}
