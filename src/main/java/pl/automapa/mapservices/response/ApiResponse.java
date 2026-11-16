package pl.automapa.mapservices.response;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pl.automapa.mapservices.exception.UnknownResponseException;

/**
 * Successful response of the Automapa API.
 *
 * <p>{@link #raw()} always holds the full decoded JSON object, including fields the SDK does not know about.
 * {@link #data()} returns the useful part: the formatter output when a response format other than {@code native}
 * was requested, otherwise the {@code result} field, or the whole raw object when the response has no
 * {@code result}. JSON objects are decoded to {@code Map<String, Object>}, arrays to {@code List<Object>}, strings
 * to {@code String}, integers to {@code Long} (or {@code BigInteger}), fractions to {@code Double} and
 * {@code true}/{@code false} to {@code Boolean}.
 *
 * <p>Instances are immutable at the top level: {@link #raw()} and {@link #meta()} are unmodifiable copies of the
 * maps passed to the constructor (nested maps and lists are shared, not copied).
 */
public final class ApiResponse {

    private final Map<String, Object> raw;
    private final int status;
    private final Map<String, Object> meta;
    private final Object formattedData;

    /**
     * Creates a response without formatter output.
     *
     * @param raw full decoded JSON response ({@code null} means an empty object)
     * @param status HTTP status code
     * @param meta request metadata such as {@code service} and {@code method} ({@code null} means none)
     */
    public ApiResponse(Map<String, Object> raw, int status, Map<String, Object> meta) {
        this(raw, status, meta, null);
    }

    /**
     * Creates a response without metadata and without formatter output.
     *
     * @param raw full decoded JSON response ({@code null} means an empty object)
     * @param status HTTP status code
     */
    public ApiResponse(Map<String, Object> raw, int status) {
        this(raw, status, null, null);
    }

    /**
     * Creates a response.
     *
     * @param raw full decoded JSON response ({@code null} means an empty object)
     * @param status HTTP status code
     * @param meta request metadata such as {@code service} and {@code method} ({@code null} means none)
     * @param formattedData formatter output returned by {@link #data()}, or {@code null} for the native format
     */
    public ApiResponse(Map<String, Object> raw, int status, Map<String, Object> meta, Object formattedData) {
        this.raw = copy(raw);
        this.status = status;
        this.meta = copy(meta);
        this.formattedData = formattedData;
    }

    /**
     * Returns the full decoded JSON response, never stripped by a formatter.
     *
     * @return unmodifiable map with every top-level field of the response
     */
    public Map<String, Object> raw() {
        return raw;
    }

    /**
     * Returns the useful part of the response.
     *
     * <p>The formatter output when it is set; otherwise the {@code result} field; when {@code result} is missing or
     * {@code null}, the whole {@link #raw()} map (like PHP {@code $raw['result'] ?? $raw}).
     *
     * @return the response data: usually a {@code Map} or a {@code List}
     */
    public Object data() {
        if (formattedData != null) {
            return formattedData;
        }
        Object result = raw.get("result");
        return result != null ? result : raw;
    }

    /**
     * Returns {@link #data()} as a JSON object.
     *
     * @return the response data as a map
     * @throws UnknownResponseException when the data is not a JSON object; the message names the expected and the
     *     actual type
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> dataAsMap() {
        Object data = data();
        if (data instanceof Map) {
            return (Map<String, Object>) data;
        }
        throw wrongType("JSON object (Map)", data);
    }

    /**
     * Returns {@link #data()} as a JSON array.
     *
     * @return the response data as a list
     * @throws UnknownResponseException when the data is not a JSON array; the message names the expected and the
     *     actual type
     */
    @SuppressWarnings("unchecked")
    public List<Object> dataAsList() {
        Object data = data();
        if (data instanceof List) {
            return (List<Object>) data;
        }
        throw wrongType("JSON array (List)", data);
    }

    /**
     * Returns the HTTP status code of the response.
     *
     * @return the HTTP status code
     */
    public int status() {
        return status;
    }

    /**
     * Returns the request metadata, for example {@code service} and {@code method}.
     *
     * @return unmodifiable map with the metadata (empty when none was given)
     */
    public Map<String, Object> meta() {
        return meta;
    }

    private UnknownResponseException wrongType(String expected, Object actual) {
        StringBuilder message = new StringBuilder("Expected the response data to be a ")
                .append(expected)
                .append(", got a ")
                .append(describe(actual));
        Object service = meta.get("service");
        Object method = meta.get("method");
        if (service != null && method != null) {
            message.append(" from ").append(service).append('.').append(method);
        }
        message.append(" (HTTP ").append(status).append("). ");
        if (actual instanceof List) {
            message.append("Use dataAsList() for this response.");
        } else if (actual instanceof Map) {
            message.append("Use dataAsMap() for this response.");
        } else {
            message.append("Use data() to read this value.");
        }
        return new UnknownResponseException(message.toString(), status, null, null);
    }

    /**
     * Describes the JSON type of a decoded value together with its Java class, e.g. {@code JSON array (List)}.
     *
     * @param value decoded value
     * @return human-readable type name
     */
    static String describe(Object value) {
        if (value == null) {
            return "JSON null (null)";
        }
        if (value instanceof Map) {
            return "JSON object (Map)";
        }
        if (value instanceof List) {
            return "JSON array (List)";
        }
        if (value instanceof String) {
            return "JSON string (String)";
        }
        if (value instanceof Number) {
            return "JSON number (" + value.getClass().getSimpleName() + ")";
        }
        if (value instanceof Boolean) {
            return "JSON boolean (Boolean)";
        }
        return "Java value (" + value.getClass().getSimpleName() + ")";
    }

    private static Map<String, Object> copy(Map<String, Object> map) {
        return map == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }
}
