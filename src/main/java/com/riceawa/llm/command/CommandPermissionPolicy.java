package com.riceawa.llm.command;

import com.riceawa.llm.compat.PermissionCompat;
import net.minecraft.commands.CommandSourceStack;

public final class CommandPermissionPolicy {
    private CommandPermissionPolicy() {}

    public static boolean canEditGlobalTemplates(CommandSourceStack source) {
        return PermissionCompat.hasGamemastersPermission(source);
    }
}
