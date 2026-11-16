package pl.automapa.mapservices.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pl.automapa.mapservices.exception.NetworkException;

/**
 * Default transport of the SDK, built on the JDK {@link java.net.http.HttpClient} (no extra dependencies).
 *
 * <p>Every response is returned as is, including 4xx and 5xx errors, which are mapped to exceptions by the caller.
 * Only failures without a response (connection, DNS, TLS, timeout or interruption) are thrown, as
 * {@link NetworkException}. The response body is decoded as UTF-8 and each response header keeps its first value.
 * Redirects are not followed.
 *
 * <p>The instance is thread safe and reuses one underlying JDK client, so share it instead of creating one per call.
 */
public final class JdkHttpClient implements HttpClient {

    private final Duration timeout;
    private final java.net.http.HttpClient client;

    /**
     * Creates the transport.
     *
     * @param timeout limit for opening the connection and, separately, for receiving the response of one request
     * @throws IllegalArgumentException when {@code timeout} is {@code null}, zero or negative
     */
    public JdkHttpClient(Duration timeout) {
        if (timeout == null) {
            throw new IllegalArgumentException(
                    "JdkHttpClient timeout is required; pass a positive duration, e.g. Duration.ofSeconds(30)");
        }
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException(
                    "JdkHttpClient timeout must be positive (got " + timeout + "); pass e.g. Duration.ofSeconds(30)");
        }
        this.timeout = timeout;
        this.client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * Returns the timeout given to the constructor.
     *
     * @return the connection and response timeout
     */
    public Duration timeout() {
        return timeout;
    }

    /**
     * Sends the request and returns the response, whatever its status code is.
     *
     * @param request the request to send
     * @return the response with the status, the UTF-8 decoded body and the first value of each header
     * @throws NetworkException when no response is received; the message names the URL, the cause and how to fix it
     * @throws IllegalArgumentException when {@code request} is {@code null} or has a header the JDK does not allow
     *     to set (e.g. {@code Host})
     */
    @Override
    public HttpResponse send(HttpRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("JdkHttpClient.send(request): request is required");
        }
        String url = request.url();
        java.net.http.HttpRequest.Builder builder = newBuilder(url);
        java.net.http.HttpRequest.BodyPublisher body = request.body().isEmpty()
                ? java.net.http.HttpRequest.BodyPublishers.noBody()
                : java.net.http.HttpRequest.BodyPublishers.ofString(request.body(), StandardCharsets.UTF_8);
        builder.timeout(timeout).method(request.method(), body);
        request.headers().forEach(builder::header);

        java.net.http.HttpResponse<String> response;
        try {
            response = client.send(
                    builder.build(), java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw new NetworkException(
                    "HTTP request to " + url + " timed out after " + timeout.toMillis() + " ms (" + describe(e)
                            + "). Check your network connection and the baseUrl setting, or increase the timeout"
                            + " if the API needs more time.",
                    e);
        } catch (IOException e) {
            throw new NetworkException("HTTP request to " + url + " failed: " + describe(e) + ". " + hint(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NetworkException(
                    "HTTP request to " + url + " was interrupted before a response was received (the thread"
                            + " interrupt flag is kept). Retry the call if the interruption was not intended. "
                            + hint(),
                    e);
        }
        return new HttpResponse(
                response.statusCode(),
                response.body(),
                firstValues(response.headers().map()));
    }

    private java.net.http.HttpRequest.Builder newBuilder(String url) {
        try {
            return java.net.http.HttpRequest.newBuilder(URI.create(url));
        } catch (IllegalArgumentException e) {
            throw new NetworkException(
                    "Cannot send HTTP request: invalid URL " + url + " (" + describe(e)
                            + "). Check the baseUrl setting; it must be an http(s) address such as"
                            + " https://api.automapa.pl/",
                    e);
        }
    }

    private String hint() {
        return "Check your network connection, the baseUrl setting and the timeout (currently " + timeout.toMillis()
                + " ms).";
    }

    private static Map<String, String> firstValues(Map<String, List<String>> headers) {
        Map<String, String> result = new LinkedHashMap<>();
        headers.forEach((name, values) -> {
            if (!name.startsWith(":") && !values.isEmpty()) {
                result.put(name, values.get(0));
            }
        });
        return result;
    }

    private static String describe(Throwable error) {
        StringBuilder text = new StringBuilder(error.getClass().getName());
        Throwable current = error;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                return text.append(": ").append(current.getMessage()).toString();
            }
            current = current.getCause();
        }
        return text.toString();
    }
}
