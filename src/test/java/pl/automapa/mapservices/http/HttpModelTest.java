package pl.automapa.mapservices.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HttpModelTest {

    @Test
    void requestKeepsAllComponents() {
        HttpRequest request = new HttpRequest(
                "POST", "https://api.automapa.pl/v3/PingPong/ping", Map.of("Content-Type", "application/json"), "{}");

        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.url()).isEqualTo("https://api.automapa.pl/v3/PingPong/ping");
        assertThat(request.headers()).containsExactly(Map.entry("Content-Type", "application/json"));
        assertThat(request.body()).isEqualTo("{}");
    }

    @Test
    void requestCopiesHeadersAndKeepsTheirOrder() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("User-Agent", "agent");
        headers.put("Session-Id", "abc");

        HttpRequest request = new HttpRequest("POST", "https://example.test", headers, "{}");
        headers.put("X-Late", "added after construction");

        assertThat(request.headers())
                .containsExactly(
                        Map.entry("Content-Type", "application/json"),
                        Map.entry("User-Agent", "agent"),
                        Map.entry("Session-Id", "abc"));
        assertThatThrownBy(() -> request.headers().put("X", "y")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void requestTreatsNullHeadersAndBodyAsEmpty() {
        HttpRequest request = new HttpRequest("POST", "https://example.test", null, null);

        assertThat(request.headers()).isEmpty();
        assertThat(request.body()).isEmpty();
    }

    @Test
    void requestRequiresMethodAndUrl() {
        assertThatThrownBy(() -> new HttpRequest(null, "https://example.test", Map.of(), ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("method");
        assertThatThrownBy(() -> new HttpRequest("POST", " ", Map.of(), ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("url");
    }

    @Test
    void responseKeepsAllComponents() {
        HttpResponse response = new HttpResponse(200, "{\"result\":1}", Map.of("Content-Type", "application/json"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"result\":1}");
        assertThat(response.headers()).containsExactly(Map.entry("Content-Type", "application/json"));
    }

    @Test
    void responseWithoutHeadersHasEmptyHeaders() {
        HttpResponse response = new HttpResponse(204, null);

        assertThat(response.headers()).isEmpty();
        assertThat(response.body()).isEmpty();
    }

    @Test
    void responseCopiesHeadersImmutably() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("A", "1");
        headers.put("B", "2");

        HttpResponse response = new HttpResponse(200, "", headers);
        headers.clear();

        assertThat(response.headers()).containsExactly(Map.entry("A", "1"), Map.entry("B", "2"));
        assertThatThrownBy(() -> response.headers().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 201, 204, 299})
    void statusesFrom200To299AreSuccess(int status) {
        assertThat(new HttpResponse(status, "").isSuccess()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 100, 199, 300, 301, 400, 403, 429, 500, 503})
    void otherStatusesAreNotSuccess(int status) {
        assertThat(new HttpResponse(status, "").isSuccess()).isFalse();
    }
}
