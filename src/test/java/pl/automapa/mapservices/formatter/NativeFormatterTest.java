package pl.automapa.mapservices.formatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.automapa.mapservices.support.Fixtures;

class NativeFormatterTest {

    private final NativeFormatter formatter = new NativeFormatter();

    @Test
    void passesMapResultThroughWithAllFields() {
        Map<String, Object> raw = Fixtures.load("geocode_success");

        Map<String, Object> output = asMap(formatter.format(raw, "Geocoder.geocode"));

        assertThat(output)
                .containsEntry("city", "Warszawa")
                .containsEntry("x", 21.0122)
                .containsEntry("unknownFutureField", "should be preserved");
        assertThat(output.keySet())
                .containsExactlyElementsOf(withRawKey(asMap(raw.get("result")).keySet()));
    }

    @Test
    void appendsRawKeyWithTheWholeResponse() {
        Map<String, Object> raw = Fixtures.load("routes_optimize_queue_success");

        Map<String, Object> output = asMap(formatter.format(raw, "Routes.optimizeQueue"));

        assertThat(output).containsEntry("requestId", "opt-req-abc123");
        assertThat(output.get("_raw")).isSameAs(raw);
    }

    @Test
    void wrapsScalarResultInDataKey() {
        Map<String, Object> raw = Map.of("result", "pong");

        Map<String, Object> output = asMap(formatter.format(raw, "PingPong.ping"));

        assertThat(output).containsOnlyKeys("data", "_raw").containsEntry("data", "pong");
        assertThat(output.get("_raw")).isSameAs(raw);
    }

    @Test
    void wrapsListResultInDataKey() {
        Map<String, Object> raw = Fixtures.load("geocodemulti_success");

        Map<String, Object> output = asMap(formatter.format(raw, "Geocoder.geocodemulti"));

        assertThat(output).containsOnlyKeys("data", "_raw");
        assertThat(output.get("data")).isInstanceOf(List.class).isSameAs(raw.get("result"));
        assertThat(output.get("_raw")).isSameAs(raw);
    }

    @Test
    void rawKeyOverridesExistingRawInResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("city", "Lodz");
        result.put("_raw", "old-value");
        Map<String, Object> raw = Map.of("result", result);

        Map<String, Object> output = asMap(formatter.format(raw, "Geocoder.geocode"));

        assertThat(output.keySet()).containsExactly("city", "_raw");
        assertThat(output.get("_raw")).isSameAs(raw);
    }

    @Test
    void usesWholeResponseWhenResultIsMissingOrNull() {
        Map<String, Object> withoutResult = Map.of("status", "ok");
        Map<String, Object> nullResult = new LinkedHashMap<>();
        nullResult.put("result", null);
        nullResult.put("status", "ok");

        assertThat(asMap(formatter.format(withoutResult, "Service.method")))
                .containsEntry("status", "ok")
                .containsEntry("_raw", withoutResult);
        Map<String, Object> output = asMap(formatter.format(nullResult, "Service.method"));
        assertThat(output.keySet()).containsExactly("result", "status", "_raw");
        assertThat(output.get("_raw")).isSameAs(nullResult);
    }

    @Test
    void doesNotModifyTheRawResponse() {
        Map<String, Object> raw = Fixtures.load("geocode_success");
        String before = raw.toString();

        Map<String, Object> output = asMap(formatter.format(raw, "Geocoder.geocode"));
        output.put("city", "changed");

        assertThat(raw.toString()).isEqualTo(before);
        assertThat(asMap(raw.get("result"))).doesNotContainKey("_raw");
    }

    @Test
    void rejectsNullResponse() {
        assertThatThrownBy(() -> formatter.format(null, "Geocoder.geocode"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("raw");
    }

    private static List<String> withRawKey(Iterable<String> keys) {
        List<String> all = new java.util.ArrayList<>();
        keys.forEach(all::add);
        all.add("_raw");
        return all;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }
}
