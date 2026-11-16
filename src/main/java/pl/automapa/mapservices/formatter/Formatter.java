package pl.automapa.mapservices.formatter;

import java.util.Map;

/**
 * Transforms a decoded API response into another output shape (for example the Google Geocoding API format).
 *
 * <p>Implementations should keep the whole original response available under the {@code _raw} key when they
 * return a map. They must not modify {@code raw}.
 */
@FunctionalInterface
public interface Formatter {

    /**
     * Formats a decoded API response.
     *
     * @param raw full decoded API response, including the {@code result} key
     * @param serviceMethod called method as {@code Service.method}, e.g. {@code Geocoder.geocode}
     * @return the formatted output
     */
    Object format(Map<String, Object> raw, String serviceMethod);
}
