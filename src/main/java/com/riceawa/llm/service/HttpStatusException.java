package com.riceawa.llm.service;

import com.riceawa.llm.logging.LLMLogSanitizer;

import java.io.IOException;

/**
 * HTTP failure details that are safe to return to callers and inspect for retry eligibility.
 */
public final class HttpStatusException extends IOException {
    private static final int MAX_RESPONSE_SUMMARY_LENGTH = 256;

    private final int statusCode;
    private final String responseSummary;
    private final long retryAfterMs;

    public HttpStatusException(int statusCode, String responseBody) {
        this(statusCode, responseBody, -1L);
    }

    HttpStatusException(int statusCode, String responseBody, long retryAfterMs) {
        super("HTTP " + statusCode + ": " + summarize(responseBody));
        this.statusCode = statusCode;
        this.responseSummary = summarize(responseBody);
        this.retryAfterMs = retryAfterMs;
    }

    public int statusCode() {
        return statusCode;
    }

    public String responseSummary() {
        return responseSummary;
    }

    long retryAfterMs() {
        return retryAfterMs;
    }

    private static String summarize(String responseBody) {
        String sanitized = LLMLogSanitizer.sanitizeContent(responseBody);
        if (sanitized == null || sanitized.isEmpty()) {
            return "[EMPTY_RESPONSE]";
        }
        return LLMLogSanitizer.truncateContent(sanitized, MAX_RESPONSE_SUMMARY_LENGTH);
    }
}
