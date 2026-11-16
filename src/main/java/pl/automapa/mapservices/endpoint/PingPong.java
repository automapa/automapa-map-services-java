package pl.automapa.mapservices.endpoint;

import java.util.Map;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.response.ApiResponse;

/**
 * The {@code PingPong} API service: a connectivity and session check.
 *
 * <pre>{@code
 * ApiResponse pong = client.pingPong().ping("hello"); // pong.dataAsMap().get("msg") -> "pong"
 * }</pre>
 */
public final class PingPong {

    private static final String SERVICE = "PingPong";

    private final ApiClient client;

    /**
     * Creates the endpoint; usually obtained from {@link ApiClient#pingPong()}.
     *
     * @param client client sending the requests
     * @throws IllegalArgumentException when {@code client} is {@code null}
     */
    public PingPong(ApiClient client) {
        this.client = EndpointParams.requireNonNull(client, "new PingPong", "client", "use client.pingPong()");
    }

    /**
     * Sends {@code {"msg": msg}} to {@code PingPong.ping}; the method requires a session, which the client opens
     * when needed.
     *
     * @param msg message to send (may be empty)
     * @return the response; on success the result contains {@code msg} with the value {@code pong}
     * @throws IllegalArgumentException when {@code msg} is {@code null}
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     */
    public ApiResponse ping(String msg) {
        return ping(msg, null);
    }

    /**
     * Sends {@code msg} with additional parameters to {@code PingPong.ping}.
     *
     * @param msg message to send (may be empty); overrides {@code msg} in {@code extra}
     * @param extra additional parameters sent unchanged, or {@code null} for none; the {@code format} key selects
     *     the response format and is not sent
     * @return the response; on success the result contains {@code msg} with the value {@code pong}
     * @throws IllegalArgumentException when {@code msg} is {@code null}
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     */
    public ApiResponse ping(String msg, Map<String, ?> extra) {
        EndpointParams.requireNonNull(msg, "PingPong.ping", "msg", "pass any text, e.g. ping(\"hello\")");
        return client.call(SERVICE, "ping", EndpointParams.merge(extra, "msg", msg));
    }
}
