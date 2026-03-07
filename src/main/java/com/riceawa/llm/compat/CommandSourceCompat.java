package com.riceawa.llm.compat;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public final class CommandSourceCompat {
    private CommandSourceCompat() {
    }

    public static void sendFeedback(ServerCommandSource source, Text message, boolean broadcastToOps) {
        //? if >=1.20 {
        source.sendFeedback(() -> message, broadcastToOps);
        //?} else {
        /*source.sendFeedback(message, broadcastToOps);
        *///?}
    }
}
