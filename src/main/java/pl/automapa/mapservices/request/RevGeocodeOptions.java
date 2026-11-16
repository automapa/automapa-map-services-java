package pl.automapa.mapservices.request;

import java.util.LinkedHashMap;
import java.util.Map;

/** Builds the {@code params} object of {@code Geocoder.revgeocode} and {@code Geocoder.revgeocodemulti}. */
final class RevGeocodeOptions {

    private RevGeocodeOptions() {}

    /**
     * Returns the options other than {@code null} in the API order.
     *
     * @param snapToBld snap the result to the nearest building
     * @param snapToBldRadius search radius of the building in metres
     * @param getPostalCode include the postal code in the result
     * @return a new map, empty when no option is set
     */
    static Map<String, Object> params(Boolean snapToBld, Integer snapToBldRadius, Boolean getPostalCode) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (snapToBld != null) {
            params.put("snapToBld", snapToBld);
        }
        if (snapToBldRadius != null) {
            params.put("snapToBldRadius", snapToBldRadius);
        }
        if (getPostalCode != null) {
            params.put("getPostalCode", getPostalCode);
        }
        return params;
    }
}
