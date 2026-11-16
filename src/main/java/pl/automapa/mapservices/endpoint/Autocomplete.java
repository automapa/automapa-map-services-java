package pl.automapa.mapservices.endpoint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.request.AutocompleteSearchRequest;
import pl.automapa.mapservices.request.SearchAddressRequest;
import pl.automapa.mapservices.response.ApiResponse;

/**
 * The {@code Autocomplete} API service: suggestions while the user types an address.
 *
 * <pre>{@code
 * ApiResponse places = client.autocomplete().search(
 *         AutocompleteSearchRequest.builder("Warszawa Domaniewska").types(List.of("place")).build());
 * List<String> codes = client.autocomplete().getPostalCodesForStreet("Warszawa", "Domaniewska");
 * }</pre>
 *
 * <p>Every method requires a session, which the client opens when needed. The results are in
 * {@code result.items}; the coordinates of an item are in {@code coords.x} (longitude) and {@code coords.y}
 * (latitude), unlike the flat {@code x}/{@code y} of the {@code Geocoder}.
 */
public final class Autocomplete {

    /** Default {@code limit} of {@link #getPostalCodesForStreet(String, String)}. */
    public static final int DEFAULT_POSTAL_CODES_LIMIT = 100;

    private static final String SERVICE = "Autocomplete";

    private final ApiClient client;

    /**
     * Creates the endpoint; usually obtained from {@link ApiClient#autocomplete()}.
     *
     * @param client client sending the requests
     * @throws IllegalArgumentException when {@code client} is {@code null}
     */
    public Autocomplete(ApiClient client) {
        this.client = EndpointParams.requireNonNull(client, "new Autocomplete", "client", "use client.autocomplete()");
    }

    /**
     * Searches for places and points of interest by phrase.
     *
     * @param request the phrase and filters, e.g. {@code AutocompleteSearchRequest.builder("Warszawa").build()}
     * @return the response; the result is {@code {"items": [...]}} with {@code type}, {@code name}, the address
     *     fields and {@code coords}
     * @throws IllegalArgumentException when {@code request} is {@code null}
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     */
    public ApiResponse search(AutocompleteSearchRequest request) {
        EndpointParams.requireNonNull(
                request,
                "Autocomplete.search",
                "request",
                "build one with AutocompleteSearchRequest.builder(query)...build()");
        return client.call(SERVICE, "search", request.toParams());
    }

    /**
     * Searches for address objects by city, street, building number or postal code.
     *
     * @param request the criteria and the searched field, e.g.
     *     {@code SearchAddressRequest.builder(query, "street").limit(5).build()}
     * @return the response; the result is {@code {"items": [...]}} with {@code city}, {@code street},
     *     {@code zipcode} and {@code count}
     * @throws IllegalArgumentException when {@code request} is {@code null}
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     */
    public ApiResponse searchAddress(SearchAddressRequest request) {
        EndpointParams.requireNonNull(
                request,
                "Autocomplete.searchAddress",
                "request",
                "build one with SearchAddressRequest.builder(query, source)...build()");
        return client.call(SERVICE, "searchAddress", request.toParams());
    }

    /**
     * Returns the unique postal codes of a street, looking at up to {@value #DEFAULT_POSTAL_CODES_LIMIT} results.
     *
     * @param city city name, e.g. {@code Warszawa}
     * @param street street name, e.g. {@code Domaniewska}
     * @return the postal codes sorted ascending, e.g. {@code [02-672, 02-676]}; empty when none is found
     * @throws IllegalArgumentException when {@code city} or {@code street} is {@code null} or blank
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     * @see #getPostalCodesForStreet(String, String, int)
     */
    public List<String> getPostalCodesForStreet(String city, String street) {
        return getPostalCodesForStreet(city, street, DEFAULT_POSTAL_CODES_LIMIT);
    }

    /**
     * Returns the unique postal codes of a street.
     *
     * <p>Calls {@code searchAddress} with {@code source = "zipcode"}, the confirmed fields {@code city} and
     * {@code street}, and collects the distinct non-empty {@code zipcode} values of the items. The codes are read
     * from the native response, so the default format of the client does not matter. Very long streets may give an
     * incomplete list when the API caps the number of results.
     *
     * @param city city name, e.g. {@code Warszawa}
     * @param street street name, e.g. {@code Domaniewska}
     * @param limit maximum number of results the API looks at
     * @return a new mutable list of the postal codes sorted ascending; empty when none is found
     * @throws IllegalArgumentException when {@code city} or {@code street} is {@code null} or blank
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     */
    public List<String> getPostalCodesForStreet(String city, String street, int limit) {
        String operation = "Autocomplete.getPostalCodesForStreet";
        EndpointParams.requireText(city, operation, "city", "pass the city name, e.g. \"Warszawa\"");
        EndpointParams.requireText(street, operation, "street", "pass the street name, e.g. \"Domaniewska\"");

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("city", city);
        query.put("street", street);
        query.put("number", "");
        query.put("zipcode", "");
        ApiResponse response = searchAddress(SearchAddressRequest.builder(query, "zipcode")
                .limit(limit)
                .selected(List.of("city", "street"))
                .build());

        TreeSet<String> codes = new TreeSet<>();
        for (Object item : items(response.raw().get("result"))) {
            if (item instanceof Map) {
                Object zipcode = ((Map<?, ?>) item).get("zipcode");
                if (zipcode instanceof String && !((String) zipcode).isEmpty()) {
                    codes.add((String) zipcode);
                }
            }
        }
        return new ArrayList<>(codes);
    }

    private static List<?> items(Object result) {
        if (result instanceof Map) {
            Object items = ((Map<?, ?>) result).get("items");
            if (items instanceof List) {
                return (List<?>) items;
            }
        }
        return List.of();
    }
}
