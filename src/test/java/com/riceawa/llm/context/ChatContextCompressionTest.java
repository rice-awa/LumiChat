package com.riceawa.llm.context;

import com.riceawa.llm.core.LLMMessage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatContextCompressionTest {
    private static final long TIMEOUT_SECONDS = 5;

    @Test
    void preservesMessagesAppendedWhileCompressionRunsAndKeepsCachesAccurate() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch compressorEntered = new CountDownLatch(1);
        CountDownLatch releaseCompressor = new CountDownLatch(1);
        AtomicBoolean receivedImmutableCopy = new AtomicBoolean();
        ChatContext context = newContext(executor, messages -> {
            compressorEntered.countDown();
            await(releaseCompressor);
            assertThrows(UnsupportedOperationException.class,
                    () -> messages.add(message("mutation")));
            receivedImmutableCopy.set(true);
            return "summary";
        });

        try {
            seedCompressibleHistory(context);
            context.scheduleCompressionIfNeeded();
            await(compressorEntered);

            context.addUserMessage("tail-1");
            context.addAssistantMessage("tail-2");
            assertEquals(30, context.calculateTotalCharacters());

            releaseCompressor.countDown();
            awaitExecutor(executor);

            assertTrue(receivedImmutableCopy.get());
            assertContents(context,
                    "=== 对话历史摘要 ===\nsummary\n=== 以下是最近的对话 ===",
                    "old-222", "keep", "tail-1", "tail-2");
            assertEquals(5, context.getMessageCount());
            assertEquals(actualCharacterCount(context), context.calculateTotalCharacters());
        } finally {
            releaseCompressor.countDown();
            shutdown(executor);
        }
    }

    @Test
    void clearInvalidatesCompressionSnapshot() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch compressorEntered = new CountDownLatch(1);
        CountDownLatch releaseCompressor = new CountDownLatch(1);
        ChatContext context = newContext(executor, messages -> {
            compressorEntered.countDown();
            await(releaseCompressor);
            return "stale summary";
        });

        try {
            seedCompressibleHistory(context);
            context.scheduleCompressionIfNeeded();
            await(compressorEntered);

            context.clear();
            context.addUserMessage("fresh");
            releaseCompressor.countDown();
            awaitExecutor(executor);

            assertContents(context, "fresh");
            assertEquals(1, context.getMessageCount());
            assertEquals(5, context.calculateTotalCharacters());
        } finally {
            releaseCompressor.countDown();
            shutdown(executor);
        }
    }

    @Test
    void concurrentSchedulesStartOnlyOneCompressor() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ExecutorService callers = Executors.newFixedThreadPool(2);
        CountDownLatch compressorEntered = new CountDownLatch(1);
        CountDownLatch releaseCompressor = new CountDownLatch(1);
        CountDownLatch callersReady = new CountDownLatch(2);
        CountDownLatch startCallers = new CountDownLatch(1);
        AtomicInteger compressorCalls = new AtomicInteger();
        ChatContext context = newContext(executor, messages -> {
            compressorCalls.incrementAndGet();
            compressorEntered.countDown();
            await(releaseCompressor);
            return "summary";
        });

        try {
            seedCompressibleHistory(context);
            Future<?> first = callers.submit(() -> scheduleAfterGate(context, callersReady, startCallers));
            Future<?> second = callers.submit(() -> scheduleAfterGate(context, callersReady, startCallers));
            await(callersReady);
            startCallers.countDown();
            first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            await(compressorEntered);
            assertEquals(1, compressorCalls.get());

            Field compressionState = ChatContext.class.getDeclaredField("compressionInProgress");
            assertEquals(AtomicBoolean.class, compressionState.getType());

            releaseCompressor.countDown();
            awaitExecutor(executor);
            assertEquals(1, compressorCalls.get());
        } finally {
            startCallers.countDown();
            releaseCompressor.countDown();
            try {
                shutdown(callers);
            } finally {
                shutdown(executor);
            }
        }
    }

    @Test
    void compressorExceptionAndEmptyResultFallbackToLatestTail() throws Exception {
        for (boolean throwException : List.of(true, false)) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            CountDownLatch compressorEntered = new CountDownLatch(1);
            CountDownLatch releaseCompressor = new CountDownLatch(1);
            ChatContext context = newContext(executor, messages -> {
                compressorEntered.countDown();
                await(releaseCompressor);
                if (throwException) {
                    throw new IllegalStateException("expected test failure");
                }
                return "   ";
            });

            try {
                seedCompressibleHistory(context);
                context.scheduleCompressionIfNeeded();
                await(compressorEntered);
                context.addUserMessage("tail");
                releaseCompressor.countDown();
                awaitExecutor(executor);

                assertContents(context, "old-222", "keep", "tail");
                assertEquals(3, context.getMessageCount());
                assertEquals(actualCharacterCount(context), context.calculateTotalCharacters());
            } finally {
                releaseCompressor.countDown();
                shutdown(executor);
            }
        }
    }

    @Test
    void systemUpdateInvalidatesFallbackSnapshot() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch compressorEntered = new CountDownLatch(1);
        CountDownLatch releaseCompressor = new CountDownLatch(1);
        ChatContext context = newContext(executor, messages -> {
            compressorEntered.countDown();
            await(releaseCompressor);
            return "";
        });

        try {
            context.addSystemMessage("old-system");
            seedCompressibleHistory(context);
            context.scheduleCompressionIfNeeded();
            await(compressorEntered);

            context.updateSystemMessage("new-system");
            context.addUserMessage("tail");
            releaseCompressor.countDown();
            awaitExecutor(executor);

            assertContents(context, "new-system", "old-111", "old-222", "keep", "tail");
            assertEquals(actualCharacterCount(context), context.calculateTotalCharacters());
        } finally {
            releaseCompressor.countDown();
            shutdown(executor);
        }
    }

    @Test
    void rejectedExecutionResetsCompressionState() {
        AtomicInteger submissions = new AtomicInteger();
        AtomicInteger compressorCalls = new AtomicInteger();
        Executor rejectFirst = command -> {
            if (submissions.getAndIncrement() == 0) {
                throw new RejectedExecutionException("expected rejection");
            }
            command.run();
        };
        ChatContext context = newContext(rejectFirst, messages -> {
            compressorCalls.incrementAndGet();
            return "summary";
        });
        seedCompressibleHistory(context);

        assertThrows(RejectedExecutionException.class, context::scheduleCompressionIfNeeded);
        context.scheduleCompressionIfNeeded();

        assertEquals(2, submissions.get());
        assertEquals(1, compressorCalls.get());
    }

    private static ChatContext newContext(Executor executor, ContextCompressor compressor) {
        return new ChatContext(UUID.randomUUID(), "default", 12, executor, compressor);
    }

    private static void seedCompressibleHistory(ChatContext context) {
        context.addUserMessage("old-111");
        context.addAssistantMessage("old-222");
        context.addUserMessage("keep");
    }

    private static LLMMessage message(String content) {
        return new LLMMessage(LLMMessage.MessageRole.USER, content);
    }

    private static void assertContents(ChatContext context, String... expected) {
        assertIterableEquals(List.of(expected), context.getMessages().stream()
                .map(LLMMessage::getContent)
                .toList());
    }

    private static int actualCharacterCount(ChatContext context) {
        return context.getMessages().stream()
                .map(LLMMessage::getContent)
                .filter(content -> content != null)
                .mapToInt(String::length)
                .sum();
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Timed out waiting for latch");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for latch", exception);
        }
    }

    private static void scheduleAfterGate(ChatContext context, CountDownLatch ready,
                                          CountDownLatch start) {
        ready.countDown();
        await(start);
        context.scheduleCompressionIfNeeded();
    }

    private static void awaitExecutor(ExecutorService executor) throws Exception {
        executor.submit(() -> { }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "Executor did not terminate");
    }
}
