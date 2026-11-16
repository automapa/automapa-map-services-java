package pl.automapa.mapservices.request;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Request of {@code Routes.optimizeQueue}: the best order of visiting the points, computed asynchronously.
 *
 * <pre>{@code
 * OptimizeQueueRequest request = OptimizeQueueRequest.builder(
 *                 List.of(
 *                         new QueuePoint("Warszawa", 21.0073642, 52.2297),
 *                         new QueuePoint("Gdansk", 18.6282, 54.352),
 *                         new QueuePoint("Krakow", 19.9448, 50.0647)),
 *                 OptimizeBy.TIME)
 *         .route(Map.of("start", "NOW"))
 *         .build();
 * // {"points": [{"id": "Warszawa", "x": 21.0073642, "y": 52.2297}, ...], "optimizeBy": "time",
 * //  "fixedEnd": true, "route": {"start": "NOW"}}
 * }</pre>
 *
 * <p>{@code fixedEnd} is always sent ({@code true} unless set); {@code route} and {@code object} are sent only when
 * set. The API optimises up to 50 points and answers with a {@code requestId}; ask for the result with
 * {@code Routes.optimizeQueueResult(requestId)} until {@code resultReady} is true. The identifiers of the points
 * must be unique, because the result lists them in the optimised order.
 */
public final class OptimizeQueueRequest extends ApiRequest {

    private static final String OPERATION = "OptimizeQueueRequest.builder";

    private final List<QueuePoint> points;
    private final OptimizeBy optimizeBy;
    private final boolean fixedEnd;
    private final Map<String, Object> route;
    private final Map<String, Object> object;

    private OptimizeQueueRequest(Builder builder) {
        super(builder);
        this.points = builder.points;
        this.optimizeBy = builder.optimizeBy;
        this.fixedEnd = builder.fixedEnd;
        this.route = builder.route;
        this.object = builder.object;
    }

    /**
     * Starts a request for points with unique identifiers.
     *
     * @param points the points, the first one is the start; up to 50 for the API; the list is copied
     * @param optimizeBy what to minimise: {@link OptimizeBy#TIME} or {@link OptimizeBy#DISTANCE}
     * @return the builder
     * @throws IllegalArgumentException when {@code points} is {@code null}, empty, contains {@code null} or two
     *     points with the same {@code id}, or {@code optimizeBy} is {@code null}
     */
    public static Builder builder(List<QueuePoint> points, OptimizeBy optimizeBy) {
        List<QueuePoint> copy = Checks.requireItems(
                points,
                OPERATION,
                "points",
                "pass the points to visit, e.g. List.of(new QueuePoint(\"Warszawa\", 21.0073642, 52.2297))");
        requireUniqueIds(copy);
        OptimizeBy checkedOptimizeBy = Checks.requireNonNull(
                optimizeBy, OPERATION, "optimizeBy", "pass OptimizeBy.TIME or OptimizeBy.DISTANCE");
        return new Builder(copy, checkedOptimizeBy);
    }

    private static void requireUniqueIds(List<QueuePoint> points) {
        Map<String, Integer> firstIndex = new HashMap<>();
        for (int i = 0; i < points.size(); i++) {
            String id = points.get(i).id();
            Integer previous = firstIndex.putIfAbsent(id, i);
            if (previous != null) {
                throw new IllegalArgumentException(OPERATION + ": id \"" + id + "\" is used by points[" + previous
                        + "] and points[" + i + "]; every point needs a unique id, because the result lists the"
                        + " ids in the optimised order");
            }
        }
    }

    @Override
    protected Map<String, Object> namedParams() {
        List<Map<String, Object>> maps = new ArrayList<>(points.size());
        for (QueuePoint point : points) {
            maps.add(point.toMap());
        }
        Map<String, Object> named = new LinkedHashMap<>();
        named.put("points", maps);
        named.put("optimizeBy", optimizeBy.apiValue());
        named.put("fixedEnd", fixedEnd);
        named.put("route", route);
        named.put("object", object);
        return named;
    }

    /** Builder of {@link OptimizeQueueRequest}; {@code fixedEnd} defaults to {@code true}. */
    public static final class Builder extends ApiRequest.Builder<Builder> {

        private final List<QueuePoint> points;
        private final OptimizeBy optimizeBy;
        private boolean fixedEnd = true;
        private Map<String, Object> route;
        private Map<String, Object> object;

        private Builder(List<QueuePoint> points, OptimizeBy optimizeBy) {
            this.points = points;
            this.optimizeBy = optimizeBy;
        }

        /**
         * Tells whether the last point is the destination.
         *
         * @param fixedEnd {@code true} (default) to end the route at the last point; {@code false} to let the API
         *     choose the end
         * @return this builder
         */
        public Builder fixedEnd(boolean fixedEnd) {
            this.fixedEnd = fixedEnd;
            return this;
        }

        /**
         * Sets the route settings: {@code start} ({@code "NOW"} or {@code "YYYY-MM-DD HH:MM"}), {@code avoidTolls},
         * {@code avoidFerries}, {@code avoidHighways}.
         *
         * @param route the settings, e.g. {@code Map.of("start", "NOW")}, or {@code null} not to send them; the map
         *     is copied in its key order
         * @return this builder
         */
        public Builder route(Map<String, ?> route) {
            this.route = Checks.copyOrNull(route);
            return this;
        }

        /**
         * Sets the vehicle: {@code type} ({@code car} or {@code truck}) and {@code truckParams}.
         *
         * @param object the vehicle, e.g. {@code Map.of("type", "car")}, or {@code null} not to send it; the map is
         *     copied in its key order
         * @return this builder
         */
        public Builder object(Map<String, ?> object) {
            this.object = Checks.copyOrNull(object);
            return this;
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
        public OptimizeQueueRequest build() {
            return new OptimizeQueueRequest(this);
        }
    }
}
