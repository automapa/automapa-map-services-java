package pl.automapa.mapservices.formatter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pass-through formatter: returns the unwrapped {@code result} with the whole response added under {@code _raw}.
 *
 * <ul>
 *   <li>a {@code result} object is returned as a copy with an extra {@code _raw} key (replacing a {@code _raw}
 *       key already present in the result);
 *   <li>any other {@code result} (string, number, boolean or list, e.g. {@code pong} from {@code PingPong.ping})
 *       is wrapped as {@code {"data": result, "_raw": raw}};
 *   <li>when {@code result} is missing or {@code null}, the whole response is used in its place.
 * </ul>
 *
 * <p>The returned map is a new mutable map; nested values are shared with {@code raw}, which is never modified.
 * Instances are stateless and thread safe.
 */
public final class NativeFormatter implements Formatter {

    /** Creates the formatter. */
    public NativeFormatter() {
        // stateless
    }

    /**
     * Unwraps {@code result} and adds the {@code _raw} key.
     *
     * @param raw full decoded API response
     * @param serviceMethod called method as {@code Service.method}; not used by this formatter
     * @return a map with the result fields (or {@code data}) and {@code _raw}
     * @throws IllegalArgumentException when {@code raw} is {@code null}
     */
    @Override
    public Map<String, Object> format(Map<String, Object> raw, String serviceMethod) {
        if (raw == null) {
            throw new IllegalArgumentException(
                    "NativeFormatter: the raw response is required but was null; pass the decoded API response map");
        }
        Object result = raw.get("result");
        if (result == null) {
            result = raw;
        }
        Map<String, Object> output = new LinkedHashMap<>();
        if (result instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) result).entrySet()) {
                output.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        } else {
            output.put("data", result);
        }
        output.put("_raw", raw);
        return output;
    }
}
