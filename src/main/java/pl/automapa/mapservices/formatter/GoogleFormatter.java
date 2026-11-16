package pl.automapa.mapservices.formatter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps geocoding responses to the shape of the Google Geocoding API, for users migrating from Google.
 *
 * <p>Geocoding methods ({@code Geocoder.geocode}, {@code Geocoder.geocodemulti}, {@code Geocoder.revgeocode},
 * {@code Geocoder.revgeocodemulti}, {@code Autocomplete.search}, {@code Autocomplete.searchAddress}) return
 * {@code {"status": "OK" | "ZERO_RESULTS", "results": [...], "_raw": raw}}. Every result has
 * {@code formatted_address}, {@code address_components} ({@code long_name}, {@code short_name}, {@code types}),
 * {@code geometry} ({@code location} with {@code lat}/{@code lng}, {@code viewport} always {@code null}),
 * {@code place_id} (always {@code null}) and {@code types}.
 *
 * <ul>
 *   <li>Coordinates: Automapa {@code x} is the longitude and {@code y} the latitude, so {@code lat = y} and
 *       {@code lng = x}; autocomplete items carry them in {@code coords}. A missing coordinate gives {@code null}.
 *   <li>The geocoder {@code quality} becomes the result type: up to 2 {@code country}, 3
 *       {@code administrative_area_level_1}, up to 5 {@code locality}, up to 7 {@code route}, otherwise
 *       {@code street_address}. Autocomplete items use their {@code subtype}, then {@code type}, then
 *       {@code place}.
 *   <li>Multi methods keep failed entries in place as {@code {"status": "ERROR", "message": ...}}.
 *   <li>An empty {@code result} (or one without mappable entries) gives {@code ZERO_RESULTS} with an empty list.
 * </ul>
 *
 * <p>Any other method passes through: a copy of the response with the whole response added under {@code _raw}.
 *
 * <p>The mapping follows the PHP SDK, including its loose conversion of field values (numbers and booleans in
 * text fields become text, numeric text in coordinates and {@code quality} becomes a number). Entries that are
 * neither objects nor lists are skipped. The response is never modified. Instances are stateless and thread safe.
 */
public final class GoogleFormatter implements Formatter {

    private static final Set<String> GEOCODING_METHODS = Set.of(
            "Geocoder.geocode",
            "Geocoder.geocodemulti",
            "Geocoder.revgeocode",
            "Geocoder.revgeocodemulti",
            "Autocomplete.search",
            "Autocomplete.searchAddress");

    private static final Pattern LEADING_NUMBER =
            Pattern.compile("^[ \\t\\n\\r\\u000B\\f]*([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)");

    private static final double MAX_PLAIN_INTEGER = 1e15;

    /** Creates the formatter. */
    public GoogleFormatter() {
        // stateless
    }

    /**
     * Maps a geocoding response to the Google Geocoding API shape; other methods pass through with {@code _raw}.
     *
     * @param raw full decoded API response, including the {@code result} key
     * @param serviceMethod called method as {@code Service.method}, e.g. {@code Geocoder.geocode}
     * @return a new mutable map: {@code status}, {@code results} and {@code _raw} for geocoding methods, otherwise
     *     the response keys plus {@code _raw}
     * @throws IllegalArgumentException when {@code raw} or {@code serviceMethod} is {@code null}
     */
    @Override
    public Map<String, Object> format(Map<String, Object> raw, String serviceMethod) {
        if (raw == null) {
            throw new IllegalArgumentException(
                    "GoogleFormatter: the raw response is required but was null; pass the decoded API response map");
        }
        if (serviceMethod == null) {
            throw new IllegalArgumentException("GoogleFormatter: the method is required but was null; pass it as"
                    + " Service.method, e.g. Geocoder.geocode");
        }
        if (!GEOCODING_METHODS.contains(serviceMethod)) {
            Map<String, Object> output = new LinkedHashMap<>(raw);
            output.put("_raw", raw);
            return output;
        }

        Object result = raw.get("result");
        List<Object> results = isEmpty(result) ? new ArrayList<>() : mapResults(result, serviceMethod);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("status", results.isEmpty() ? "ZERO_RESULTS" : "OK");
        output.put("results", results);
        output.put("_raw", raw);
        return output;
    }

    private List<Object> mapResults(Object result, String method) {
        List<Object> mapped = new ArrayList<>();
        if (!isArray(result)) {
            return mapped;
        }
        Map<?, ?> resultMap = asMap(result);

        Object items = resultMap.get("items");
        if (isArray(items)) {
            mapEach(values(items), mapped);
            return mapped;
        }

        if (method.contains("multi")) {
            return mapMultiResults(values(result));
        }

        if (hasCoordinatesOrCity(resultMap)) {
            mapped.add(mapSingleResult(resultMap));
            return mapped;
        }

        mapEach(values(result), mapped);
        return mapped;
    }

    private List<Object> mapMultiResults(Collection<?> items) {
        List<Object> mapped = new ArrayList<>();
        for (Object item : items) {
            if (!isArray(item)) {
                continue;
            }
            Map<?, ?> itemMap = asMap(item);

            if ("ERROR".equals(itemMap.get("status"))) {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("status", "ERROR");
                error.put("message", toPhpString(itemMap.get("message")));
                mapped.add(error);
            } else if (hasCoordinatesOrCity(itemMap)) {
                mapped.add(mapSingleResult(itemMap));
            } else {
                mapEach(values(item), mapped);
            }
        }
        return mapped;
    }

    /** Maps every object or list element; other elements (where PHP would fail with a TypeError) are skipped. */
    private void mapEach(Collection<?> elements, List<Object> mapped) {
        for (Object element : elements) {
            if (isArray(element)) {
                mapped.add(mapSingleResult(asMap(element)));
            }
        }
    }

    private static boolean hasCoordinatesOrCity(Map<?, ?> result) {
        return isset(result, "x") || isset(result, "y") || isset(result, "city");
    }

    private Map<String, Object> mapSingleResult(Map<?, ?> result) {
        if (isArray(result.get("coords"))) {
            return mapAutocompleteResult(result);
        }
        return googleResult(
                buildFormattedAddress(result),
                buildAddressComponents(result),
                coordinate(result, "y"),
                coordinate(result, "x"),
                qualityToType(toPhpInt(result.get("quality"))));
    }

    /**
     * Maps an autocomplete item: {@code place} instead of {@code city}, {@code number} instead of {@code house},
     * {@code postal_code}/{@code zipcode} instead of {@code pcode}, coordinates under {@code coords}.
     */
    private Map<String, Object> mapAutocompleteResult(Map<?, ?> result) {
        Map<?, ?> coords = asMap(result.get("coords"));
        return googleResult(
                buildAutocompleteFormattedAddress(result),
                buildAutocompleteAddressComponents(result),
                coordinate(coords, "y"),
                coordinate(coords, "x"),
                toPhpString(firstSet(result, "subtype", "type", "place")));
    }

    private static Map<String, Object> googleResult(
            String formattedAddress, List<Object> components, Double lat, Double lng, String type) {
        Map<String, Object> location = new LinkedHashMap<>();
        location.put("lat", lat);
        location.put("lng", lng);
        Map<String, Object> geometry = new LinkedHashMap<>();
        geometry.put("location", location);
        geometry.put("viewport", null);

        List<Object> types = new ArrayList<>();
        types.add(type);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("formatted_address", formattedAddress);
        output.put("address_components", components);
        output.put("geometry", geometry);
        output.put("place_id", null);
        output.put("types", types);
        return output;
    }

    private static String buildAutocompleteFormattedAddress(Map<?, ?> result) {
        List<String> parts = new ArrayList<>();
        if (isset(result, "street")) {
            parts.add(streetWithNumber(result, "number"));
        }

        String city = autocompleteCity(result);
        String pcode = autocompletePostalCode(result);
        if (!pcode.isEmpty() && !city.isEmpty()) {
            parts.add(pcode + " " + city);
        } else if (!city.isEmpty()) {
            parts.add(city);
        }
        return String.join(", ", parts);
    }

    private static List<Object> buildAutocompleteAddressComponents(Map<?, ?> result) {
        List<Object> components = new ArrayList<>();
        addComponent(components, result, "street", "route");
        addComponent(components, result, "number", "street_number");

        String city = autocompleteCity(result);
        if (!city.isEmpty()) {
            components.add(component(city, "locality", "political"));
        }
        String pcode = autocompletePostalCode(result);
        if (!pcode.isEmpty()) {
            components.add(component(pcode, "postal_code"));
        }

        addComponent(components, result, "province", "administrative_area_level_1", "political");
        addComponent(components, result, "district", "sublocality", "political");
        return components;
    }

    private static String autocompleteCity(Map<?, ?> result) {
        return toPhpString(firstSet(result, "place", "city", ""));
    }

    private static String autocompletePostalCode(Map<?, ?> result) {
        return toPhpString(firstSet(result, "postal_code", "zipcode", ""));
    }

    private static String buildFormattedAddress(Map<?, ?> result) {
        List<String> parts = new ArrayList<>();
        if (isset(result, "street")) {
            parts.add(streetWithNumber(result, "house"));
        }

        if (isset(result, "pcode") && isset(result, "city")) {
            parts.add(toPhpString(result.get("pcode")) + " " + toPhpString(result.get("city")));
        } else if (isset(result, "city")) {
            parts.add(toPhpString(result.get("city")));
        }

        if (isset(result, "country")) {
            parts.add(toPhpString(result.get("country")));
        }
        return String.join(", ", parts);
    }

    private static List<Object> buildAddressComponents(Map<?, ?> result) {
        List<Object> components = new ArrayList<>();
        addComponent(components, result, "street", "route");
        addComponent(components, result, "house", "street_number");
        addComponent(components, result, "city", "locality", "political");
        addComponent(components, result, "pcode", "postal_code");
        addComponent(components, result, "province", "administrative_area_level_1", "political");
        addComponent(components, result, "district", "sublocality", "political");
        addComponent(components, result, "country", "country", "political");
        return components;
    }

    private static String streetWithNumber(Map<?, ?> result, String numberKey) {
        String street = toPhpString(result.get("street"));
        if (isset(result, numberKey)) {
            street += " " + toPhpString(result.get(numberKey));
        }
        return street;
    }

    private static void addComponent(List<Object> components, Map<?, ?> result, String key, String... types) {
        if (isset(result, key)) {
            components.add(component(toPhpString(result.get(key)), types));
        }
    }

    private static Map<String, Object> component(String name, String... types) {
        Map<String, Object> component = new LinkedHashMap<>();
        component.put("long_name", name);
        component.put("short_name", name);
        component.put("types", new ArrayList<>(List.of(types)));
        return component;
    }

    private static String qualityToType(long quality) {
        if (quality <= 2) {
            return "country";
        }
        if (quality == 3) {
            return "administrative_area_level_1";
        }
        if (quality <= 5) {
            return "locality";
        }
        if (quality <= 7) {
            return "route";
        }
        return "street_address";
    }

    // --- PHP array semantics ------------------------------------------------------------------------------------

    /** PHP {@code is_array}: a decoded JSON object or list. */
    private static boolean isArray(Object value) {
        return value instanceof Map || value instanceof List;
    }

    /** View of a PHP array by string key; a list has no string keys, so it reads as an empty map. */
    private static Map<?, ?> asMap(Object array) {
        return array instanceof Map ? (Map<?, ?>) array : Map.of();
    }

    /** Values of a PHP array in order. */
    private static Collection<?> values(Object array) {
        return array instanceof Map ? ((Map<?, ?>) array).values() : (List<?>) array;
    }

    /** PHP {@code isset($array[$key])}: the key is present and its value is not {@code null}. */
    private static boolean isset(Map<?, ?> array, String key) {
        return array.get(key) != null;
    }

    /** PHP {@code $a[$k1] ?? $a[$k2] ?? $fallback}. */
    private static Object firstSet(Map<?, ?> array, String firstKey, String secondKey, String fallback) {
        if (isset(array, firstKey)) {
            return array.get(firstKey);
        }
        if (isset(array, secondKey)) {
            return array.get(secondKey);
        }
        return fallback;
    }

    /** PHP {@code isset($array[$key]) ? (float) $array[$key] : null}. */
    private static Double coordinate(Map<?, ?> array, String key) {
        return isset(array, key) ? toPhpFloat(array.get(key)) : null;
    }

    /** PHP {@code empty()}. */
    private static boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Boolean) {
            return !((Boolean) value);
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() == 0;
        }
        if (value instanceof String) {
            return ((String) value).isEmpty() || "0".equals(value);
        }
        if (value instanceof Map) {
            return ((Map<?, ?>) value).isEmpty();
        }
        if (value instanceof Collection) {
            return ((Collection<?>) value).isEmpty();
        }
        return false;
    }

    /** PHP {@code (string)} for decoded JSON values: {@code null} is empty, booleans are {@code "1"}/{@code ""}. */
    private static String toPhpString(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Boolean) {
            return ((Boolean) value) ? "1" : "";
        }
        if (value instanceof Double || value instanceof Float) {
            double number = ((Number) value).doubleValue();
            if (number == Math.rint(number) && Math.abs(number) < MAX_PLAIN_INTEGER) {
                return Long.toString((long) number);
            }
            return Double.toString(number);
        }
        if (isArray(value)) {
            return "Array";
        }
        return value.toString();
    }

    /** PHP {@code (float)}: numeric prefix of text, {@code 1}/{@code 0} for booleans and non-empty/empty arrays. */
    private static double toPhpFloat(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            Matcher matcher = LEADING_NUMBER.matcher((String) value);
            return matcher.find() ? Double.parseDouble(matcher.group(1)) : 0.0;
        }
        return isEmpty(value) ? 0.0 : 1.0;
    }

    /** PHP {@code (int)}: truncates numbers, reads the numeric prefix of text; {@code null} is {@code 0}. */
    private static long toPhpInt(Object value) {
        if (value instanceof Double || value instanceof Float) {
            return (long) ((Number) value).doubleValue();
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return (long) toPhpFloat(value);
    }
}
