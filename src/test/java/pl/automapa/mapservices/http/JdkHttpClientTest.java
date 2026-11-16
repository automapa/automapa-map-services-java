package pl.automapa.mapservices.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pl.automapa.mapservices.exception.NetworkException;

/** Exercises the real JDK transport against a local {@link HttpServer} bound to an ephemeral port (offline). */
class JdkHttpClientTest {

    /** "Zaklad zolty" with Polish diacritics, built from code points to keep the source ASCII only. */
    private static final String POLISH_TEXT = "Zak" + Character.toString(0x142) + "ad " + Character.toString(0x17c)
            + Character.toString(0xf3) + Character.toString(0x142) + "ty";

    private HttpServer server;
    private ExecutorService executor;
    private final CountDownLatch release = new CountDownLatch(1);

    @AfterEach
    void stopServer() {
        release.countDown();
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void sendsMethodHeadersAndBodyAndReturnsTheEcho() {
        String url = start(exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String echo = exchange.getRequestMethod()
                    + "|" + exchange.getRequestURI().getPath()
                    + "|" + exchange.getRequestHeaders().getFirst("Content-Type")
                    + "|" + exchange.getRequestHeaders().getFirst("User-Agent")
                    + "|" + exchange.getRequestHeaders().getFirst("Session-Id")
                    + "|" + body;
            exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
            exchange.getResponseHeaders().add("X-Echo", "first");
            exchange.getResponseHeaders().add("X-Echo", "second");
            reply(exchange, 200, echo);
        });
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("User-Agent", "automapa-java-sdk/test");
        headers.put("Session-Id", "session-123");

        HttpResponse response = new JdkHttpClient(Duration.ofSeconds(5))
                .send(new HttpRequest("POST", url + "/v3/PingPong/ping", headers, "{\"msg\":\"" + POLISH_TEXT + "\"}"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.body())
                .isEqualTo("POST|/v3/PingPong/ping|application/json|automapa-java-sdk/test|session-123|{\"msg\":\""
                        + POLISH_TEXT + "\"}");
        assertThat(header(response, "X-Echo")).isEqualTo("first");
        assertThat(header(response, "Content-Type")).isEqualTo("text/plain; charset=utf-8");
        assertThat(response.headers().keySet()).noneMatch(name -> name.startsWith(":"));
    }

    @Test
    void decodesTheResponseBodyAsUtf8EvenWithoutCharset() {
        String url = start(exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            reply(exchange, 200, "{\"city\":\"" + POLISH_TEXT + "\"}");
        });

        HttpResponse response =
                new JdkHttpClient(Duration.ofSeconds(5)).send(new HttpRequest("POST", url + "/x", Map.of(), "{}"));

        assertThat(response.body()).isEqualTo("{\"city\":\"" + POLISH_TEXT + "\"}");
    }

    @Test
    void sendsTheGivenMethodWithoutBody() {
        String url = start(exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            reply(exchange, 200, exchange.getRequestMethod() + ":" + body.length);
        });

        HttpResponse response =
                new JdkHttpClient(Duration.ofSeconds(5)).send(new HttpRequest("GET", url + "/x", Map.of(), ""));

        assertThat(response.body()).isEqualTo("GET:0");
    }

    @Test
    void returnsErrorResponsesInsteadOfThrowing() {
        String url = start(exchange -> reply(exchange, 403, "{\"message\":\"Forbidden - no permission\"}"));

        HttpResponse response = new JdkHttpClient(Duration.ofSeconds(5))
                .send(new HttpRequest("POST", url + "/v3/Geocoder/geocode", Map.of(), "{}"));

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.body()).isEqualTo("{\"message\":\"Forbidden - no permission\"}");
    }

    @Test
    void returnsServerErrorWithEmptyBody() {
        String url = start(exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });

        HttpResponse response =
                new JdkHttpClient(Duration.ofSeconds(5)).send(new HttpRequest("POST", url + "/x", Map.of(), "{}"));

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).isEmpty();
    }

    @Test
    void timeoutBecomesNetworkExceptionWithUrlCauseAndHint() {
        String url = start(exchange -> {
            awaitRelease();
            reply(exchange, 200, "too late");
        });
        String target = url + "/v3/Routes/route";

        assertThatThrownBy(() ->
                        new JdkHttpClient(Duration.ofMillis(200)).send(new HttpRequest("POST", target, Map.of(), "{}")))
                .isInstanceOf(NetworkException.class)
                .hasCauseInstanceOf(HttpTimeoutException.class)
                .hasMessageContaining(target)
                .hasMessageContaining("timed out after 200 ms")
                .hasMessageContaining("timeout")
                .hasMessageContaining("baseUrl")
                .satisfies(
                        e -> assertThat(((NetworkException) e).getStatusCode()).isZero());
    }

    @Test
    void refusedConnectionBecomesNetworkException() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }
        String target = "http://127.0.0.1:" + closedPort + "/v3/PingPong/ping";

        assertThatThrownBy(() ->
                        new JdkHttpClient(Duration.ofSeconds(5)).send(new HttpRequest("POST", target, Map.of(), "{}")))
                .isInstanceOf(NetworkException.class)
                .hasCauseInstanceOf(ConnectException.class)
                .hasMessageContaining(target)
                .hasMessageContaining("network connection")
                .hasMessageContaining("baseUrl");
    }

    @Test
    void interruptionBecomesNetworkExceptionAndRestoresTheInterruptFlag() {
        String url = start(exchange -> {
            awaitRelease();
            reply(exchange, 200, "too late");
        });
        String target = url + "/v3/PingPong/ping";
        JdkHttpClient client = new JdkHttpClient(Duration.ofSeconds(30));

        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> client.send(new HttpRequest("POST", target, Map.of(), "{}")))
                    .isInstanceOf(NetworkException.class)
                    .hasCauseInstanceOf(InterruptedException.class)
                    .hasMessageContaining(target)
                    .hasMessageContaining("interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void invalidUrlBecomesNetworkExceptionWithBaseUrlHint() {
        JdkHttpClient client = new JdkHttpClient(Duration.ofSeconds(1));

        assertThatThrownBy(() -> client.send(new HttpRequest("POST", "ftp://example.test/v3/A/b", Map.of(), "{}")))
                .isInstanceOf(NetworkException.class)
                .hasMessageContaining("ftp://example.test/v3/A/b")
                .hasMessageContaining("baseUrl");
        assertThatThrownBy(() -> client.send(new HttpRequest("POST", "http://exa mple.test/v3", Map.of(), "{}")))
                .isInstanceOf(NetworkException.class)
                .hasMessageContaining("http://exa mple.test/v3")
                .hasMessageContaining("baseUrl");
    }

    @Test
    void rejectsMissingOrNonPositiveTimeout() {
        assertThatThrownBy(() -> new JdkHttpClient(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeout")
                .hasMessageContaining("Duration.ofSeconds(30)");
        assertThatThrownBy(() -> new JdkHttpClient(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> new JdkHttpClient(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void exposesTheTimeout() {
        assertThat(new JdkHttpClient(Duration.ofSeconds(7)).timeout()).isEqualTo(Duration.ofSeconds(7));
    }

    @Test
    void rejectsNullRequest() {
        assertThatThrownBy(() -> new JdkHttpClient(Duration.ofSeconds(1)).send(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("request");
    }

    private String start(HttpHandler handler) {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot start the local test HTTP server", e);
        }
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/", handler);
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void awaitRelease() {
        try {
            release.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String header(HttpResponse response, String name) {
        return response.headers().entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
