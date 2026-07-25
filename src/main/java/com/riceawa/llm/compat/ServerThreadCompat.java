package com.riceawa.llm.compat;

import net.minecraft.server.MinecraftServer;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Stable entry point for scheduling work on the Minecraft server thread.
 */
public final class ServerThreadCompat {
    private ServerThreadCompat() {
    }

    public static CompletableFuture<Void> execute(MinecraftServer server, Runnable runnable) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(runnable, "runnable");

        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            server.execute(() -> {
                try {
                    runnable.run();
                    future.complete(null);
                } catch (Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
            });
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
        return future;
    }
}
