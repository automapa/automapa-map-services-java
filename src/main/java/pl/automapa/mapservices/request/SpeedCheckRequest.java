package pl.automapa.mapservices.request;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Request of {@code Road.speedCheck}: the speed limits for cars and trucks at one point.
 *
 * <pre>{@code
 * SpeedCheckRequest request = SpeedCheckRequest.builder(15.2154636, 52.3118895)
 *         .dir(350)
 *         .build();
 * // {"x": 15.2154636, "y": 52.3118895, "dir": 350}
 * }</pre>
 *
 * <p>{@code x} is the longitude and {@code y} the latitude; unset options are left out.
 */
public final class SpeedCheckRequest extends ApiRequest {

    private final double x;
    private final double y;
    private final Integer dir;
    private final Integer distLimit;

    private SpeedCheckRequest(Builder builder) {
        super(builder);
        this.x = builder.x;
        this.y = builder.y;
        this.dir = builder.dir;
        this.distLimit = builder.distLimit;
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
        String operation = "SpeedCheckRequest.builder";
        String example = "SpeedCheckRequest.builder(15.2154636, 52.3118895)";
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
        return named;
    }

    /** Builder of {@link SpeedCheckRequest}; unset options are left out of the request. */
    public static final class Builder extends ApiRequest.Builder<Builder> {

        private final double x;
        private final double y;
        private Integer dir;
        private Integer distLimit;

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

        @Override
        protected Builder self() {
            return this;
        }

        /**
         * Builds the request.
         *
         * @return the request
         */
        public SpeedCheckRequest build() {
            return new SpeedCheckRequest(this);
        }
    }
}
