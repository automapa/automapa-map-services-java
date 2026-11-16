package pl.automapa.mapservices.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.automapa.mapservices.exception.UnknownResponseException;

class ApiResponseTest {

    private static Map<String, Object> map(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    @Test
    void dataReturnsResultField() {
        Map<String, Object> result = map("city", "Warszawa", "x", 21.01);
        ApiResponse response = new ApiResponse(map("result", result, "status", "OK"), 200, map(), null);

        assertThat(response.data()).isEqualTo(result);
    }

    @Test
    void dataReturnsWholeRawWhenResultKeyIsMissing() {
        Map<String, Object> raw = map("message", "pong");
        ApiResponse response = new ApiResponse(raw, 200, map(), null);

        assertThat(response.data()).isEqualTo(raw);
    }

    @Test
    void dataReturnsWholeRawWhenResultIsNullLikePhpNullCoalescing() {
        Map<String, Object> raw = map("result", null, "status", "OK");
        ApiResponse response = new ApiResponse(raw, 200, map(), null);

        assertThat(response.data()).isEqualTo(raw);
    }

    @Test
    void formattedDataOverridesResult() {
        Map<String, Object> formatted = map("status", "OK", "results", List.of());
        ApiResponse response = new ApiResponse(map("result", map("city", "Gdansk")), 200, map(), formatted);

        assertThat(response.data()).isEqualTo(formatted);
        assertThat(response.raw()).containsKey("result");
    }

    @Test
    void rawKeepsFullResponseInOrder() {
        Map<String, Object> raw = map("result", map("city", "Gdansk"), "unknownTopLevelField", "preserved", "a", 1L);
        ApiResponse response = new ApiResponse(raw, 200, map(), null);

        assertThat(response.raw()).isEqualTo(raw);
        assertThat(response.raw().keySet()).containsExactly("result", "unknownTopLevelField", "a");
    }

    @Test
    void rawAndMetaAreUnmodifiableCopies() {
        Map<String, Object> raw = map("result", 1L);
        Map<String, Object> meta = map("service", "Geocoder", "method", "geocode");
        ApiResponse response = new ApiResponse(raw, 200, meta, null);
        raw.put("later", true);
        meta.put("later", true);

        assertThat(response.raw()).containsOnlyKeys("result");
        assertThat(response.meta()).containsOnlyKeys("service", "method");
        assertThatThrownBy(() -> response.raw().put("x", 1)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> response.meta().put("x", 1)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullRawAndMetaBecomeEmptyMaps() {
        ApiResponse response = new ApiResponse(null, 204, null, null);

        assertThat(response.raw()).isEmpty();
        assertThat(response.meta()).isEmpty();
        assertThat(response.data()).isEqualTo(Map.of());
    }

    @Test
    void statusAndMetaAreExposed() {
        ApiResponse response = new ApiResponse(map(), 201, map("service", "Routes", "method", "route"), null);

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.meta()).containsEntry("service", "Routes").containsEntry("method", "route");
    }

    @Test
    void shortConstructorHasNoMetaAndNoFormattedData() {
        ApiResponse response = new ApiResponse(map("result", "ok"), 200);

        assertThat(response.meta()).isEmpty();
        assertThat(response.data()).isEqualTo("ok");
    }

    @Test
    void dataAsMapReturnsObjectResult() {
        ApiResponse response = new ApiResponse(map("result", map("city", "Warszawa")), 200);

        Map<String, Object> data = response.dataAsMap();

        assertThat(data).containsEntry("city", "Warszawa");
    }

    @Test
    void dataAsMapReturnsRawWithoutResult() {
        ApiResponse response = new ApiResponse(map("message", "pong"), 200);

        assertThat(response.dataAsMap()).containsEntry("message", "pong");
    }

    @Test
    void dataAsListReturnsArrayResult() {
        List<Object> result = new ArrayList<>(List.of(map("x", 1L), map("x", 2L)));
        ApiResponse response = new ApiResponse(map("result", result), 200);

        List<Object> data = response.dataAsList();

        assertThat(data).hasSize(2).isEqualTo(result);
    }

    @Test
    void dataAsListUsesFormattedData() {
        ApiResponse response = new ApiResponse(map("result", map()), 200, map(), List.of("a"));

        assertThat(response.dataAsList()).containsExactly("a");
    }

    @Test
    void dataAsMapOnArrayNamesExpectedAndActualType() {
        ApiResponse response = new ApiResponse(
                map("result", List.of(1L)), 200, map("service", "Geocoder", "method", "geocodemulti"), null);

        assertThatThrownBy(response::dataAsMap)
                .isInstanceOf(UnknownResponseException.class)
                .hasMessageContaining("JSON object (Map)")
                .hasMessageContaining("JSON array (List)")
                .hasMessageContaining("Geocoder.geocodemulti")
                .hasMessageContaining("dataAsList()")
                .satisfies(e -> assertThat(((UnknownResponseException) e).getStatusCode())
                        .isEqualTo(200));
    }

    @Test
    void dataAsListOnObjectNamesExpectedAndActualType() {
        ApiResponse response = new ApiResponse(map("result", map("city", "Warszawa")), 200);

        assertThatThrownBy(response::dataAsList)
                .isInstanceOf(UnknownResponseException.class)
                .hasMessageContaining("JSON array (List)")
                .hasMessageContaining("JSON object (Map)")
                .hasMessageContaining("dataAsMap()");
    }

    @Test
    void wrongTypeMessagesNameScalarTypes() {
        assertThatThrownBy(() -> new ApiResponse(map("result", "text"), 200).dataAsMap())
                .hasMessageContaining("JSON string (String)");
        assertThatThrownBy(() -> new ApiResponse(map("result", 5L), 200).dataAsList())
                .hasMessageContaining("JSON number (Long)");
        assertThatThrownBy(() -> new ApiResponse(map("result", 1.5), 200).dataAsList())
                .hasMessageContaining("JSON number (Double)");
        assertThatThrownBy(() -> new ApiResponse(map("result", true), 200).dataAsMap())
                .hasMessageContaining("JSON boolean (Boolean)");
        assertThatThrownBy(() -> new ApiResponse(map(), 200, map(), new Object()).dataAsMap())
                .hasMessageContaining("(Object)");
    }

    @Test
    void wrongTypeMessageWithoutMetaDoesNotNameEndpoint() {
        assertThatThrownBy(() -> new ApiResponse(map("result", "text"), 200).dataAsMap())
                .hasMessageStartingWith("Expected the response data to be a JSON object (Map), got a JSON string");
    }
}
