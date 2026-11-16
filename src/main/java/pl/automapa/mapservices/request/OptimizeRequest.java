package pl.automapa.mapservices.request;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Request of {@code Routes.optimize}: the best order of visiting the points, computed synchronously.
 *
 * <pre>{@code
 * OptimizeRequest request = OptimizeRequest.builder(
 *                 List.of(
 *                         new Point(21.0073642, 52.2297), // Warszawa
 *                         new Point(18.6282, 54.352), // Gdansk
 *                         new Point(16.9252, 52.4064), // Poznan
 *                         new Point(19.9448, 50.0647)), // Krakow
 *                 OptimizeType.TIME)
 *         .fixedEnd(false)
 *         .object(Map.of("type", "car"))
 *         .build();
 * // {"points": [[21.0073642, 52.2297], ...], "type": 1, "fixedEnd": false, "object": {"type": "car"}}
 * }</pre>
 *
 * <p>{@code fixedEnd} is always sent ({@code true} unless set); {@code route} and {@code object} are sent only when
 * set. The API optimises up to 10 points; use {@link OptimizeQueueRequest} for up to 50. The SDK does not check
 * the limit and leaves the validation to the API.
 */
public final class OptimizeRequest extends ApiRequest {

    private final List<Point> points;
    private final OptimizeType type;
    private final boolean fixedEnd;
    private final Map<String, Object> route;
    private final Map<String, Object> object;

    private OptimizeRequest(Builder builder) {
        super(builder);
        this.points = builder.points;
        this.type = builder.type;
        this.fixedEnd = builder.fixedEnd;
        this.route = builder.route;
        this.object = builder.object;
    }

    /**
     * Starts a request for points.
     *
     * @param points the points ({@code x} = longitude, {@code y} = latitude), the first one is the start; up to 10
     *     for the API; the list is copied
     * @param type what to minimise: {@link OptimizeType#TIME} or {@link OptimizeType#DISTANCE}
     * @return the builder
     * @throws IllegalArgumentException when {@code points} is {@code null}, empty or contains {@code null}, or
     *     {@code type} is {@code null}
     */
    public static Builder builder(List<Point> points, OptimizeType type) {
        List<Point> copy = Checks.requireItems(
                points,
                "OptimizeRequest.builder",
                "points",
                "pass the points to visit, e.g. List.of(new Point(21.0073642, 52.2297), new Point(18.6282, 54.352))");
        OptimizeType checkedType = Checks.requireNonNull(
                type, "OptimizeRequest.builder", "type", "pass OptimizeType.TIME or OptimizeType.DISTANCE");
        return new Builder(copy, checkedType);
    }

    @Override
    protected Map<String, Object> namedParams() {
        List<List<Double>> coordinates = new ArrayList<>(points.size());
        for (Point point : points) {
            coordinates.add(point.toList());
        }
        Map<String, Object> named = new LinkedHashMap<>();
        named.put("points", coordinates);
        named.put("type", type.apiValue());
        named.put("fixedEnd", fixedEnd);
        named.put("route", route);
        named.put("object", object);
        return named;
    }

    /** Builder of {@link OptimizeRequest}; {@code fixedEnd} defaults to {@code true}. */
    public static final class Builder extends ApiRequest.Builder<Builder> {

        private final List<Point> points;
        private final OptimizeType type;
        private boolean fixedEnd = true;
        private Map<String, Object> route;
        private Map<String, Object> object;

        private Builder(List<Point> points, OptimizeType type) {
            this.points = points;
            this.type = type;
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
         * Sets the route settings: {@code avoidTolls}, {@code avoidFerries}, {@code avoidHighways}.
         *
         * @param route the settings, e.g. {@code Map.of("avoidTolls", true)}, or {@code null} not to send them; the
         *     map is copied in its key order
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
        public OptimizeRequest build() {
            return new OptimizeRequest(this);
        }
    }
}
