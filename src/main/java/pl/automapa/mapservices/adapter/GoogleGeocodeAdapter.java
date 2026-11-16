package pl.automapa.mapservices.adapter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.request.GeocodeRequest;

/**
 * Drop-in adapter for applications using the Google Geocoding API.
 *
 * <p>Accepts the request parameters of the Google Geocoding API ({@code address} text, {@code components} filter)
 * and returns a response in the Google structure ({@code status}, {@code results}, {@code _raw}):
 *
 * <pre>{@code
 * // Before (Google):
 * Map<String, Object> result = googleGeocoder.geocode(Map.of("address", "Domaniewska 37, Warszawa"));
 *
 * // After (Automapa):
 * GoogleGeocodeAdapter adapter = new GoogleGeocodeAdapter(client);
 * Map<String, Object> result = adapter.geocode(Map.of("address", "Domaniewska 37, Warszawa"));
 * }</pre>
 *
 * <p>The address is taken, in this order of priority, from:
 *
 * <ul>
 *   <li>{@code components} as text, e.g. {@code locality:Warszawa|route:Domaniewska|street_number:37}
 *       (recommended; supported keys: {@code locality}, {@code route}, {@code street_number}, {@code postal_code},
 *       {@code administrative_area_level_1}, {@code country}, {@code sublocality}, {@code neighborhood}),
 *   <li>{@code address} as a {@code Map} with the Automapa keys ({@code city}, {@code country}, {@code province},
 *       {@code district}, {@code community}, {@code quarter}, {@code pcode}, {@code street}, {@code house}),
 *   <li>{@code address} as free-form text parsed best-effort: {@code "Domaniewska 37, 02-672 Warszawa"},
 *       {@code "Domaniewska 37, Warszawa"} or {@code "Warszawa"}.
 * </ul>
 *
 * <p>The Google-only parameters {@code address}, {@code components}, {@code language}, {@code key}, {@code region},
 * {@code result_type} and {@code location_type} are accepted and not sent; {@code maxResults} is sent (default
 * {@code 1}); any other parameter is sent to the API unchanged. The call always uses the {@code google} format.
 */
public final class GoogleGeocodeAdapter {

    private static final String OPERATION = "GoogleGeocodeAdapter.geocode";

    private static final Map<String, String> COMPONENT_MAP = Map.of(
            "locality", "city",
            "route", "street",
            "street_number", "house",
            "postal_code", "pcode",
            "administrative_area_level_1", "province",
            "country", "country",
            "sublocality", "district",
            "neighborhood", "district");

    private static final Set<String> GOOGLE_ONLY_PARAMS =
            Set.of("address", "components", "language", "key", "region", "result_type", "location_type");

    private static final String MAX_RESULTS = "maxResults";

    private static final Pattern CITY_WITH_POSTAL_CODE = Pattern.compile("^(\\d{2}-\\d{3})\\s+(.+)$");

    private static final Pattern STREET_WITH_HOUSE = Pattern.compile("^(.+?)\\s+(\\d+\\w*)$");

    private final ApiClient client;

    /**
     * Creates the adapter.
     *
     * @param client client sending the requests, e.g. {@code ApiClient.create(Config.fromEnvironment())}
     * @throws IllegalArgumentException when {@code client} is {@code null}
     */
    public GoogleGeocodeAdapter(ApiClient client) {
        if (client == null) {
            throw new IllegalArgumentException("new GoogleGeocodeAdapter: client is required but was null; "
                    + "pass a client, e.g. new GoogleGeocodeAdapter(ApiClient.create(Config.fromEnvironment()))");
        }
        this.client = client;
    }

    /**
     * Geocodes an address given as Google Geocoding API parameters.
     *
     * @param params Google-style parameters: {@code components} (text), {@code address} (text or {@code Map}),
     *     optionally {@code maxResults} (number, default {@code 1}) and other parameters sent unchanged; see the class
     *     description
     * @return the Google Geocoding API structure: {@code status}, {@code results} and the native response in
     *     {@code _raw}
     * @throws IllegalArgumentException when {@code params} is {@code null}, has neither a {@code components} text
     *     nor an {@code address} text or map, or {@code maxResults} is not an integer
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     */
    public Map<String, Object> geocode(Map<String, ?> params) {
        if (params == null) {
            throw new IllegalArgumentException(OPERATION + ": params is required but was null; pass e.g. "
                    + "Map.of(\"address\", \"Domaniewska 37, Warszawa\")");
        }
        Map<String, ?> address = resolveAddress(params);

        Map<String, Object> forwardable = new LinkedHashMap<>(params);
        forwardable.keySet().removeAll(GOOGLE_ONLY_PARAMS);
        Object maxResults = forwardable.remove(MAX_RESULTS);

        GeocodeRequest request = GeocodeRequest.builder(text(address, "city", ""))
                .country(text(address, "country", null))
                .province(text(address, "province", null))
                .district(text(address, "district", null))
                .community(text(address, "community", null))
                .quarter(text(address, "quarter", null))
                .pcode(text(address, "pcode", null))
                .street(text(address, "street", null))
                .house(text(address, "house", null))
                .maxResults(toMaxResults(maxResults))
                .extra(forwardable)
                .format("google")
                .build();

        return client.geocoder().geocode(request).dataAsMap();
    }

    /** Address from the parameters; priority: components text, address map, address text. */
    private static Map<String, ?> resolveAddress(Map<String, ?> params) {
        Object components = params.get("components");
        if (components instanceof String) {
            return parseComponents((String) components);
        }
        Object address = params.get("address");
        if (address instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, ?> structured = (Map<String, ?>) address;
            return structured;
        }
        if (address instanceof String) {
            return parseAddressString((String) address);
        }
        throw new IllegalArgumentException(OPERATION + ": either \"address\" (text such as "
                + "\"Domaniewska 37, Warszawa\" or a Map such as {\"city\": \"Warszawa\", \"street\": \"Domaniewska\"}) "
                + "or \"components\" (text such as \"locality:Warszawa|route:Domaniewska|street_number:37\") "
                + "must be provided; got keys " + params.keySet());
    }

    /** {@code locality:Warszawa|route:Domaniewska} to {@code {city: Warszawa, street: Domaniewska}}. */
    private static Map<String, String> parseComponents(String components) {
        Map<String, String> address = new LinkedHashMap<>();
        for (String part : components.split("\\|", -1)) {
            int colon = part.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String field = COMPONENT_MAP.get(part.substring(0, colon).trim());
            if (field != null) {
                address.put(field, part.substring(colon + 1).trim());
            }
        }
        return address;
    }

    /** Best-effort parse of {@code "Domaniewska 37, 02-672 Warszawa"}, {@code "Domaniewska 37, Warszawa"}. */
    private static Map<String, String> parseAddressString(String address) {
        String[] parts = address.split(",", -1);
        Map<String, String> result = new LinkedHashMap<>();
        if (parts.length == 1) {
            result.put("city", parts[0].trim());
            return result;
        }

        String cityPart = parts[parts.length - 1].trim();
        Matcher city = CITY_WITH_POSTAL_CODE.matcher(cityPart);
        if (city.matches()) {
            result.put("pcode", city.group(1));
            result.put("city", city.group(2));
        } else {
            result.put("city", cityPart);
        }

        String streetPart = parts[0].trim();
        Matcher street = STREET_WITH_HOUSE.matcher(streetPart);
        if (street.matches()) {
            result.put("street", street.group(1).trim());
            result.put("house", street.group(2));
        } else {
            result.put("street", streetPart);
        }
        return result;
    }

    private static String text(Map<String, ?> address, String key, String fallback) {
        Object value = address.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static Integer toMaxResults(Object value) {
        if (value == null) {
            return 1;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.valueOf(((String) value).trim());
            } catch (NumberFormatException e) {
                throw invalidMaxResults(value);
            }
        }
        throw invalidMaxResults(value);
    }

    private static IllegalArgumentException invalidMaxResults(Object value) {
        return new IllegalArgumentException(OPERATION + ": maxResults must be an integer but was \"" + value + "\" ("
                + value.getClass().getSimpleName() + "); pass e.g. Map.of(\"maxResults\", 5) or leave it out for 1");
    }
}
