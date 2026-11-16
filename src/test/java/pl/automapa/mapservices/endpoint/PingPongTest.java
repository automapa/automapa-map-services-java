package pl.automapa.mapservices.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.Config;
import pl.automapa.mapservices.http.HttpRequest;
import pl.automapa.mapservices.response.ApiResponse;
import pl.automapa.mapservices.support.Fixtures;
import pl.automapa.mapservices.support.MockHttpClient;

class PingPongTest {

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

    private static Map<String, Object> pong() {
        return Map.of("result", Map.of("msg", "pong"));
    }

    @Test
    void pingSendsMsgWithSessionHeader() {
        mockHttp.addResponse("PingPong", "ping", pong());

        ApiResponse response = client.pingPong().ping("hello");

        HttpRequest request = mockHttp.lastRequest();
        assertThat(request.url()).isEqualTo("https://api.automapa.pl/v3/PingPong/ping");
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.headers()).containsEntry("Session-Id", SESSION_ID);
        assertThat(mockHttp.lastPayload()).containsExactly(Map.entry("msg", "hello"));
        assertThat(response.dataAsMap()).containsEntry("msg", "pong");
        assertThat(response.meta()).containsEntry("service", "PingPong").containsEntry("method", "ping");
        assertThat(mockHttp.requestCount()).isEqualTo(3);
    }

    @Test
    void pingAllowsEmptyMessage() {
        mockHttp.addResponse("PingPong", "ping", pong());

        client.pingPong().ping("");

        assertThat(mockHttp.lastPayload()).containsExactly(Map.entry("msg", ""));
    }

    @Test
    void pingMergesExtraAndNamedMsgWins() {
        mockHttp.addResponse("PingPong", "ping", pong());
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("msg", "from-extra");
        extra.put("echo", true);

        client.pingPong().ping("hello", extra);

        assertThat(mockHttp.lastPayload()).containsExactly(Map.entry("msg", "hello"), Map.entry("echo", true));
        assertThat(extra).containsEntry("msg", "from-extra");
    }

    @Test
    void pingFormatFromExtraIsNotSentToTheApi() {
        mockHttp.addResponse("PingPong", "ping", pong());

        ApiResponse response = client.pingPong().ping("hello", Map.of("format", "google"));

        assertThat(mockHttp.lastPayload()).containsExactly(Map.entry("msg", "hello"));
        assertThat(response.raw()).containsKey("result");
    }

    @Test
    void pingWithNullExtraSendsMsgOnly() {
        mockHttp.addResponse("PingPong", "ping", pong());

        client.pingPong().ping("hello", null);

        assertThat(mockHttp.lastPayload()).containsExactly(Map.entry("msg", "hello"));
    }

    @Test
    void pingReusesTheOpenSession() {
        mockHttp.addResponse("PingPong", "ping", pong());
        mockHttp.addResponse("PingPong", "ping", pong());

        client.pingPong().ping("one");
        client.pingPong().ping("two");

        assertThat(mockHttp.requestCount()).isEqualTo(4);
        assertThat(mockHttp.lastRequest().headers()).containsEntry("Session-Id", SESSION_ID);
    }

    @Test
    void pingRejectsNullMessage() {
        assertThatThrownBy(() -> client.pingPong().ping(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PingPong.ping")
                .hasMessageContaining("msg is required");
        assertThat(mockHttp.requestCount()).isZero();
    }

    @Test
    void constructorRequiresClient() {
        assertThatThrownBy(() -> new PingPong(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("client.pingPong()");
    }
}
