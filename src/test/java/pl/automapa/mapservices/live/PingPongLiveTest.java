package pl.automapa.mapservices.live;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.Config;
import pl.automapa.mapservices.http.HttpResponse;
import pl.automapa.mapservices.http.JdkHttpClient;
import pl.automapa.mapservices.response.ApiResponse;
import pl.automapa.mapservices.session.InMemorySessionStorage;
import pl.automapa.mapservices.session.SessionManager;

/** PingPong service on the live API (suite {@code pingpong}). */
class PingPongLiveTest extends LiveApiTestBase {

    @Override
    protected String suiteName() {
        return "pingpong";
    }

    @Test
    void pingReturnsPong() {
        HttpResponse response = sendRequest("PingPong", "ping", Map.of("msg", "ping"), acquireSession());

        skipIfNoPermission(response);
        Map<String, Object> body = assertSuccessResponse(response);
        assertThat(result(body)).containsEntry("msg", "pong");
    }

    @Test
    void pingFailsWithoutSessionHeader() {
        HttpResponse response = sendRequest("PingPong", "ping", Map.of("msg", "ping"), null);

        assertThat(response.statusCode() / 100)
                .as("PingPong.ping should fail without the Session-Id header, got HTTP %d", response.statusCode())
                .isNotEqualTo(2);
    }

    @Test
    void pingViaFacadeReturnsPong() {
        ApiResponse response = callLive(() -> buildApiClient().pingPong().ping("ping"));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.dataAsMap()).containsEntry("msg", "pong");
    }

    @Test
    void invalidStoredSessionIsRenewedAfterHttp403() {
        Config config = buildApiClient().getConfig();
        JdkHttpClient http = new JdkHttpClient(config.getTimeout());
        InMemorySessionStorage storage = new InMemorySessionStorage();
        storage.store("invalid-session-id");
        ApiClient client = new ApiClient(config, http, new SessionManager(config, http, storage), null);

        ApiResponse response = callLive(() -> client.pingPong().ping("ping"));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.dataAsMap()).containsEntry("msg", "pong");
        assertThat(storage.get()).as("renewed session id").isNotBlank().isNotEqualTo("invalid-session-id");
    }
}
