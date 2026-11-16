package pl.automapa.mapservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.automapa.mapservices.http.JdkHttpClient;
import pl.automapa.mapservices.response.ApiResponse;

/** Checks {@link ApiClient#create(Config)} against a local {@link HttpServer} on an ephemeral port (offline). */
class ApiClientCreateTest {

    private HttpServer server;
    private final List<String> received = new ArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            synchronized (received) {
                received.add(exchange.getRequestMethod() + " "
                        + exchange.getRequestURI().getPath() + " "
                        + exchange.getRequestHeaders().getFirst("User-Agent") + " " + body);
            }
            String path = exchange.getRequestURI().getPath();
            String reply;
            if (path.endsWith("/Session/getSalt")) {
                reply = "{\"result\":{\"salt\":\"abc\"}}";
            } else if (path.endsWith("/Session/generateSession")) {
                reply = "{\"result\":{\"sessionId\":\"local-session\"}}";
            } else {
                reply = "{\"result\":\"" + exchange.getRequestHeaders().getFirst("Session-Id") + "\"}";
            }
            byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private Config localConfig(Duration timeout) {
        return Config.builder("local-key", "local-pass")
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/")
                .timeout(timeout)
                .build();
    }

    @Test
    void createUsesJdkHttpClientWithTheTimeoutFromConfig() {
        Config config = localConfig(Duration.ofSeconds(7));

        ApiClient client = ApiClient.create(config);

        assertThat(client.getConfig()).isSameAs(config);
        assertThat(client.httpClient()).isInstanceOf(JdkHttpClient.class);
        assertThat(((JdkHttpClient) client.httpClient()).timeout()).isEqualTo(Duration.ofSeconds(7));
    }

    @Test
    void createdClientCallsTheApiOverHttpWithALazySession() {
        ApiClient client = ApiClient.create(localConfig(Duration.ofSeconds(5)));

        ApiResponse response = client.call("PingPong", "ping", java.util.Map.of("msg", "hello"));

        assertThat(response.data()).isEqualTo("local-session");
        String userAgent = "automapa-java-sdk/" + ApiClient.SDK_VERSION;
        assertThat(received)
                .containsExactly(
                        "POST /v3/Session/getSalt " + userAgent + " {\"key\":\"local-key\"}",
                        "POST /v3/Session/generateSession " + userAgent + " {\"key\":\"local-key\",\"pass\":\""
                                + pl.automapa.mapservices.session.PasswordHasher.hash("local-pass", "abc") + "\"}",
                        "POST /v3/PingPong/ping " + userAgent + " {\"msg\":\"hello\"}");
    }
}
