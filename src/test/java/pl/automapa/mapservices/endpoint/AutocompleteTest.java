package pl.automapa.mapservices.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.Config;
import pl.automapa.mapservices.http.HttpRequest;
import pl.automapa.mapservices.request.AutocompleteSearchRequest;
import pl.automapa.mapservices.request.SearchAddressRequest;
import pl.automapa.mapservices.response.ApiResponse;
import pl.automapa.mapservices.support.Fixtures;
import pl.automapa.mapservices.support.MockHttpClient;

class AutocompleteTest {

    /** Session id from the {@code session_generate} fixture. */
    private static final String SESSION_ID = "qPPdEpdtpb";

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
        assertThat(request.url()).isEqualTo("https://api.automapa.pl/v3/Autocomplete/" + method);
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.headers()).containsEntry("Session-Id", SESSION_ID);
    }

    private static Map<String, Object> streetQuery() {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("city", "Warszawa");
        query.put("street", "Dom");
        query.put("number", "");
        query.put("zipcode", "");
        return query;
    }

    private static Map<String, Object> items(Object... items) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", List.of(items));
        return Map.of("result", result);
    }

    private static Map<String, Object> item(Object zipcode) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("city", "Warszawa");
        item.put("street", "Domaniewska");
        item.put("zipcode", zipcode);
        return item;
    }

    // --- search ---

    @Test
    void searchSendsQueryWithSession() {
        mockHttp.addResponse("Autocomplete", "search", Fixtures.load("autocomplete_search_success"));

        client.autocomplete()
                .search(AutocompleteSearchRequest.builder("Warszawa Domaniewska 37")
                        .build());

        assertProtectedCall("search");
        assertThat(mockHttp.lastRequest().body()).isEqualTo("{\"query\":\"Warszawa Domaniewska 37\"}");
    }

    @Test
    void searchSendsTypes() {
        mockHttp.addResponse("Autocomplete", "search", Fixtures.load("autocomplete_search_success"));

        client.autocomplete()
                .search(AutocompleteSearchRequest.builder("Warszawa")
                        .types(List.of("place"))
                        .build());

        assertThat(mockHttp.lastPayload())
                .containsExactly(Map.entry("query", "Warszawa"), Map.entry("types", List.of("place")));
    }

    @Test
    void searchForwardsExtraAndNamedQueryWins() {
        mockHttp.addResponse("Autocomplete", "search", Fixtures.load("autocomplete_search_success"));

        client.autocomplete()
                .search(AutocompleteSearchRequest.builder("Krakow")
                        .extra(Map.of("query", "OVERRIDDEN"))
                        .extra("futureParam", "value")
                        .extra("debugMode", true)
                        .build());

        assertThat(mockHttp.lastPayload())
                .containsExactly(
                        Map.entry("query", "Krakow"), Map.entry("futureParam", "value"), Map.entry("debugMode", true));
    }

    @Test
    void searchReturnsDataAndKeepsUnknownFields() {
        Map<String, Object> fixture = Fixtures.load("autocomplete_search_success");
        mockHttp.addResponse("Autocomplete", "search", fixture);

        ApiResponse response = client.autocomplete()
                .search(AutocompleteSearchRequest.builder("Warszawa Domaniewska 37")
                        .build());

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.raw()).isEqualTo(fixture);
        assertThat(response.data()).isEqualTo(fixture.get("result"));
        List<?> items = (List<?>) response.dataAsMap().get("items");
        assertThat(items).hasSize(2);
        Map<?, ?> first = (Map<?, ?>) items.get(0);
        assertThat(first.get("unknownFutureField")).isEqualTo("should be preserved");
        assertThat(((Map<?, ?>) first.get("coords")).keySet()).isEqualTo(java.util.Set.of("x", "y"));
        assertThat(response.meta()).containsEntry("service", "Autocomplete").containsEntry("method", "search");
    }

    @Test
    void searchHandlesEmptyResults() {
        mockHttp.addResponse("Autocomplete", "search", Fixtures.load("autocomplete_search_empty"));

        ApiResponse response = client.autocomplete()
                .search(AutocompleteSearchRequest.builder("xyzabc123notexist").build());

        assertThat(response.dataAsMap()).containsEntry("items", List.of());
    }

    @Test
    void searchWithGoogleFormatIsNotSentAndFormatsTheResponse() {
        mockHttp.addResponse("Autocomplete", "search", Fixtures.load("autocomplete_search_success"));

        ApiResponse response = client.autocomplete()
                .search(AutocompleteSearchRequest.builder("Warszawa")
                        .format("google")
                        .build());

        assertThat(mockHttp.lastPayload()).doesNotContainKey("format");
        assertThat(response.dataAsMap()).containsEntry("status", "OK").containsKeys("results", "_raw");
    }

    @Test
    void searchRejectsNullRequest() {
        assertThatThrownBy(() -> client.autocomplete().search(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Autocomplete.search")
                .hasMessageContaining("AutocompleteSearchRequest.builder");
        assertThat(mockHttp.requestCount()).isZero();
    }

    // --- searchAddress ---

    @Test
    void searchAddressSendsQueryAndSourceWithSession() {
        mockHttp.addResponse("Autocomplete", "searchAddress", Fixtures.load("autocomplete_search_address_success"));

        client.autocomplete()
                .searchAddress(
                        SearchAddressRequest.builder(streetQuery(), "street").build());

        assertProtectedCall("searchAddress");
        assertThat(mockHttp.lastRequest().body())
                .isEqualTo("{\"query\":{\"city\":\"Warszawa\",\"street\":\"Dom\",\"number\":\"\",\"zipcode\":\"\"},"
                        + "\"source\":\"street\"}");
    }

    @Test
    void searchAddressSendsLimitAndSelected() {
        mockHttp.addResponse("Autocomplete", "searchAddress", Fixtures.load("autocomplete_search_address_success"));

        client.autocomplete()
                .searchAddress(SearchAddressRequest.builder(streetQuery(), "street")
                        .limit(5)
                        .selected(List.of("city"))
                        .build());

        assertThat(mockHttp.lastPayload())
                .containsExactly(
                        Map.entry("query", streetQuery()),
                        Map.entry("source", "street"),
                        Map.entry("limit", 5L),
                        Map.entry("selected", List.of("city")));
    }

    @Test
    void searchAddressForwardsExtraAndNamedParamsWin() {
        mockHttp.addResponse("Autocomplete", "searchAddress", Fixtures.load("autocomplete_search_address_success"));

        client.autocomplete()
                .searchAddress(SearchAddressRequest.builder(streetQuery(), "street")
                        .extra("futureParam", "value")
                        .extra("source", "OVERRIDDEN")
                        .build());

        assertThat(mockHttp.lastPayload())
                .containsExactly(
                        Map.entry("futureParam", "value"),
                        Map.entry("source", "street"),
                        Map.entry("query", streetQuery()));
    }

    @Test
    void searchAddressReturnsDataAndKeepsUnknownFields() {
        Map<String, Object> fixture = Fixtures.load("autocomplete_search_address_success");
        mockHttp.addResponse("Autocomplete", "searchAddress", fixture);

        ApiResponse response = client.autocomplete()
                .searchAddress(
                        SearchAddressRequest.builder(streetQuery(), "street").build());

        assertThat(response.raw()).isEqualTo(fixture);
        List<?> items = (List<?>) response.dataAsMap().get("items");
        assertThat(items).isNotEmpty();
        Map<?, ?> first = (Map<?, ?>) items.get(0);
        assertThat(first.get("zipcode")).isEqualTo("02-672");
        assertThat(first.get("unknownFutureField")).isEqualTo("should be preserved");
    }

    @Test
    void searchAddressRejectsNullRequest() {
        assertThatThrownBy(() -> client.autocomplete().searchAddress(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Autocomplete.searchAddress")
                .hasMessageContaining("SearchAddressRequest.builder");
        assertThat(mockHttp.requestCount()).isZero();
    }

    // --- getPostalCodesForStreet ---

    @Test
    void getPostalCodesForStreetSendsZipcodeSearchWithDefaultLimit() {
        mockHttp.addResponse("Autocomplete", "searchAddress", Fixtures.load("autocomplete_search_address_success"));

        client.autocomplete().getPostalCodesForStreet("Warszawa", "Domaniewska");

        assertProtectedCall("searchAddress");
        assertThat(mockHttp.lastRequest().body())
                .isEqualTo("{\"query\":{\"city\":\"Warszawa\",\"street\":\"Domaniewska\",\"number\":\"\","
                        + "\"zipcode\":\"\"},\"source\":\"zipcode\",\"limit\":100,\"selected\":[\"city\",\"street\"]}");
    }

    @Test
    void getPostalCodesForStreetSendsTheGivenLimit() {
        mockHttp.addResponse("Autocomplete", "searchAddress", Fixtures.load("autocomplete_search_address_success"));

        client.autocomplete().getPostalCodesForStreet("Warszawa", "Domaniewska", 20);

        assertThat(mockHttp.lastPayload()).containsEntry("limit", 20L);
    }

    @Test
    void getPostalCodesForStreetReturnsTheCodeOfTheFixture() {
        mockHttp.addResponse("Autocomplete", "searchAddress", Fixtures.load("autocomplete_search_address_success"));

        assertThat(client.autocomplete().getPostalCodesForStreet("Warszawa", "Domaniewska"))
                .containsExactly("02-672");
    }

    @Test
    void getPostalCodesForStreetReturnsUniqueSortedCodesWithoutEmptyOnes() {
        Map<String, Object> noZipcode = new LinkedHashMap<>(Map.of("city", "Warszawa"));
        mockHttp.addResponse(
                "Autocomplete",
                "searchAddress",
                items(
                        item("02-676"),
                        item("02-672"),
                        item(""),
                        item("02-676"),
                        item(null),
                        noZipcode,
                        "not an object",
                        item("02-673")));

        List<String> codes = client.autocomplete().getPostalCodesForStreet("Warszawa", "Domaniewska");

        assertThat(codes).containsExactly("02-672", "02-673", "02-676");
    }

    @Test
    void getPostalCodesForStreetReturnsEmptyListForNoItems() {
        mockHttp.addResponse("Autocomplete", "searchAddress", Fixtures.load("autocomplete_search_empty"));

        assertThat(client.autocomplete().getPostalCodesForStreet("Warszawa", "Nieistniejaca"))
                .isEmpty();
    }

    @Test
    void getPostalCodesForStreetReturnsEmptyListWhenItemsAreMissing() {
        mockHttp.addResponse("Autocomplete", "searchAddress", Map.of("result", Map.of()));

        assertThat(client.autocomplete().getPostalCodesForStreet("Warszawa", "Domaniewska"))
                .isEmpty();
    }

    @Test
    void getPostalCodesForStreetReturnsEmptyListWhenResultIsNotAnObject() {
        mockHttp.addResponse("Autocomplete", "searchAddress", Map.of("result", List.of()));

        assertThat(client.autocomplete().getPostalCodesForStreet("Warszawa", "Domaniewska"))
                .isEmpty();
    }

    @Test
    void getPostalCodesForStreetReadsTheNativeResultWhenTheDefaultFormatIsGoogle() {
        ApiClient googleClient = new ApiClient(
                Config.builder("test-key", "test-pass").defaultFormat("google").build(), mockHttp);
        mockHttp.addResponse("Autocomplete", "searchAddress", Fixtures.load("autocomplete_search_address_success"));

        assertThat(googleClient.autocomplete().getPostalCodesForStreet("Warszawa", "Domaniewska"))
                .containsExactly("02-672");
    }

    @Test
    void getPostalCodesForStreetReturnsAMutableList() {
        mockHttp.addResponse("Autocomplete", "searchAddress", Fixtures.load("autocomplete_search_address_success"));

        List<String> codes = client.autocomplete().getPostalCodesForStreet("Warszawa", "Domaniewska");
        codes.add("00-001");

        assertThat(codes).isInstanceOf(ArrayList.class).hasSize(2);
    }

    @Test
    void getPostalCodesForStreetRejectsMissingCity() {
        assertThatThrownBy(() -> client.autocomplete().getPostalCodesForStreet(null, "Domaniewska"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Autocomplete.getPostalCodesForStreet")
                .hasMessageContaining("city is required but was null");
        assertThatThrownBy(() -> client.autocomplete().getPostalCodesForStreet(" ", "Domaniewska"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("city is required but was empty");
        assertThat(mockHttp.requestCount()).isZero();
    }

    @Test
    void getPostalCodesForStreetRejectsMissingStreet() {
        assertThatThrownBy(() -> client.autocomplete().getPostalCodesForStreet("Warszawa", null, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("street is required but was null");
        assertThatThrownBy(() -> client.autocomplete().getPostalCodesForStreet("Warszawa", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("street is required but was empty");
        assertThat(mockHttp.requestCount()).isZero();
    }

    @Test
    void constructorRejectsNullClient() {
        assertThatThrownBy(() -> new Autocomplete(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("new Autocomplete")
                .hasMessageContaining("client.autocomplete()");
    }
}
