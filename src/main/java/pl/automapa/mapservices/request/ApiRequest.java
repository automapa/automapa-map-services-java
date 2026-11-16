package pl.automapa.mapservices.request;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base of the request objects: named parameters of an endpoint method plus {@code extra} parameters sent to the API
 * unchanged.
 *
 * <p>{@link #toParams()} merges them like PHP {@code array_merge($extra, $named)}: first a copy of {@code extra},
 * then every named parameter other than {@code null} overrides the key of the same name.
 */
public abstract class ApiRequest {

    private static final String FORMAT_PARAM = "format";

    private final Map<String, Object> extra;

    /**
     * Copies the {@code extra} parameters of the builder; later changes of the builder do not affect this request.
     *
     * @param builder builder of the concrete request
     */
    protected ApiRequest(Builder<?> builder) {
        this.extra = new LinkedHashMap<>(builder.extra);
    }

    /**
     * Returns the request body: {@code extra} first, then the named parameters other than {@code null}. A named key
     * already present in {@code extra} keeps its position and gets the named value. The {@code format} key, when
     * set, selects the response format and is removed by the client before sending.
     *
     * @return a new mutable map in insertion order
     */
    public final Map<String, Object> toParams() {
        Map<String, Object> params = new LinkedHashMap<>(extra);
        for (Map.Entry<String, Object> entry : namedParams().entrySet()) {
            if (entry.getValue() != null) {
                params.put(entry.getKey(), entry.getValue());
            }
        }
        return params;
    }

    /**
     * Returns the named parameters of the concrete request in the order of the API; {@code null} values are left
     * out of {@link #toParams()}.
     *
     * @return the named parameters
     */
    protected abstract Map<String, Object> namedParams();

    /**
     * Base builder with {@code extra} parameters and the response format.
     *
     * @param <B> type of the concrete builder, returned by the chained methods
     */
    public abstract static class Builder<B extends Builder<B>> {

        private final Map<String, Object> extra = new LinkedHashMap<>();

        /** Creates an empty builder. */
        protected Builder() {}

        /**
         * Adds parameters sent to the API unchanged; later calls override earlier keys.
         *
         * @param extra parameters to add, or {@code null} for none; the map is copied, never modified
         * @return this builder
         * @throws IllegalArgumentException when a key is {@code null}
         */
        public final B extra(Map<String, ?> extra) {
            if (extra != null) {
                for (Map.Entry<String, ?> entry : extra.entrySet()) {
                    extra(entry.getKey(), entry.getValue());
                }
            }
            return self();
        }

        /**
         * Adds one parameter sent to the API unchanged.
         *
         * @param key parameter name
         * @param value parameter value; {@code null} is sent as JSON {@code null}
         * @return this builder
         * @throws IllegalArgumentException when {@code key} is {@code null}
         */
        public final B extra(String key, Object value) {
            if (key == null) {
                throw new IllegalArgumentException(
                        "ApiRequest.Builder.extra: key is required but was null; use a parameter name of the API");
            }
            extra.put(key, value);
            return self();
        }

        /**
         * Selects the response format, e.g. {@code google}; the format is not sent to the API.
         *
         * @param format name of a registered formatter, or {@code null} to use the default format of the client
         * @return this builder
         */
        public final B format(String format) {
            if (format == null) {
                extra.remove(FORMAT_PARAM);
                return self();
            }
            return extra(FORMAT_PARAM, format);
        }

        /**
         * Returns this builder as its concrete type.
         *
         * @return this builder
         */
        protected abstract B self();
    }
}
