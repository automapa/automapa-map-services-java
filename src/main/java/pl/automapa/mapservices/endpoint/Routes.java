package pl.automapa.mapservices.endpoint;

import java.util.Map;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.request.MatrixRequest;
import pl.automapa.mapservices.request.OptimizeQueueRequest;
import pl.automapa.mapservices.request.OptimizeRequest;
import pl.automapa.mapservices.request.RouteRequest;
import pl.automapa.mapservices.response.ApiResponse;

/**
 * The {@code Routes} API service: routes, distance matrices and the optimisation of the order of points.
 *
 * <pre>{@code
 * ApiResponse route = client.routes().route(RouteRequest.builder(
 *                 List.of(new Point(21.0073642, 52.2297), new Point(18.6282, 54.352)),
 *                 Map.of("type", "optimal"),
 *                 Map.of("type", "car"))
 *         .build());
 * long metres = ((Number) route.dataAsMap().get("length")).longValue();
 * }</pre>
 *
 * <p>Every method requires a session, which the client opens when needed.
 */
public final class Routes {

    private static final String SERVICE = "Routes";

    private final ApiClient client;

    /**
     * Creates the endpoint; usually obtained from {@link ApiClient#routes()}.
     *
     * @param client client sending the requests
     * @throws IllegalArgumentException when {@code client} is {@code null}
     */
    public Routes(ApiClient client) {
        this.client = EndpointParams.requireNonNull(client, "new Routes", "client", "use client.routes()");
    }

    /**
     * Calculates a route through the points.
     *
     * @param request waypoints, route settings and the vehicle, e.g.
     *     {@code RouteRequest.builder(points, Map.of("type", "optimal"), Map.of("type", "car")).build()}
     * @return the response; the result object has {@code length} (metres), {@code eta} (minutes), {@code mapurl},
     *     {@code routePoints}, {@code desc} (directions) and {@code tolls}
     * @throws IllegalArgumentException when {@code request} is {@code null}
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     */
    public ApiResponse route(RouteRequest request) {
        EndpointParams.requireNonNull(
                request,
                "Routes.route",
                "request",
                "build one with RouteRequest.builder(points, route, object).build()");
        return client.call(SERVICE, "route", request.toParams());
    }

    /**
     * Calculates the route length and travel time between the points; the API needs at least 4 different points.
     *
     * @param request points and options, e.g. {@code MatrixRequest.builder(points, MatrixType.TIME).build()}
     * @return the response; the result is a list of objects with {@code points} (indexes, e.g. {@code "0-1"}),
     *     {@code length} (metres) and {@code duration} (minutes)
     * @throws IllegalArgumentException when {@code request} is {@code null}
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error, e.g.
     *     {@link pl.automapa.mapservices.exception.ValidationException} for fewer than 4 different points
     */
    public ApiResponse matrix(MatrixRequest request) {
        EndpointParams.requireNonNull(
                request,
                "Routes.matrix",
                "request",
                "build one with MatrixRequest.builder(points, MatrixType.TIME).build()");
        return client.call(SERVICE, "matrix", request.toParams());
    }

    /**
     * Finds the best order of visiting up to 10 points; the result is computed synchronously.
     *
     * @param request points and options, e.g. {@code OptimizeRequest.builder(points, OptimizeType.TIME).build()}
     * @return the response; the result object has {@code order} (indexes of the points separated by commas, e.g.
     *     {@code "0,3,2,1"}), {@code points} in that order and {@code sections} with {@code x}, {@code y},
     *     {@code eta} (minutes) and {@code dist} (metres) from the start
     * @throws IllegalArgumentException when {@code request} is {@code null}
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     */
    public ApiResponse optimize(OptimizeRequest request) {
        EndpointParams.requireNonNull(
                request,
                "Routes.optimize",
                "request",
                "build one with OptimizeRequest.builder(points, OptimizeType.TIME).build()");
        return client.call(SERVICE, "optimize", request.toParams());
    }

    /**
     * Queues the optimisation of the order of up to 50 points; get the result with
     * {@link #optimizeQueueResult(String)}.
     *
     * @param request points with unique ids and options, e.g.
     *     {@code OptimizeQueueRequest.builder(points, OptimizeBy.TIME).build()}
     * @return the response; the result object has {@code requestId}
     * @throws IllegalArgumentException when {@code request} is {@code null}
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     */
    public ApiResponse optimizeQueue(OptimizeQueueRequest request) {
        EndpointParams.requireNonNull(
                request,
                "Routes.optimizeQueue",
                "request",
                "build one with OptimizeQueueRequest.builder(points, OptimizeBy.TIME).build()");
        return client.call(SERVICE, "optimizeQueue", request.toParams());
    }

    /**
     * Returns the state of a queued optimisation; the SDK does not poll, call it again until {@code resultReady} is
     * true.
     *
     * @param requestId the {@code requestId} returned by {@link #optimizeQueue(OptimizeQueueRequest)}
     * @return the response; the result object has {@code resultReady} and, when ready, {@code optimizedSequence}
     *     (ids of the points in the optimised order)
     * @throws IllegalArgumentException when {@code requestId} is {@code null} or blank
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     */
    public ApiResponse optimizeQueueResult(String requestId) {
        return optimizeQueueResult(requestId, null);
    }

    /**
     * Returns the state of a queued optimisation with additional parameters.
     *
     * @param requestId the {@code requestId} returned by {@link #optimizeQueue(OptimizeQueueRequest)}; overrides
     *     {@code requestId} in {@code extra}
     * @param extra additional parameters sent unchanged, or {@code null} for none; the {@code format} key selects
     *     the response format and is not sent
     * @return the response; the result object has {@code resultReady} and, when ready, {@code optimizedSequence}
     * @throws IllegalArgumentException when {@code requestId} is {@code null} or blank
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     */
    public ApiResponse optimizeQueueResult(String requestId, Map<String, ?> extra) {
        EndpointParams.requireText(
                requestId,
                "Routes.optimizeQueueResult",
                "requestId",
                "pass the requestId from the result of Routes.optimizeQueue");
        return client.call(SERVICE, "optimizeQueueResult", EndpointParams.merge(extra, "requestId", requestId));
    }
}
