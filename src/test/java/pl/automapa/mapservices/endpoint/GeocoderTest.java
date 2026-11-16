package pl.automapa.mapservices.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.Config;
import pl.automapa.mapservices.http.HttpRequest;
import pl.automapa.mapservices.request.GeocodeRequest;
import pl.automapa.mapservices.request.Point;
import pl.automapa.mapservices.request.RevGeocodeMultiRequest;
import pl.automapa.mapservices.request.RevGeocodeRequest;
import pl.automapa.mapservices.response.ApiResponse;
import pl.automapa.mapservices.support.Fixtures;
import pl.automapa.mapservices.support.MockHttpClient;

class GeocoderTest {

    /** Session id from the {@code session_generate} fixture. */
    private static final String SESSION_ID = "qPPdEpdtpb";

    private static final Point DOMANIEWSKA = new Point(21.0073642, 52.18288);

    private MockHttpClient mockHttp;
    private ApiClient client;

    @BeforeEach
    void setUp() {
        mockHttp = new MockHttpClient();
        client = new ApiClient(Config.builder("test-key", "test-pass").build(), mockHttp);
        mockHttp.addResponse("Session", "getSalt", Fixtures.load("session_salt"));
        mockHttp.addResponse("Session", "generateSession", Fixtures.load("session_generate"));
    }

    private void assertProtectedCall(String method) {
        HttpRequest request = mockHttp.lastRequest();
        assertThat(request.url()).isEqualTo("https://api.automapa.pl/v3/Geocoder/" + method);
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.headers()).containsEntry("Session-Id", SESSION_ID);
    }

    // --- geocode ---

    @Test
    void geocodeSendsAddressAndMaxResultsWithSession() {
        mockHttp.addResponse("Geocoder", "geocode", Fixtures.load("geocode_success"));

        client.geocoder()
                .geocode(GeocodeRequest.builder("Warszawa")
                        .street("Domaniewska")
                        .maxResults(3)
                        .build());

        assertProtectedCall("geocode");
        assertThat(mockHttp.lastPayload())
                .containsExactly(
                        Map.entry("address", Map.of("city", "Warszawa", "street", "Domaniewska")),
                        Map.entry("maxResults", 3L));
    }

    @Test
    void geocodeWithCityOnlySendsNoNullKeys() {
        mockHttp.addResponse("Geocoder", "geocode", Fixtures.load("geocode_success"));

        client.geocoder().geocode(GeocodeRequest.builder("Warszawa").build());

        assertThat(mockHttp.lastPayload()).containsExactly(Map.entry("address", Map.of("city", "Warszawa")));
    }

    @Test
    void geocodeWithEmptyCitySendsEmptyAddressObject() {
        mockHttp.addResponse("Geocoder", "geocode", Fixtures.load("geocode_success"));

        client.geocoder().geocode(GeocodeRequest.builder("").build());

        assertThat(mockHttp.lastRequest().body()).isEqualTo("{\"address\":{}}");
    }

    @Test
    void geocodePassesUnknownParamsFlat() {
        mockHttp.addResponse("Geocoder", "geocode", Fixtures.load("geocode_success"));

        client.geocoder()
                .geocode(GeocodeRequest.builder("Krakow")
                        .extra("futureParam", "value")
                        .extra("anotherExtra", 42)
                        .build());

        assertThat(mockHttp.lastPayload())
                .containsExactly(
                        Map.entry("futureParam", "value"),
                        Map.entry("anotherExtra", 42L),
                        Map.entry("address", Map.of("city", "Krakow")));
    }

    @Test
    void geocodeNamedParamsOverrideExtra() {
        mockHttp.addResponse("Geocoder", "geocode", Fixtures.load("geocode_success"));

        client.geocoder()
                .geocode(GeocodeRequest.builder("Warszawa")
                        .street("Nowa")
                        .extra("address", Map.of("city", "OldCity", "street", "OldStreet"))
                        .build());

        assertThat(mockHttp.lastPayload())
                .containsExactly(Map.entry("address", Map.of("city", "Warszawa", "street", "Nowa")));
    }

    @Test
    void geocodeReturnsDataAndKeepsUnknownFields() {
        Map<String, Object> fixture = Fixtures.load("geocode_success");
        mockHttp.addResponse("Geocoder", "geocode", fixture);

        ApiResponse response =
                client.geocoder().geocode(GeocodeRequest.builder("Warszawa").build());

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.raw()).isEqualTo(fixture);
        assertThat(response.dataAsMap())
                .containsEntry("city", "Warszawa")
                .containsEntry("unknownFutureField", "should be preserved");
        assertThat(response.meta()).containsEntry("service", "Geocoder").containsEntry("method", "geocode");
    }

    @Test
    void geocodeHandlesEmptyResults() {
        mockHttp.addResponse("Geocoder", "geocode", Fixtures.load("geocode_empty_results"));

        ApiResponse response =
                client.geocoder().geocode(GeocodeRequest.builder("xyzabc").build());

        assertThat(response.dataAsList()).isEmpty();
    }

    @Test
    void geocodeWithGoogleFormatIsNotSentAndFormatsTheResponse() {
        mockHttp.addResponse("Geocoder", "geocode", Fixtures.load("geocode_success"));

        ApiResponse response = client.geocoder()
                .geocode(GeocodeRequest.builder("Warszawa").format("google").build());

        assertThat(mockHttp.lastPayload()).doesNotContainKey("format");
        assertThat(response.dataAsMap()).containsEntry("status", "OK").containsKeys("results", "_raw");
        assertThat(response.raw()).containsKey("result");
    }

    @Test
    void geocodeRejectsNullRequest() {
        assertThatThrownBy(() -> client.geocoder().geocode(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Geocoder.geocode")
                .hasMessageContaining("GeocodeRequest.builder");
        assertThat(mockHttp.requestCount()).isZero();
    }

    // --- geocodemulti ---

    @Test
    void geocodemultiSendsAddresses() {
        mockHttp.addResponse("Geocoder", "geocodemulti", Fixtures.load("geocodemulti_success"));
        List<Map<String, Object>> addresses = List.of(Map.of("city", "Warszawa"), Map.of("city", "Krakow"));

        client.geocoder().geocodemulti(addresses);

        assertProtectedCall("geocodemulti");
        assertThat(mockHttp.lastPayload()).containsExactly(Map.entry("addresses", addresses));
    }

    @Test
    void geocodemultiMergesExtraAndNamedAddressesWin() {
        mockHttp.addResponse("Geocoder", "geocodemulti", Fixtures.load("geocodemulti_success"));
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("addresses", List.of());
        extra.put("future", true);

        client.geocoder().geocodemulti(List.of(Map.of("city", "Gdansk")), extra);

        assertThat(mockHttp.lastPayload())
                .containsExactly(Map.entry("addresses", List.of(Map.of("city", "Gdansk"))), Map.entry("future", true));
        assertThat(extra).containsEntry("addresses", List.of());
    }

    @Test
    void geocodemultiWithNullExtraSendsAddressesOnly() {
        mockHttp.addResponse("Geocoder", "geocodemulti", Fixtures.load("geocodemulti_success"));

        client.geocoder().geocodemulti(List.of(Map.of("city", "Gdansk")), null);

        assertThat(mockHttp.lastPayload()).containsOnlyKeys("addresses");
    }

    @Test
    void geocodemultiCopiesTheAddressList() {
        mockHttp.addResponse("Geocoder", "geocodemulti", Fixtures.load("geocodemulti_success"));
        List<Map<String, Object>> addresses = new ArrayList<>(List.of(Map.of("city", "Gdansk")));

        client.geocoder().geocodemulti(addresses);

        assertThat(addresses).hasSize(1);
    }

    @Test
    void geocodemultiKeepsTheMixedResultArray() {
        Map<String, Object> fixture = Fixtures.load("geocodemulti_success");
        mockHttp.addResponse("Geocoder", "geocodemulti", fixture);

        ApiResponse response = client.geocoder().geocodemulti(List.of(Map.of("city", "Warszawa")));

        assertThat(response.raw()).isEqualTo(fixture);
        assertThat(response.dataAsList()).hasSize(3);
        assertThat(response.dataAsList().get(1)).isEqualTo(Map.of("status", "ERROR", "message", "Unknown address"));
    }

    @Test
    void geocodemultiRejectsNullAddresses() {
        assertThatThrownBy(() -> client.geocoder().geocodemulti(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Geocoder.geocodemulti")
                .hasMessageContaining("addresses is required but was null");
        assertThat(mockHttp.requestCount()).isZero();
    }

    @Test
    void geocodemultiRejectsEmptyAddresses() {
        assertThatThrownBy(() -> client.geocoder().geocodemulti(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("addresses is required but was empty");
        assertThat(mockHttp.requestCount()).isZero();
    }

    @Test
    void geocodemultiRejectsNullAddressWithItsIndex() {
        List<Map<String, Object>> addresses = Arrays.asList(Map.of("city", "Gdansk"), null);

        assertThatThrownBy(() -> client.geocoder().geocodemulti(addresses))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("addresses[1]");
        assertThat(mockHttp.requestCount()).isZero();
    }

    // --- revgeocode ---

    @Test
    void revgeocodeAlwaysSendsEmptyParamsObject() {
        mockHttp.addResponse("Geocoder", "revgeocode", Fixtures.load("revgeocode_success"));

        client.geocoder().revgeocode(RevGeocodeRequest.builder(DOMANIEWSKA).build());

        assertProtectedCall("revgeocode");
        assertThat(mockHttp.lastRequest().body()).isEqualTo("{\"point\":[21.0073642,52.18288],\"params\":{}}");
    }

    @Test
    void revgeocodeSendsPointAndParams() {
        mockHttp.addResponse("Geocoder", "revgeocode", Fixtures.load("revgeocode_success"));

        client.geocoder()
                .revgeocode(RevGeocodeRequest.builder(DOMANIEWSKA)
                        .snapToBld(true)
                        .snapToBldRadius(50)
                        .build());

        assertThat(mockHttp.lastPayload())
                .containsExactly(
                        Map.entry("point", List.of(21.0073642, 52.18288)),
                        Map.entry("params", Map.of("snapToBld", true, "snapToBldRadius", 50L)));
    }

    @Test
    void revgeocodeSendsSnapToBldFalse() {
        mockHttp.addResponse("Geocoder", "revgeocode", Fixtures.load("revgeocode_success"));

        client.geocoder()
                .revgeocode(RevGeocodeRequest.builder(new Point(21.0, 52.0))
                        .snapToBld(false)
                        .build());

        assertThat(mockHttp.lastPayload()).containsEntry("params", Map.of("snapToBld", false));
    }

    @Test
    void revgeocodeReturnsData() {
        Map<String, Object> fixture = Fixtures.load("revgeocode_success");
        mockHttp.addResponse("Geocoder", "revgeocode", fixture);

        ApiResponse response = client.geocoder()
                .revgeocode(RevGeocodeRequest.builder(new Point(21.0, 52.0)).build());

        assertThat(response.raw()).isEqualTo(fixture);
        assertThat(response.dataAsMap()).containsKey("city");
    }

    @Test
    void revgeocodeRejectsNullRequest() {
        assertThatThrownBy(() -> client.geocoder().revgeocode(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Geocoder.revgeocode")
                .hasMessageContaining("RevGeocodeRequest.builder");
        assertThat(mockHttp.requestCount()).isZero();
    }

    // --- revgeocodemulti ---

    @Test
    void revgeocodemultiSendsPointsAndEmptyParams() {
        mockHttp.addResponse("Geocoder", "revgeocodemulti", Fixtures.load("revgeocodemulti_success"));

        client.geocoder()
                .revgeocodemulti(RevGeocodeMultiRequest.builder(List.of(DOMANIEWSKA, new Point(18.3865894, 54.57206)))
                        .build());

        assertProtectedCall("revgeocodemulti");
        assertThat(mockHttp.lastRequest().body())
                .isEqualTo("{\"points\":[[21.0073642,52.18288],[18.3865894,54.57206]],\"params\":{}}");
    }

    @Test
    void revgeocodemultiSendsParams() {
        mockHttp.addResponse("Geocoder", "revgeocodemulti", Fixtures.load("revgeocodemulti_success"));

        client.geocoder()
                .revgeocodemulti(RevGeocodeMultiRequest.builder(List.of(DOMANIEWSKA))
                        .getPostalCode(true)
                        .build());

        assertThat(mockHttp.lastPayload()).containsEntry("params", Map.of("getPostalCode", true));
    }

    @Test
    void revgeocodemultiKeepsTheResultArray() {
        Map<String, Object> fixture = Fixtures.load("revgeocodemulti_success");
        mockHttp.addResponse("Geocoder", "revgeocodemulti", fixture);

        ApiResponse response = client.geocoder()
                .revgeocodemulti(RevGeocodeMultiRequest.builder(List.of(new Point(21.0, 52.0)))
                        .build());

        assertThat(response.raw()).isEqualTo(fixture);
        assertThat(response.dataAsList()).hasSize(3);
    }

    @Test
    void revgeocodemultiRejectsNullRequest() {
        assertThatThrownBy(() -> client.geocoder().revgeocodemulti(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Geocoder.revgeocodemulti")
                .hasMessageContaining("RevGeocodeMultiRequest.builder");
        assertThat(mockHttp.requestCount()).isZero();
    }

    @Test
    void constructorRequiresClient() {
        assertThatThrownBy(() -> new Geocoder(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("client.geocoder()");
    }
}
