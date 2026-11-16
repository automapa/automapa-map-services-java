package pl.automapa.mapservices.request;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Request of {@code Geocoder.revgeocodemulti}: many points to addresses in one call.
 *
 * <pre>{@code
 * RevGeocodeMultiRequest request = RevGeocodeMultiRequest.builder(
 *                 List.of(new Point(21.0073642, 52.18288), new Point(18.6282, 54.3520)))
 *         .build();
 * // {"points": [[21.0073642, 52.18288], [18.6282, 54.352]], "params": {}}
 * }</pre>
 *
 * <p>The {@code params} object is always sent, also empty ({@code {}}).
 */
public final class RevGeocodeMultiRequest extends ApiRequest {

    private final List<Point> points;
    private final Boolean snapToBld;
    private final Integer snapToBldRadius;
    private final Boolean getPostalCode;

    private RevGeocodeMultiRequest(Builder builder) {
        super(builder);
        this.points = builder.points;
        this.snapToBld = builder.snapToBld;
        this.snapToBldRadius = builder.snapToBldRadius;
        this.getPostalCode = builder.getPostalCode;
    }

    /**
     * Starts a request for points.
     *
     * @param points the points ({@code x} = longitude, {@code y} = latitude); the list is copied
     * @return the builder
     * @throws IllegalArgumentException when {@code points} is {@code null}, empty or contains {@code null}
     */
    public static Builder builder(List<Point> points) {
        return new Builder(Checks.requireItems(
                points,
                "RevGeocodeMultiRequest.builder",
                "points",
                "pass e.g. List.of(new Point(21.0073642, 52.18288))"));
    }

    @Override
    protected Map<String, Object> namedParams() {
        List<List<Double>> coordinates = new ArrayList<>(points.size());
        for (Point point : points) {
            coordinates.add(point.toList());
        }
        Map<String, Object> named = new LinkedHashMap<>();
        named.put("points", coordinates);
        named.put("params", RevGeocodeOptions.params(snapToBld, snapToBldRadius, getPostalCode));
        return named;
    }

    /** Builder of {@link RevGeocodeMultiRequest}; unset options are left out of {@code params}. */
    public static final class Builder extends ApiRequest.Builder<Builder> {

        private final List<Point> points;
        private Boolean snapToBld;
        private Integer snapToBldRadius;
        private Boolean getPostalCode;

        private Builder(List<Point> points) {
            this.points = points;
        }

        /**
         * Snaps the results to the nearest buildings.
         *
         * @param snapToBld {@code true} to snap, {@code false} not to, {@code null} to use the API default
         * @return this builder
         */
        public Builder snapToBld(Boolean snapToBld) {
            this.snapToBld = snapToBld;
            return this;
        }

        /**
         * Sets the radius of the building search.
         *
         * @param snapToBldRadius radius in metres, or {@code null} to use the API default
         * @return this builder
         */
        public Builder snapToBldRadius(Integer snapToBldRadius) {
            this.snapToBldRadius = snapToBldRadius;
            return this;
        }

        /**
         * Includes the postal codes in the results when available.
         *
         * @param getPostalCode {@code true} to include them, or {@code null} to use the API default
         * @return this builder
         */
        public Builder getPostalCode(Boolean getPostalCode) {
            this.getPostalCode = getPostalCode;
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
        public RevGeocodeMultiRequest build() {
            return new RevGeocodeMultiRequest(this);
        }
    }
}
