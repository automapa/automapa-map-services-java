package pl.automapa.mapservices.formatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Port of the PHP SDK {@code tests/Unit/GoogleFormatterTest.php} plus PHP edge cases of the mapping. */
class GoogleFormatterTest {

    private final GoogleFormatter formatter = new GoogleFormatter();

    // --- helpers ------------------------------------------------------------------------------------------------

    /** Builds a map with insertion order from alternating key/value arguments; values may be {@code null}. */
    static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            out.put((String) keyValues[i], keyValues[i + 1]);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    static List<Object> asList(Object value) {
        assertThat(value).isInstanceOf(List.class);
        return (List<Object>) value;
    }

    private Map<String, Object> format(Map<String, Object> raw, String method) {
        return asMap(formatter.format(raw, method));
    }

    private static List<Object> results(Map<String, Object> output) {
        return asList(output.get("results"));
    }

    private static Map<String, Object> firstResult(Map<String, Object> output) {
        return asMap(results(output).get(0));
    }

    private static Map<String, Object> location(Map<String, Object> result) {
        return asMap(asMap(result.get("geometry")).get("location"));
    }

    private static List<Object> componentTypes(Map<String, Object> result) {
        List<Object> types = new ArrayList<>();
        for (Object component : asList(result.get("address_components"))) {
            types.add(asMap(component).get("types"));
        }
        return types;
    }

    private Map<String, Object> geocodeSingle(Map<String, Object> result) {
        return firstResult(format(map("result", result), "Geocoder.geocode"));
    }

    // --- port of GoogleFormatterTest.php ------------------------------------------------------------------------

    @Test
    void mapsGeocodeResultToGoogleFormat() {
        Map<String, Object> raw = map(
                "result",
                map(
                        "x",
                        21.0122,
                        "y",
                        52.2297,
                        "city",
                        "Warszawa",
                        "street",
                        "Domaniewska",
                        "house",
                        "17A",
                        "pcode",
                        "02-672",
                        "country",
                        "Polska",
                        "province",
                        "mazowieckie",
                        "district",
                        "Mokot\u00f3w",
                        "quality",
                        9L));

        Map<String, Object> output = format(raw, "Geocoder.geocode");

        assertThat(output).containsEntry("status", "OK");
        assertThat(results(output)).hasSize(1);
        Map<String, Object> result = firstResult(output);
        assertThat(result)
                .containsEntry("formatted_address", "Domaniewska 17A, 02-672 Warszawa, Polska")
                .containsEntry("place_id", null)
                .containsEntry("types", List.of("street_address"));
        assertThat(asMap(result.get("geometry"))).containsEntry("viewport", null);
    }

    @Test
    void setsLatFromYAndLngFromX() {
        Map<String, Object> result = geocodeSingle(map("x", 21.0122, "y", 52.2297, "city", "Warszawa", "quality", 9L));

        assertThat(location(result)).containsExactly(Map.entry("lat", 52.2297), Map.entry("lng", 21.0122));
    }

    @Test
    void returnsZeroResultsForEmptyResult() {
        Map<String, Object> raw = map("result", List.of());

        Map<String, Object> output = format(raw, "Geocoder.geocode");

        assertThat(output)
                .containsExactly(
                        Map.entry("status", "ZERO_RESULTS"), Map.entry("results", List.of()), Map.entry("_raw", raw));
    }

    @Test
    void rawAttachedToFormattedOutput() {
        Map<String, Object> raw = map("result", map("x", 21.0122, "y", 52.2297, "city", "Warszawa", "quality", 9L));

        Map<String, Object> output = format(raw, "Geocoder.geocode");

        assertThat(output.keySet()).containsExactly("status", "results", "_raw");
        assertThat(output.get("_raw")).isSameAs(raw);
    }

    @Test
    void rawPreservesUnknownFields() {
        Map<String, Object> raw = map(
                "result",
                map(
                        "x",
                        21.0122,
                        "y",
                        52.2297,
                        "city",
                        "Warszawa",
                        "quality",
                        9L,
                        "unknownFutureField",
                        "preserved",
                        "mapLink",
                        "https://api.automapa.pl/map"));

        Map<String, Object> output = format(raw, "Geocoder.geocode");

        Map<String, Object> rawResult = asMap(asMap(output.get("_raw")).get("result"));
        assertThat(rawResult)
                .containsEntry("unknownFutureField", "preserved")
                .containsEntry("mapLink", "https://api.automapa.pl/map");
    }

    @Test
    void nonGeocodingMethodPassesThrough() {
        Map<String, Object> raw = map("result", map("distance", 123L, "duration", 456L));

        Map<String, Object> output = format(raw, "Routes.route");

        assertThat(asMap(output.get("result"))).containsEntry("distance", 123L);
        assertThat(output.get("_raw")).isEqualTo(raw);
    }

    @Test
    void viewportAndPlaceIdAreAlwaysNull() {
        Map<String, Object> result = geocodeSingle(map("x", 18.0, "y", 50.0, "city", "Katowice", "quality", 5L));

        assertThat(result).containsEntry("place_id", null);
        assertThat(asMap(result.get("geometry"))).containsEntry("viewport", null);
    }

    @Test
    void mapsAddressComponentsCorrectly() {
        Map<String, Object> result = geocodeSingle(map(
                "x",
                21.0,
                "y",
                52.0,
                "city",
                "Warszawa",
                "street",
                "Marsza\u0142kowska",
                "house",
                "10",
                "pcode",
                "00-590",
                "province",
                "mazowieckie",
                "district",
                "\u015ar\u00f3dmie\u015bcie",
                "country",
                "Polska",
                "quality",
                9L));

        assertThat(componentTypes(result))
                .containsExactly(
                        List.of("route"),
                        List.of("street_number"),
                        List.of("locality", "political"),
                        List.of("postal_code"),
                        List.of("administrative_area_level_1", "political"),
                        List.of("sublocality", "political"),
                        List.of("country", "political"));
    }

    @Test
    void revgeocodeMethodIsMappedAsGeocoding() {
        Map<String, Object> raw = map("result", map("x", 19.94, "y", 50.06, "city", "Krak\u00f3w", "quality", 7L));

        Map<String, Object> output = format(raw, "Geocoder.revgeocode");

        assertThat(output).containsEntry("status", "OK");
        assertThat(results(output)).hasSize(1);
    }

    @Test
    void mapsAutocompleteSearchResultToGoogleFormat() {
        Map<String, Object> raw = map(
                "result",
                map(
                        "items",
                        List.of(map(
                                "type",
                                "place",
                                "subtype",
                                "building",
                                "name",
                                "Domaniewska 37, 02-672 Warszawa",
                                "place",
                                "Warszawa",
                                "street",
                                "Domaniewska",
                                "number",
                                "37",
                                "postal_code",
                                "02-672",
                                "province",
                                "mazowieckie",
                                "district",
                                "Warszawa",
                                "coords",
                                map("x", 21.0071318, "y", 52.18284)))));

        Map<String, Object> output = format(raw, "Autocomplete.search");

        assertThat(output).containsEntry("status", "OK");
        assertThat(results(output)).hasSize(1);
        Map<String, Object> result = firstResult(output);
        assertThat(result)
                .containsEntry("formatted_address", "Domaniewska 37, 02-672 Warszawa")
                .containsEntry("types", List.of("building"))
                .containsEntry("place_id", null);
        assertThat(asMap(result.get("geometry"))).containsEntry("viewport", null);
    }

    @Test
    void autocompleteSearchSetsLatFromCoordsYAndLngFromCoordsX() {
        Map<String, Object> raw = map(
                "result",
                map(
                        "items",
                        List.of(map(
                                "type",
                                "place",
                                "subtype",
                                "building",
                                "place",
                                "Warszawa",
                                "street",
                                "Domaniewska",
                                "number",
                                "37",
                                "postal_code",
                                "02-672",
                                "coords",
                                map("x", 21.0071318, "y", 52.18284)))));

        Map<String, Object> result = firstResult(format(raw, "Autocomplete.search"));

        assertThat(location(result)).containsExactly(Map.entry("lat", 52.18284), Map.entry("lng", 21.0071318));
    }

    @Test
    void mapsAutocompleteSearchAddressResultToGoogleFormat() {
        Map<String, Object> raw = map(
                "result",
                map(
                        "items",
                        List.of(map(
                                "city",
                                "Warszawa",
                                "street",
                                "Domaniewska",
                                "zipcode",
                                "02-672",
                                "count",
                                1L,
                                "coords",
                                map("x", 21.0071318, "y", 52.18284)))));

        Map<String, Object> output = format(raw, "Autocomplete.searchAddress");

        assertThat(output).containsEntry("status", "OK");
        assertThat(results(output)).hasSize(1);
        Map<String, Object> result = firstResult(output);
        assertThat(location(result)).containsExactly(Map.entry("lat", 52.18284), Map.entry("lng", 21.0071318));
        assertThat(result).containsEntry("formatted_address", "Domaniewska, 02-672 Warszawa");
    }

    @Test
    void autocompleteSearchAddressWithoutCoordsHasNullLocation() {
        Map<String, Object> raw = map(
                "result",
                map(
                        "items",
                        List.of(map(
                                "city",
                                "Warszawa",
                                "street",
                                "Domaniewska",
                                "zipcode",
                                "02-672",
                                "count",
                                3L,
                                "coords",
                                map("x", null, "y", null)))));

        Map<String, Object> result = firstResult(format(raw, "Autocomplete.searchAddress"));

        assertThat(location(result)).containsEntry("lat", null).containsEntry("lng", null);
    }

    @Test
    void returnsZeroResultsForEmptyAutocompleteItems() {
        Map<String, Object> raw = map("result", map("items", List.of()));

        Map<String, Object> output = format(raw, "Autocomplete.search");

        assertThat(output)
                .containsExactly(
                        Map.entry("status", "ZERO_RESULTS"), Map.entry("results", List.of()), Map.entry("_raw", raw));
    }

    @Test
    void rawAttachedForAutocompleteSearch() {
        Map<String, Object> raw = map(
                "result",
                map(
                        "items",
                        List.of(map(
                                "type",
                                "place",
                                "subtype",
                                "building",
                                "place",
                                "Warszawa",
                                "street",
                                "Domaniewska",
                                "number",
                                "37",
                                "postal_code",
                                "02-672",
                                "coords",
                                map("x", 21.0071318, "y", 52.18284),
                                "unknownFutureField",
                                "should be preserved"))));

        Map<String, Object> output = format(raw, "Autocomplete.search");

        assertThat(output.get("_raw")).isSameAs(raw);
        Map<String, Object> item =
                asMap(asList(asMap(asMap(output.get("_raw")).get("result")).get("items"))
                        .get(0));
        assertThat(item).containsEntry("unknownFutureField", "should be preserved");
    }

    // --- argument validation ------------------------------------------------------------------------------------

    @Test
    void rejectsNullRawWithAHint() {
        assertThatThrownBy(() -> formatter.format(null, "Geocoder.geocode"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GoogleFormatter")
                .hasMessageContaining("raw response is required");
    }

    @Test
    void rejectsNullMethodWithAHint() {
        assertThatThrownBy(() -> formatter.format(map("result", "x"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GoogleFormatter")
                .hasMessageContaining("Geocoder.geocode");
    }

    // --- pass-through of other methods --------------------------------------------------------------------------

    @Test
    void passThroughCopiesAllKeysInOrderAndAppendsRaw() {
        Map<String, Object> raw = map("result", "pong", "extra", 1L);

        Map<String, Object> output = format(raw, "PingPong.ping");

        assertThat(output).containsExactly(Map.entry("result", "pong"), Map.entry("extra", 1L), Map.entry("_raw", raw));
        assertThat(raw).containsOnlyKeys("result", "extra");
    }

    @Test
    void passThroughReplacesAnExistingRawKeyInPlaceLikeArrayMerge() {
        Map<String, Object> raw = map("_raw", "old", "result", 1L);

        Map<String, Object> output = format(raw, "Road.getSegmentInfo");

        assertThat(output.keySet()).containsExactly("_raw", "result");
        assertThat(output.get("_raw")).isSameAs(raw);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Geocoder.Geocode", "geocoder.geocode", "Autocomplete.getPostalCodesForStreet", ""})
    void methodMatchingIsExactAndCaseSensitive(String method) {
        Map<String, Object> raw = map("result", map("x", 1.0, "y", 2.0, "city", "A"));

        Map<String, Object> output = format(raw, method);

        assertThat(output).containsKeys("result", "_raw").doesNotContainKey("status");
    }

    // --- empty results (PHP empty()) ----------------------------------------------------------------------------

    @Test
    void missingResultGivesZeroResults() {
        assertThat(format(map("other", 1L), "Geocoder.geocode")).containsEntry("status", "ZERO_RESULTS");
    }

    @Test
    void nullResultGivesZeroResults() {
        assertThat(format(map("result", null), "Geocoder.geocode")).containsEntry("status", "ZERO_RESULTS");
    }

    @Test
    void phpEmptyScalarResultsGiveZeroResults() {
        for (Object empty : Arrays.asList("", "0", 0L, 0.0, false, Map.of(), List.of())) {
            assertThat(format(map("result", empty), "Geocoder.geocode"))
                    .as("result %s", empty)
                    .containsEntry("status", "ZERO_RESULTS");
        }
    }

    @Test
    void nonEmptyScalarResultGivesZeroResults() {
        for (Object scalar : Arrays.asList("abc", 1L, 2.5, true)) {
            assertThat(format(map("result", scalar), "Geocoder.geocode"))
                    .as("result %s", scalar)
                    .containsEntry("status", "ZERO_RESULTS")
                    .containsEntry("results", List.of());
        }
    }

    // --- result shapes ------------------------------------------------------------------------------------------

    @Test
    void singleResultIsDetectedByAnyOfXYOrCity() {
        assertThat(results(format(map("result", map("x", 1.0)), "Geocoder.geocode")))
                .hasSize(1);
        assertThat(results(format(map("result", map("y", 1.0)), "Geocoder.geocode")))
                .hasSize(1);
        assertThat(results(format(map("result", map("city", "A")), "Geocoder.geocode")))
                .hasSize(1);
    }

    @Test
    void listResultOfNonMultiMethodMapsEachElement() {
        Map<String, Object> raw =
                map("result", List.of(map("city", "A", "quality", 9L), map("city", "B", "quality", 3L)));

        List<Object> results = results(format(raw, "Geocoder.geocode"));

        assertThat(results).hasSize(2);
        assertThat(asMap(results.get(0))).containsEntry("formatted_address", "A");
        assertThat(asMap(results.get(1)))
                .containsEntry("formatted_address", "B")
                .containsEntry("types", List.of("administrative_area_level_1"));
    }

    @Test
    void objectResultWithoutCoordinatesMapsEachValue() {
        Map<String, Object> raw = map("result", map("first", map("city", "A"), "second", map("city", "B")));

        List<Object> results = results(format(raw, "Geocoder.geocode"));

        assertThat(results).extracting(r -> asMap(r).get("formatted_address")).containsExactly("A", "B");
    }

    @Test
    void nullKeysAreTreatedAsMissingLikeIsset() {
        Map<String, Object> raw = map("result", map("x", null, "y", null, "city", null, "item", map("city", "A")));

        List<Object> results = results(format(raw, "Geocoder.geocode"));

        assertThat(results).hasSize(1);
        assertThat(asMap(results.get(0))).containsEntry("formatted_address", "A");
    }

    @Test
    void nonMapElementsAreMappedAsEmptyOrSkipped() {
        Map<String, Object> raw = map("result", Arrays.asList(List.of("a"), "scalar", null, map("city", "A")));

        List<Object> results = results(format(raw, "Geocoder.geocode"));

        assertThat(results).hasSize(2);
        assertThat(asMap(results.get(0)))
                .containsEntry("formatted_address", "")
                .containsEntry("address_components", List.of())
                .containsEntry("types", List.of("country"));
        assertThat(location(asMap(results.get(0)))).containsEntry("lat", null).containsEntry("lng", null);
        assertThat(asMap(results.get(1))).containsEntry("formatted_address", "A");
    }

    @Test
    void itemsGivenAsObjectAreMappedByValue() {
        Map<String, Object> raw = map("result", map("items", map("a", map("place", "A", "coords", map("x", 1L)))));

        Map<String, Object> result = firstResult(format(raw, "Autocomplete.search"));

        assertThat(result).containsEntry("formatted_address", "A");
        assertThat(location(result)).containsEntry("lat", null).containsEntry("lng", 1.0);
    }

    @Test
    void itemsThatAreNotACollectionAreIgnored() {
        Map<String, Object> raw = map("result", map("items", "text", "city", "A"));

        Map<String, Object> result = firstResult(format(raw, "Autocomplete.search"));

        assertThat(result).containsEntry("formatted_address", "A");
    }

    // --- multi --------------------------------------------------------------------------------------------------

    @Test
    void multiErrorItemWithoutMessageGetsEmptyMessage() {
        Map<String, Object> raw = map("result", List.of(map("status", "ERROR")));

        assertThat(results(format(raw, "Geocoder.geocodemulti")))
                .containsExactly(map("status", "ERROR", "message", ""));
    }

    @Test
    void multiErrorMessageIsConvertedToString() {
        Map<String, Object> raw = map("result", List.of(map("status", "ERROR", "message", 42L)));

        assertThat(results(format(raw, "Geocoder.revgeocodemulti")))
                .containsExactly(map("status", "ERROR", "message", "42"));
    }

    @Test
    void multiSkipsScalarItemsAndNonMapValues() {
        Map<String, Object> raw = map(
                "result",
                Arrays.asList(
                        "scalar",
                        null,
                        map("status", "OK", "a", map("city", "A"), "b", "text"),
                        List.of(map("y", 1L))));

        List<Object> results = results(format(raw, "Geocoder.geocodemulti"));

        assertThat(results).hasSize(2);
        assertThat(asMap(results.get(0))).containsEntry("formatted_address", "A");
        assertThat(location(asMap(results.get(1)))).containsEntry("lat", 1.0);
    }

    @Test
    void multiWithOnlyEmptyItemsGivesZeroResults() {
        Map<String, Object> raw = map("result", List.of(List.of(), "x"));

        assertThat(format(raw, "Geocoder.geocodemulti"))
                .containsEntry("status", "ZERO_RESULTS")
                .containsEntry("results", List.of());
    }

    @Test
    void multiResultGivenAsObjectIsIteratedByValue() {
        Map<String, Object> raw = map("result", map("0", map("city", "A"), "1", map("status", "ERROR")));

        List<Object> results = results(format(raw, "Geocoder.geocodemulti"));

        assertThat(results).hasSize(2);
        assertThat(asMap(results.get(0))).containsEntry("formatted_address", "A");
        assertThat(results.get(1)).isEqualTo(map("status", "ERROR", "message", ""));
    }

    // --- quality to types ---------------------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
        "-5,country",
        "0,country",
        "1,country",
        "2,country",
        "3,administrative_area_level_1",
        "4,locality",
        "5,locality",
        "6,route",
        "7,route",
        "8,street_address",
        "9,street_address",
        "100,street_address"
    })
    void mapsQualityToTypeLikePhp(long quality, String type) {
        assertThat(geocodeSingle(map("city", "A", "quality", quality))).containsEntry("types", List.of(type));
    }

    @Test
    void qualityIsCastToIntegerLikePhp() {
        assertThat(geocodeSingle(map("city", "A", "quality", 3.9)))
                .containsEntry("types", List.of("administrative_area_level_1"));
        assertThat(geocodeSingle(map("city", "A", "quality", "9"))).containsEntry("types", List.of("street_address"));
        assertThat(geocodeSingle(map("city", "A", "quality", " 6 apples"))).containsEntry("types", List.of("route"));
        assertThat(geocodeSingle(map("city", "A", "quality", "abc"))).containsEntry("types", List.of("country"));
        assertThat(geocodeSingle(map("city", "A", "quality", true))).containsEntry("types", List.of("country"));
        assertThat(geocodeSingle(map("city", "A", "quality", List.of(1L)))).containsEntry("types", List.of("country"));
        assertThat(geocodeSingle(map("city", "A", "quality", null))).containsEntry("types", List.of("country"));
        assertThat(geocodeSingle(map("city", "A"))).containsEntry("types", List.of("country"));
    }

    // --- casting of coordinates and strings ---------------------------------------------------------------------

    @Test
    void coordinatesAreCastToDoubleLikePhp() {
        assertThat(location(geocodeSingle(map("x", 21L, "y", "52.5"))))
                .containsExactly(Map.entry("lat", 52.5), Map.entry("lng", 21.0));
        assertThat(location(geocodeSingle(map("x", "1e2", "y", "-.5abc"))))
                .containsExactly(Map.entry("lat", -0.5), Map.entry("lng", 100.0));
        assertThat(location(geocodeSingle(map("x", "abc", "y", true))))
                .containsExactly(Map.entry("lat", 1.0), Map.entry("lng", 0.0));
        assertThat(location(geocodeSingle(map("x", false, "y", map("a", 1L)))))
                .containsExactly(Map.entry("lat", 1.0), Map.entry("lng", 0.0));
        assertThat(location(geocodeSingle(map("x", List.of(), "y", ""))))
                .containsExactly(Map.entry("lat", 0.0), Map.entry("lng", 0.0));
    }

    @Test
    void scalarFieldsAreCastToStringLikePhp() {
        Map<String, Object> result = geocodeSingle(map("street", "Main", "house", 17L, "city", 2.0, "pcode", 1.5));

        assertThat(result).containsEntry("formatted_address", "Main 17, 1.5 2");
        assertThat(asList(result.get("address_components")))
                .extracting(c -> asMap(c).get("long_name"))
                .containsExactly("Main", "17", "2", "1.5");
    }

    @Test
    void booleansAreCastToStringLikePhp() {
        Map<String, Object> result = geocodeSingle(map("x", 1.0, "street", true, "house", false));

        assertThat(result).containsEntry("formatted_address", "1 ");
        assertThat(asList(result.get("address_components")))
                .extracting(c -> asMap(c).get("long_name"))
                .containsExactly("1", "");
    }

    @Test
    void largeAndFractionalDoublesKeepTheirValueAsString() {
        Map<String, Object> result = geocodeSingle(map("x", 1.0, "house", 1.0e20, "street", -0.25));

        assertThat(asList(result.get("address_components")))
                .extracting(c -> asMap(c).get("long_name"))
                .containsExactly("-0.25", "1.0E20");
    }

    // --- formatted address and components -----------------------------------------------------------------------

    @Test
    void geocoderAddressSkipsPostalCodeWithoutCityAndHouseWithoutStreet() {
        Map<String, Object> result = geocodeSingle(map("y", 1.0, "house", "5", "pcode", "00-001", "country", "PL"));

        assertThat(result).containsEntry("formatted_address", "PL");
        assertThat(componentTypes(result))
                .containsExactly(List.of("street_number"), List.of("postal_code"), List.of("country", "political"));
    }

    @Test
    void componentHasLongNameShortNameAndTypes() {
        Map<String, Object> result = geocodeSingle(map("x", 1.0, "street", "Main"));

        assertThat(asList(result.get("address_components")))
                .containsExactly(map("long_name", "Main", "short_name", "Main", "types", List.of("route")));
    }

    @Test
    void geocoderResultKeysAreInGoogleOrder() {
        Map<String, Object> result = geocodeSingle(map("city", "A"));

        assertThat(result.keySet())
                .containsExactly("formatted_address", "address_components", "geometry", "place_id", "types");
        assertThat(asMap(result.get("geometry")).keySet()).containsExactly("location", "viewport");
    }

    // --- autocomplete items -------------------------------------------------------------------------------------

    private Map<String, Object> autocompleteSingle(Map<String, Object> item) {
        return firstResult(format(map("result", map("items", List.of(item))), "Autocomplete.search"));
    }

    @Test
    void autocompleteTypeFallsBackFromSubtypeToTypeToPlace() {
        assertThat(autocompleteSingle(map("type", "street", "subtype", null, "coords", map())))
                .containsEntry("types", List.of("street"));
        assertThat(autocompleteSingle(map("coords", map()))).containsEntry("types", List.of("place"));
    }

    @Test
    void autocompleteCityFallsBackFromPlaceToCityAndPostalCodeToZipcode() {
        Map<String, Object> result =
                autocompleteSingle(map("city", "Gdansk", "zipcode", "80-001", "coords", map("x", 18.6, "y", 54.3)));

        assertThat(result).containsEntry("formatted_address", "80-001 Gdansk");
        assertThat(componentTypes(result)).containsExactly(List.of("locality", "political"), List.of("postal_code"));
    }

    @Test
    void autocompletePostalCodeWithoutCityIsOnlyAComponent() {
        Map<String, Object> result =
                autocompleteSingle(map("street", "Main", "postal_code", "00-001", "district", "D", "coords", map()));

        assertThat(result).containsEntry("formatted_address", "Main");
        assertThat(componentTypes(result))
                .containsExactly(List.of("route"), List.of("postal_code"), List.of("sublocality", "political"));
    }

    @Test
    void autocompleteNumberWithoutStreetIsOnlyAComponent() {
        Map<String, Object> result = autocompleteSingle(map("number", 7L, "place", "A", "coords", map()));

        assertThat(result).containsEntry("formatted_address", "A");
        assertThat(asList(result.get("address_components")))
                .extracting(c -> asMap(c).get("long_name"))
                .containsExactly("7", "A");
    }

    @Test
    void autocompleteCoordsAsListGiveNullLocation() {
        Map<String, Object> result = autocompleteSingle(map("place", "A", "coords", List.of(1.0, 2.0)));

        assertThat(location(result)).containsEntry("lat", null).containsEntry("lng", null);
        assertThat(result).containsEntry("types", List.of("place"));
    }

    @Test
    void autocompleteItemWithNonCollectionCoordsIsMappedAsGeocoderResult() {
        Map<String, Object> result = autocompleteSingle(map("city", "A", "coords", "1,2", "quality", 9L));

        assertThat(result).containsEntry("types", List.of("street_address"));
    }

    @Test
    void doesNotModifyTheRawResponse() {
        Map<String, Object> item = new HashMap<>(map("city", "A", "x", 1.0));
        Map<String, Object> raw = new HashMap<>(map("result", item));

        format(raw, "Geocoder.geocode");

        assertThat(raw).containsOnlyKeys("result");
        assertThat(item).containsOnlyKeys("city", "x");
    }
}
