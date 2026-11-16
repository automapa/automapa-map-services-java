package pl.automapa.mapservices.request;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Request of {@code Geocoder.revgeocode}: one point to an address.
 *
 * <pre>{@code
 * RevGeocodeRequest request = RevGeocodeRequest.builder(new Point(21.0073642, 52.18288))
 *         .snapToBld(true)
 *         .build();
 * // {"point": [21.0073642, 52.18288], "params": {"snapToBld": true}}
 * }</pre>
 *
 * <p>The {@code params} object is always sent, also empty ({@code {}}).
 */
public final class RevGeocodeRequest extends ApiRequest {

    private final Point point;
    private final Boolean snapToBld;
    private final Integer snapToBldRadius;
    private final Boolean getPostalCode;

    private RevGeocodeRequest(Builder builder) {
        super(builder);
        this.point = builder.point;
        this.snapToBld = builder.snapToBld;
        this.snapToBldRadius = builder.snapToBldRadius;
        this.getPostalCode = builder.getPostalCode;
    }

    /**
     * Starts a request for a point.
     *
     * @param point the point ({@code x} = longitude, {@code y} = latitude)
     * @return the builder
     * @throws IllegalArgumentException when {@code point} is {@code null}
     */
    public static Builder builder(Point point) {
        return new Builder(Checks.requireNonNull(
                point, "RevGeocodeRequest.builder", "point", "pass e.g. new Point(21.0073642, 52.18288)"));
    }

    @Override
    protected Map<String, Object> namedParams() {
        Map<String, Object> named = new LinkedHashMap<>();
        named.put("point", point.toList());
        named.put("params", RevGeocodeOptions.params(snapToBld, snapToBldRadius, getPostalCode));
        return named;
    }

    /** Builder of {@link RevGeocodeRequest}; unset options are left out of {@code params}. */
    public static final class Builder extends ApiRequest.Builder<Builder> {

        private final Point point;
        private Boolean snapToBld;
        private Integer snapToBldRadius;
        private Boolean getPostalCode;

        private Builder(Point point) {
            this.point = point;
        }

        /**
         * Snaps the result to the nearest building.
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
         * Includes the postal code in the result when available.
         *
         * @param getPostalCode {@code true} to include it, or {@code null} to use the API default
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
        public RevGeocodeRequest build() {
            return new RevGeocodeRequest(this);
        }
    }
}
