package com.riceawa.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for ServerPlayer and related classes.
 * This is needed because Minecraft 1.21.11 removed some public methods.
 */
@Mixin(ServerPlayer.class)
public interface ServerPlayerEntityAccessor {

    /**
     * Access the private server field in ServerPlayer.
     * @return The MinecraftServer instance
     */
    @Accessor("server")
    MinecraftServer getServerInstance();
}
