package pl.automapa.mapservices.request;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Request of {@code Geocoder.geocode}: one structured address to coordinates.
 *
 * <pre>{@code
 * GeocodeRequest request = GeocodeRequest.builder("Warszawa")
 *         .street("Domaniewska")
 *         .house("37")
 *         .maxResults(1)
 *         .build();
 * // {"address": {"city": "Warszawa", "street": "Domaniewska", "house": "37"}, "maxResults": 1}
 * }</pre>
 *
 * <p>The {@code address} object is always sent and contains only the fields that are neither {@code null} nor
 * empty; {@code maxResults} is sent only when set.
 */
public final class GeocodeRequest extends ApiRequest {

    private final String city;
    private final String country;
    private final String province;
    private final String district;
    private final String community;
    private final String quarter;
    private final String pcode;
    private final String street;
    private final String house;
    private final Integer maxResults;

    private GeocodeRequest(Builder builder) {
        super(builder);
        this.city = builder.city;
        this.country = builder.country;
        this.province = builder.province;
        this.district = builder.district;
        this.community = builder.community;
        this.quarter = builder.quarter;
        this.pcode = builder.pcode;
        this.street = builder.street;
        this.house = builder.house;
        this.maxResults = builder.maxResults;
    }

    /**
     * Starts a request for a city.
     *
     * @param city city name; {@code ""} is allowed and left out of the address (e.g. to geocode a postal code only)
     * @return the builder
     * @throws IllegalArgumentException when {@code city} is {@code null}
     */
    public static Builder builder(String city) {
        return new Builder(Checks.requireNonNull(
                city,
                "GeocodeRequest.builder",
                "city",
                "pass the city name, e.g. builder(\"Warszawa\"), or \"\" to leave it out of the address"));
    }

    @Override
    protected Map<String, Object> namedParams() {
        Map<String, Object> address = new LinkedHashMap<>();
        putText(address, "city", city);
        putText(address, "country", country);
        putText(address, "province", province);
        putText(address, "district", district);
        putText(address, "community", community);
        putText(address, "quarter", quarter);
        putText(address, "pcode", pcode);
        putText(address, "street", street);
        putText(address, "house", house);

        Map<String, Object> named = new LinkedHashMap<>();
        named.put("address", address);
        named.put("maxResults", maxResults);
        return named;
    }

    private static void putText(Map<String, Object> address, String key, String value) {
        if (value != null && !value.isEmpty()) {
            address.put(key, value);
        }
    }

    /** Builder of {@link GeocodeRequest}; {@code null} or empty values are left out of the address. */
    public static final class Builder extends ApiRequest.Builder<Builder> {

        private final String city;
        private String country;
        private String province;
        private String district;
        private String community;
        private String quarter;
        private String pcode;
        private String street;
        private String house;
        private Integer maxResults;

        private Builder(String city) {
            this.city = city;
        }

        /**
         * Sets the country.
         *
         * @param country country name or code, e.g. {@code PL}
         * @return this builder
         */
        public Builder country(String country) {
            this.country = country;
            return this;
        }

        /**
         * Sets the province (voivodeship).
         *
         * @param province province name, e.g. {@code mazowieckie}
         * @return this builder
         */
        public Builder province(String province) {
            this.province = province;
            return this;
        }

        /**
         * Sets the district (powiat).
         *
         * @param district district name
         * @return this builder
         */
        public Builder district(String district) {
            this.district = district;
            return this;
        }

        /**
         * Sets the community (gmina).
         *
         * @param community community name
         * @return this builder
         */
        public Builder community(String community) {
            this.community = community;
            return this;
        }

        /**
         * Sets the quarter of the city.
         *
         * @param quarter quarter name, e.g. {@code Mokotow}
         * @return this builder
         */
        public Builder quarter(String quarter) {
            this.quarter = quarter;
            return this;
        }

        /**
         * Sets the postal code.
         *
         * @param pcode postal code, e.g. {@code 02-672}
         * @return this builder
         */
        public Builder pcode(String pcode) {
            this.pcode = pcode;
            return this;
        }

        /**
         * Sets the street.
         *
         * @param street street name, e.g. {@code Domaniewska}
         * @return this builder
         */
        public Builder street(String street) {
            this.street = street;
            return this;
        }

        /**
         * Sets the house number.
         *
         * @param house house number, e.g. {@code 37} or {@code 17A}
         * @return this builder
         */
        public Builder house(String house) {
            this.house = house;
            return this;
        }

        /**
         * Limits the number of results.
         *
         * @param maxResults maximum number of results, or {@code null} to use the API default
         * @return this builder
         */
        public Builder maxResults(Integer maxResults) {
            this.maxResults = maxResults;
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
        public GeocodeRequest build() {
            return new GeocodeRequest(this);
        }
    }
}
