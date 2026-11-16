package pl.automapa.mapservices.json;

/**
 * A value with its own JSON representation, e.g. an enum sent to the API as a number or a code.
 *
 * <p>{@link Json#stringify(Object)} writes the object returned by {@link #toJsonValue()} instead of the object
 * itself.
 */
@FunctionalInterface
public interface JsonValue {

    /**
     * Returns the value to write in place of this object.
     *
     * @return any value supported by {@link Json#stringify(Object)}, usually a {@code String} or a {@code Number}
     */
    Object toJsonValue();
}
