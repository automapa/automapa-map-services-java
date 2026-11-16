package pl.automapa.mapservices.live;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.automapa.mapservices.request.AutocompleteSearchRequest;
import pl.automapa.mapservices.request.SearchAddressRequest;
import pl.automapa.mapservices.response.ApiResponse;

/** Autocomplete service on the live API (suite {@code autocomplete}). */
class AutocompleteLiveTest extends LiveApiTestBase {

    @Override
    protected String suiteName() {
        return "autocomplete";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        assertThat(value).isInstanceOf(List.class);
        return (List<Object>) value;
    }

    @Test
    void searchReturnsPlacesWithCoords() {
        ApiResponse response = callLive(() -> buildApiClient()
                .autocomplete()
                .search(AutocompleteSearchRequest.builder("Warszawa Domaniewska 37")
                        .build()));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.raw()).containsKey("result");
        List<Object> items = list(response.dataAsMap().get("items"));
        assertThat(items).as("search should return at least one result").isNotEmpty();

        Map<String, Object> first = map(items.get(0));
        assertThat(first).containsKeys("type", "name", "coords");
        assertThat(map(first.get("coords"))).containsKeys("x", "y");
    }

    @Test
    void searchWithPlaceTypeReturnsPlacesOnly() {
        ApiResponse response = callLive(() -> buildApiClient()
                .autocomplete()
                .search(AutocompleteSearchRequest.builder("Warszawa Domaniewska")
                        .types(List.of("place"))
                        .build()));

        List<Object> items = list(response.dataAsMap().get("items"));
        assertThat(items).isNotEmpty();
        for (Object item : items) {
            assertThat(map(item)).containsEntry("type", "place");
        }
    }

    @Test
    void searchAddressReturnsStreets() {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("city", "Warszawa");
        query.put("street", "Dom");
        query.put("number", "");
        query.put("zipcode", "");

        ApiResponse response = callLive(() -> buildApiClient()
                .autocomplete()
                .searchAddress(SearchAddressRequest.builder(query, "street")
                        .selected(List.of("city"))
                        .build()));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.raw()).containsKey("result");
        List<Object> items = list(response.dataAsMap().get("items"));
        assertThat(items).as("searchAddress should return at least one result").isNotEmpty();
        assertThat(map(items.get(0))).containsKeys("city", "street");
    }

    @Test
    void getPostalCodesForStreetReturnsSortedUniqueCodes() {
        List<String> codes =
                callLive(() -> buildApiClient().autocomplete().getPostalCodesForStreet("Warszawa", "Domaniewska"));

        assertThat(codes).as("Domaniewska in Warsaw has postal codes").isNotEmpty();
        assertThat(codes).allMatch(code -> code.matches("\\d{2}-\\d{3}"), "postal code NN-NNN");
        assertThat(codes).isSorted().doesNotHaveDuplicates();
        assertThat(codes).as("Domaniewska 37 is in 02-672").contains("02-672");
    }
}
