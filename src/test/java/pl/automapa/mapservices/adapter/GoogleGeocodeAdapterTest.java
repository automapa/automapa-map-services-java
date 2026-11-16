package pl.automapa.mapservices.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.Config;
import pl.automapa.mapservices.http.HttpRequest;
import pl.automapa.mapservices.support.Fixtures;
import pl.automapa.mapservices.support.MockHttpClient;

class GoogleGeocodeAdapterTest {

    /** Session id from the {@code session_generate} fixture. */
    private static final String SESSION_ID = "qPPdEpdtpb";

    private static final String KRAKOW = "Krak\u00f3w";
    private static final String GDANSK = "Gda\u0144sk";

    private MockHttpClient mockHttp;
    private GoogleGeocodeAdapter adapter;

    @BeforeEach
    void setUp() {
        mockHttp = new MockHttpClient();
        ApiClient client = new ApiClient(Config.builder("test-key", "test-pass").build(), mockHttp);
        mockHttp.addResponse("Session", "getSalt", Fixtures.load("session_salt"));
        mockHttp.addResponse("Session", "generateSession", Fixtures.load("session_generate"));
        mockHttp.addResponse("Geocoder", "geocode", Fixtures.load("geocode_success"));
        adapter = new GoogleGeocodeAdapter(client);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sentAddress() {
        return (Map<String, Object>) mockHttp.lastPayload().get("address");
    }

    // --- components string ---

    @Test
    void componentsStringIsParsedIntoTheAddress() {
        adapter.geocode(Map.of("components", "locality:Warszawa|route:Domaniewska|street_number:37"));

        assertThat(sentAddress())
                .containsExactly(
                        Map.entry("city", "Warszawa"), Map.entry("street", "Domaniewska"), Map.entry("house", "37"));
    }

    @Test
    void componentsStringMapsAllSupportedKeys() {
        adapter.geocode(Map.of(
                "components",
                "locality:" + KRAKOW + "|route:Floria\u0144ska|street_number:1|postal_code:31-019"
                        + "|administrative_area_level_1:ma\u0142opolskie|country:Polska"));

        assertThat(sentAddress())
                .containsExactly(
                        Map.entry("city", KRAKOW),
                        Map.entry("country", "Polska"),
                        Map.entry("province", "ma\u0142opolskie"),
                        Map.entry("pcode", "31-019"),
                        Map.entry("street", "Floria\u0144ska"),
                        Map.entry("house", "1"));
    }

    @Test
    void sublocalityAndNeighborhoodMapToDistrictLastOneWins() {
        adapter.geocode(Map.of("components", "locality:Warszawa|sublocality:Mokotow"));
        assertThat(sentAddress()).containsEntry("district", "Mokotow");

        mockHttp.addResponse("Geocoder", "geocode", Fixtures.load("geocode_success"));
        adapter.geocode(Map.of("components", "locality:Warszawa|sublocality:Mokotow|neighborhood:Sluzew"));
        assertThat(sentAddress()).containsEntry("district", "Sluzew");
    }

    @Test
    void componentsSkipPartsWithoutColonAndUnknownKeysAndTrimWhitespace() {
        adapter.geocode(Map.of("components", " locality : Warszawa |garbage|premise:X|route:Domaniewska"));

        assertThat(sentAddress()).containsExactly(Map.entry("city", "Warszawa"), Map.entry("street", "Domaniewska"));
    }

    @Test
    void componentsValueMayContainColon() {
        adapter.geocode(Map.of("components", "locality:Warszawa|route:Al. Jana:Pawla"));

        assertThat(sentAddress()).containsEntry("street", "Al. Jana:Pawla");
    }

    @Test
    void postalCodeOnlyComponentDoesNotSendEmptyCity() {
        adapter.geocode(Map.of("components", "postal_code:02-672"));

        assertThat(sentAddress()).containsExactly(Map.entry("pcode", "02-672"));
    }

    @Test
    void componentsTakePriorityOverAddressString() {
        adapter.geocode(Map.of("address", "This should be ignored", "components", "locality:" + KRAKOW));

        assertThat(sentAddress()).containsExactly(Map.entry("city", KRAKOW));
    }

    @Test
    void nonStringComponentsFallBackToAddress() {
        adapter.geocode(Map.of("components", List.of("locality:" + KRAKOW), "address", GDANSK));

        assertThat(sentAddress()).containsExactly(Map.entry("city", GDANSK));
    }

    // --- free-form address string ---

    @Test
    void addressStringIsParsedIntoStreetHouseAndCity() {
        adapter.geocode(Map.of("address", "Domaniewska 37, Warszawa"));

        assertThat(sentAddress())
                .containsExactly(
                        Map.entry("city", "Warszawa"), Map.entry("street", "Domaniewska"), Map.entry("house", "37"));
    }

    @Test
    void addressStringWithPostalCode() {
        adapter.geocode(Map.of("address", "Domaniewska 37, 02-672 Warszawa"));

        assertThat(sentAddress())
                .containsExactly(
                        Map.entry("city", "Warszawa"),
                        Map.entry("pcode", "02-672"),
                        Map.entry("street", "Domaniewska"),
                        Map.entry("house", "37"));
    }

    @Test
    void cityOnlyAddressStringSendsCityOnly() {
        adapter.geocode(Map.of("address", GDANSK));

        assertThat(sentAddress()).containsExactly(Map.entry("city", GDANSK));
    }

    @Test
    void addressStringWithoutHouseNumberSendsStreetOnly() {
        adapter.geocode(Map.of("address", "Aleje Jerozolimskie, Warszawa"));

        assertThat(sentAddress())
                .containsExactly(Map.entry("city", "Warszawa"), Map.entry("street", "Aleje Jerozolimskie"));
    }

    @Test
    void addressStringKeepsLettersOfTheHouseNumberAndMultiWordStreet() {
        adapter.geocode(Map.of("address", "  Aleja Armii Ludowej   17A ,  Warszawa  "));

        assertThat(sentAddress())
                .containsExactly(
                        Map.entry("city", "Warszawa"),
                        Map.entry("street", "Aleja Armii Ludowej"),
                        Map.entry("house", "17A"));
    }

    @Test
    void addressStringUsesFirstPartAsStreetAndLastAsCity() {
        adapter.geocode(Map.of("address", "Domaniewska 37, Mokotow, Warszawa"));

        assertThat(sentAddress())
                .containsExactly(
                        Map.entry("city", "Warszawa"), Map.entry("street", "Domaniewska"), Map.entry("house", "37"));
    }

    @Test
    void emptyAddressStringSendsEmptyAddressObject() {
        adapter.geocode(Map.of("address", ""));

        assertThat(sentAddress()).isEmpty();
    }

    // --- structured address map ---

    @Test
    void addressMapPassesThrough() {
        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("city", GDANSK);
        structured.put("street", "D\u0142uga");
        structured.put("house", "1");

        adapter.geocode(Map.of("address", structured));

        assertThat(sentAddress()).isEqualTo(structured);
    }

    @Test
    void addressMapSendsAllKnownFieldsAsTextAndDropsNullAndUnknownOnes() {
        Map<String, Object> structured = new HashMap<>();
        structured.put("city", "Warszawa");
        structured.put("country", "PL");
        structured.put("province", "mazowieckie");
        structured.put("district", "warszawski");
        structured.put("community", "Warszawa");
        structured.put("quarter", "Mokotow");
        structured.put("pcode", "02-672");
        structured.put("street", null);
        structured.put("house", 37);
        structured.put("unknown", "dropped");

        adapter.geocode(Map.of("address", structured));

        assertThat(sentAddress())
                .containsExactly(
                        Map.entry("city", "Warszawa"),
                        Map.entry("country", "PL"),
                        Map.entry("province", "mazowieckie"),
                        Map.entry("district", "warszawski"),
                        Map.entry("community", "Warszawa"),
                        Map.entry("quarter", "Mokotow"),
                        Map.entry("pcode", "02-672"),
                        Map.entry("house", "37"));
    }

    @Test
    void addressMapWithoutCitySendsNoCity() {
        adapter.geocode(Map.of("address", Map.of("pcode", "02-672")));

        assertThat(sentAddress()).containsExactly(Map.entry("pcode", "02-672"));
    }

    // --- request: URL, session, payload ---

    @Test
    void callsGeocoderGeocodeWithSessionAndExactPayload() {
        adapter.geocode(Map.of("address", "Warszawa"));

        HttpRequest request = mockHttp.lastRequest();
        assertThat(request.url()).isEqualTo("https://api.automapa.pl/v3/Geocoder/geocode");
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.headers()).containsEntry("Session-Id", SESSION_ID);
        assertThat(mockHttp.lastPayload())
                .containsExactly(Map.entry("address", Map.of("city", "Warszawa")), Map.entry("maxResults", 1L));
    }

    @Test
    void formatIsNotSentToTheApi() {
        adapter.geocode(Map.of("address", "Warszawa"));

        assertThat(mockHttp.lastPayload()).doesNotContainKey("format");
    }

    @Test
    void googleOnlyParamsAreNotSent() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("address", "Warszawa");
        params.put("components", null);
        params.put("language", "pl");
        params.put("key", "google-api-key");
        params.put("region", "pl");
        params.put("result_type", "street_address");
        params.put("location_type", "ROOFTOP");

        adapter.geocode(params);

        assertThat(mockHttp.lastPayload()).containsOnlyKeys("address", "maxResults");
    }

    @Test
    void otherParamsAreForwardedAndNamedOnesWin() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("customOption", "kept");
        params.put("address", "Warszawa");
        params.put("format", "native");
        params.put("maxResults", 2);

        Map<String, Object> result = adapter.geocode(params);

        assertThat(mockHttp.lastPayload())
                .containsExactly(
                        Map.entry("customOption", "kept"),
                        Map.entry("address", Map.of("city", "Warszawa")),
                        Map.entry("maxResults", 2L));
        assertThat(result).containsEntry("status", "OK");
    }

    @Test
    void maxResultsIsForwarded() {
        adapter.geocode(Map.of("address", "Warszawa", "maxResults", 5));

        assertThat(mockHttp.lastPayload()).containsEntry("maxResults", 5L);
    }

    @Test
    void maxResultsDefaultsToOneWhenMissingOrNull() {
        Map<String, Object> params = new HashMap<>();
        params.put("address", "Warszawa");
        params.put("maxResults", null);

        adapter.geocode(params);

        assertThat(mockHttp.lastPayload()).containsEntry("maxResults", 1L);
    }

    @Test
    void maxResultsAcceptsNumericTextAndOtherNumbers() {
        adapter.geocode(Map.of("address", "Warszawa", "maxResults", " 4 "));
        assertThat(mockHttp.lastPayload()).containsEntry("maxResults", 4L);

        mockHttp.addResponse("Geocoder", "geocode", Fixtures.load("geocode_success"));
        adapter.geocode(Map.of("address", "Warszawa", "maxResults", 3L));
        assertThat(mockHttp.lastPayload()).containsEntry("maxResults", 3L);
    }

    @Test
    void maxResultsThatIsNotAnIntegerIsRejected() {
        assertThatThrownBy(() -> adapter.geocode(Map.of("address", "Warszawa", "maxResults", "many")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxResults")
                .hasMessageContaining("many");
        assertThatThrownBy(() -> adapter.geocode(Map.of("address", "Warszawa", "maxResults", true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxResults");
    }

    // --- response ---

    @Test
    void returnsGoogleFormatParsedFromTheFixture() {
        Map<String, Object> result = adapter.geocode(Map.of("address", "Warszawa"));

        assertThat(result).containsKeys("status", "results", "_raw").containsEntry("status", "OK");
        Object results = result.get("results");
        assertThat(results).isInstanceOf(List.class);
        Map<?, ?> first = (Map<?, ?>) ((List<?>) results).get(0);
        Map<?, ?> location = (Map<?, ?>) ((Map<?, ?>) first.get("geometry")).get("location");
        assertThat(location.get("lat")).isEqualTo(52.2297);
        assertThat(location.get("lng")).isEqualTo(21.0122);
    }

    // --- errors ---

    @Test
    void missingAddressAndComponentsIsRejected() {
        assertThatThrownBy(() -> adapter.geocode(Map.of("language", "pl", "key", "some-key")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("\"address\"")
                .hasMessageContaining("\"components\"");
        assertThat(mockHttp.requestCount()).isZero();
    }

    @Test
    void addressOfAnotherTypeIsRejected() {
        assertThatThrownBy(() -> adapter.geocode(Map.of("address", 42)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("\"address\"");
    }

    @Test
    void nullParamsAreRejected() {
        assertThatThrownBy(() -> adapter.geocode(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("params");
    }

    @Test
    void nullClientIsRejected() {
        assertThatThrownBy(() -> new GoogleGeocodeAdapter(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("client");
    }
}
