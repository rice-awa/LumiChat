package com.riceawa.llm.util;

import com.riceawa.llm.compat.PermissionCompat;
import com.riceawa.mixin.ServerPlayerEntityAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class for accessing Entity world and server references in Minecraft 1.21.11.
 * 
 * In Minecraft 1.21.11, several methods were removed from Entity/Player:
 * - getWorld() / getEntityLevel() - removed
 * - getServer() - removed from Player
 * - getPos() - removed
 * - hasPermissionLevel() - removed from CommandSourceStack
 * 
 * This utility provides alternative ways to access these values.
 */
public final class EntityHelper {
    
    private EntityHelper() {} // Prevent instantiation
    
    /**
     * Get the ServerLevel from a ServerPlayer.
     * Uses the server reference to get the overworld as context for the command source.
     */
    public static ServerLevel getServerWorld(ServerPlayer player) {
        return player.createCommandSourceStack().getLevel();
    }
    
    /**
     * Get the MinecraftServer from a ServerPlayer.
     * Uses the private server field via Mixin accessor.
     */
    public static MinecraftServer getServer(ServerPlayer player) {
        return ((ServerPlayerEntityAccessor) player).getServerInstance();
    }
    
    /**
     * Get the MinecraftServer from any Player.
     * Returns null if the player is not a ServerPlayer.
     */
    @Nullable
    public static MinecraftServer getServerSafe(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            return getServer(serverPlayer);
        }
        return null;
    }
    
    /**
     * Get the Level from any Player.
     * For ServerPlayer, uses the server world.
     * For client players, returns null.
     */
    @Nullable
    public static Level getWorld(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            return getServerWorld(serverPlayer);
        }
        // For client-side players, we cannot reliably get the world
        return null;
    }
    
    /**
     * Get the entity's position as a Vec3.
     * In 1.21.11, Entity.getPos() was removed.
     * Use getEyePosition() or construct from getX(), getY(), getZ().
     */
    public static Vec3 getPos(Entity entity) {
        return entity.getEyePosition();
    }
    
    /**
     * Get the entity's exact position (feet position).
     * Uses getX(), getY(), getZ() instead of removed getPos().
     */
    public static Vec3 getExactPos(Entity entity) {
        return new Vec3(entity.getX(), entity.getY(), entity.getZ());
    }
    
    /**
     * Check if a player has OP permission.
     * Uses the player's permission predicate from their command source.
     */
    public static boolean isOperator(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            CommandSourceStack source = serverPlayer.createCommandSourceStack();
            return hasPermissionLevel(source, 2);
        }
        return false;
    }
    
    /**
     * Check if a CommandSourceStack has a specific permission level.
     * Delegates to PermissionCompat for version compatibility.
     */
    public static boolean hasPermissionLevel(CommandSourceStack source, int level) {
        return PermissionCompat.hasPermissionLevel(source, level);
    }
    
    /**
     * Get a ServerLevel from a Player, with proper type checking.
     * Returns null if the player is not in a server world.
     */
    @Nullable
    public static ServerLevel getServerWorldSafe(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            try {
                return getServerWorld(serverPlayer);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
