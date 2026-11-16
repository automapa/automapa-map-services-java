package pl.automapa.mapservices.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.automapa.mapservices.exception.NetworkException;

/** Port of the PHP {@code DebugHttpClientTest} plus the masking rules of PRD 3.6. */
class DebugHttpClientTest {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
    private final List<HttpRequest> received = new ArrayList<>();

    @Test
    void delegatesSendToInnerClientWithTheSameRequest() {
        HttpRequest request = new HttpRequest(
                "POST", "https://example.com/v3/Foo/bar", Map.of("Content-Type", "application/json"), "{\"x\":1}");

        new DebugHttpClient(recording(new HttpResponse(200, "{\"status\":\"ok\"}")), out).send(request);

        assertThat(received).containsExactly(request);
    }

    @Test
    void returnsResponseFromInner() {
        HttpResponse response = new HttpResponse(201, "created");

        HttpResponse actual = new DebugHttpClient(recording(response), out)
                .send(new HttpRequest("POST", "https://example.com/v3/Foo/bar", Map.of(), ""));

        assertThat(actual).isSameAs(response);
    }

    @Test
    void writesRequestToOutput() {
        HttpRequest request = new HttpRequest(
                "POST",
                "https://api.example.com/v3/Routes/route",
                Map.of("Content-Type", "application/json"),
                "{\"points\":[]}");

        new DebugHttpClient(recording(new HttpResponse(200, "{\"result\":\"ok\"}")), out).send(request);

        assertThat(written())
                .contains("=== REQUEST ===")
                .contains("POST https://api.example.com/v3/Routes/route")
                .contains("Content-Type: application/json")
                .contains("{\"points\":[]}");
    }

    @Test
    void writesResponseToOutput() {
        HttpResponse response = new HttpResponse(200, "{\"result\":\"ok\"}", Map.of("X-Custom", "value"));

        new DebugHttpClient(recording(response), out)
                .send(new HttpRequest("POST", "https://example.com/", Map.of(), ""));

        assertThat(written())
                .contains("=== RESPONSE (200) ===")
                .contains("X-Custom: value")
                .contains("{\"result\":\"ok\"}");
    }

    @Test
    void handlesEmptyBodyAndHeaders() {
        new DebugHttpClient(recording(new HttpResponse(204, "")), out)
                .send(new HttpRequest("POST", "https://example.com/", Map.of(), ""));

        assertThat(written())
                .isEqualTo("\n=== REQUEST ===\nPOST https://example.com/\n===============\n"
                        + "\n=== RESPONSE (204) ===\n=====================\n");
    }

    @Test
    void writesBlocksInTheSameLayoutAsPhp() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("User-Agent", "automapa-java-sdk/0.1.0");
        HttpRequest request = new HttpRequest("POST", "https://api.automapa.pl/v3/PingPong/ping", headers, "{}");
        HttpResponse response =
                new HttpResponse(200, "{\"result\":\"pong\"}", Map.of("Content-Type", "application/json"));

        new DebugHttpClient(recording(response), out).send(request);

        assertThat(written())
                .isEqualTo("\n=== REQUEST ===\n"
                        + "POST https://api.automapa.pl/v3/PingPong/ping\n"
                        + "Content-Type: application/json\n"
                        + "User-Agent: automapa-java-sdk/0.1.0\n"
                        + "\n"
                        + "{}\n"
                        + "===============\n"
                        + "\n=== RESPONSE (200) ===\n"
                        + "Content-Type: application/json\n"
                        + "\n"
                        + "{\"result\":\"pong\"}\n"
                        + "=====================\n");
    }

    @Test
    void masksSessionIdHeaderInRequestAndResponse() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Session-Id", "secret-session-123");
        HttpResponse response = new HttpResponse(200, "{}", Map.of("session-id", "secret-session-456"));

        new DebugHttpClient(recording(response), out)
                .send(new HttpRequest("POST", "https://example.com/v3/Geocoder/geocode", headers, "{}"));

        assertThat(written())
                .contains("Session-Id: ***")
                .contains("session-id: ***")
                .doesNotContain("secret-session-123")
                .doesNotContain("secret-session-456");
    }

    @Test
    void doesNotMaskTheRequestSentToTheInnerClient() {
        HttpRequest request = new HttpRequest(
                "POST",
                "https://example.com/v3/Session/generateSession",
                Map.of("Session-Id", "abc"),
                "{\"key\":\"k\",\"pass\":\"hash\"}");

        new DebugHttpClient(recording(new HttpResponse(200, "{}")), out).send(request);

        assertThat(received).containsExactly(request);
        assertThat(received.get(0).body()).contains("\"pass\":\"hash\"");
    }

    @Test
    void masksPassFieldInJsonBodies() {
        HttpRequest request = new HttpRequest(
                "POST",
                "https://example.com/v3/Session/generateSession",
                Map.of(),
                "{\"key\":\"api-key\",\"pass\":\"5f4dcc3b5aa765d61d8327deb882cf99\"}");
        HttpResponse response = new HttpResponse(200, "{\"echo\":{\"pass\" : \"esc\\\"aped\"},\"other\":1}");

        new DebugHttpClient(recording(response), out).send(request);

        assertThat(written())
                .contains("{\"key\":\"api-key\",\"pass\":\"***\"}")
                .contains("{\"echo\":{\"pass\" : \"***\"},\"other\":1}")
                .doesNotContain("5f4dcc3b5aa765d61d8327deb882cf99")
                .doesNotContain("aped");
    }

    @Test
    void masksNonStringPassValues() {
        HttpRequest request =
                new HttpRequest("POST", "https://example.com/", Map.of(), "{\"pass\":12345,\"x\":[{\"pass\":null}]}");

        new DebugHttpClient(recording(new HttpResponse(200, "")), out).send(request);

        assertThat(written()).contains("{\"pass\":\"***\",\"x\":[{\"pass\":\"***\"}]}");
    }

    @Test
    void keepsOtherFieldsThatOnlyContainPassInTheirName() {
        HttpRequest request = new HttpRequest(
                "POST",
                "https://example.com/",
                Map.of(),
                "{\"password_hint\":\"a\",\"bypass\":\"b\",\"msg\":\"pass\"}");

        new DebugHttpClient(recording(new HttpResponse(200, "")), out).send(request);

        assertThat(written()).contains("{\"password_hint\":\"a\",\"bypass\":\"b\",\"msg\":\"pass\"}");
    }

    @Test
    void printsTheRequestAndPropagatesTheInnerFailure() {
        HttpClient failing = request -> {
            throw new NetworkException("boom", new java.io.IOException("down"));
        };

        assertThatThrownBy(() -> new DebugHttpClient(failing, out)
                        .send(new HttpRequest("POST", "https://example.com/x", Map.of(), "{}")))
                .isInstanceOf(NetworkException.class)
                .hasMessage("boom");
        assertThat(written()).contains("=== REQUEST ===").doesNotContain("=== RESPONSE");
    }

    @Test
    void defaultsToStandardError() {
        PrintStream original = System.err;
        System.setErr(out);
        try {
            new DebugHttpClient(recording(new HttpResponse(200, "")))
                    .send(new HttpRequest("POST", "https://example.com/x", Map.of(), ""));
        } finally {
            System.setErr(original);
        }

        assertThat(written()).contains("=== REQUEST ===").contains("=== RESPONSE (200) ===");
    }

    @Test
    void rejectsMissingArguments() {
        HttpClient inner = recording(new HttpResponse(200, ""));

        assertThatThrownBy(() -> new DebugHttpClient(null, out))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inner");
        assertThatThrownBy(() -> new DebugHttpClient(inner, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out");
    }

    private HttpClient recording(HttpResponse response) {
        return request -> {
            received.add(request);
            return response;
        };
    }

    private String written() {
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
