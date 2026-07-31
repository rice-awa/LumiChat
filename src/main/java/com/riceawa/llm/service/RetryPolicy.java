package com.riceawa.llm.service;

import java.util.function.DoubleSupplier;

/**
 * Calculates retry eligibility and capped exponential backoff delays.
 */
public final class RetryPolicy {
    private static final long MAX_DELAY_MILLIS = 30_000L;

    private final long baseDelayMillis;
    private final double multiplier;

    public RetryPolicy(long baseDelayMillis, double multiplier) {
        this.baseDelayMillis = Math.max(0L, baseDelayMillis);
        this.multiplier = multiplier > 0.0D ? multiplier : 1.0D;
    }

    public static boolean isRetryable(int statusCode) {
        return statusCode == 429 || statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    public long nextDelayMillis(int attempt, long retryAfterMs, DoubleSupplier jitter) {
        int normalizedAttempt = Math.max(1, attempt);
        double backoff = baseDelayMillis * Math.pow(multiplier, normalizedAttempt - 1);
        double jitterValue = jitter == null ? 1.0D : jitter.getAsDouble();
        long jitteredBackoff = cap(backoff * boundedJitter(jitterValue));
        long serverDelay = Math.max(0L, retryAfterMs);
        return Math.min(MAX_DELAY_MILLIS, Math.max(jitteredBackoff, serverDelay));
    }

    private static double boundedJitter(double jitter) {
        if (Double.isNaN(jitter) || jitter < 0.5D) {
            return 0.5D;
        }
        return Math.min(jitter, Math.nextDown(1.5D));
    }

    private static long cap(double delay) {
        if (Double.isNaN(delay) || delay <= 0.0D) {
            return 0L;
        }
        if (Double.isInfinite(delay) || delay >= MAX_DELAY_MILLIS) {
            return MAX_DELAY_MILLIS;
        }
        return (long) delay;
    }
}
