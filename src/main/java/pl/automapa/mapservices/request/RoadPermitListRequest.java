package pl.automapa.mapservices.request;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Request of {@code RoadPermit.list}: optional filters of the road permit documents.
 *
 * <pre>{@code
 * RoadPermitListRequest request = RoadPermitListRequest.builder()
 *         .documentName("ZDiK.TO.461.219.2020.AO")
 *         .build();
 * // {"document_name": "ZDiK.TO.461.219.2020.AO"}
 * }</pre>
 *
 * <p>Unset filters are left out and the API returns every document of the environment; the endpoint adds
 * {@code environment}.
 */
public final class RoadPermitListRequest extends ApiRequest {

    private final String id;
    private final String documentName;

    private RoadPermitListRequest(Builder builder) {
        super(builder);
        this.id = builder.id;
        this.documentName = builder.documentName;
    }

    /**
     * Starts a request without filters.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected Map<String, Object> namedParams() {
        Map<String, Object> named = new LinkedHashMap<>();
        named.put("id", id);
        named.put("document_name", documentName);
        return named;
    }

    /** Builder of {@link RoadPermitListRequest}; unset filters are left out of the request. */
    public static final class Builder extends ApiRequest.Builder<Builder> {

        private String id;
        private String documentName;

        private Builder() {}

        /**
         * Returns only the document with this id.
         *
         * @param id document id returned by {@code add} or {@code list}, or {@code null} for no filter
         * @return this builder
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * Returns only the documents with this name (sent as {@code document_name}).
         *
         * @param documentName document name, e.g. {@code ZDiK.TO.461.219.2020.AO}, or {@code null} for no filter
         * @return this builder
         */
        public Builder documentName(String documentName) {
            this.documentName = documentName;
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
        public RoadPermitListRequest build() {
            return new RoadPermitListRequest(this);
        }
    }
}
