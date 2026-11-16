package pl.automapa.mapservices.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.automapa.mapservices.adapter.GoogleGeocodeAdapter;

/** {@link GoogleGeocodeAdapter} on the live API (suite {@code geocoder}). */
class GoogleGeocodeAdapterLiveTest extends LiveApiTestBase {

    @Override
    protected String suiteName() {
        return "geocoder";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> firstLocation(Map<String, Object> result) {
        assertThat(result).containsKeys("status", "results", "_raw").containsEntry("status", "OK");
        Object results = result.get("results");
        assertThat(results).isInstanceOf(List.class);
        assertThat((List<?>) results).isNotEmpty();
        return map(map(map(((List<?>) results).get(0)).get("geometry")).get("location"));
    }

    private static double number(Object value) {
        assertThat(value).isInstanceOf(Number.class);
        return ((Number) value).doubleValue();
    }

    @Test
    void addressTextResolvesToDomaniewska37() {
        Map<String, Object> result = callLive(() -> new GoogleGeocodeAdapter(buildApiClient())
                .geocode(Map.of("address", "Domaniewska 37, Warszawa", "language", "pl", "key", "n/a")));

        Map<String, Object> location = firstLocation(result);
        assertThat(number(location.get("lat"))).isCloseTo(52.18, within(0.01));
        assertThat(number(location.get("lng"))).isCloseTo(21.00, within(0.02));
    }

    @Test
    void componentsFilterResolvesToDomaniewska37() {
        Map<String, Object> result = callLive(() -> new GoogleGeocodeAdapter(buildApiClient())
                .geocode(Map.of("components", "locality:Warszawa|route:Domaniewska|street_number:37")));

        Map<String, Object> location = firstLocation(result);
        assertThat(number(location.get("lat"))).isCloseTo(52.18, within(0.01));
        assertThat(number(location.get("lng"))).isCloseTo(21.00, within(0.02));
    }
}
