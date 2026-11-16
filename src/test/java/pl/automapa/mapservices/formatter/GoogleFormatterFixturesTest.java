package pl.automapa.mapservices.formatter;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.automapa.mapservices.formatter.GoogleFormatterTest.asList;
import static pl.automapa.mapservices.formatter.GoogleFormatterTest.asMap;
import static pl.automapa.mapservices.formatter.GoogleFormatterTest.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.automapa.mapservices.support.Fixtures;

/**
 * Formats every geocoding and autocomplete fixture and checks the whole output, plus the shape against the
 * canonical Google Geocoding API response in {@code google_geocoding_api_reference.json}.
 */
class GoogleFormatterFixturesTest {

    /** Google result types the formatter may produce (subset of the Google Geocoding API type list). */
    private static final Set<Object> VALID_GOOGLE_TYPES = Set.of(
            "street_address",
            "street_number",
            "route",
            "locality",
            "administrative_area_level_1",
            "administrative_area_level_2",
            "country",
            "postal_code",
            "political",
            "sublocality",
            "neighborhood",
            "premise",
            "natural_feature",
            "airport",
            "park",
            "point_of_interest",
            "geometric_center",
            "approximate",
            "building");

    private final GoogleFormatter formatter = new GoogleFormatter();

    private Map<String, Object> format(String fixture, String method) {
        Map<String, Object> raw = Fixtures.load(fixture);
        Map<String, Object> output = asMap(formatter.format(raw, method));
        assertThat(output.get("_raw")).isSameAs(raw);
        assertThat(raw).isEqualTo(Fixtures.load(fixture));
        return output;
    }

    private static Map<String, Object> component(String name, String... types) {
        return map("long_name", name, "short_name", name, "types", List.of(types));
    }

    private static Map<String, Object> result(
            String address, List<Map<String, Object>> components, Double lat, Double lng, String type) {
        return map(
                "formatted_address",
                address,
                "address_components",
                components,
                "geometry",
                map("location", map("lat", lat, "lng", lng), "viewport", null),
                "place_id",
                null,
                "types",
                List.of(type));
    }

    private static Map<String, Object> error(String message) {
        return map("status", "ERROR", "message", message);
    }

    private static void assertOk(Map<String, Object> output, Object... expectedResults) {
        assertThat(output.keySet()).containsExactly("status", "results", "_raw");
        assertThat(output).containsEntry("status", "OK");
        assertThat(asList(output.get("results"))).containsExactly(expectedResults);
    }

    // --- geocoder -----------------------------------------------------------------------------------------------

    @Test
    void geocodeSuccess() {
        assertOk(
                format("geocode_success", "Geocoder.geocode"),
                result(
                        "Domaniewska 17A, 02-672 Warszawa, Polska",
                        List.of(
                                component("Domaniewska", "route"),
                                component("17A", "street_number"),
                                component("Warszawa", "locality", "political"),
                                component("02-672", "postal_code"),
                                component("mazowieckie", "administrative_area_level_1", "political"),
                                component("Mokot\u00f3w", "sublocality", "political"),
                                component("Polska", "country", "political")),
                        52.2297,
                        21.0122,
                        "street_address"));
    }

    @Test
    void geocodeEmptyResults() {
        Map<String, Object> output = format("geocode_empty_results", "Geocoder.geocode");

        assertThat(output.keySet()).containsExactly("status", "results", "_raw");
        assertThat(output).containsEntry("status", "ZERO_RESULTS").containsEntry("results", List.of());
    }

    @Test
    void geocodemultiSuccessKeepsErrorItemsInOrder() {
        assertOk(
                format("geocodemulti_success", "Geocoder.geocodemulti"),
                result(
                        "Domaniewska 37, 02-672 Warszawa, PL",
                        List.of(
                                component("Domaniewska", "route"),
                                component("37", "street_number"),
                                component("Warszawa", "locality", "political"),
                                component("02-672", "postal_code"),
                                component("mazowieckie", "administrative_area_level_1", "political"),
                                component("Warszawa", "sublocality", "political"),
                                component("PL", "country", "political")),
                        52.18288,
                        21.0073642,
                        "street_address"),
                error("Unknown address"),
                result(
                        "Grunwaldzka 2, 84-230 Rumia, PL",
                        List.of(
                                component("Grunwaldzka", "route"),
                                component("2", "street_number"),
                                component("Rumia", "locality", "political"),
                                component("84-230", "postal_code"),
                                component("pomorskie", "administrative_area_level_1", "political"),
                                component("wejherowski", "sublocality", "political"),
                                component("PL", "country", "political")),
                        54.57206,
                        18.3865894,
                        "street_address"));
    }

    @Test
    void revgeocodeSuccess() {
        assertOk(
                format("revgeocode_success", "Geocoder.revgeocode"),
                result(
                        "Domaniewska 17A, 02-672 Warszawa, Polska",
                        List.of(
                                component("Domaniewska", "route"),
                                component("17A", "street_number"),
                                component("Warszawa", "locality", "political"),
                                component("02-672", "postal_code"),
                                component("mazowieckie", "administrative_area_level_1", "political"),
                                component("Polska", "country", "political")),
                        52.2297,
                        21.0122,
                        "street_address"));
    }

    @Test
    void revgeocodemultiSuccessWithoutCoordinatesOrQuality() {
        assertOk(
                format("revgeocodemulti_success", "Geocoder.revgeocodemulti"),
                result(
                        "Domaniewska 37p, Warszawa, PL",
                        List.of(
                                component("Domaniewska", "route"),
                                component("37p", "street_number"),
                                component("Warszawa", "locality", "political"),
                                component("mazowieckie", "administrative_area_level_1", "political"),
                                component("Warszawa", "sublocality", "political"),
                                component("PL", "country", "political")),
                        null,
                        null,
                        "country"),
                error("Point outside Europe"),
                result(
                        "vulitsa Karalya 5, 220004 Minsk, BY",
                        List.of(
                                component("vulitsa Karalya", "route"),
                                component("5", "street_number"),
                                component("Minsk", "locality", "political"),
                                component("220004", "postal_code"),
                                component("Minsk", "administrative_area_level_1", "political"),
                                component("Minsk", "sublocality", "political"),
                                component("BY", "country", "political")),
                        null,
                        null,
                        "country"));
    }

    // --- autocomplete -------------------------------------------------------------------------------------------

    @Test
    void autocompleteSearchSuccess() {
        assertOk(
                format("autocomplete_search_success", "Autocomplete.search"),
                result(
                        "Domaniewska 37, 02-672 Warszawa",
                        List.of(
                                component("Domaniewska", "route"),
                                component("37", "street_number"),
                                component("Warszawa", "locality", "political"),
                                component("02-672", "postal_code"),
                                component("mazowieckie", "administrative_area_level_1", "political"),
                                component("Warszawa", "sublocality", "political")),
                        52.18284,
                        21.0071318,
                        "building"),
                result(
                        "Domaniewska 37A, 02-672 Warszawa",
                        List.of(
                                component("Domaniewska", "route"),
                                component("37A", "street_number"),
                                component("Warszawa", "locality", "political"),
                                component("02-672", "postal_code"),
                                component("mazowieckie", "administrative_area_level_1", "political"),
                                component("Warszawa", "sublocality", "political")),
                        52.18238,
                        21.0070156,
                        "building"));
    }

    /**
     * The fixture item has no {@code coords}, so (as in PHP) it is mapped like a geocoder result: {@code zipcode}
     * is not a geocoder field and there is no {@code quality}.
     */
    @Test
    void autocompleteSearchAddressItemWithoutCoordsIsMappedLikePhp() {
        assertOk(
                format("autocomplete_search_address_success", "Autocomplete.searchAddress"),
                result(
                        "Domaniewska, Warszawa",
                        List.of(component("Domaniewska", "route"), component("Warszawa", "locality", "political")),
                        null,
                        null,
                        "country"));
    }

    @Test
    void autocompleteSearchEmpty() {
        Map<String, Object> output = format("autocomplete_search_empty", "Autocomplete.search");

        assertThat(output).containsEntry("status", "ZERO_RESULTS").containsEntry("results", List.of());
    }

    // --- other methods ------------------------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
        "routes_route_success,Routes.route",
        "routes_matrix_success,Routes.matrix",
        "road_get_segment_info_success,Road.getSegmentInfo",
        "session_salt,Session.getSalt"
    })
    void otherMethodsPassThroughWithRaw(String fixture, String method) {
        Map<String, Object> raw = Fixtures.load(fixture);

        Map<String, Object> output = format(fixture, method);

        List<String> expectedKeys = new ArrayList<>(raw.keySet());
        expectedKeys.add("_raw");
        assertThat(output.keySet()).containsExactlyElementsOf(expectedKeys);
        raw.forEach((key, value) -> assertThat(output).containsEntry(key, value));
    }

    // --- shape against the Google reference ---------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
        "geocode_success,Geocoder.geocode",
        "geocodemulti_success,Geocoder.geocodemulti",
        "revgeocode_success,Geocoder.revgeocode",
        "revgeocodemulti_success,Geocoder.revgeocodemulti",
        "autocomplete_search_success,Autocomplete.search",
        "autocomplete_search_address_success,Autocomplete.searchAddress"
    })
    void outputShapeFollowsTheGoogleReference(String fixture, String method) {
        Map<String, Object> reference = Fixtures.load("google_geocoding_api_reference");
        Map<String, Object> referenceResult =
                asMap(asList(reference.get("results")).get(0));
        Map<String, Object> referenceComponent =
                asMap(asList(referenceResult.get("address_components")).get(0));
        Map<String, Object> referenceGeometry = asMap(referenceResult.get("geometry"));
        Map<String, Object> referenceLocation = asMap(referenceGeometry.get("location"));

        Map<String, Object> output = format(fixture, method);

        assertThat(reference.keySet()).contains("status", "results");
        assertThat(output.keySet()).containsAll(List.of("status", "results"));
        for (Object item : asList(output.get("results"))) {
            Map<String, Object> result = asMap(item);
            if ("ERROR".equals(result.get("status"))) {
                continue;
            }
            assertThat(referenceResult.keySet()).containsAll(result.keySet());
            assertThat(result.keySet())
                    .containsExactlyInAnyOrder(
                            "formatted_address", "address_components", "geometry", "place_id", "types");
            assertThat(result.get("formatted_address")).isInstanceOf(String.class);
            assertThat(result.get("place_id")).isNull();
            Map<String, Object> geometry = asMap(result.get("geometry"));
            assertThat(referenceGeometry.keySet()).containsAll(geometry.keySet());
            assertThat(geometry.get("viewport")).isNull();
            assertThat(asMap(geometry.get("location")).keySet()).containsExactlyElementsOf(referenceLocation.keySet());
            for (Object component : asList(result.get("address_components"))) {
                Map<String, Object> componentMap = asMap(component);
                assertThat(componentMap.keySet()).containsExactlyElementsOf(referenceComponent.keySet());
                assertThat(asList(componentMap.get("types"))).isSubsetOf(VALID_GOOGLE_TYPES);
            }
            assertThat(asList(result.get("types"))).hasSize(1).isSubsetOf(VALID_GOOGLE_TYPES);
        }
    }
}
