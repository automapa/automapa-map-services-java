package pl.automapa.mapservices.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoadPermitRequestTest {

    private static Map<String, Object> polygon() {
        Map<String, Object> poly = new LinkedHashMap<>();
        poly.put("type", "Polygon");
        poly.put(
                "coordinates",
                List.of(List.of(
                        List.of(20.993155973821672, 50.01069571221776),
                        List.of(20.994428585759994, 50.01089900951218),
                        List.of(20.994138589184054, 50.011559010137475),
                        List.of(20.923253, 50.0118279),
                        List.of(20.993155973821672, 50.01069571221776))));
        return poly;
    }

    @Test
    void minimalRequestSendsOnlyPermissionsAndPoly() {
        RoadPermitRequest request =
                RoadPermitRequest.builder(Map.of("tr_tonnage", 12), polygon()).build();

        assertThat(request.toParams())
                .containsExactly(Map.entry("permissions", Map.of("tr_tonnage", 12)), Map.entry("poly", polygon()));
    }

    @Test
    void fullRequestSendsSnakeCaseKeysInApiOrder() {
        Map<String, Number> permissions = new LinkedHashMap<>();
        permissions.put("tr_tonnage", 12);
        permissions.put("tr_tonn_axis", 8.5);

        RoadPermitRequest request = RoadPermitRequest.builder(permissions, polygon())
                .dateStart("2026-01-01")
                .dateEnd("2026-12-31")
                .description("Test permit")
                .documentName("DOC-001")
                .location("Tarnow")
                .isValidAfterExpiry(false)
                .build();

        assertThat(request.toParams())
                .containsExactly(
                        Map.entry("permissions", permissions),
                        Map.entry("poly", polygon()),
                        Map.entry("date_start", "2026-01-01"),
                        Map.entry("date_end", "2026-12-31"),
                        Map.entry("description", "Test permit"),
                        Map.entry("document_name", "DOC-001"),
                        Map.entry("location", "Tarnow"),
                        Map.entry("is_valid_after_expiry", false));
    }

    @Test
    void nullOptionalFieldsAreLeftOut() {
        RoadPermitRequest request = RoadPermitRequest.builder(Map.of("tr_tonnage", 12), polygon())
                .dateStart(null)
                .dateEnd(null)
                .description(null)
                .documentName(null)
                .location(null)
                .isValidAfterExpiry(null)
                .build();

        assertThat(request.toParams()).containsOnlyKeys("permissions", "poly");
    }

    @Test
    void namedFieldsOverrideExtra() {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("date_start", "1999-01-01");
        extra.put("newApiField", "some-value");

        RoadPermitRequest request = RoadPermitRequest.builder(Map.of("tr_tonnage", 12), polygon())
                .extra(extra)
                .dateStart("2026-06-01")
                .build();

        assertThat(request.toParams())
                .containsExactly(
                        Map.entry("date_start", "2026-06-01"),
                        Map.entry("newApiField", "some-value"),
                        Map.entry("permissions", Map.of("tr_tonnage", 12)),
                        Map.entry("poly", polygon()));
    }

    @Test
    void copiesPermissionsAndPoly() {
        Map<String, Number> permissions = new HashMap<>(Map.of("tr_tonnage", 12));
        Map<String, Object> poly = polygon();

        RoadPermitRequest request = RoadPermitRequest.builder(permissions, poly).build();
        permissions.put("tr_height", 4.0);
        poly.put("type", "MultiPolygon");

        assertThat(request.toParams())
                .containsEntry("permissions", Map.of("tr_tonnage", 12))
                .containsEntry("poly", polygon());
    }

    @Test
    void rejectsNullOrEmptyPermissions() {
        assertThatThrownBy(() -> RoadPermitRequest.builder(null, polygon()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RoadPermitRequest.builder")
                .hasMessageContaining("permissions is required but was null")
                .hasMessageContaining("Map.of(\"tr_tonnage\", 12)");
        assertThatThrownBy(() -> RoadPermitRequest.builder(Map.of(), polygon()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("permissions is required but was empty");
    }

    @Test
    void rejectsNullOrEmptyPoly() {
        assertThatThrownBy(() -> RoadPermitRequest.builder(Map.of("tr_tonnage", 12), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("poly is required but was null")
                .hasMessageContaining("Polygon");
        assertThatThrownBy(() -> RoadPermitRequest.builder(Map.of("tr_tonnage", 12), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("poly is required but was empty");
    }
}
