package com.riceawa.llm.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public final class MessageCompat {
    private MessageCompat() {
    }

    public static void displayClientMessage(Player player, Component message, boolean overlay) {
        //? >=26.1 {
        /*if (overlay) {
            player.sendOverlayMessage(message);
        } else {
            player.sendSystemMessage(message);
        }
        *///?} else {
        player.displayClientMessage(message, overlay);
        //?}
    }
}
