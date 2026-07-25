package com.riceawa.llm.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.riceawa.llm.compat.MessageCompat;
import com.riceawa.llm.config.LLMChatConfig;
import com.riceawa.llm.config.Provider;
import com.riceawa.llm.config.ProviderManager;
import com.riceawa.llm.service.LLMServiceManager;
import com.riceawa.llm.service.ProviderHealthChecker;
import com.riceawa.llm.util.EntityHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.List;

public final class ProviderCommands {
    private ProviderCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("provider")
                .then(Commands.literal("list")
                        .executes(ProviderCommands::handleListProviders))
                .then(Commands.literal("switch")
                        .then(Commands.argument("provider", StringArgumentType.word())
                                .executes(ProviderCommands::handleSwitchProvider)))
                .then(Commands.literal("check")
                        .executes(ProviderCommands::handleCheckProviders)
                        .then(Commands.argument("provider", StringArgumentType.word())
                                .executes(ProviderCommands::handleCheckSpecificProvider)))
                .then(Commands.literal("help")
                        .executes(ProviderCommands::handleProviderHelp));
    }

    private static int handleListProviders(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        LLMChatConfig config = LLMChatConfig.getInstance();
        ProviderManager providerManager =
            new ProviderManager(config.getProviders());

        MessageCompat.displayClientMessage(player, Component.literal("🔍 正在检测Provider状态...").withStyle(ChatFormatting.YELLOW), false);

        List<Provider> providers = config.getProviders();
        if (providers.isEmpty()) {
            MessageCompat.displayClientMessage(player, Component.literal("  没有配置任何providers").withStyle(ChatFormatting.RED), false);
            return 1;
        }

        providerManager.getDetailedConfigurationReport().whenComplete((report, throwable) -> {
            if (throwable != null) {
                MessageCompat.displayClientMessage(player, Component.literal("❌ 获取Provider状态失败: " + throwable.getMessage())
                    .withStyle(ChatFormatting.RED), false);
                showBasicProviderList(player, config, providers);
            } else {
                MessageCompat.displayClientMessage(player, Component.literal("📡 Provider状态报告:").withStyle(ChatFormatting.AQUA), false);
                String[] lines = report.getReportText().split("\n");
                for (String line : lines) {
                    if (!line.trim().isEmpty()) {
                        ChatFormatting color = ChatFormatting.WHITE;
                        if (line.contains("🟢")) color = ChatFormatting.GREEN;
                        else if (line.contains("🔴")) color = ChatFormatting.RED;
                        else if (line.contains("⚠️")) color = ChatFormatting.YELLOW;
                        else if (line.contains("✅")) color = ChatFormatting.GREEN;

                        MessageCompat.displayClientMessage(player, Component.literal(line).withStyle(color), false);
                    }
                }

                String currentProvider = config.getCurrentProvider();
                if (!currentProvider.isEmpty()) {
                    MessageCompat.displayClientMessage(player, Component.literal(""), false);
                    MessageCompat.displayClientMessage(player, Component.literal("📌 当前使用: " + currentProvider + " / " + config.getCurrentModel())
                        .withStyle(ChatFormatting.AQUA), false);
                }
            }
        });

        return 1;
    }

    private static void showBasicProviderList(Player player, LLMChatConfig config, List<Provider> providers) {
        LLMServiceManager serviceManager = LLMServiceManager.getInstance();
        String currentProvider = config.getCurrentProvider();

        for (Provider provider : providers) {
            String prefix = provider.getName().equals(currentProvider) ? "* " : "  ";
            boolean available = serviceManager.isServiceAvailable(provider.getName());
            String status = available ? "可用" : "不可用";
            ChatFormatting color = available ?
                (provider.getName().equals(currentProvider) ? ChatFormatting.GREEN : ChatFormatting.WHITE) :
                ChatFormatting.RED;

            MessageCompat.displayClientMessage(player, Component.literal(prefix + provider.getName() + " (" + status + ") - " + provider.getApiBaseUrl())
                    .withStyle(color), false);
        }
    }

    private static int handleSwitchProvider(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        if (!EntityHelper.hasPermissionLevel(source, 2)) {
            MessageCompat.displayClientMessage(player, Component.literal("只有OP可以切换API提供商").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        String providerName = StringArgumentType.getString(context, "provider");
        LLMChatConfig config = LLMChatConfig.getInstance();
        LLMServiceManager serviceManager = LLMServiceManager.getInstance();

        Provider provider = config.getProvider(providerName);
        if (provider == null) {
            MessageCompat.displayClientMessage(player, Component.literal("Provider不存在: " + providerName).withStyle(ChatFormatting.RED), false);
            return 0;
        }

        List<String> supportedModels = config.getSupportedModels(providerName);
        if (supportedModels.isEmpty()) {
            MessageCompat.displayClientMessage(player, Component.literal("无法切换到 " + providerName + "：该provider没有配置任何模型").withStyle(ChatFormatting.RED), false);
            return 0;
        }

        String defaultModel = supportedModels.get(0);

        if (serviceManager.switchToProvider(providerName, defaultModel)) {
            MessageCompat.displayClientMessage(player, Component.literal("已切换到provider: " + providerName).withStyle(ChatFormatting.GREEN), false);
            MessageCompat.displayClientMessage(player, Component.literal("默认模型已设置为: " + defaultModel).withStyle(ChatFormatting.GRAY), false);
        } else {
            MessageCompat.displayClientMessage(player, Component.literal("切换失败，provider配置无效: " + providerName).withStyle(ChatFormatting.RED), false);
            return 0;
        }

        return 1;
    }

    private static int handleCheckProviders(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        LLMChatConfig config = LLMChatConfig.getInstance();
        ProviderManager providerManager =
            new ProviderManager(config.getProviders());

        List<Provider> providers = config.getProviders();
        if (providers.isEmpty()) {
            MessageCompat.displayClientMessage(player, Component.literal("❌ 没有配置任何providers").withStyle(ChatFormatting.RED), false);
            return 1;
        }

        MessageCompat.displayClientMessage(player, Component.literal("🔍 正在强制检测所有Provider状态...").withStyle(ChatFormatting.YELLOW), false);
        MessageCompat.displayClientMessage(player, Component.literal("⏱️ 检测超时时间已提高到30秒，请耐心等待").withStyle(ChatFormatting.GRAY), false);

        providerManager.clearHealthCache();

        providerManager.checkAllProvidersHealth().whenComplete((healthMap, throwable) -> {
            if (throwable != null) {
                MessageCompat.displayClientMessage(player, Component.literal("❌ 强制检测失败: " + throwable.getMessage())
                    .withStyle(ChatFormatting.RED), false);
                return;
            }

            MessageCompat.displayClientMessage(player, Component.literal("").withStyle(ChatFormatting.GRAY), false);
            MessageCompat.displayClientMessage(player, Component.literal("📡 强制检测结果:").withStyle(ChatFormatting.AQUA), false);

            int onlineCount = 0;
            int totalCount = providers.size();

            for (Provider provider : providers) {
                ProviderHealthChecker.HealthStatus health = healthMap.get(provider.getName());
                String status;
                ChatFormatting color;

                if (health != null) {
                    if (health.isHealthy()) {
                        status = "🟢 在线";
                        color = ChatFormatting.GREEN;
                        onlineCount++;
                    } else {
                        status = "🔴 离线 - " + health.getMessage();
                        color = ChatFormatting.RED;
                    }

                    String checkTime = health.getFormattedCheckTime();
                    MessageCompat.displayClientMessage(player, Component.literal("  " + provider.getName() + ": " + status + " (检测时间: " + checkTime + ")")
                        .withStyle(color), false);
                } else {
                    MessageCompat.displayClientMessage(player, Component.literal("  " + provider.getName() + ": ❓ 检测失败")
                        .withStyle(ChatFormatting.GRAY), false);
                }
            }

            MessageCompat.displayClientMessage(player, Component.literal("").withStyle(ChatFormatting.GRAY), false);
            MessageCompat.displayClientMessage(player, Component.literal("📊 检测汇总: " + onlineCount + "/" + totalCount + " 个Provider在线")
                .withStyle(onlineCount > 0 ? ChatFormatting.GREEN : ChatFormatting.RED), false);

            if (onlineCount == 0) {
                MessageCompat.displayClientMessage(player, Component.literal("⚠️ 所有Provider都离线，请检查网络连接和API密钥配置")
                    .withStyle(ChatFormatting.YELLOW), false);
            }
        });

        return 1;
    }

    private static int handleCheckSpecificProvider(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        String providerName = StringArgumentType.getString(context, "provider");
        LLMChatConfig config = LLMChatConfig.getInstance();

        Provider provider = config.getProvider(providerName);
        if (provider == null) {
            MessageCompat.displayClientMessage(player, Component.literal("❌ Provider不存在: " + providerName).withStyle(ChatFormatting.RED), false);
            return 0;
        }

        ProviderManager providerManager =
            new ProviderManager(config.getProviders());

        MessageCompat.displayClientMessage(player, Component.literal("🔍 正在强制检测Provider: " + providerName + "...").withStyle(ChatFormatting.YELLOW), false);
        MessageCompat.displayClientMessage(player, Component.literal("⏱️ 检测超时时间已提高到30秒，请耐心等待").withStyle(ChatFormatting.GRAY), false);

        ProviderHealthChecker.getInstance().clearCache(providerName);

        providerManager.checkProviderHealth(providerName).whenComplete((health, throwable) -> {
            if (throwable != null) {
                MessageCompat.displayClientMessage(player, Component.literal("❌ 检测失败: " + throwable.getMessage())
                    .withStyle(ChatFormatting.RED), false);
                return;
            }

            MessageCompat.displayClientMessage(player, Component.literal("").withStyle(ChatFormatting.GRAY), false);
            MessageCompat.displayClientMessage(player, Component.literal("📡 检测结果:").withStyle(ChatFormatting.AQUA), false);

            String status;
            ChatFormatting color;

            if (health.isHealthy()) {
                status = "🟢 在线";
                color = ChatFormatting.GREEN;
                MessageCompat.displayClientMessage(player, Component.literal("  " + providerName + ": " + status).withStyle(color), false);
                MessageCompat.displayClientMessage(player, Component.literal("  检测时间: " + health.getFormattedCheckTime()).withStyle(ChatFormatting.GRAY), false);
                MessageCompat.displayClientMessage(player, Component.literal("  ✅ Provider工作正常，可以正常使用").withStyle(ChatFormatting.GREEN), false);
            } else {
                status = "🔴 离线";
                color = ChatFormatting.RED;
                MessageCompat.displayClientMessage(player, Component.literal("  " + providerName + ": " + status).withStyle(color), false);
                MessageCompat.displayClientMessage(player, Component.literal("  检测时间: " + health.getFormattedCheckTime()).withStyle(ChatFormatting.GRAY), false);
                MessageCompat.displayClientMessage(player, Component.literal("  ❌ 错误信息: " + health.getMessage()).withStyle(ChatFormatting.RED), false);

                switch (health.getErrorType()) {
                    case AUTH_ERROR:
                        MessageCompat.displayClientMessage(player, Component.literal("  💡 建议: 检查API密钥是否正确配置").withStyle(ChatFormatting.YELLOW), false);
                        break;
                    case NETWORK_ERROR:
                        MessageCompat.displayClientMessage(player, Component.literal("  💡 建议: 检查网络连接和防火墙设置").withStyle(ChatFormatting.YELLOW), false);
                        break;
                    case CONFIG_ERROR:
                        MessageCompat.displayClientMessage(player, Component.literal("  💡 建议: 检查Provider配置是否完整").withStyle(ChatFormatting.YELLOW), false);
                        break;
                    case RATE_LIMIT_ERROR:
                        MessageCompat.displayClientMessage(player, Component.literal("  💡 建议: API调用频率过高，请稍后再试").withStyle(ChatFormatting.YELLOW), false);
                        break;
                    case MODEL_ERROR:
                        MessageCompat.displayClientMessage(player, Component.literal("  💡 建议: 检查模型名称是否正确").withStyle(ChatFormatting.YELLOW), false);
                        break;
                    case API_ERROR:
                        MessageCompat.displayClientMessage(player, Component.literal("  💡 建议: 检查API服务状态").withStyle(ChatFormatting.YELLOW), false);
                        break;
                    default:
                        MessageCompat.displayClientMessage(player, Component.literal("  💡 建议: 请检查配置文件和网络连接").withStyle(ChatFormatting.YELLOW), false);
                        break;
                }
            }
        });

        return 1;
    }

    private static int handleProviderHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        MessageCompat.displayClientMessage(player, Component.literal("=== AI服务提供商管理 ===").withStyle(ChatFormatting.GOLD), false);
        MessageCompat.displayClientMessage(player, Component.literal(""), false);
        MessageCompat.displayClientMessage(player, Component.literal("📡 可用命令:").withStyle(ChatFormatting.AQUA), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat provider list - 列出所有配置的AI服务提供商").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat provider switch <provider> - 切换到指定的服务提供商 (仅OP)").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat provider check - 强制检测所有Provider状态").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  /llmchat provider check <provider> - 强制检测指定Provider状态").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal(""), false);
        MessageCompat.displayClientMessage(player, Component.literal("🔍 健康检查功能:").withStyle(ChatFormatting.AQUA), false);
        MessageCompat.displayClientMessage(player, Component.literal("  • 检测超时时间: 30秒 (已提高)").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("  • 强制检测会清除缓存，获取最新状态").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("  • 显示详细的错误信息和解决建议").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("  • 支持错误类型分类: 配置、认证、网络、API等").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal(""), false);
        MessageCompat.displayClientMessage(player, Component.literal("💡 说明:").withStyle(ChatFormatting.YELLOW), false);
        MessageCompat.displayClientMessage(player, Component.literal("  • 支持多个AI服务: OpenAI, OpenRouter, DeepSeek等").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("  • 每个provider需要配置API密钥和支持的模型").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("  • 切换provider会自动设置为该provider的第一个模型").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("  • list命令会显示缓存的健康状态，check命令强制重新检测").withStyle(ChatFormatting.GRAY), false);

        return 1;
    }
}
