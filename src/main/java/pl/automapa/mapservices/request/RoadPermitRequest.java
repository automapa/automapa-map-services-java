package pl.automapa.mapservices.request;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Request of {@code RoadPermit.add} and {@code RoadPermit.overwrite}: a road permit document with the permitted
 * vehicle limits inside an area.
 *
 * <pre>{@code
 * Map<String, Object> poly = new LinkedHashMap<>();
 * poly.put("type", "Polygon");
 * poly.put("coordinates", List.of(List.of(
 *         List.of(20.9931, 50.0106), List.of(20.9944, 50.0108), List.of(20.9941, 50.0115),
 *         List.of(20.9931, 50.0106))));
 * RoadPermitRequest request = RoadPermitRequest.builder(Map.of("tr_tonnage", 12), poly)
 *         .documentName("ZDiK.TO.461.219.2020.AO")
 *         .dateStart("2026-01-01")
 *         .dateEnd("2026-12-31")
 *         .build();
 * // {"permissions": {"tr_tonnage": 12}, "poly": {"type": "Polygon", "coordinates": [...]},
 * //  "date_start": "2026-01-01", "date_end": "2026-12-31", "document_name": "ZDiK.TO.461.219.2020.AO"}
 * }</pre>
 *
 * <p>{@code permissions} maps limit names of the API (e.g. {@code tr_tonnage}, {@code tr_tonn_axis},
 * {@code tr_height}) to numbers; {@code poly} is a GeoJSON {@code Polygon} with {@code [longitude, latitude]}
 * positions. The SDK does not validate the geometry, dates or lengths; the API does. Unset optional fields are left
 * out; the endpoint adds {@code environment}. An {@code id} given in {@code extra} sends the request to
 * {@code overwrite} instead of {@code add}.
 */
public final class RoadPermitRequest extends ApiRequest {

    private final Map<String, Object> permissions;
    private final Map<String, Object> poly;
    private final String dateStart;
    private final String dateEnd;
    private final String description;
    private final String documentName;
    private final String location;
    private final Boolean isValidAfterExpiry;

    private RoadPermitRequest(Builder builder) {
        super(builder);
        this.permissions = builder.permissions;
        this.poly = builder.poly;
        this.dateStart = builder.dateStart;
        this.dateEnd = builder.dateEnd;
        this.description = builder.description;
        this.documentName = builder.documentName;
        this.location = builder.location;
        this.isValidAfterExpiry = builder.isValidAfterExpiry;
    }

    /**
     * Starts a request for the permitted limits inside an area.
     *
     * @param permissions limit names of the API mapped to numbers, e.g. {@code Map.of("tr_tonnage", 12)}; the map is
     *     copied in its key order
     * @param poly the area as a GeoJSON {@code Polygon}: {@code type} {@code Polygon} and {@code coordinates} with
     *     closed rings of {@code [longitude, latitude]} positions; the map is copied in its key order
     * @return the builder
     * @throws IllegalArgumentException when {@code permissions} or {@code poly} is {@code null} or empty
     */
    public static Builder builder(Map<String, ? extends Number> permissions, Map<String, ?> poly) {
        String operation = "RoadPermitRequest.builder";
        Map<String, Object> permissionsCopy = Checks.requireEntries(
                permissions,
                operation,
                "permissions",
                "pass the permitted limits, e.g. Map.of(\"tr_tonnage\", 12) or Map.of(\"tr_height\", 4.0)");
        Map<String, Object> polyCopy = Checks.requireEntries(
                poly,
                operation,
                "poly",
                "pass the area as a GeoJSON Polygon, e.g. Map.of(\"type\", \"Polygon\", \"coordinates\","
                        + " List.of(ring)) with a closed ring of List.of(longitude, latitude) positions");
        return new Builder(permissionsCopy, polyCopy);
    }

    @Override
    protected Map<String, Object> namedParams() {
        Map<String, Object> named = new LinkedHashMap<>();
        named.put("permissions", permissions);
        named.put("poly", poly);
        named.put("date_start", dateStart);
        named.put("date_end", dateEnd);
        named.put("description", description);
        named.put("document_name", documentName);
        named.put("location", location);
        named.put("is_valid_after_expiry", isValidAfterExpiry);
        return named;
    }

    /** Builder of {@link RoadPermitRequest}; unset optional fields are left out of the request. */
    public static final class Builder extends ApiRequest.Builder<Builder> {

        private final Map<String, Object> permissions;
        private final Map<String, Object> poly;
        private String dateStart;
        private String dateEnd;
        private String description;
        private String documentName;
        private String location;
        private Boolean isValidAfterExpiry;

        private Builder(Map<String, Object> permissions, Map<String, Object> poly) {
            this.permissions = permissions;
            this.poly = poly;
        }

        /**
         * Sets the first day of validity (sent as {@code date_start}).
         *
         * @param dateStart date in the format of the API, e.g. {@code 2026-01-01}, or {@code null} to leave it out
         * @return this builder
         */
        public Builder dateStart(String dateStart) {
            this.dateStart = dateStart;
            return this;
        }

        /**
         * Sets the last day of validity (sent as {@code date_end}).
         *
         * @param dateEnd date in the format of the API, e.g. {@code 2026-12-31}, or {@code null} to leave it out
         * @return this builder
         */
        public Builder dateEnd(String dateEnd) {
            this.dateEnd = dateEnd;
            return this;
        }

        /**
         * Sets the description of the document.
         *
         * @param description free text, or {@code null} to leave it out
         * @return this builder
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the name of the document (sent as {@code document_name}).
         *
         * @param documentName document name, e.g. {@code ZDiK.TO.461.219.2020.AO}, or {@code null} to leave it out
         * @return this builder
         */
        public Builder documentName(String documentName) {
            this.documentName = documentName;
            return this;
        }

        /**
         * Sets the place the document applies to.
         *
         * @param location place name, e.g. {@code Tarnow}, or {@code null} to leave it out
         * @return this builder
         */
        public Builder location(String location) {
            this.location = location;
            return this;
        }

        /**
         * Sets whether the permit stays valid after {@code date_end} (sent as {@code is_valid_after_expiry}).
         *
         * @param isValidAfterExpiry {@code true} to keep it valid, {@code false} not to, or {@code null} to leave it
         *     out
         * @return this builder
         */
        public Builder isValidAfterExpiry(Boolean isValidAfterExpiry) {
            this.isValidAfterExpiry = isValidAfterExpiry;
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
        public RoadPermitRequest build() {
            return new RoadPermitRequest(this);
        }
    }
}
