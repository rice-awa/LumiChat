package com.riceawa.llm.function;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

import java.net.IDN;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Validates configured Wiki API endpoints before the mod sends a request.
 */
public final class WikiEndpointPolicy {

    private WikiEndpointPolicy() {
    }

    /**
     * Parses and validates a Wiki API base URL against the administrator allowlist.
     *
     * @throws IllegalArgumentException when the endpoint is not a permitted HTTPS origin
     */
    public static HttpUrl validate(String baseUrl, Set<String> allowedHosts) {
        if (allowedHosts == null || allowedHosts.isEmpty()) {
            throw rejectedEndpoint();
        }

        HttpUrl endpoint = HttpUrl.parse(baseUrl);
        if (endpoint == null
                || !endpoint.isHttps()
                || !endpoint.encodedUsername().isEmpty()
                || !endpoint.encodedPassword().isEmpty()
                || endpoint.port() != 443) {
            throw rejectedEndpoint();
        }

        String host = normalizeHost(endpoint.host());
        if (host == null || isIpLiteral(host) || !normalizeAllowedHosts(allowedHosts).contains(host)) {
            throw rejectedEndpoint();
        }

        return endpoint;
    }

    /**
     * Creates an HTTP client that cannot follow a redirect to another origin.
     */
    public static OkHttpClient.Builder newSecureClientBuilder() {
        return new OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false);
    }

    private static Set<String> normalizeAllowedHosts(Set<String> allowedHosts) {
        Set<String> normalized = new HashSet<>();
        for (String allowedHost : allowedHosts) {
            String host = normalizeHost(allowedHost);
            if (host != null && !isIpLiteral(host)) {
                normalized.add(host);
            }
        }
        return normalized;
    }

    private static String normalizeHost(String host) {
        if (host == null || host.isEmpty()) {
            return null;
        }
        try {
            return IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean isIpLiteral(String host) {
        if (host.indexOf(':') >= 0) {
            return true;
        }

        String numericHost = host;
        while (numericHost.endsWith(".")) {
            numericHost = numericHost.substring(0, numericHost.length() - 1);
        }

        String[] parts = numericHost.split("\\.", -1);
        if (parts.length < 1 || parts.length > 4) {
            return false;
        }

        long[] maximums;
        switch (parts.length) {
            case 1:
                maximums = new long[]{0xFFFFFFFFL};
                break;
            case 2:
                maximums = new long[]{0xFFL, 0xFFFFFFL};
                break;
            case 3:
                maximums = new long[]{0xFFL, 0xFFL, 0xFFFFL};
                break;
            case 4:
                maximums = new long[]{0xFFL, 0xFFL, 0xFFL, 0xFFL};
                break;
            default:
                return false;
        }

        for (int index = 0; index < parts.length; index++) {
            if (!isNumericIpv4Part(parts[index], maximums[index])) {
                return false;
            }
        }
        return true;
    }

    private static boolean isNumericIpv4Part(String part, long maximum) {
        if (part.isEmpty()) {
            return false;
        }

        int radix = 10;
        int start = 0;
        if (part.length() > 2 && part.charAt(0) == '0'
                && (part.charAt(1) == 'x' || part.charAt(1) == 'X')) {
            radix = 16;
            start = 2;
        } else if (part.length() > 1 && part.charAt(0) == '0') {
            radix = 8;
            start = 1;
        }
        if (start == part.length()) {
            return false;
        }

        long value = 0;
        for (int index = start; index < part.length(); index++) {
            int digit = Character.digit(part.charAt(index), radix);
            if (digit < 0 || value > (maximum - digit) / radix) {
                return false;
            }
            value = value * radix + digit;
        }
        return true;
    }

    private static IllegalArgumentException rejectedEndpoint() {
        return new IllegalArgumentException("Wiki endpoint is not permitted");
    }
}
