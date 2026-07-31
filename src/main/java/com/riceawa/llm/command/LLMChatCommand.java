package com.riceawa.llm.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;

public class LLMChatCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        LogCommand.register(dispatcher, registryAccess);

        HistoryCommand.register(dispatcher, registryAccess);

        LiteralArgumentBuilder<CommandSourceStack> root = ChatCommands.build();
        root.then(TemplateCommands.build());
        root.then(ProviderCommands.build());
        root.then(ModelCommands.build());
        root.then(BroadcastCommands.build());
        root.then(ChatModeCommands.build());

        dispatcher.register(root);
    }
}
