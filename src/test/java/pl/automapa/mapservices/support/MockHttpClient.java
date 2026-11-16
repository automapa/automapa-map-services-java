package pl.automapa.mapservices.support;

import java.net.ConnectException;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import pl.automapa.mapservices.exception.NetworkException;
import pl.automapa.mapservices.http.HttpClient;
import pl.automapa.mapservices.http.HttpRequest;
import pl.automapa.mapservices.http.HttpResponse;
import pl.automapa.mapservices.json.Json;

/**
 * In-memory {@link HttpClient} for tests; it never makes real network calls.
 *
 * <pre>{@code
 * MockHttpClient mock = new MockHttpClient();
 * mock.addResponse("Geocoder", "geocode", Fixtures.load("geocode_success"));
 * }</pre>
 *
 * <p>Responses are matched by the last two path segments of the request URL ({@code /Service/method}), so the base
 * URL and the API version do not matter. Responses are served FIFO per endpoint; when an endpoint has no queued
 * response, the wildcard queue ({@link #addRawResponse}, {@link #addNetworkFailure}) is used. Every request is logged
 * before a response is chosen. All methods are thread safe.
 */
public final class MockHttpClient implements HttpClient {

    private static final String WILDCARD = "*";

    private final Map<String, Deque<Reply>> responses = new HashMap<>();
    private final List<HttpRequest> requestLog = new ArrayList<>();

    /**
     * Creates a queued reply for {@link #addSequence}.
     *
     * @param statusCode HTTP status code
     * @param body response body written as JSON
     * @return the reply
     */
    public static Reply reply(int statusCode, Object body) {
        return new Reply(statusCode, body, null);
    }

    /**
     * Registers a response with status 200 for an endpoint.
     *
     * @param service API service name
     * @param method API method name
     * @param body response body written as JSON (usually a fixture with the {@code result} wrapper)
     */
    public void addResponse(String service, String method, Object body) {
        addResponse(service, method, body, 200);
    }

    /**
     * Registers a response for an endpoint; repeated calls queue responses (FIFO).
     *
     * @param service API service name
     * @param method API method name
     * @param body response body written as JSON
     * @param statusCode HTTP status code
     */
    public synchronized void addResponse(String service, String method, Object body, int statusCode) {
        enqueue(key(service, method), reply(statusCode, body));
    }

    /**
     * Registers a wildcard response used when the requested endpoint has no queued response (error scenarios).
     *
     * @param statusCode HTTP status code
     * @param body response body written as JSON
     */
    public synchronized void addRawResponse(int statusCode, Object body) {
        enqueue(WILDCARD, reply(statusCode, body));
    }

    /** Queues a wildcard {@link NetworkException} with the message {@code Connection refused}. */
    public void addNetworkFailure() {
        addNetworkFailure("Connection refused");
    }

    /**
     * Queues a wildcard {@link NetworkException} thrown by the next {@link #send} that has no endpoint response.
     *
     * @param message exception message
     */
    public synchronized void addNetworkFailure(String message) {
        enqueue(WILDCARD, new Reply(0, null, message));
    }

    /**
     * Queues several responses for an endpoint (retry and session refresh tests).
     *
     * @param service API service name
     * @param method API method name
     * @param sequence replies created with {@link #reply}
     */
    public synchronized void addSequence(String service, String method, List<Reply> sequence) {
        for (Reply reply : sequence) {
            enqueue(key(service, method), reply);
        }
    }

    @Override
    public HttpResponse send(HttpRequest request) {
        Reply reply;
        String path = URI.create(request.url()).getPath();
        synchronized (this) {
            requestLog.add(request);
            reply = poll(endpointKey(path));
            if (reply == null) {
                reply = poll(WILDCARD);
            }
        }
        if (reply == null) {
            String[] endpoint = endpointKey(path).split("/", 2);
            throw new IllegalStateException("MockHttpClient: no response registered for " + request.method() + " "
                    + path + "; call mock.addResponse(\"" + endpoint[0] + "\", \""
                    + (endpoint.length > 1 ? endpoint[1] : "") + "\", Fixtures.load(\"...\")) in the test setup");
        }
        if (reply.networkFailure != null) {
            throw new NetworkException(reply.networkFailure, new ConnectException(reply.networkFailure));
        }
        return new HttpResponse(
                reply.statusCode, Json.stringify(reply.body), Map.of("Content-Type", "application/json"));
    }

    /**
     * Returns the most recent request.
     *
     * @return the request
     * @throws IllegalStateException when no request has been made yet
     */
    public synchronized HttpRequest lastRequest() {
        if (requestLog.isEmpty()) {
            throw new IllegalStateException("MockHttpClient: no requests have been made yet");
        }
        return requestLog.get(requestLog.size() - 1);
    }

    /**
     * Returns the decoded JSON body of the most recent request.
     *
     * @return the payload ({@code {}} for an empty body)
     * @throws IllegalStateException when no request has been made yet or the body is not a JSON object
     */
    public Map<String, Object> lastPayload() {
        return payload(lastRequest());
    }

    /**
     * Returns the decoded JSON bodies of all requests in chronological order.
     *
     * @return the payloads
     */
    public List<Map<String, Object>> allPayloads() {
        List<Map<String, Object>> payloads = new ArrayList<>();
        for (HttpRequest request : allRequests()) {
            payloads.add(payload(request));
        }
        return payloads;
    }

    /**
     * Returns all requests in chronological order.
     *
     * @return a copy of the request log
     */
    public synchronized List<HttpRequest> allRequests() {
        return List.copyOf(requestLog);
    }

    /**
     * Returns the number of requests sent so far.
     *
     * @return the request count
     */
    public synchronized int requestCount() {
        return requestLog.size();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(HttpRequest request) {
        if (request.body().isEmpty()) {
            return Map.of();
        }
        Object parsed = Json.parse(request.body());
        if (!(parsed instanceof Map)) {
            throw new IllegalStateException(
                    "MockHttpClient: request body of " + request.url() + " is not a JSON object: " + request.body());
        }
        return (Map<String, Object>) parsed;
    }

    private void enqueue(String key, Reply reply) {
        responses.computeIfAbsent(key, k -> new ArrayDeque<>()).add(reply);
    }

    private Reply poll(String key) {
        Deque<Reply> queue = responses.get(key);
        return queue == null ? null : queue.poll();
    }

    private static String key(String service, String method) {
        return service + "/" + method;
    }

    private static String endpointKey(String path) {
        String[] segments = path.split("/");
        if (segments.length < 2) {
            return path;
        }
        return key(segments[segments.length - 2], segments[segments.length - 1]);
    }

    /** A queued reply: an HTTP response or a network failure. */
    public static final class Reply {
        private final int statusCode;
        private final Object body;
        private final String networkFailure;

        private Reply(int statusCode, Object body, String networkFailure) {
            this.statusCode = statusCode;
            this.body = body;
            this.networkFailure = networkFailure;
        }
    }
}
