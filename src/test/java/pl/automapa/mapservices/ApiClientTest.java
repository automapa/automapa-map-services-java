package pl.automapa.mapservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.automapa.mapservices.exception.AuthenticationException;
import pl.automapa.mapservices.exception.NetworkException;
import pl.automapa.mapservices.exception.ServerException;
import pl.automapa.mapservices.exception.SessionException;
import pl.automapa.mapservices.formatter.FormatterRegistry;
import pl.automapa.mapservices.formatter.GoogleFormatter;
import pl.automapa.mapservices.formatter.NativeFormatter;
import pl.automapa.mapservices.http.HttpRequest;
import pl.automapa.mapservices.response.ApiResponse;
import pl.automapa.mapservices.session.InMemorySessionStorage;
import pl.automapa.mapservices.session.PasswordHasher;
import pl.automapa.mapservices.session.SessionManager;
import pl.automapa.mapservices.support.Fixtures;
import pl.automapa.mapservices.support.MockHttpClient;

class ApiClientTest {

    private static final String STORED_SESSION = "test-session-id";

    private MockHttpClient mockHttp;
    private Config config;
    private ApiClient client;

    @BeforeEach
    void setUp() {
        mockHttp = new MockHttpClient();
        config = Config.builder("test-key", "test-pass").build();
        client = new ApiClient(config, mockHttp, managerWithStoredSession(config), null);
    }

    private SessionManager managerWithStoredSession(Config managerConfig) {
        InMemorySessionStorage storage = new InMemorySessionStorage();
        storage.store(STORED_SESSION);
        return new SessionManager(managerConfig, mockHttp, storage);
    }

    private ApiClient clientWithoutSession() {
        return new ApiClient(config, mockHttp);
    }

    private void queueSessionOpening() {
        mockHttp.addResponse("Session", "getSalt", Fixtures.load("session_salt"));
        mockHttp.addResponse("Session", "generateSession", Fixtures.load("session_generate"));
    }

    private long countRequests(String service, String method) {
        return mockHttp.allRequests().stream()
                .filter(request -> request.url().endsWith("/" + service + "/" + method))
                .count();
    }

    private static Map<String, Object> upperFormatterOutput(Map<String, Object> raw, String serviceMethod) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("serviceMethod", serviceMethod.toUpperCase(java.util.Locale.ROOT));
        out.put("rawKeys", List.copyOf(raw.keySet()));
        return out;
    }

    private FormatterRegistry registryWithUpper() {
        FormatterRegistry registry = new FormatterRegistry();
        registry.register("native", new NativeFormatter());
        registry.register("upper", ApiClientTest::upperFormatterOutput);
        return registry;
    }

    // --- construction -------------------------------------------------------------------------------------------

    @Test
    void getConfigReturnsTheConfigGivenToTheConstructor() {
        assertThat(client.getConfig()).isSameAs(config);
        assertThat(clientWithoutSession().getConfig()).isSameAs(config);
    }

    @Test
    void constructorRejectsMissingConfigWithAHint() {
        assertThatThrownBy(() -> new ApiClient(null, mockHttp))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("config is required")
                .hasMessageContaining("Config.builder(");
    }

    @Test
    void constructorRejectsMissingHttpClientWithAHint() {
        assertThatThrownBy(() -> new ApiClient(config, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("httpClient is required")
                .hasMessageContaining("ApiClient.create(config)");
    }

    @Test
    void createRejectsMissingConfigWithAHint() {
        assertThatThrownBy(() -> ApiClient.create(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("config is required");
    }

    // --- call: request ------------------------------------------------------------------------------------------

    @Test
    void callDispatchesPostToServiceAndMethodUrl() {
        mockHttp.addResponse("Geocoder", "geocode", Fixtures.load("geocode_success"));

        client.call("Geocoder", "geocode", Map.of("address", Map.of("city", "Warszawa")));

        HttpRequest request = mockHttp.lastRequest();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.url()).isEqualTo("https://api.automapa.pl/v3/Geocoder/geocode");
        assertThat(request.headers())
                .containsEntry("Content-Type", "application/json")
                .containsEntry("User-Agent", "automapa-java-sdk/" + ApiClient.SDK_VERSION);
    }

    @Test
    void callUsesBaseUrlAndVersionFromConfig() {
        Config custom = Config.builder("test-key", "test-pass")
                .baseUrl("http://localhost:8080/api/")
                .version("v9")
                .userAgent("my-app/1.0")
                .build();
        ApiClient customClient = new ApiClient(custom, mockHttp, managerWithStoredSession(custom), null);
        mockHttp.addResponse("PingPong", "ping", Map.of("result", "pong"));

        customClient.call("PingPong", "ping", Map.of("msg", "hello"));

        assertThat(mockHttp.lastRequest().url()).isEqualTo("http://localhost:8080/api/v9/PingPong/ping");
        assertThat(mockHttp.lastRequest().headers()).containsEntry("User-Agent", "my-app/1.0");
    }

    @Test
    void callPassesParamsAsJsonBody() {
        mockHttp.addResponse("Geocoder", "geocode", Map.of("result", List.of()));

        client.call("Geocoder", "geocode", Map.of("address", Map.of("city", "Test"), "maxResults", 5));

        Map<String, Object> payload = mockHttp.lastPayload();
        assertThat(payload).containsEntry("address", Map.of("city", "Test"));
        assertThat(payload).containsEntry("maxResults", 5L);
    }

    @Test
    void callPassesUnknownParamsFlat() {
        mockHttp.addResponse("Geocoder", "geocode", Map.of("result", List.of()));

        client.call("Geocoder", "geocode", Map.of("unknownFutureParam", true, "anotherUnknown", 42));

        assertThat(mockHttp.lastPayload())
                .containsEntry("unknownFutureParam", true)
                .containsEntry("anotherUnknown", 42L);
    }

    @Test
    void callWithoutParamsSendsAnEmptyJsonObject() {
        mockHttp.addResponse("PingPong", "ping", Map.of("result", "pong"));

        client.call("PingPong", "ping");

        assertThat(mockHttp.lastRequest().body()).isEqualTo("{}");
    }

    @Test
    void callWithNullParamsSendsAnEmptyJsonObject() {
        mockHttp.addResponse("PingPong", "ping", Map.of("result", "pong"));

        client.call("PingPong", "ping", null);

        assertThat(mockHttp.lastRequest().body()).isEqualTo("{}");
    }

    @Test
    void callKeepsTheOrderOfParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("zeta", 1);
        params.put("alpha", 2);
        params.put("format", "native");
        params.put("mid", 3);
        mockHttp.addResponse("Geocoder", "geocode", Map.of("result", List.of()));

        client.call("Geocoder", "geocode", params);

        assertThat(mockHttp.lastRequest().body()).isEqualTo("{\"zeta\":1,\"alpha\":2,\"mid\":3}");
    }

    @Test
    void callDoesNotSendFormatParamToApi() {
        mockHttp.addResponse("Geocoder", "geocode", Map.of("result", Map.of("city", "Wroclaw")));

        client.call("Geocoder", "geocode", Map.of("address", Map.of("city", "Wroclaw"), "format", "google"));

        assertThat(mockHttp.lastPayload()).doesNotContainKey("format").containsKey("address");
    }

    @Test
    void callDoesNotModifyTheCallerParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("format", "native");
        params.put("msg", "hello");
        mockHttp.addResponse("PingPong", "ping", Map.of("result", "pong"));

        client.call("PingPong", "ping", params);

        assertThat(params).containsOnlyKeys("format", "msg");
    }

    @ParameterizedTest
    @CsvSource({"'',method", "' ',method", "Geocoder,''", "Geocoder,' '"})
    void callRejectsBlankServiceOrMethodBeforeAnyRequest(String service, String method) {
        assertThatThrownBy(() -> client.call(service, method, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ApiClient.call")
                .hasMessageContaining("is required");
        assertThat(mockHttp.requestCount()).isZero();
    }

    @Test
    void callRejectsNullServiceOrMethodBeforeAnyRequest() {
        assertThatThrownBy(() -> clientWithoutSession().call(null, "geocode"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("service is required, e.g. Geocoder");
        assertThatThrownBy(() -> clientWithoutSession().call("Geocoder", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("method is required, e.g. geocode");
        assertThat(mockHttp.requestCount()).isZero();
    }

    // --- call: response -----------------------------------------------------------------------------------------

    @Test
    void callReturnsApiResponseWithRawStatusAndData() {
        Map<String, Object> fixture = Fixtures.load("geocode_success");
        mockHttp.addResponse("Geocoder", "geocode", fixture);

        ApiResponse response = client.call("Geocoder", "geocode", Map.of());

        assertThat(response.raw()).isEqualTo(fixture);
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.data()).isEqualTo(fixture.get("result"));
    }

    @Test
    void metaContainsServiceAndMethod() {
        mockHttp.addResponse("PingPong", "ping", Map.of("result", "pong"));

        ApiResponse response = client.call("PingPong", "ping", Map.of());

        assertThat(response.meta()).containsExactly(Map.entry("service", "PingPong"), Map.entry("method", "ping"));
    }

    @Test
    void callThrowsAuthenticationExceptionOn401() {
        mockHttp.addRawResponse(401, Map.of("message", "Invalid API key"));

        assertThatThrownBy(() -> client.call("Geocoder", "geocode", Map.of()))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("Invalid API key");
    }

    @Test
    void callThrowsServerExceptionOn500() {
        mockHttp.addRawResponse(500, Map.of("message", "Internal error"));

        assertThatThrownBy(() -> client.call("Geocoder", "geocode", Map.of())).isInstanceOf(ServerException.class);
    }

    @Test
    void networkExceptionPropagatesThroughCall() {
        mockHttp.addNetworkFailure("Connection refused");

        assertThatThrownBy(() -> client.call("Geocoder", "geocode", Map.of()))
                .isInstanceOf(NetworkException.class)
                .hasMessageContaining("Connection refused");
    }

    // --- call: session ------------------------------------------------------------------------------------------

    @Test
    void callInjectsSessionIdHeaderForSessionRequiringEndpoints() {
        mockHttp.addResponse("Geocoder", "geocode", Fixtures.load("geocode_success"));

        client.call("Geocoder", "geocode", Map.of());

        assertThat(mockHttp.lastRequest().headers()).containsEntry("Session-Id", STORED_SESSION);
        assertThat(mockHttp.requestCount()).isEqualTo(1);
    }

    @Test
    void callOpensTheSessionLazilyOnTheFirstProtectedCallOnly() {
        queueSessionOpening();
        mockHttp.addResponse("PingPong", "ping", Map.of("result", "pong"));
        mockHttp.addResponse("PingPong", "ping", Map.of("result", "pong"));
        ApiClient fresh = clientWithoutSession();

        fresh.call("PingPong", "ping", Map.of("msg", "a"));
        fresh.call("PingPong", "ping", Map.of("msg", "b"));

        assertThat(mockHttp.requestCount()).isEqualTo(4);
        List<HttpRequest> requests = mockHttp.allRequests();
        assertThat(requests.get(0).url()).endsWith("/Session/getSalt");
        assertThat(requests.get(1).url()).endsWith("/Session/generateSession");
        assertThat(requests.get(2).headers()).containsEntry("Session-Id", "qPPdEpdtpb");
        assertThat(requests.get(3).headers()).containsEntry("Session-Id", "qPPdEpdtpb");
    }

    @Test
    void callToSessionMethodsDoesNotOpenASessionNorSendTheHeader() {
        mockHttp.addResponse("Session", "getSalt", Fixtures.load("session_salt"));
        mockHttp.addResponse("Session", "generateSession", Fixtures.load("session_generate"));
        ApiClient fresh = clientWithoutSession();

        fresh.call("Session", "getSalt", Map.of("key", "test-key"));
        fresh.call("Session", "generateSession", Map.of("key", "test-key", "pass", "hash"));

        assertThat(mockHttp.requestCount()).isEqualTo(2);
        assertThat(mockHttp.allRequests())
                .allSatisfy(request -> assertThat(request.headers()).doesNotContainKey("Session-Id"));
    }

    @Test
    void callRetriesOnceWithANewSessionOn403() {
        mockHttp.addSequence(
                "Geocoder",
                "geocode",
                List.of(
                        MockHttpClient.reply(403, Map.of("message", "Session expired")),
                        MockHttpClient.reply(200, Fixtures.load("geocode_success"))));
        queueSessionOpening();

        ApiResponse response = client.call("Geocoder", "geocode", Map.of("address", Map.of("city", "Warszawa")));

        assertThat(response.status()).isEqualTo(200);
        assertThat(mockHttp.requestCount()).isEqualTo(4);
        List<HttpRequest> requests = mockHttp.allRequests();
        assertThat(requests.get(0).headers()).containsEntry("Session-Id", STORED_SESSION);
        assertThat(requests.get(1).url()).endsWith("/Session/getSalt");
        assertThat(requests.get(2).url()).endsWith("/Session/generateSession");
        assertThat(requests.get(3).headers()).containsEntry("Session-Id", "qPPdEpdtpb");
        assertThat(requests.get(3).body()).isEqualTo(requests.get(0).body());
    }

    @Test
    void secondConsecutive403ThrowsSessionExceptionWithoutAnotherRetry() {
        mockHttp.addSequence(
                "Geocoder",
                "geocode",
                List.of(
                        MockHttpClient.reply(403, Map.of("message", "Session expired")),
                        MockHttpClient.reply(403, Map.of("message", "Forbidden - no permission"))));
        queueSessionOpening();

        assertThatThrownBy(() -> client.call("Geocoder", "geocode", Map.of()))
                .isInstanceOf(SessionException.class)
                .hasMessageContaining("Forbidden - no permission")
                .hasMessageContaining("HTTP 403");
        assertThat(mockHttp.requestCount()).isEqualTo(4);
    }

    @Test
    void forbiddenResponseOfAMethodWithoutSessionIsNotRetried() {
        mockHttp.addResponse("Session", "getSalt", Map.of("message", "Forbidden"), 403);

        assertThatThrownBy(() -> clientWithoutSession().call("Session", "getSalt", Map.of("key", "test-key")))
                .isInstanceOf(SessionException.class);
        assertThat(mockHttp.requestCount()).isEqualTo(1);
    }

    @Test
    void sixteenConcurrentCallsOnAFreshClientOpenExactlyOneSession() throws Exception {
        int threads = 16;
        queueSessionOpening();
        for (int i = 0; i < threads; i++) {
            mockHttp.addResponse("PingPong", "ping", Map.of("result", "pong"));
        }
        ApiClient fresh = clientWithoutSession();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<ApiResponse>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                String msg = "msg-" + i;
                futures.add(executor.submit(() -> {
                    start.await();
                    return fresh.call("PingPong", "ping", Map.of("msg", msg));
                }));
            }
            start.countDown();
            for (Future<ApiResponse> future : futures) {
                assertThat(future.get(10, TimeUnit.SECONDS).data()).isEqualTo("pong");
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(countRequests("Session", "getSalt")).isEqualTo(1);
        assertThat(countRequests("Session", "generateSession")).isEqualTo(1);
        assertThat(countRequests("PingPong", "ping")).isEqualTo(threads);
        assertThat(mockHttp.allRequests())
                .filteredOn(request -> request.url().endsWith("/PingPong/ping"))
                .allSatisfy(request -> assertThat(request.headers()).containsEntry("Session-Id", "qPPdEpdtpb"));
    }

    // --- call: formats ------------------------------------------------------------------------------------------

    @Test
    void callUsesNativeFormatByDefault() {
        Map<String, Object> result = Map.of("city", "Poznan", "x", 16.92, "y", 52.40);
        mockHttp.addResponse("Geocoder", "geocode", Map.of("result", result));

        ApiResponse response = client.call("Geocoder", "geocode", Map.of());

        assertThat(response.data()).isEqualTo(result);
    }

    @Test
    void explicitNativeFormatReturnsTheResultUnchanged() {
        Map<String, Object> result = Map.of("city", "Poznan");
        mockHttp.addResponse("Geocoder", "geocode", Map.of("result", result));

        ApiResponse response = client.call("Geocoder", "geocode", Map.of("format", "native"));

        assertThat(response.data()).isEqualTo(result);
    }

    @Test
    void registeredFormatFromParamsProducesFormattedDataAndKeepsRaw() {
        ApiClient formatting = new ApiClient(config, mockHttp, managerWithStoredSession(config), registryWithUpper());
        Map<String, Object> fixture = Fixtures.load("geocode_success");
        mockHttp.addResponse("Geocoder", "geocode", fixture);

        ApiResponse response = formatting.call("Geocoder", "geocode", Map.of("format", "upper"));

        assertThat(response.data()).isEqualTo(upperFormatterOutput(fixture, "Geocoder.geocode"));
        assertThat(response.raw()).isEqualTo(fixture);
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.meta()).containsEntry("service", "Geocoder").containsEntry("method", "geocode");
    }

    @Test
    void defaultFormatFromConfigIsAppliedWhenParamsHaveNoFormat() {
        Config upperConfig =
                Config.builder("test-key", "test-pass").defaultFormat("upper").build();
        ApiClient formatting =
                new ApiClient(upperConfig, mockHttp, managerWithStoredSession(upperConfig), registryWithUpper());
        Map<String, Object> fixture = Map.of("result", "pong");
        mockHttp.addResponse("PingPong", "ping", fixture);

        ApiResponse response = formatting.call("PingPong", "ping", Map.of("msg", "x"));

        assertThat(response.data()).isEqualTo(upperFormatterOutput(fixture, "PingPong.ping"));
    }

    @Test
    void formatParamOverridesTheDefaultFormatFromConfig() {
        Config upperConfig =
                Config.builder("test-key", "test-pass").defaultFormat("upper").build();
        ApiClient formatting =
                new ApiClient(upperConfig, mockHttp, managerWithStoredSession(upperConfig), registryWithUpper());
        mockHttp.addResponse("PingPong", "ping", Map.of("result", "pong"));

        ApiResponse response = formatting.call("PingPong", "ping", Map.of("format", "native"));

        assertThat(response.data()).isEqualTo("pong");
    }

    @Test
    void nullFormatParamFallsBackToTheDefaultFormat() {
        Map<String, Object> params = new HashMap<>();
        params.put("format", null);
        mockHttp.addResponse("PingPong", "ping", Map.of("result", "pong"));

        ApiResponse response = client.call("PingPong", "ping", params);

        assertThat(response.data()).isEqualTo("pong");
        assertThat(mockHttp.lastPayload()).doesNotContainKey("format");
    }

    @Test
    void googleFormatIsRegisteredByDefault() {
        Map<String, Object> fixture = Fixtures.load("geocode_success");
        mockHttp.addResponse("Geocoder", "geocode", fixture);

        ApiResponse response = client.call("Geocoder", "geocode", Map.of("format", "google"));

        assertThat(response.data()).isEqualTo(new GoogleFormatter().format(fixture, "Geocoder.geocode"));
        assertThat(response.raw()).isEqualTo(fixture);
    }

    @Test
    void callAppliesGoogleFormatterWhenConfiguredAsDefaultFormat() {
        Config googleConfig =
                Config.builder("test-key", "test-pass").defaultFormat("google").build();
        ApiClient google = new ApiClient(googleConfig, mockHttp, managerWithStoredSession(googleConfig), null);
        mockHttp.addResponse(
                "Geocoder", "geocode", Map.of("result", Map.of("x", 21.0122, "y", 52.2297, "city", "Warszawa")));

        ApiResponse response = google.call("Geocoder", "geocode", Map.of());

        assertThat(response.data())
                .asInstanceOf(InstanceOfAssertFactories.MAP)
                .containsEntry("status", "OK")
                .containsKey("results");
    }

    @Test
    void unregisteredFormatFallsBackToNativeLikePhp() {
        mockHttp.addResponse("PingPong", "ping", Map.of("result", "pong"));

        ApiResponse response = client.call("PingPong", "ping", Map.of("format", "unknown-format"));

        assertThat(response.data()).isEqualTo("pong");
    }

    // --- openSession --------------------------------------------------------------------------------------------

    @Test
    void openSessionSendsHashedPasswordAndReturnsSessionId() {
        queueSessionOpening();

        String sessionId = clientWithoutSession().openSession();

        assertThat(sessionId).isEqualTo("qPPdEpdtpb");
        assertThat(mockHttp.requestCount()).isEqualTo(2);
        List<Map<String, Object>> payloads = mockHttp.allPayloads();
        assertThat(payloads.get(0)).isEqualTo(Map.of("key", "test-key"));
        assertThat(payloads.get(1))
                .isEqualTo(Map.of("key", "test-key", "pass", PasswordHasher.hash("test-pass", "xNnlXNS3Bq")));
        assertThat(payloads.get(1).get("pass")).isNotEqualTo("test-pass");
    }

    @Test
    void openSessionThenCallReusesSessionId() {
        queueSessionOpening();
        mockHttp.addResponse("PingPong", "ping", Map.of("result", "pong"));
        ApiClient fresh = clientWithoutSession();

        String sessionId = fresh.openSession();
        fresh.call("PingPong", "ping", Map.of("msg", "hello"));

        assertThat(mockHttp.requestCount()).isEqualTo(3);
        assertThat(mockHttp.lastRequest().headers()).containsEntry("Session-Id", sessionId);
    }

    @Test
    void openSessionReturnsTheStoredSessionWithoutRequests() {
        assertThat(client.openSession()).isEqualTo(STORED_SESSION);
        assertThat(mockHttp.requestCount()).isZero();
    }

    // --- request ------------------------------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
        "https://api.automapa.pl/, /v3/Foo/bar, https://api.automapa.pl/v3/Foo/bar",
        "https://api.automapa.pl/, v3/Foo/bar, https://api.automapa.pl/v3/Foo/bar",
        "https://api.automapa.pl, /v3/Foo/bar, https://api.automapa.pl/v3/Foo/bar",
        "https://api.automapa.pl///, ///v3/Foo/bar, https://api.automapa.pl/v3/Foo/bar",
        "http://localhost:8080/api/, v3/Foo/bar, http://localhost:8080/api/v3/Foo/bar"
    })
    void requestJoinsBaseUrlAndPathWithOneSlash(String baseUrl, String path, String expectedUrl) {
        Config custom = Config.builder("test-key", "test-pass").baseUrl(baseUrl).build();
        mockHttp.addRawResponse(200, Map.of("result", List.of()));

        new ApiClient(custom, mockHttp).request("POST", path, Map.of("x", 1));

        assertThat(mockHttp.lastRequest().url()).isEqualTo(expectedUrl);
    }

    @Test
    void requestSendsMethodHeadersAndParamsWithoutSession() {
        mockHttp.addRawResponse(200, Map.of("result", "ok"));

        ApiResponse response = clientWithoutSession().request("PUT", "/v3/Foo/bar", Map.of("x", 1, "format", "google"));

        HttpRequest request = mockHttp.lastRequest();
        assertThat(mockHttp.requestCount()).isEqualTo(1);
        assertThat(request.method()).isEqualTo("PUT");
        assertThat(request.headers())
                .containsEntry("Content-Type", "application/json")
                .containsEntry("User-Agent", "automapa-java-sdk/" + ApiClient.SDK_VERSION)
                .doesNotContainKey("Session-Id");
        assertThat(mockHttp.lastPayload()).isEqualTo(Map.of("x", 1L, "format", "google"));
        assertThat(response.data()).isEqualTo("ok");
        assertThat(response.status()).isEqualTo(200);
    }

    @Test
    void requestMetaContainsThePathOnly() {
        mockHttp.addRawResponse(200, Map.of("result", "ok"));

        ApiResponse response = client.request("POST", "/v3/Foo/bar", Map.of());

        assertThat(response.meta()).containsExactly(Map.entry("path", "/v3/Foo/bar"));
    }

    @Test
    void requestWithoutParamsSendsAnEmptyJsonObject() {
        mockHttp.addRawResponse(200, Map.of("result", "ok"));
        mockHttp.addRawResponse(200, Map.of("result", "ok"));

        client.request("POST", "/v3/Foo/bar");
        String withoutParams = mockHttp.lastRequest().body();
        client.request("POST", "/v3/Foo/bar", null);

        assertThat(withoutParams).isEqualTo("{}");
        assertThat(mockHttp.lastRequest().body()).isEqualTo("{}");
    }

    @Test
    void requestDoesNotApplyFormatters() {
        Config upperConfig =
                Config.builder("test-key", "test-pass").defaultFormat("upper").build();
        ApiClient formatting = new ApiClient(upperConfig, mockHttp, null, registryWithUpper());
        mockHttp.addRawResponse(200, Map.of("result", "ok"));

        assertThat(formatting.request("POST", "/v3/Foo/bar").data()).isEqualTo("ok");
    }

    @Test
    void requestMapsHttpErrors() {
        mockHttp.addRawResponse(401, Map.of("message", "Invalid API key"));

        assertThatThrownBy(() -> client.request("POST", "/v3/Foo/bar")).isInstanceOf(AuthenticationException.class);
    }

    @Test
    void networkExceptionPropagatesThroughRequest() {
        mockHttp.addNetworkFailure("Operation timed out");

        assertThatThrownBy(() -> client.request("POST", "/v3/Foo/bar", Map.of()))
                .isInstanceOf(NetworkException.class)
                .hasMessageContaining("Operation timed out");
    }

    @Test
    void requestRejectsBlankHttpMethodOrPath() {
        assertThatThrownBy(() -> client.request(" ", "/v3/Foo/bar"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ApiClient.request: httpMethod is required, e.g. POST");
        assertThatThrownBy(() -> client.request("POST", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ApiClient.request: path is required, e.g. /v3/PingPong/ping");
        assertThat(mockHttp.requestCount()).isZero();
    }

    @Test
    void endpointFactoriesReturnEndpointsBoundToThisClient() {
        mockHttp.addResponse("PingPong", "ping", Map.of("result", Map.of("msg", "pong")));
        mockHttp.addResponse("Session", "getSalt", Fixtures.load("session_salt"));

        assertThat(client.session()).isInstanceOf(pl.automapa.mapservices.endpoint.Session.class);
        assertThat(client.pingPong()).isInstanceOf(pl.automapa.mapservices.endpoint.PingPong.class);
        assertThat(client.pingPong().ping("hello").dataAsMap()).containsEntry("msg", "pong");
        assertThat(mockHttp.lastRequest().headers()).containsEntry("Session-Id", STORED_SESSION);
        assertThat(client.session().getSalt("test-key").dataAsMap()).containsEntry("salt", "xNnlXNS3Bq");
    }

    @Test
    void endpointFactoriesReturnTheSameStatelessInstance() {
        assertThat(client.session()).isSameAs(client.session());
        assertThat(client.pingPong()).isSameAs(client.pingPong());
        assertThat(client.geocoder()).isSameAs(client.geocoder());
        assertThat(client.autocomplete()).isSameAs(client.autocomplete());
        assertThat(client.routes()).isSameAs(client.routes());
        assertThat(client.road()).isSameAs(client.road());
    }

    @Test
    void roadFactoryReturnsTheRoadEndpoint() {
        mockHttp.addResponse("Road", "speedCheck", Fixtures.load("road_speed_check_success"));

        assertThat(client.road()).isInstanceOf(pl.automapa.mapservices.endpoint.Road.class);
        assertThat(client.road()
                        .speedCheck(pl.automapa.mapservices.request.SpeedCheckRequest.builder(15.2154636, 52.3118895)
                                .build())
                        .dataAsMap())
                .containsEntry("speedLimit", 140L);
        assertThat(mockHttp.lastRequest().url()).isEqualTo("https://api.automapa.pl/v3/Road/speedCheck");
        assertThat(mockHttp.lastRequest().headers()).containsEntry("Session-Id", STORED_SESSION);
    }

    @Test
    void routesFactoryReturnsTheRoutesEndpoint() {
        mockHttp.addResponse("Routes", "route", Fixtures.load("routes_route_success"));

        assertThat(client.routes()).isInstanceOf(pl.automapa.mapservices.endpoint.Routes.class);
        assertThat(client.routes()
                        .route(pl.automapa.mapservices.request.RouteRequest.builder(
                                        List.of(
                                                new pl.automapa.mapservices.request.Point(21.0073642, 52.2297),
                                                new pl.automapa.mapservices.request.Point(18.6282, 54.352)),
                                        Map.of(),
                                        Map.of("type", "car"))
                                .build())
                        .dataAsMap())
                .containsEntry("length", 341618L);
        assertThat(mockHttp.lastRequest().headers()).containsEntry("Session-Id", STORED_SESSION);
    }

    @Test
    void autocompleteFactoryReturnsTheAutocompleteEndpoint() {
        mockHttp.addResponse("Autocomplete", "search", Fixtures.load("autocomplete_search_success"));

        assertThat(client.autocomplete()).isInstanceOf(pl.automapa.mapservices.endpoint.Autocomplete.class);
        assertThat(client.autocomplete()
                        .search(pl.automapa.mapservices.request.AutocompleteSearchRequest.builder("Warszawa")
                                .build())
                        .dataAsMap())
                .containsKey("items");
        assertThat(mockHttp.lastRequest().headers()).containsEntry("Session-Id", STORED_SESSION);
    }

    @Test
    void geocoderFactoryReturnsTheGeocoderEndpoint() {
        mockHttp.addResponse("Geocoder", "geocode", Fixtures.load("geocode_success"));

        assertThat(client.geocoder()).isInstanceOf(pl.automapa.mapservices.endpoint.Geocoder.class);
        assertThat(client.geocoder()
                        .geocode(pl.automapa.mapservices.request.GeocodeRequest.builder("Warszawa")
                                .build())
                        .dataAsMap())
                .containsEntry("city", "Warszawa");
        assertThat(mockHttp.lastRequest().headers()).containsEntry("Session-Id", STORED_SESSION);
    }
}
