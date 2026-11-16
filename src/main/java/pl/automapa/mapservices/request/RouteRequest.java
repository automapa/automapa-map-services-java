package pl.automapa.mapservices.request;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Request of {@code Routes.route}: a route through the points with its length, travel time and directions.
 *
 * <pre>{@code
 * Map<String, Object> route = new LinkedHashMap<>();
 * route.put("type", "optimal"); // "quick", "short" or "optimal"
 * route.put("traffic", true);
 * RouteRequest request = RouteRequest.builder(
 *                 List.of(new Point(21.0073642, 52.2297), new Point(18.6282, 54.352)),
 *                 route,
 *                 Map.of("type", "car"))
 *         .build();
 * // {"points": [[21.0073642, 52.2297], [18.6282, 54.352]], "route": {"type": "optimal", "traffic": true},
 * //  "object": {"type": "car"}}
 * }</pre>
 *
 * <p>{@code route} and {@code object} are nested objects of the API passed as maps: {@code route} holds the route
 * settings ({@code start}, {@code type}, {@code traffic}, {@code desc}, {@code avoidTolls}, {@code avoidFerries},
 * {@code avoidHighways}, {@code calculateTolls}, {@code vehicleCatForEToll}, ...), {@code object} the vehicle
 * ({@code type} {@code car} or {@code truck}, {@code truckParams} with {@code axleLoad}, {@code truckWeight},
 * {@code truckLength}, {@code truckWidth}, {@code truckHeight}, {@code euroNormForToll}).
 */
public final class RouteRequest extends ApiRequest {

    private final List<Point> points;
    private final Map<String, Object> route;
    private final Map<String, Object> object;

    private RouteRequest(Builder builder) {
        super(builder);
        this.points = builder.points;
        this.route = builder.route;
        this.object = builder.object;
    }

    /**
     * Starts a request for waypoints, route settings and the vehicle.
     *
     * @param points the waypoints in order ({@code x} = longitude, {@code y} = latitude); the list is copied
     * @param route route settings, e.g. {@code Map.of("type", "optimal", "traffic", true)}, or an empty map for
     *     the API defaults; the map is copied in its key order
     * @param object the vehicle, e.g. {@code Map.of("type", "car")}; the map is copied in its key order
     * @return the builder
     * @throws IllegalArgumentException when {@code points} is {@code null}, empty or contains {@code null}, or
     *     {@code route} or {@code object} is {@code null}
     */
    public static Builder builder(List<Point> points, Map<String, ?> route, Map<String, ?> object) {
        String operation = "RouteRequest.builder";
        List<Point> copy = Checks.requireItems(
                points,
                operation,
                "points",
                "pass the waypoints, e.g. List.of(new Point(21.0073642, 52.2297), new Point(18.6282, 54.352))");
        Checks.requireNonNull(
                route, operation, "route", "pass the route settings, e.g. Map.of(\"type\", \"optimal\"), or Map.of()");
        Checks.requireNonNull(object, operation, "object", "pass the vehicle, e.g. Map.of(\"type\", \"car\")");
        return new Builder(copy, copyOf(route), copyOf(object));
    }

    private static Map<String, Object> copyOf(Map<String, ?> map) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    @Override
    protected Map<String, Object> namedParams() {
        List<List<Double>> coordinates = new ArrayList<>(points.size());
        for (Point point : points) {
            coordinates.add(point.toList());
        }
        Map<String, Object> named = new LinkedHashMap<>();
        named.put("points", coordinates);
        named.put("route", route);
        named.put("object", object);
        return named;
    }

    /** Builder of {@link RouteRequest}; all fields of the API are given to {@link RouteRequest#builder}. */
    public static final class Builder extends ApiRequest.Builder<Builder> {

        private final List<Point> points;
        private final Map<String, Object> route;
        private final Map<String, Object> object;

        private Builder(List<Point> points, Map<String, Object> route, Map<String, Object> object) {
            this.points = points;
            this.route = route;
            this.object = object;
        }

        @Override
        protected Builder self() {
            return this;
        }

        /**
         * Builds the request.
         *
         * @return the request
         */
        public RouteRequest build() {
            return new RouteRequest(this);
        }
    }
}
