package pl.automapa.mapservices.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.automapa.mapservices.http.HttpResponse;
import pl.automapa.mapservices.json.Json;
import pl.automapa.mapservices.request.GeocodeRequest;
import pl.automapa.mapservices.request.Point;
import pl.automapa.mapservices.request.RevGeocodeMultiRequest;
import pl.automapa.mapservices.request.RevGeocodeRequest;
import pl.automapa.mapservices.response.ApiResponse;

/** Geocoder service on the live API (suite {@code geocoder}), including the {@code google} response format. */
class GeocoderLiveTest extends LiveApiTestBase {

    private static final Point DOMANIEWSKA = new Point(21.0073642, 52.18288);

    private static final Set<String> GOOGLE_TYPES = Set.of(
            "street_address",
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
            "approximate");

    @Override
    protected String suiteName() {
        return "geocoder";
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

    private static double number(Object value) {
        assertThat(value).isInstanceOf(Number.class);
        return ((Number) value).doubleValue();
    }

    @Test
    void geocodeByStructuredAddress() {
        Map<String, Object> params =
                Map.of("address", Map.of("city", "Warszawa", "street", "Domaniewska", "house", "37"), "maxResults", 1);
        HttpResponse response = sendRequest("Geocoder", "geocode", params, acquireSession());

        skipIfNoPermission(response);
        List<Object> results = list(assertSuccessResponse(response).get("result"));

        assertThat(results).as("Geocode should return at least one result").isNotEmpty();
        Map<String, Object> first = map(results.get(0));
        assertThat(first).containsKeys("x", "y", "quality");
        assertThat(number(first.get("quality"))).isGreaterThanOrEqualTo(5);
        assertThat(number(first.get("x"))).as("longitude in Warsaw").isBetween(20.8, 21.3);
        assertThat(number(first.get("y"))).as("latitude in Warsaw").isBetween(52.0, 52.4);
    }

    @Test
    void revgeocodeWithEmptyParamsObjectReturnsCity() {
        HttpResponse response = sendRequest(
                "Geocoder",
                "revgeocode",
                Map.of("point", List.of(21.0073642, 52.18288), "params", Map.of()),
                acquireSession());

        skipIfNoPermission(response);
        assertThat(String.valueOf(result(assertSuccessResponse(response)).get("city")))
                .containsIgnoringCase("Warszawa");
    }

    @Test
    void geocodeWithoutSessionFails() {
        HttpResponse response = sendRequest(
                "Geocoder", "geocode", Map.of("address", Map.of("city", "Warszawa"), "maxResults", 1), null);

        assertThat(response.statusCode() / 100)
                .as("Geocoder.geocode should fail without the Session-Id header, got HTTP %d", response.statusCode())
                .isNotEqualTo(2);
    }

    @Test
    void geocodeViaFacadeReturnsResults() {
        ApiResponse response = callLive(() -> buildApiClient()
                .geocoder()
                .geocode(GeocodeRequest.builder("Warszawa")
                        .street("Domaniewska")
                        .house("37")
                        .maxResults(1)
                        .build()));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.dataAsList())
                .as("Geocoder facade should return a result")
                .isNotEmpty();
        assertThat(response.raw()).containsKey("result");
    }

    @Test
    void revgeocodeViaFacadeReturnsCity() {
        ApiResponse response = callLive(() -> buildApiClient()
                .geocoder()
                .revgeocode(
                        RevGeocodeRequest.builder(DOMANIEWSKA).snapToBld(true).build()));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.dataAsMap()).containsKey("city");
        assertThat(response.raw()).containsKey("result");
    }

    @Test
    void revgeocodeViaFacadeWithoutOptionsSendsEmptyParamsObject() {
        ApiResponse response = callLive(() -> buildApiClient()
                .geocoder()
                .revgeocode(RevGeocodeRequest.builder(DOMANIEWSKA).build()));

        assertThat(response.status()).isEqualTo(200);
        assertThat(String.valueOf(response.dataAsMap().get("city"))).containsIgnoringCase("Warszawa");
    }

    @Test
    void geocodemultiViaFacadeReturnsResults() {
        ApiResponse response = callLive(() -> buildApiClient()
                .geocoder()
                .geocodemulti(List.of(
                        Map.of("city", "Warszawa", "street", "Domaniewska", "house", "37"),
                        Map.of("city", "Gda\u0144sk", "street", "D\u0142uga", "house", "1"))));

        assertThat(response.status()).isEqualTo(200);
        List<Object> groups = response.dataAsList();
        assertThat(groups).as("geocodemulti should return a result").isNotEmpty();
        Object firstGroup = groups.get(0);
        Map<String, Object> first =
                firstGroup instanceof List ? map(list(firstGroup).get(0)) : map(firstGroup);
        assertThat(first).containsKeys("x", "y");
        assertThat(response.raw()).containsKey("result");
    }

    @Test
    void revgeocodemultiViaFacadeReturnsResults() {
        ApiResponse response = callLive(() -> buildApiClient()
                .geocoder()
                .revgeocodemulti(RevGeocodeMultiRequest.builder(List.of(DOMANIEWSKA, new Point(18.6282, 54.3520)))
                        .build()));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.dataAsList())
                .as("revgeocodemulti should return a result")
                .isNotEmpty();
        assertThat(response.raw()).containsKey("result");
    }

    @Test
    void geocodeWithGoogleFormatMatchesGoogleGeocodingShape() {
        ApiResponse response = callLive(() -> buildApiClient()
                .geocoder()
                .geocode(GeocodeRequest.builder("Warszawa")
                        .street("Domaniewska")
                        .house("37")
                        .maxResults(1)
                        .format("google")
                        .build()));

        Map<String, Object> data = response.dataAsMap();
        assertThat(data).containsKeys("status", "results", "_raw").containsEntry("status", "OK");
        assertThat(map(data.get("_raw"))).containsKey("result");
        List<Object> results = list(data.get("results"));
        assertThat(results).isNotEmpty();

        Map<String, Object> first = map(results.get(0));
        assertThat(first).containsKeys("formatted_address", "address_components", "geometry", "place_id", "types");
        assertThat(first.get("formatted_address")).asString().isNotEmpty();

        List<Object> components = list(first.get("address_components"));
        assertThat(components).isNotEmpty();
        boolean locality = false;
        for (Object item : components) {
            Map<String, Object> component = map(item);
            assertThat(component.get("long_name")).isInstanceOf(String.class);
            assertThat(component.get("short_name")).isInstanceOf(String.class);
            List<Object> types = list(component.get("types"));
            assertThat(types).isNotEmpty();
            locality |= types.contains("locality");
        }
        assertThat(locality).as("a city geocode yields a locality component").isTrue();

        Map<String, Object> location = map(map(first.get("geometry")).get("location"));
        assertThat(location.get("lat")).isInstanceOf(Double.class);
        assertThat(location.get("lng")).isInstanceOf(Double.class);

        List<Object> types = list(first.get("types"));
        assertThat(types)
                .as("types must contain a Google type, got %s", Json.stringify(types))
                .anyMatch(GOOGLE_TYPES::contains);
    }

    @Test
    void geocodeWithGoogleFormatMapsCoordinates() {
        ApiResponse response = callLive(() -> buildApiClient("google")
                .geocoder()
                .geocode(GeocodeRequest.builder("Warszawa").maxResults(1).build()));

        Object nativeResult = response.raw().get("result");
        Map<String, Object> nativeFirst =
                nativeResult instanceof List ? map(list(nativeResult).get(0)) : map(nativeResult);
        Map<String, Object> location =
                map(map(map(list(response.dataAsMap().get("results")).get(0)).get("geometry"))
                        .get("location"));

        assertThat(number(location.get("lat"))).isCloseTo(number(nativeFirst.get("y")), within(0.0001));
        assertThat(number(location.get("lng"))).isCloseTo(number(nativeFirst.get("x")), within(0.0001));
    }
}
