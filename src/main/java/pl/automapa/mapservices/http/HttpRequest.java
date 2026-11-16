package pl.automapa.mapservices.http;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Transport-independent HTTP request sent by the SDK.
 *
 * <p>The headers are copied into an unmodifiable map that keeps their insertion order.
 *
 * @param method HTTP method, e.g. {@code POST}
 * @param url absolute request URL
 * @param headers request headers ({@code null} means no headers)
 * @param body request body ({@code null} means an empty body)
 */
public record HttpRequest(String method, String url, Map<String, String> headers, String body) {

    /**
     * Validates the components and makes an immutable copy of the headers.
     *
     * @param method HTTP method, e.g. {@code POST}
     * @param url absolute request URL
     * @param headers request headers ({@code null} means no headers)
     * @param body request body ({@code null} means an empty body)
     * @throws IllegalArgumentException when {@code method} or {@code url} is {@code null} or blank
     */
    public HttpRequest {
        requireText(method, "method");
        requireText(url, "url");
        headers = copyHeaders(headers);
        body = body == null ? "" : body;
    }

    static Map<String, String> copyHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "HttpRequest " + name + " is required; pass a non-empty value (got " + value + ")");
        }
    }
}
