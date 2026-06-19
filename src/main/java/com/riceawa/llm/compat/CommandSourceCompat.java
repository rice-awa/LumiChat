package com.riceawa.llm.compat;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

public final class CommandSourceCompat {
    private CommandSourceCompat() {
    }

    public static void sendFeedback(CommandSourceStack source, Component message) {
        sendFeedback(source, message, false);
    }

    public static void sendFeedback(CommandSourceStack source, Component message, boolean broadcastToOps) {
        //? if >=1.20 {
        source.sendSuccess(() -> message, broadcastToOps);
        //?} else {
        /*source.sendSuccess(message, broadcastToOps);
        *///?}
    }
}
