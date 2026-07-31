package com.riceawa.llm.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryPolicyTest {

    @Test
    void recognizesOnlyConfiguredRecoverableHttpStatuses() {
        assertTrue(RetryPolicy.isRetryable(429));
        assertTrue(RetryPolicy.isRetryable(502));
        assertTrue(RetryPolicy.isRetryable(503));
        assertTrue(RetryPolicy.isRetryable(504));
        assertFalse(RetryPolicy.isRetryable(400));
        assertFalse(RetryPolicy.isRetryable(500));
    }

    @Test
    void retryAfterTakesPrecedenceWhenItExceedsJitteredBackoff() {
        RetryPolicy policy = new RetryPolicy(1_000L, 2.0D);

        assertEquals(2_000L, policy.nextDelayMillis(1, 2_000L, () -> 0.5D));
        assertEquals(30_000L, policy.nextDelayMillis(1, 60_000L, () -> 0.5D));
    }

    @Test
    void appliesBoundedJitterAndThirtySecondCeiling() {
        RetryPolicy policy = new RetryPolicy(20_000L, 2.0D);

        assertEquals(10_000L, policy.nextDelayMillis(1, -1L, () -> 0.5D));
        assertEquals(29_999L, policy.nextDelayMillis(1, -1L, () -> 1.49999D));
        assertEquals(30_000L, policy.nextDelayMillis(2, 60_000L, () -> 1.0D));
    }
}
