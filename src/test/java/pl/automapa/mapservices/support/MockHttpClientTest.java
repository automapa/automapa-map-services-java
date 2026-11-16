package pl.automapa.mapservices.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import pl.automapa.mapservices.exception.NetworkException;
import pl.automapa.mapservices.http.HttpClient;
import pl.automapa.mapservices.http.HttpRequest;
import pl.automapa.mapservices.http.HttpResponse;
import pl.automapa.mapservices.http.RequestBuilder;
import pl.automapa.mapservices.json.Json;

class MockHttpClientTest {

    private final RequestBuilder builder = new RequestBuilder("https://api.automapa.pl", "v3", "test-agent");
    private final MockHttpClient mock = new MockHttpClient();

    private HttpRequest request(String service, String method, Map<String, ?> params) {
        return builder.build(service, method, params, null);
    }

    @Test
    void isAnHttpClient() {
        assertThat(mock).isInstanceOf(HttpClient.class);
    }

    @Test
    void returnsRegisteredResponseAsJsonWithDefaultStatus200() {
        mock.addResponse("PingPong", "ping", Map.of("result", "pong"));

        HttpResponse response = mock.send(request("PingPong", "ping", Map.of()));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(Json.parse(response.body())).isEqualTo(Map.of("result", "pong"));
    }

    @Test
    void returnsRegisteredStatus() {
        mock.addResponse("Geocoder", "geocode", Fixtures.load("errors/auth_error"), 401);

        HttpResponse response = mock.send(request("Geocoder", "geocode", Map.of()));

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(Json.parse(response.body())).isEqualTo(Fixtures.load("errors/auth_error.json"));
    }

    @Test
    void queuesResponsesPerEndpointInFifoOrder() {
        mock.addResponse("PingPong", "ping", Map.of("result", "first"));
        mock.addResponse("PingPong", "ping", Map.of("result", "second"));
        mock.addResponse("Geocoder", "geocode", Map.of("result", "other"));

        assertThat(mock.send(request("PingPong", "ping", Map.of())).body()).contains("first");
        assertThat(mock.send(request("Geocoder", "geocode", Map.of())).body()).contains("other");
        assertThat(mock.send(request("PingPong", "ping", Map.of())).body()).contains("second");
    }

    @Test
    void matchesEndpointRegardlessOfBaseUrlAndVersion() {
        mock.addResponse("PingPong", "ping", Map.of("result", "pong"));
        HttpRequest request =
                new RequestBuilder("http://localhost:1/api", "v4", "a").build("PingPong", "ping", Map.of(), null);

        assertThat(mock.send(request).statusCode()).isEqualTo(200);
    }

    @Test
    void sequenceIsServedInOrder() {
        mock.addSequence(
                "Geocoder",
                "geocode",
                List.of(
                        MockHttpClient.reply(403, Fixtures.load("errors/session_error")),
                        MockHttpClient.reply(200, Fixtures.load("geocode_success"))));

        assertThat(mock.send(request("Geocoder", "geocode", Map.of())).statusCode())
                .isEqualTo(403);
        assertThat(mock.send(request("Geocoder", "geocode", Map.of())).statusCode())
                .isEqualTo(200);
    }

    @Test
    void rawResponseIsWildcardUsedWhenEndpointHasNoResponse() {
        mock.addResponse("Session", "getSalt", Fixtures.load("session_salt"));
        mock.addRawResponse(500, Fixtures.load("errors/server_error"));

        assertThat(mock.send(request("Geocoder", "geocode", Map.of())).statusCode())
                .isEqualTo(500);
        assertThat(mock.send(request("Session", "getSalt", Map.of())).statusCode())
                .isEqualTo(200);
    }

    @Test
    void endpointResponseWinsOverWildcard() {
        mock.addRawResponse(429, Fixtures.load("errors/rate_limit"));
        mock.addResponse("PingPong", "ping", Map.of("result", "pong"));

        assertThat(mock.send(request("PingPong", "ping", Map.of())).statusCode())
                .isEqualTo(200);
        assertThat(mock.send(request("PingPong", "ping", Map.of())).statusCode())
                .isEqualTo(429);
    }

    @Test
    void networkFailureThrowsNetworkExceptionAndIsLogged() {
        mock.addNetworkFailure("Connection refused");

        assertThatThrownBy(() -> mock.send(request("PingPong", "ping", Map.of("msg", "x"))))
                .isInstanceOf(NetworkException.class)
                .hasMessage("Connection refused")
                .satisfies(
                        e -> assertThat(((NetworkException) e).getStatusCode()).isZero());
        assertThat(mock.requestCount()).isEqualTo(1);
    }

    @Test
    void defaultNetworkFailureHasReadableMessage() {
        mock.addNetworkFailure();

        assertThatThrownBy(() -> mock.send(request("PingPong", "ping", Map.of())))
                .isInstanceOf(NetworkException.class)
                .hasMessageContaining("Connection refused");
    }

    @Test
    void missingResponseFailsWithReadableHint() {
        assertThatThrownBy(() -> mock.send(request("Routes", "route", Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("/v3/Routes/route")
                .hasMessageContaining("addResponse(\"Routes\", \"route\"");
        assertThat(mock.requestCount()).isEqualTo(1);
    }

    @Test
    void logsRequestsAndPayloads() {
        mock.addResponse("PingPong", "ping", Map.of("result", "a"));
        mock.addResponse("PingPong", "ping", Map.of("result", "b"));

        HttpRequest first = request("PingPong", "ping", Map.of("msg", "one"));
        HttpRequest second = request("PingPong", "ping", Map.of("msg", "two"));
        mock.send(first);
        mock.send(second);

        assertThat(mock.requestCount()).isEqualTo(2);
        assertThat(mock.lastRequest()).isSameAs(second);
        assertThat(mock.lastPayload()).isEqualTo(Map.of("msg", "two"));
        assertThat(mock.allPayloads()).containsExactly(Map.of("msg", "one"), Map.of("msg", "two"));
        assertThat(mock.allRequests()).containsExactly(first, second);
    }

    @Test
    void emptyBodyIsLoggedAsEmptyPayload() {
        mock.addResponse("PingPong", "ping", Map.of());

        mock.send(new HttpRequest("POST", "https://api.automapa.pl/v3/PingPong/ping", Map.of(), ""));

        assertThat(mock.lastPayload()).isEmpty();
    }

    @Test
    void nonObjectPayloadFailsWithReadableMessage() {
        mock.addResponse("PingPong", "ping", Map.of());

        mock.send(new HttpRequest("POST", "https://api.automapa.pl/v3/PingPong/ping", Map.of(), "[1]"));

        assertThatThrownBy(mock::lastPayload)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a JSON object");
    }

    @Test
    void accessingLogBeforeAnyRequestFailsWithReadableMessage() {
        assertThat(mock.requestCount()).isZero();
        assertThat(mock.allPayloads()).isEmpty();
        assertThatThrownBy(mock::lastRequest)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no requests have been made yet");
        assertThatThrownBy(mock::lastPayload)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no requests have been made yet");
    }

    @Test
    void isThreadSafe() throws Exception {
        int threads = 8;
        int perThread = 50;
        for (int i = 0; i < threads * perThread; i++) {
            mock.addResponse("PingPong", "ping", Map.of("result", i));
        }
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                tasks.add(() -> {
                    int ok = 0;
                    for (int i = 0; i < perThread; i++) {
                        if (mock.send(request("PingPong", "ping", Map.of())).isSuccess()) {
                            ok++;
                        }
                    }
                    return ok;
                });
            }
            int total = 0;
            for (Future<Integer> future : pool.invokeAll(tasks)) {
                total += future.get();
            }
            assertThat(total).isEqualTo(threads * perThread);
            assertThat(mock.requestCount()).isEqualTo(threads * perThread);
            assertThatThrownBy(() -> mock.send(request("PingPong", "ping", Map.of())))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            pool.shutdownNow();
        }
    }
}
