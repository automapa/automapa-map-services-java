package pl.automapa.mapservices.request;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Request of {@code Autocomplete.search}: places and points of interest matching a phrase.
 *
 * <pre>{@code
 * AutocompleteSearchRequest request = AutocompleteSearchRequest.builder("Warszawa Domaniewska")
 *         .types(List.of("place"))
 *         .build();
 * // {"query": "Warszawa Domaniewska", "types": ["place"]}
 * }</pre>
 *
 * <p>{@code types} is sent only when set; without it the API returns both places and points of interest. The
 * coordinates of the results are in {@code coords.x} (longitude) and {@code coords.y} (latitude).
 */
public final class AutocompleteSearchRequest extends ApiRequest {

    private final String query;
    private final List<String> types;

    private AutocompleteSearchRequest(Builder builder) {
        super(builder);
        this.query = builder.query;
        this.types = builder.types;
    }

    /**
     * Starts a request for a search phrase.
     *
     * @param query the phrase typed by the user, e.g. {@code Warszawa Domaniewska 37}
     * @return the builder
     * @throws IllegalArgumentException when {@code query} is {@code null} or blank
     */
    public static Builder builder(String query) {
        return new Builder(Checks.requireText(
                query,
                "AutocompleteSearchRequest.builder",
                "query",
                "pass the phrase typed by the user, e.g. builder(\"Warszawa Domaniewska\")"));
    }

    @Override
    protected Map<String, Object> namedParams() {
        Map<String, Object> named = new LinkedHashMap<>();
        named.put("query", query);
        named.put("types", types);
        return named;
    }

    /** Builder of {@link AutocompleteSearchRequest}; unset {@code types} are left out of the request. */
    public static final class Builder extends ApiRequest.Builder<Builder> {

        private final String query;
        private List<String> types;

        private Builder(String query) {
            this.query = query;
        }

        /**
         * Filters the results by type.
         *
         * @param types {@code place}, {@code poi} or both; {@code null} returns both (the list is copied)
         * @return this builder
         */
        public Builder types(List<String> types) {
            this.types = types == null ? null : Collections.unmodifiableList(new ArrayList<>(types));
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
        public AutocompleteSearchRequest build() {
            return new AutocompleteSearchRequest(this);
        }
    }
}
