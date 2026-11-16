package pl.automapa.mapservices.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.automapa.mapservices.json.Json;

class RequestBuilderTest {

    private static final String USER_AGENT = "automapa-java-sdk/1.2.3";

    private final RequestBuilder builder = new RequestBuilder("https://api.automapa.pl/", "v3", USER_AGENT);

    @Test
    void buildsCorrectUrl() {
        HttpRequest request = builder.build("Geocoder", "geocode", Map.of(), null);

        assertThat(request.url()).isEqualTo("https://api.automapa.pl/v3/Geocoder/geocode");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://api.automapa.pl", "https://api.automapa.pl/", "https://api.automapa.pl///"})
    void trailingSlashesOfBaseUrlAreTrimmed(String baseUrl) {
        HttpRequest request = new RequestBuilder(baseUrl, "v3", USER_AGENT).build("PingPong", "ping", Map.of(), null);

        assertThat(request.url()).isEqualTo("https://api.automapa.pl/v3/PingPong/ping");
        assertThat(URI.create(request.url()).getPath()).doesNotContain("//");
    }

    @Test
    void usesConfiguredVersion() {
        HttpRequest request = new RequestBuilder("http://localhost:8080/api", "v4", USER_AGENT)
                .build("Road", "speedCheck", Map.of(), null);

        assertThat(request.url()).isEqualTo("http://localhost:8080/api/v4/Road/speedCheck");
    }

    @Test
    void methodIsPost() {
        assertThat(builder.build("Geocoder", "geocode", Map.of(), null).method())
                .isEqualTo("POST");
    }

    @Test
    void setsContentTypeAndUserAgentWithoutSession() {
        HttpRequest request = builder.build("PingPong", "ping", Map.of(), null);

        assertThat(request.headers())
                .containsExactly(Map.entry("Content-Type", "application/json"), Map.entry("User-Agent", USER_AGENT));
    }

    @Test
    void setsSessionIdHeaderWhenProvided() {
        HttpRequest request = builder.build("Geocoder", "geocode", Map.of(), "my-session-id");

        assertThat(request.headers()).containsEntry("Session-Id", "my-session-id");
    }

    @Test
    void omitsSessionIdHeaderWhenNull() {
        assertThat(builder.build("PingPong", "ping", Map.of(), null).headers()).doesNotContainKey("Session-Id");
    }

    @Test
    void bodyIsJsonOfParamsInInsertionOrder() {
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("city", "Warszawa");
        address.put("street", "Domaniewska");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("address", address);
        params.put("maxResults", 5);

        HttpRequest request = builder.build("Geocoder", "geocode", params, null);

        assertThat(request.body())
                .isEqualTo("{\"address\":{\"city\":\"Warszawa\",\"street\":\"Domaniewska\"},\"maxResults\":5}");
    }

    @Test
    void emptyParamsProduceEmptyJsonObject() {
        assertThat(builder.build("PingPong", "ping", Map.of(), null).body()).isEqualTo("{}");
    }

    @Test
    void nullParamsProduceEmptyJsonObject() {
        assertThat(builder.build("PingPong", "ping", null, null).body()).isEqualTo("{}");
    }

    @Test
    void shortBuildSendsNoSessionAndEmptyBody() {
        HttpRequest request = builder.build("PingPong", "ping");

        assertThat(request.body()).isEqualTo("{}");
        assertThat(request.headers()).doesNotContainKey("Session-Id");
    }

    @Test
    void unknownParamsPassedFlatToBodyWithoutEnvelope() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("address", Map.of("city", "Test"));
        params.put("unknownFutureParam", true);
        params.put("anotherUnknown", Map.of("nested", "value"));
        params.put("points", List.of(List.of(21.0, 52.2)));

        @SuppressWarnings("unchecked")
        Map<String, Object> decoded = (Map<String, Object>)
                Json.parse(builder.build("Geocoder", "geocode", params, null).body());

        assertThat(decoded)
                .containsOnlyKeys("address", "unknownFutureParam", "anotherUnknown", "points")
                .doesNotContainKeys("extra", "extended", "params");
    }

    @Test
    void exposesConfiguration() {
        assertThat(builder.baseUrl()).isEqualTo("https://api.automapa.pl/");
        assertThat(builder.version()).isEqualTo("v3");
        assertThat(builder.userAgent()).isEqualTo(USER_AGENT);
    }

    @Test
    void rejectsMissingConfiguration() {
        assertThatThrownBy(() -> new RequestBuilder(null, "v3", USER_AGENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("baseUrl");
        assertThatThrownBy(() -> new RequestBuilder("https://api.automapa.pl", " ", USER_AGENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version");
        assertThatThrownBy(() -> new RequestBuilder("https://api.automapa.pl", "v3", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userAgent");
    }

    @Test
    void rejectsMissingServiceOrMethod() {
        assertThatThrownBy(() -> builder.build(null, "ping", Map.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("service");
        assertThatThrownBy(() -> builder.build("PingPong", "", Map.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("method");
    }
}
