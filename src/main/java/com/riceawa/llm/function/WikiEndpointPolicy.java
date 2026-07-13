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

        String[] octets = host.split("\\.", -1);
        if (octets.length != 4) {
            return false;
        }
        for (String octet : octets) {
            if (octet.isEmpty() || octet.length() > 3) {
                return false;
            }
            for (int index = 0; index < octet.length(); index++) {
                if (!Character.isDigit(octet.charAt(index))) {
                    return false;
                }
            }
            try {
                if (Integer.parseInt(octet) > 255) {
                    return false;
                }
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        return true;
    }

    private static IllegalArgumentException rejectedEndpoint() {
        return new IllegalArgumentException("Wiki endpoint is not permitted");
    }
}
