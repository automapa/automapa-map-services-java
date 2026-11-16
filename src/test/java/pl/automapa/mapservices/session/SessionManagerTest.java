package pl.automapa.mapservices.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.automapa.mapservices.Config;
import pl.automapa.mapservices.exception.AuthenticationException;
import pl.automapa.mapservices.exception.NetworkException;
import pl.automapa.mapservices.exception.ServerException;
import pl.automapa.mapservices.exception.SessionException;
import pl.automapa.mapservices.exception.UnknownResponseException;
import pl.automapa.mapservices.http.HttpRequest;
import pl.automapa.mapservices.http.HttpResponse;
import pl.automapa.mapservices.support.Fixtures;
import pl.automapa.mapservices.support.MockHttpClient;

class SessionManagerTest {

    private MockHttpClient mockHttp;
    private Config config;

    @BeforeEach
    void setUp() {
        mockHttp = new MockHttpClient();
        config = Config.builder("test-key", "test-pass").build();
    }

    private SessionManager makeManager() {
        return new SessionManager(config, mockHttp, new InMemorySessionStorage());
    }

    private void addSessionFixtures() {
        mockHttp.addResponse("Session", "getSalt", Fixtures.load("session_salt.json"));
        mockHttp.addResponse("Session", "generateSession", Fixtures.load("session_generate.json"));
    }

    @Test
    void ensureSessionCallsSaltAndGenerateSession() {
        addSessionFixtures();

        String sessionId = makeManager().ensureSession();

        assertThat(sessionId).isEqualTo("qPPdEpdtpb");
        assertThat(mockHttp.requestCount()).isEqualTo(2);
    }

    @Test
    void generateSessionBodyHasKeyBeforePassLikePhp() {
        addSessionFixtures();

        makeManager().ensureSession();

        assertThat(mockHttp.lastRequest().body())
                .isEqualTo(
                        "{\"key\":\"test-key\",\"pass\":\"" + PasswordHasher.hash("test-pass", "xNnlXNS3Bq") + "\"}");
    }

    @Test
    void sessionRequestsUseConfigAndCarryNoSessionHeader() {
        config = Config.builder("test-key", "test-pass")
                .baseUrl("https://api.example.test/")
                .version("v9")
                .userAgent("custom-agent/1.2")
                .build();
        addSessionFixtures();

        makeManager().ensureSession();

        List<HttpRequest> requests = mockHttp.allRequests();
        assertThat(requests)
                .extracting(HttpRequest::url)
                .containsExactly(
                        "https://api.example.test/v9/Session/getSalt",
                        "https://api.example.test/v9/Session/generateSession");
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.headers()).doesNotContainKey("Session-Id");
            assertThat(request.headers()).containsEntry("User-Agent", "custom-agent/1.2");
        });
    }

    @Test
    void initSessionSendsKeyAndHashedPassword() {
        addSessionFixtures();

        makeManager().ensureSession();

        List<Map<String, Object>> payloads = mockHttp.allPayloads();
        assertThat(payloads.get(0)).isEqualTo(Map.of("key", "test-key"));
        assertThat(payloads.get(1))
                .isEqualTo(Map.of("key", "test-key", "pass", PasswordHasher.hash("test-pass", "xNnlXNS3Bq")));
        assertThat(payloads.get(1).get("pass")).isEqualTo("a53555c51b3cadaf99501cc687d715f6");
    }

    @Test
    void ensureSessionReturnsCachedSessionId() {
        mockHttp.addResponse("Session", "getSalt", Map.of("result", Map.of("salt", "abc")));
        mockHttp.addResponse("Session", "generateSession", Map.of("result", Map.of("sessionId", "first-id")));

        SessionManager manager = makeManager();
        String first = manager.ensureSession();
        String second = manager.ensureSession();

        assertThat(second).isEqualTo(first).isEqualTo("first-id");
        assertThat(mockHttp.requestCount()).isEqualTo(2);
    }

    @Test
    void ensureSessionStoresSessionIdInGivenStorage() {
        addSessionFixtures();
        InMemorySessionStorage storage = new InMemorySessionStorage();

        new SessionManager(config, mockHttp, storage).ensureSession();

        assertThat(storage.get()).isEqualTo("qPPdEpdtpb");
    }

    @Test
    void ensureSessionUsesSessionAlreadyInStorageWithoutRequests() {
        InMemorySessionStorage storage = new InMemorySessionStorage();
        storage.store("stored-id");

        String sessionId = new SessionManager(config, mockHttp, storage).ensureSession();

        assertThat(sessionId).isEqualTo("stored-id");
        assertThat(mockHttp.requestCount()).isZero();
    }

    @Test
    void clearSessionAllowsReinit() {
        mockHttp.addResponse("Session", "getSalt", Map.of("result", Map.of("salt", "s1")));
        mockHttp.addResponse("Session", "generateSession", Map.of("result", Map.of("sessionId", "id-1")));
        mockHttp.addResponse("Session", "getSalt", Map.of("result", Map.of("salt", "s2")));
        mockHttp.addResponse("Session", "generateSession", Map.of("result", Map.of("sessionId", "id-2")));

        SessionManager manager = makeManager();
        manager.ensureSession();
        manager.clearSession();
        String second = manager.ensureSession();

        assertThat(second).isEqualTo("id-2");
        assertThat(mockHttp.requestCount()).isEqualTo(4);
        assertThat(mockHttp.allPayloads().get(3).get("pass")).isEqualTo(PasswordHasher.hash("test-pass", "s2"));
    }

    @Test
    void clearSessionEmptiesStorage() {
        InMemorySessionStorage storage = new InMemorySessionStorage();
        storage.store("stored-id");

        new SessionManager(config, mockHttp, storage).clearSession();

        assertThat(storage.get()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
        "Session, getSalt, false",
        "Session, generateSession, false",
        "Session, login, true",
        "PingPong, ping, true",
        "Geocoder, geocode, true",
        "Routes, route, true",
        "session, getSalt, true",
        "Session, getsalt, true",
    })
    void requiresSessionOnlyForProtectedMethods(String service, String method, boolean expected) {
        assertThat(makeManager().requiresSession(service, method)).isEqualTo(expected);
    }

    @Test
    void getSaltAuthenticationErrorIsMappedAndNothingIsStored() {
        mockHttp.addResponse("Session", "getSalt", Fixtures.load("errors/auth_error.json"), 401);
        InMemorySessionStorage storage = new InMemorySessionStorage();
        SessionManager manager = new SessionManager(config, mockHttp, storage);

        assertThatThrownBy(manager::ensureSession).isInstanceOf(AuthenticationException.class);
        assertThat(storage.get()).isNull();
        assertThat(mockHttp.requestCount()).isEqualTo(1);
    }

    @Test
    void generateSessionErrorIsMappedByExceptionMapper() {
        mockHttp.addResponse("Session", "getSalt", Fixtures.load("session_salt.json"));
        mockHttp.addResponse("Session", "generateSession", Map.of("message", "Forbidden"), 403);
        InMemorySessionStorage storage = new InMemorySessionStorage();

        assertThatThrownBy(() -> new SessionManager(config, mockHttp, storage).ensureSession())
                .isInstanceOf(SessionException.class)
                .hasMessageContaining("Forbidden")
                .hasMessageContaining("HTTP 403");
        assertThat(storage.get()).isNull();
    }

    @Test
    void serverErrorIsMappedByExceptionMapper() {
        mockHttp.addResponse("Session", "getSalt", Fixtures.load("errors/server_error.json"), 500);

        assertThatThrownBy(() -> makeManager().ensureSession()).isInstanceOf(ServerException.class);
    }

    @Test
    void nonJsonResponseIsUnknownResponse() {
        SessionManager manager =
                new SessionManager(config, request -> new HttpResponse(200, "<html>"), new InMemorySessionStorage());

        assertThatThrownBy(manager::ensureSession).isInstanceOf(UnknownResponseException.class);
    }

    @Test
    void networkFailurePropagates() {
        mockHttp.addNetworkFailure();

        assertThatThrownBy(() -> makeManager().ensureSession()).isInstanceOf(NetworkException.class);
    }

    @Test
    void missingSaltIsUnknownResponseWithDescription() {
        mockHttp.addResponse("Session", "getSalt", Map.of("result", Map.of()));

        assertThatThrownBy(() -> makeManager().ensureSession())
                .isInstanceOf(UnknownResponseException.class)
                .hasMessageContaining("Session.getSalt")
                .hasMessageContaining("result.salt")
                .hasMessageContaining("missing")
                .hasMessageContaining("HTTP 200")
                .satisfies(e -> assertThat(((UnknownResponseException) e).getStatusCode())
                        .isEqualTo(200));
        assertThat(mockHttp.requestCount()).isEqualTo(1);
    }

    @Test
    void missingResultObjectIsUnknownResponse() {
        mockHttp.addResponse("Session", "getSalt", Map.of("status", "ok"));

        assertThatThrownBy(() -> makeManager().ensureSession())
                .isInstanceOf(UnknownResponseException.class)
                .hasMessageContaining("result.salt")
                .hasMessageContaining("missing");
    }

    @Test
    void nonStringSaltIsUnknownResponseNamingTheType() {
        mockHttp.addResponse("Session", "getSalt", Map.of("result", Map.of("salt", 12345)));

        assertThatThrownBy(() -> makeManager().ensureSession())
                .isInstanceOf(UnknownResponseException.class)
                .hasMessageContaining("result.salt")
                .hasMessageContaining("JSON number");
    }

    @Test
    void resultThatIsNotAnObjectIsUnknownResponse() {
        mockHttp.addResponse("Session", "getSalt", Map.of("result", List.of("xNnlXNS3Bq")));

        assertThatThrownBy(() -> makeManager().ensureSession())
                .isInstanceOf(UnknownResponseException.class)
                .hasMessageContaining("result.salt")
                .hasMessageContaining("JSON array");
    }

    @Test
    void emptySaltIsUnknownResponse() {
        mockHttp.addResponse("Session", "getSalt", Map.of("result", Map.of("salt", "")));

        assertThatThrownBy(() -> makeManager().ensureSession())
                .isInstanceOf(UnknownResponseException.class)
                .hasMessageContaining("result.salt")
                .hasMessageContaining("empty");
    }

    @Test
    void missingSessionIdIsUnknownResponseAndNothingIsStored() {
        mockHttp.addResponse("Session", "getSalt", Fixtures.load("session_salt.json"));
        mockHttp.addResponse("Session", "generateSession", Map.of("result", Map.of("session", "x")));
        InMemorySessionStorage storage = new InMemorySessionStorage();

        assertThatThrownBy(() -> new SessionManager(config, mockHttp, storage).ensureSession())
                .isInstanceOf(UnknownResponseException.class)
                .hasMessageContaining("Session.generateSession")
                .hasMessageContaining("result.sessionId")
                .hasMessageContaining("missing");
        assertThat(storage.get()).isNull();
    }

    @Test
    void emptySessionIdIsUnknownResponse() {
        mockHttp.addResponse("Session", "getSalt", Fixtures.load("session_salt.json"));
        mockHttp.addResponse("Session", "generateSession", Map.of("result", Map.of("sessionId", "")));

        assertThatThrownBy(() -> makeManager().ensureSession())
                .isInstanceOf(UnknownResponseException.class)
                .hasMessageContaining("result.sessionId")
                .hasMessageContaining("empty");
    }

    @Test
    void errorMessagesNeverContainTheSaltOrPassword() {
        mockHttp.addResponse("Session", "getSalt", Map.of("result", Map.of("salt", "SECRET-SALT", "x", 1)));
        mockHttp.addResponse("Session", "generateSession", Map.of("result", Map.of("sessionId", 42)));

        assertThatThrownBy(() -> makeManager().ensureSession())
                .isInstanceOf(UnknownResponseException.class)
                .hasMessageContaining("JSON number")
                .hasMessageNotContaining("SECRET-SALT")
                .hasMessageNotContaining("test-pass")
                .hasMessageNotContaining("42");
    }

    @Test
    void concurrentEnsureSessionOpensExactlyOneSession() throws Exception {
        addSessionFixtures();
        SessionManager manager = makeManager();
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return manager.ensureSession();
                }));
            }
            start.countDown();

            for (Future<String> result : results) {
                assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo("qPPdEpdtpb");
            }
        } finally {
            executor.shutdownNow();
        }
        assertThat(mockHttp.requestCount()).isEqualTo(2);
    }

    @Test
    void constructorRejectsNullArguments() {
        InMemorySessionStorage storage = new InMemorySessionStorage();

        assertThatThrownBy(() -> new SessionManager(null, mockHttp, storage))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("config");
        assertThatThrownBy(() -> new SessionManager(config, null, storage))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("httpClient");
        assertThatThrownBy(() -> new SessionManager(config, mockHttp, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("storage");
    }
}
