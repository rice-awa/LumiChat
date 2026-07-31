package com.riceawa.llm.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ConcurrencyManagerTest {

    @Test
    void doesNotLeakPermitsAfterImmediateAcquire() throws Exception {
        ConcurrencyManager manager = ConcurrencyManager.createForTest(
                new ConcurrencyManager.ConcurrencyConfig(1, 1, 100, 1, 1, 1000));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            CompletableFuture<String> first = manager.submitRequest(() -> {
                entered.countDown();
                await(release);
                return "first";
            }, "first");

            assertTrue(entered.await(1, TimeUnit.SECONDS));
            release.countDown();
            assertEquals("first", first.get(1, TimeUnit.SECONDS));
            assertEquals("second", manager.submitRequest(() -> "second", "second")
                    .get(1, TimeUnit.SECONDS));
            assertTrue(awaitCondition(() -> manager.getStats().activeRequests == 0
                    && manager.getStats().queuedRequests == 0, 1, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            manager.shutdown();
        }
    }

    @Test
    void queuedCountReturnsToZeroAfterWait() throws Exception {
        ConcurrencyManager manager = ConcurrencyManager.createForTest(
                new ConcurrencyManager.ConcurrencyConfig(1, 2, 1000, 2, 2, 1000));
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        CountDownLatch releaseSecond = new CountDownLatch(1);

        try {
            CompletableFuture<String> first = manager.submitRequest(() -> {
                firstEntered.countDown();
                await(releaseFirst);
                return "first";
            }, "first");
            assertTrue(firstEntered.await(1, TimeUnit.SECONDS));

            CompletableFuture<String> second = manager.submitRequest(() -> {
                secondEntered.countDown();
                await(releaseSecond);
                return "second";
            }, "second");
            assertTrue(awaitCondition(() -> manager.getStats().queuedRequests == 1,
                    1, TimeUnit.SECONDS));

            releaseFirst.countDown();
            assertEquals("first", first.get(1, TimeUnit.SECONDS));
            assertTrue(secondEntered.await(1, TimeUnit.SECONDS));
            assertEquals(1, manager.getStats().activeRequests);
            assertEquals(0, manager.getStats().queuedRequests);
            releaseSecond.countDown();
            assertEquals("second", second.get(1, TimeUnit.SECONDS));
            assertTrue(awaitCondition(() -> manager.getStats().activeRequests == 0
                    && manager.getStats().queuedRequests == 0, 1, TimeUnit.SECONDS));
        } finally {
            releaseFirst.countDown();
            releaseSecond.countDown();
            manager.shutdown();
        }
    }

    @Test
    void timeoutDoesNotReleaseUnownedPermit() throws Exception {
        ConcurrencyManager manager = ConcurrencyManager.createForTest(
                new ConcurrencyManager.ConcurrencyConfig(1, 2, 150, 3, 3, 1000));
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch thirdEntered = new CountDownLatch(1);

        try {
            CompletableFuture<String> first = manager.submitRequest(() -> {
                firstEntered.countDown();
                await(releaseFirst);
                return "first";
            }, "first");
            assertTrue(firstEntered.await(1, TimeUnit.SECONDS));

            CompletableFuture<String> timedOut = manager.submitRequest(() -> "unexpected", "timeout");
            assertFutureFailure(timedOut, TimeoutException.class);
            assertTrue(awaitCondition(() -> manager.getStats().queuedRequests == 0,
                    1, TimeUnit.SECONDS));

            CompletableFuture<String> third = manager.submitRequest(() -> {
                thirdEntered.countDown();
                return "third";
            }, "third");
            assertTrue(awaitCondition(() -> manager.getStats().queuedRequests == 1,
                    1, TimeUnit.SECONDS));
            assertFalse(thirdEntered.await(50, TimeUnit.MILLISECONDS));

            releaseFirst.countDown();
            assertEquals("first", first.get(1, TimeUnit.SECONDS));
            assertEquals("third", third.get(1, TimeUnit.SECONDS));
        } finally {
            releaseFirst.countDown();
            manager.shutdown();
        }
    }

    @Test
    void rejectionDoesNotReleaseUnownedPermit() throws Exception {
        ConcurrencyManager manager = ConcurrencyManager.createForTest(
                new ConcurrencyManager.ConcurrencyConfig(1, 1, 1000, 1, 2, 1000));
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        CountDownLatch thirdEntered = new CountDownLatch(1);

        try {
            CompletableFuture<String> first = manager.submitRequest(() -> {
                firstEntered.countDown();
                await(releaseFirst);
                return "first";
            }, "first");
            assertTrue(firstEntered.await(1, TimeUnit.SECONDS));

            CompletableFuture<String> second = manager.submitRequest(() -> {
                secondEntered.countDown();
                return "second";
            }, "second");
            CompletableFuture<String> third = manager.submitRequest(() -> {
                thirdEntered.countDown();
                return "third";
            }, "third");
            assertTrue(awaitCondition(() -> manager.getStats().queuedRequests == 1,
                    1, TimeUnit.SECONDS));

            CompletableFuture<String> rejected = manager.submitRequest(() -> "rejected", "rejected");
            assertFutureFailure(rejected, RuntimeException.class);
            assertFalse(thirdEntered.await(100, TimeUnit.MILLISECONDS));

            releaseFirst.countDown();
            assertEquals("first", first.get(1, TimeUnit.SECONDS));
            assertEquals("third", third.get(1, TimeUnit.SECONDS));
            assertTrue(secondEntered.await(1, TimeUnit.SECONDS));
            assertEquals("second", second.get(1, TimeUnit.SECONDS));
            assertTrue(awaitCondition(() -> manager.getStats().activeRequests == 0
                    && manager.getStats().queuedRequests == 0, 1, TimeUnit.SECONDS));
        } finally {
            releaseFirst.countDown();
            manager.shutdown();
        }
    }

    @Test
    void taskFailureReleasesPermit() throws Exception {
        ConcurrencyManager manager = ConcurrencyManager.createForTest(
                new ConcurrencyManager.ConcurrencyConfig(1, 1, 1000, 1, 1, 1000));
        CountDownLatch failureEntered = new CountDownLatch(1);

        try {
            CompletableFuture<String> failed = manager.submitRequest(() -> {
                failureEntered.countDown();
                throw new AssertionError("task failed");
            }, "failed");

            assertTrue(failureEntered.await(1, TimeUnit.SECONDS));
            assertFutureFailure(failed, AssertionError.class);
            assertEquals("recovered", manager.submitRequest(() -> "recovered", "recovered")
                    .get(1, TimeUnit.SECONDS));
            assertTrue(awaitCondition(() -> manager.getStats().activeRequests == 0
                    && manager.getStats().queuedRequests == 0, 1, TimeUnit.SECONDS));
        } finally {
            manager.shutdown();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(exception);
        }
    }

    private static boolean awaitCondition(BooleanSupplier condition, long timeout, TimeUnit unit)
            throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        CountDownLatch pollInterval = new CountDownLatch(1);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            pollInterval.await(1, TimeUnit.MILLISECONDS);
        }
        return condition.getAsBoolean();
    }

    private static void assertFutureFailure(CompletableFuture<?> future,
                                            Class<? extends Throwable> expectedCause) throws Exception {
        try {
            future.get(2, TimeUnit.SECONDS);
            fail("Expected future to fail with " + expectedCause.getSimpleName());
        } catch (ExecutionException exception) {
            assertInstanceOf(expectedCause, exception.getCause());
        }
    }
}
