package pl.automapa.mapservices.http;

import java.util.Map;

/**
 * Transport-independent HTTP response received by the SDK.
 *
 * <p>The headers are copied into an unmodifiable map that keeps their insertion order.
 *
 * @param statusCode HTTP status code
 * @param body response body ({@code null} means an empty body)
 * @param headers response headers ({@code null} means no headers)
 */
public record HttpResponse(int statusCode, String body, Map<String, String> headers) {

    /**
     * Normalizes the body and makes an immutable copy of the headers.
     *
     * @param statusCode HTTP status code
     * @param body response body ({@code null} means an empty body)
     * @param headers response headers ({@code null} means no headers)
     */
    public HttpResponse {
        body = body == null ? "" : body;
        headers = HttpRequest.copyHeaders(headers);
    }

    /**
     * Creates a response without headers.
     *
     * @param statusCode HTTP status code
     * @param body response body ({@code null} means an empty body)
     */
    public HttpResponse(int statusCode, String body) {
        this(statusCode, body, Map.of());
    }

    /**
     * Tells whether the status code is in the 2xx range.
     *
     * @return {@code true} for status codes 200-299
     */
    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }
}
