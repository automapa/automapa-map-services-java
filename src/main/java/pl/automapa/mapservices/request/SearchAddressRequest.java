package pl.automapa.mapservices.request;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Request of {@code Autocomplete.searchAddress}: address objects by city, street, building number or postal code.
 *
 * <pre>{@code
 * Map<String, Object> query = new LinkedHashMap<>();
 * query.put("city", "Warszawa");
 * query.put("street", "Dom");
 * query.put("number", "");
 * query.put("zipcode", "");
 * SearchAddressRequest request = SearchAddressRequest.builder(query, "street")
 *         .limit(5)
 *         .selected(List.of("city"))
 *         .build();
 * // {"query": {"city": "Warszawa", "street": "Dom", "number": "", "zipcode": ""}, "source": "street",
 * //  "limit": 5, "selected": ["city"]}
 * }</pre>
 *
 * <p>{@code limit} and {@code selected} are sent only when set.
 */
public final class SearchAddressRequest extends ApiRequest {

    private final Map<String, Object> query;
    private final String source;
    private final Integer limit;
    private final List<String> selected;

    private SearchAddressRequest(Builder builder) {
        super(builder);
        this.query = builder.query;
        this.source = builder.source;
        this.limit = builder.limit;
        this.selected = builder.selected;
    }

    /**
     * Starts a request for search criteria.
     *
     * @param query search criteria with the keys {@code city}, {@code street}, {@code number} and {@code zipcode},
     *     e.g. {@code Map.of("city", "Warszawa", "street", "Dom", "number", "", "zipcode", "")}; the map is copied
     *     in its key order
     * @param source the field being searched: {@code place}, {@code street}, {@code number} or {@code zipcode}
     * @return the builder
     * @throws IllegalArgumentException when {@code query} is {@code null} or empty, or {@code source} is
     *     {@code null} or blank
     */
    public static Builder builder(Map<String, ?> query, String source) {
        Map<String, Object> copy = Checks.requireEntries(
                query,
                "SearchAddressRequest.builder",
                "query",
                "pass the criteria, e.g. Map.of(\"city\", \"Warszawa\", \"street\", \"Dom\", \"number\", \"\", "
                        + "\"zipcode\", \"\")");
        String checkedSource = Checks.requireText(
                source,
                "SearchAddressRequest.builder",
                "source",
                "pass the field being searched: \"place\", \"street\", \"number\" or \"zipcode\"");
        return new Builder(copy, checkedSource);
    }

    @Override
    protected Map<String, Object> namedParams() {
        Map<String, Object> named = new LinkedHashMap<>();
        named.put("query", query);
        named.put("source", source);
        named.put("limit", limit);
        named.put("selected", selected);
        return named;
    }

    /** Builder of {@link SearchAddressRequest}; unset optional fields are left out of the request. */
    public static final class Builder extends ApiRequest.Builder<Builder> {

        private final Map<String, Object> query;
        private final String source;
        private Integer limit;
        private List<String> selected;

        private Builder(Map<String, Object> query, String source) {
            this.query = query;
            this.source = source;
        }

        /**
         * Limits the number of results.
         *
         * @param limit maximum number of results, or {@code null} to use the API default (10)
         * @return this builder
         */
        public Builder limit(Integer limit) {
            this.limit = limit;
            return this;
        }

        /**
         * Marks the fields already confirmed by the user.
         *
         * @param selected field names, e.g. {@code List.of("city")}, or {@code null} for none (the list is copied)
         * @return this builder
         */
        public Builder selected(List<String> selected) {
            this.selected = selected == null ? null : Collections.unmodifiableList(new ArrayList<>(selected));
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
        public SearchAddressRequest build() {
            return new SearchAddressRequest(this);
        }
    }
}
