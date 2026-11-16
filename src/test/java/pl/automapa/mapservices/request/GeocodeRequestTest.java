package pl.automapa.mapservices.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GeocodeRequestTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> address(Map<String, Object> params) {
        return (Map<String, Object>) params.get("address");
    }

    @Test
    void cityOnlySendsAddressWithCity() {
        Map<String, Object> params = GeocodeRequest.builder("Warszawa").build().toParams();

        assertThat(params).containsOnlyKeys("address");
        assertThat(address(params)).containsExactly(Map.entry("city", "Warszawa"));
    }

    @Test
    void allFieldsInPhpOrder() {
        Map<String, Object> params = GeocodeRequest.builder("Warszawa")
                .house("37")
                .street("Domaniewska")
                .pcode("02-672")
                .quarter("Mokotow")
                .community("Warszawa")
                .district("Warszawa")
                .province("mazowieckie")
                .country("PL")
                .maxResults(3)
                .build()
                .toParams();

        assertThat(params).containsOnlyKeys("address", "maxResults").containsEntry("maxResults", 3);
        assertThat(address(params))
                .containsExactly(
                        Map.entry("city", "Warszawa"),
                        Map.entry("country", "PL"),
                        Map.entry("province", "mazowieckie"),
                        Map.entry("district", "Warszawa"),
                        Map.entry("community", "Warszawa"),
                        Map.entry("quarter", "Mokotow"),
                        Map.entry("pcode", "02-672"),
                        Map.entry("street", "Domaniewska"),
                        Map.entry("house", "37"));
        assertThat(params.keySet()).containsExactly("address", "maxResults");
    }

    @Test
    void emptyAndNullFieldsAreLeftOutOfTheAddress() {
        Map<String, Object> params = GeocodeRequest.builder("Warszawa")
                .street("")
                .house(null)
                .country("")
                .pcode("02-672")
                .build()
                .toParams();

        assertThat(address(params)).containsExactly(Map.entry("city", "Warszawa"), Map.entry("pcode", "02-672"));
    }

    @Test
    void emptyCityIsAllowedAndLeftOut() {
        Map<String, Object> params =
                GeocodeRequest.builder("").street("Domaniewska").build().toParams();

        assertThat(address(params)).containsExactly(Map.entry("street", "Domaniewska"));
    }

    @Test
    void addressIsAlwaysSentEvenWhenEmpty() {
        Map<String, Object> params = GeocodeRequest.builder("").build().toParams();

        assertThat(params).containsOnlyKeys("address");
        assertThat(address(params)).isEmpty();
    }

    @Test
    void nullCityIsRejected() {
        assertThatThrownBy(() -> GeocodeRequest.builder(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GeocodeRequest")
                .hasMessageContaining("city is required")
                .hasMessageContaining("\"\"");
    }

    @Test
    void maxResultsIsSentOnlyWhenSet() {
        assertThat(GeocodeRequest.builder("Warszawa").build().toParams()).doesNotContainKey("maxResults");
        assertThat(GeocodeRequest.builder("Warszawa").maxResults(null).build().toParams())
                .doesNotContainKey("maxResults");
        assertThat(GeocodeRequest.builder("Warszawa").maxResults(1).build().toParams())
                .containsEntry("maxResults", 1);
    }

    @Test
    void namedAddressOverridesAddressFromExtra() {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("address", Map.of("city", "OldCity", "street", "OldStreet"));
        extra.put("futureParam", "value");

        Map<String, Object> params = GeocodeRequest.builder("Warszawa")
                .street("Nowa")
                .extra(extra)
                .build()
                .toParams();

        assertThat(params.keySet()).containsExactly("address", "futureParam");
        assertThat(address(params)).containsExactly(Map.entry("city", "Warszawa"), Map.entry("street", "Nowa"));
    }

    @Test
    void maxResultsFromExtraIsKeptWhenNotSet() {
        Map<String, Object> params = GeocodeRequest.builder("Warszawa")
                .extra("maxResults", 5)
                .build()
                .toParams();

        assertThat(params).containsEntry("maxResults", 5);
    }

    @Test
    void formatGoesToExtra() {
        Map<String, Object> params =
                GeocodeRequest.builder("Warszawa").format("google").build().toParams();

        assertThat(params).containsEntry("format", "google");
    }
}
