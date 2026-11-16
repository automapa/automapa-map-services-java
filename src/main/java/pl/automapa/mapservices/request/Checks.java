package pl.automapa.mapservices.request;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Validates the required values of request builders. */
final class Checks {

    private Checks() {}

    /**
     * Requires a non-null value.
     *
     * @param value the value
     * @param operation operation for the message, e.g. {@code GeocodeRequest.builder}
     * @param name value name
     * @param hint how to pass a correct value
     * @param <T> value type
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
     * Requires a non-blank text.
     *
     * @param value the text
     * @param operation operation for the message
     * @param name value name
     * @param hint how to pass a correct value
     * @return the text
     * @throws IllegalArgumentException when the text is {@code null} or blank
     */
    static String requireText(String value, String operation, String name, String hint) {
        requireNonNull(value, operation, name, hint);
        if (value.isBlank()) {
            throw new IllegalArgumentException(operation + ": " + name + " is required but was empty; " + hint);
        }
        return value;
    }

    /**
     * Requires a non-empty map and returns its copy in the same key order.
     *
     * @param map the map
     * @param operation operation for the message
     * @param name map name
     * @param hint how to pass a correct map
     * @return an unmodifiable copy of the map in insertion order
     * @throws IllegalArgumentException when the map is {@code null} or empty
     */
    static Map<String, Object> requireEntries(Map<String, ?> map, String operation, String name, String hint) {
        requireNonNull(map, operation, name, hint);
        if (map.isEmpty()) {
            throw new IllegalArgumentException(operation + ": " + name + " is required but was empty; " + hint);
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    /**
     * Requires a non-empty list without {@code null} items and returns its immutable copy.
     *
     * @param list the list
     * @param operation operation for the message
     * @param name list name
     * @param hint how to pass a correct list
     * @param <T> item type
     * @return an immutable copy of the list
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
        return List.copyOf(list);
    }

    /**
     * Requires a finite coordinate.
     *
     * @param value the coordinate
     * @param operation operation for the message
     * @param name coordinate name, e.g. {@code x (longitude)}
     * @param example example of a correct call
     * @return the coordinate
     * @throws IllegalArgumentException when the coordinate is NaN or infinite
     */
    static double requireFinite(double value, String operation, String name, String example) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(operation + ": " + name + " must be a finite number but was " + value
                    + "; pass degrees as numbers, e.g. " + example);
        }
        return value;
    }

    /**
     * Copies an optional map in its key order.
     *
     * @param map the map, or {@code null}
     * @return an unmodifiable copy of the map in insertion order, or {@code null} when the map is {@code null}
     */
    static Map<String, Object> copyOrNull(Map<String, ?> map) {
        return map == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }
}
