package pl.automapa.mapservices.request;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Request of {@code Road.getSegmentInfo}: the road segment nearest to a point.
 *
 * <pre>{@code
 * SegmentInfoRequest request = SegmentInfoRequest.builder(21.00736, 52.18288)
 *         .distLimit(50)
 *         .minCategory(1)
 *         .build();
 * // {"x": 21.00736, "y": 52.18288, "distLimit": 50, "minCategory": 1}
 * }</pre>
 *
 * <p>{@code x} is the longitude and {@code y} the latitude; unset options are left out and the API uses its
 * defaults.
 */
public final class SegmentInfoRequest extends ApiRequest {

    private final double x;
    private final double y;
    private final Integer dir;
    private final Integer distLimit;
    private final Integer minCategory;
    private final Boolean ignoreNoWaySegments;
    private final Boolean treatTruckTollsAsTolls;
    private final Boolean ignoreSegmentsUnderConstruction;
    private final Boolean ignorePhysicallyBlockedSegs;
    private final Integer useTolls;
    private final Integer useFerries;
    private final Integer useHighways;

    private SegmentInfoRequest(Builder builder) {
        super(builder);
        this.x = builder.x;
        this.y = builder.y;
        this.dir = builder.dir;
        this.distLimit = builder.distLimit;
        this.minCategory = builder.minCategory;
        this.ignoreNoWaySegments = builder.ignoreNoWaySegments;
        this.treatTruckTollsAsTolls = builder.treatTruckTollsAsTolls;
        this.ignoreSegmentsUnderConstruction = builder.ignoreSegmentsUnderConstruction;
        this.ignorePhysicallyBlockedSegs = builder.ignorePhysicallyBlockedSegs;
        this.useTolls = builder.useTolls;
        this.useFerries = builder.useFerries;
        this.useHighways = builder.useHighways;
    }

    /**
     * Starts a request for a point.
     *
     * @param x longitude in degrees
     * @param y latitude in degrees
     * @return the builder
     * @throws IllegalArgumentException when a coordinate is NaN or infinite
     */
    public static Builder builder(double x, double y) {
        String operation = "SegmentInfoRequest.builder";
        String example = "SegmentInfoRequest.builder(21.00736, 52.18288)";
        return new Builder(
                Checks.requireFinite(x, operation, "x (longitude)", example),
                Checks.requireFinite(y, operation, "y (latitude)", example));
    }

    @Override
    protected Map<String, Object> namedParams() {
        Map<String, Object> named = new LinkedHashMap<>();
        named.put("x", x);
        named.put("y", y);
        named.put("dir", dir);
        named.put("distLimit", distLimit);
        named.put("minCategory", minCategory);
        named.put("ignoreNoWaySegments", ignoreNoWaySegments);
        named.put("treatTruckTollsAsTolls", treatTruckTollsAsTolls);
        named.put("ignoreSegmentsUnderConstruction", ignoreSegmentsUnderConstruction);
        named.put("ignorePhysicallyBlockedSegs", ignorePhysicallyBlockedSegs);
        named.put("useTolls", useTolls);
        named.put("useFerries", useFerries);
        named.put("useHighways", useHighways);
        return named;
    }

    /** Builder of {@link SegmentInfoRequest}; unset options are left out of the request. */
    public static final class Builder extends ApiRequest.Builder<Builder> {

        private final double x;
        private final double y;
        private Integer dir;
        private Integer distLimit;
        private Integer minCategory;
        private Boolean ignoreNoWaySegments;
        private Boolean treatTruckTollsAsTolls;
        private Boolean ignoreSegmentsUnderConstruction;
        private Boolean ignorePhysicallyBlockedSegs;
        private Integer useTolls;
        private Integer useFerries;
        private Integer useHighways;

        private Builder(double x, double y) {
            this.x = x;
            this.y = y;
        }

        /**
         * Sets the direction of travel.
         *
         * @param dir degrees 0-359 (-1 = not set), or {@code null} to use the API default
         * @return this builder
         */
        public Builder dir(Integer dir) {
            this.dir = dir;
            return this;
        }

        /**
         * Sets the search radius.
         *
         * @param distLimit radius in metres (API default 50), or {@code null} to use the API default
         * @return this builder
         */
        public Builder distLimit(Integer distLimit) {
            this.distLimit = distLimit;
            return this;
        }

        /**
         * Sets the minimum road category the point may be snapped to.
         *
         * @param minCategory road category (API default 0), or {@code null} to use the API default
         * @return this builder
         */
        public Builder minCategory(Integer minCategory) {
            this.minCategory = minCategory;
            return this;
        }

        /**
         * Ignores segments closed to traffic in both directions.
         *
         * @param ignoreNoWaySegments {@code true} to ignore them (API default {@code false}), or {@code null} to use
         *     the API default
         * @return this builder
         */
        public Builder ignoreNoWaySegments(Boolean ignoreNoWaySegments) {
            this.ignoreNoWaySegments = ignoreNoWaySegments;
            return this;
        }

        /**
         * Treats truck tolls as tolls.
         *
         * @param treatTruckTollsAsTolls {@code true} to take truck tolls into account (API default {@code false}), or
         *     {@code null} to use the API default
         * @return this builder
         */
        public Builder treatTruckTollsAsTolls(Boolean treatTruckTollsAsTolls) {
            this.treatTruckTollsAsTolls = treatTruckTollsAsTolls;
            return this;
        }

        /**
         * Ignores segments under construction.
         *
         * @param ignoreSegmentsUnderConstruction {@code true} to ignore them (API default {@code false}), or
         *     {@code null} to use the API default
         * @return this builder
         */
        public Builder ignoreSegmentsUnderConstruction(Boolean ignoreSegmentsUnderConstruction) {
            this.ignoreSegmentsUnderConstruction = ignoreSegmentsUnderConstruction;
            return this;
        }

        /**
         * Ignores physically blocked segments.
         *
         * @param ignorePhysicallyBlockedSegs {@code true} to ignore them (API default {@code false}), or
         *     {@code null} to use the API default
         * @return this builder
         */
        public Builder ignorePhysicallyBlockedSegs(Boolean ignorePhysicallyBlockedSegs) {
            this.ignorePhysicallyBlockedSegs = ignorePhysicallyBlockedSegs;
            return this;
        }

        /**
         * Filters toll segments.
         *
         * @param useTolls {@code 1} to accept toll roads, {@code 0} to reject them, {@code -1} not to check (API
         *     default), or {@code null} to use the API default
         * @return this builder
         */
        public Builder useTolls(Integer useTolls) {
            this.useTolls = useTolls;
            return this;
        }

        /**
         * Filters ferry connections.
         *
         * @param useFerries {@code 1} to accept ferries, {@code 0} to reject them, {@code -1} not to check (API
         *     default), or {@code null} to use the API default
         * @return this builder
         */
        public Builder useFerries(Integer useFerries) {
            this.useFerries = useFerries;
            return this;
        }

        /**
         * Filters highway segments.
         *
         * @param useHighways {@code 1} to accept highways, {@code 0} to ignore them, {@code -1} not to check (API
         *     default), or {@code null} to use the API default
         * @return this builder
         */
        public Builder useHighways(Integer useHighways) {
            this.useHighways = useHighways;
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
        public SegmentInfoRequest build() {
            return new SegmentInfoRequest(this);
        }
    }
}
