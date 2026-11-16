package pl.automapa.mapservices.endpoint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds request parameters of endpoint methods and validates their arguments. */
final class EndpointParams {

    private EndpointParams() {}

    /**
     * Copies {@code extra} and puts the named parameters over it, like PHP {@code array_merge($extra, $named)}: a
     * named key already present in {@code extra} keeps its position and gets the named value.
     *
     * @param extra additional parameters, or {@code null} for none; never modified
     * @param keyValues named parameters as alternating keys and values; {@code null} values are skipped
     * @return a new mutable map
     */
    static Map<String, Object> merge(Map<String, ?> extra, Object... keyValues) {
        Map<String, Object> params = extra == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extra);
        for (int i = 0; i < keyValues.length; i += 2) {
            Object value = keyValues[i + 1];
            if (value != null) {
                params.put((String) keyValues[i], value);
            }
        }
        return params;
    }

    /**
     * Requires a non-blank text argument.
     *
     * @param value argument value
     * @param operation operation for the message, e.g. {@code Session.getSalt}
     * @param name argument name
     * @param hint how to get or pass a correct value
     * @return the value
     * @throws IllegalArgumentException when the value is {@code null} or blank; the message never contains it
     */
    static String requireText(String value, String operation, String name, String hint) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(operation + ": " + name + " is required but was "
                    + (value == null ? "null" : "empty") + "; " + hint);
        }
        return value;
    }

    /**
     * Requires a non-null argument.
     *
     * @param value argument value
     * @param operation operation for the message, e.g. {@code new Session}
     * @param name argument name
     * @param hint how to get or pass a correct value
     * @param <T> argument type
     * @return the value
     * @throws IllegalArgumentException when the value is {@code null}
     */
    static <T> T requireNonNull(T value, String operation, String name, String hint) {
        if (value == null) {
            throw new IllegalArgumentException(operation + ": " + name + " is required but was null; " + hint);
        }
        return value;
    }

    /**
     * Requires a non-empty list without {@code null} items.
     *
     * @param list argument value
     * @param operation operation for the message, e.g. {@code Geocoder.geocodemulti}
     * @param name argument name
     * @param hint how to pass a correct list
     * @param <T> item type
     * @return a new mutable copy of the list
     * @throws IllegalArgumentException when the list is {@code null}, empty or contains {@code null}
     */
    static <T> List<T> requireItems(List<? extends T> list, String operation, String name, String hint) {
        requireNonNull(list, operation, name, hint);
        if (list.isEmpty()) {
            throw new IllegalArgumentException(operation + ": " + name + " is required but was empty; " + hint);
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == null) {
                throw new IllegalArgumentException(
                        operation + ": " + name + "[" + i + "] is null; every item is required; " + hint);
            }
        }
        return new ArrayList<>(list);
    }
}
