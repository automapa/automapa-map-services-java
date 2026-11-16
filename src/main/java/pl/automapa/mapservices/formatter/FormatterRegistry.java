package pl.automapa.mapservices.formatter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Named collection of {@link Formatter}s. A new registry is empty; the API client registers its default
 * formatters itself.
 *
 * <p>Names are case sensitive. Instances are thread safe.
 */
public final class FormatterRegistry {

    private final Map<String, Formatter> formatters = new LinkedHashMap<>();

    /** Creates an empty registry. */
    public FormatterRegistry() {
        // empty
    }

    /**
     * Registers a formatter, replacing any formatter registered under the same name.
     *
     * @param name format name, e.g. {@code google}
     * @param formatter the formatter
     * @throws IllegalArgumentException when {@code name} is {@code null} or blank, or {@code formatter} is
     *     {@code null}
     */
    public synchronized void register(String name, Formatter formatter) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("FormatterRegistry.register: the format name is required but was "
                    + (name == null ? "null" : "empty") + "; pass a name such as \"google\"");
        }
        if (formatter == null) {
            throw new IllegalArgumentException("FormatterRegistry.register: the formatter for \"" + name
                    + "\" is required but was null; pass a Formatter instance, e.g. new NativeFormatter()");
        }
        formatters.put(name, formatter);
    }

    /**
     * Returns the formatter registered under {@code name}.
     *
     * @param name format name
     * @return the formatter
     * @throws IllegalArgumentException when no formatter is registered under {@code name}; the message lists the
     *     available names
     */
    public synchronized Formatter get(String name) {
        Formatter formatter = name == null ? null : formatters.get(name);
        if (formatter == null) {
            String available = formatters.isEmpty() ? "none" : String.join(", ", formatters.keySet());
            throw new IllegalArgumentException("Formatter " + (name == null ? "null" : "\"" + name + "\"")
                    + " is not registered. Available: " + available
                    + ". Use one of the available names or add it with FormatterRegistry.register(name, formatter)");
        }
        return formatter;
    }

    /**
     * Tells whether a formatter is registered under {@code name}.
     *
     * @param name format name; {@code null} gives {@code false}
     * @return {@code true} when registered
     */
    public synchronized boolean has(String name) {
        return name != null && formatters.containsKey(name);
    }
}
