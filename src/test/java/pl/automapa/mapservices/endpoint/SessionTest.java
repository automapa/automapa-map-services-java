package pl.automapa.mapservices.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.Config;
import pl.automapa.mapservices.exception.AuthenticationException;
import pl.automapa.mapservices.exception.UnknownResponseException;
import pl.automapa.mapservices.http.HttpRequest;
import pl.automapa.mapservices.response.ApiResponse;
import pl.automapa.mapservices.session.PasswordHasher;
import pl.automapa.mapservices.support.Fixtures;
import pl.automapa.mapservices.support.MockHttpClient;

class SessionTest {

    private static final String SALT = "xNnlXNS3Bq";
    private static final String SESSION_ID = "qPPdEpdtpb";

    private MockHttpClient mockHttp;
    private ApiClient client;

    @BeforeEach
    void setUp() {
        mockHttp = new MockHttpClient();
        client = new ApiClient(Config.builder("test-key", "test-pass").build(), mockHttp);
    }

    private static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    @Test
    void getSaltSendsKeyOnlyWithoutSessionHeader() {
        mockHttp.addResponse("Session", "getSalt", Fixtures.load("session_salt"));

        ApiResponse response = client.session().getSalt("test-key");

        assertThat(mockHttp.lastPayload()).containsExactly(Map.entry("key", "test-key"));
        HttpRequest request = mockHttp.lastRequest();
        assertThat(request.url()).isEqualTo("https://api.automapa.pl/v3/Session/getSalt");
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.headers()).doesNotContainKey("Session-Id");
        assertThat(response.dataAsMap()).containsEntry("salt", SALT);
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.meta()).containsEntry("service", "Session").containsEntry("method", "getSalt");
        assertThat(mockHttp.requestCount()).isEqualTo(1);
    }

    @Test
    void getSaltMergesExtraAndNamedKeyWins() {
        mockHttp.addResponse("Session", "getSalt", Fixtures.load("session_salt"));

        client.session().getSalt("test-key", map("key", "from-extra", "lang", "pl"));

        assertThat(mockHttp.lastPayload())
                .containsExactly(Map.entry("key", "test-key"), Map.entry("lang", "pl"))
                .doesNotContainValue("from-extra");
    }

    @Test
    void getSaltWithNullExtraSendsKeyOnly() {
        mockHttp.addResponse("Session", "getSalt", Fixtures.load("session_salt"));

        client.session().getSalt("test-key", null);

        assertThat(mockHttp.lastPayload()).containsExactly(Map.entry("key", "test-key"));
    }

    @Test
    void extraMapOfTheCallerIsNotModified() {
        mockHttp.addResponse("Session", "getSalt", Fixtures.load("session_salt"));
        Map<String, Object> extra = map("key", "from-extra");

        client.session().getSalt("test-key", extra);

        assertThat(extra).containsExactly(Map.entry("key", "from-extra"));
    }

    @Test
    void generateSessionSendsKeyAndPassAsIsInOrder() {
        mockHttp.addResponse("Session", "generateSession", Fixtures.load("session_generate"));

        ApiResponse response = client.session().generateSession("test-key", "already-hashed");

        assertThat(mockHttp.lastPayload())
                .containsExactly(Map.entry("key", "test-key"), Map.entry("pass", "already-hashed"));
        assertThat(mockHttp.lastRequest().url()).isEqualTo("https://api.automapa.pl/v3/Session/generateSession");
        assertThat(mockHttp.lastRequest().headers()).doesNotContainKey("Session-Id");
        assertThat(response.dataAsMap()).containsEntry("sessionId", SESSION_ID);
    }

    @Test
    void generateSessionMergesExtraAndNamedFieldsWin() {
        mockHttp.addResponse("Session", "generateSession", Fixtures.load("session_generate"));

        client.session()
                .generateSession(
                        "test-key", "already-hashed", map("pass", "x", "ttl", 60, "key", "y", "nothing", null));

        assertThat(mockHttp.lastPayload())
                .containsExactly(
                        Map.entry("pass", "already-hashed"),
                        Map.entry("ttl", 60L),
                        Map.entry("key", "test-key"),
                        new AbstractMap.SimpleEntry<>("nothing", null));
    }

    @Test
    void generateSessionDoesNotStoreTheSessionInTheClient() {
        mockHttp.addResponse("Session", "generateSession", Fixtures.load("session_generate"));
        mockHttp.addResponse("Session", "getSalt", Fixtures.load("session_salt"));
        mockHttp.addResponse("Session", "generateSession", map("result", map("sessionId", "client-session")));

        client.session().generateSession("test-key", "already-hashed");

        assertThat(client.openSession()).isEqualTo("client-session");
        assertThat(mockHttp.requestCount()).isEqualTo(3);
    }

    @Test
    void loginFetchesSaltAndSendsHashedPassword() {
        mockHttp.addResponse("Session", "getSalt", Fixtures.load("session_salt"));
        mockHttp.addResponse("Session", "generateSession", Fixtures.load("session_generate"));

        ApiResponse response = client.session().login("test-key", "plain-password");

        assertThat(response.dataAsMap()).containsEntry("sessionId", SESSION_ID);
        assertThat(response.meta()).containsEntry("method", "generateSession");
        assertThat(mockHttp.requestCount()).isEqualTo(2);
        List<Map<String, Object>> payloads = mockHttp.allPayloads();
        assertThat(payloads.get(0)).containsExactly(Map.entry("key", "test-key"));
        assertThat(payloads.get(1))
                .containsExactly(
                        Map.entry("key", "test-key"), Map.entry("pass", PasswordHasher.hash("plain-password", SALT)));
        assertThat(payloads.get(1).get("pass")).isNotEqualTo("plain-password");
        assertThat(mockHttp.allRequests())
                .allSatisfy(request -> assertThat(request.headers()).doesNotContainKey("Session-Id"));
    }

    @Test
    void loginPassesExtraToGenerateSessionOnly() {
        mockHttp.addResponse("Session", "getSalt", Fixtures.load("session_salt"));
        mockHttp.addResponse("Session", "generateSession", Fixtures.load("session_generate"));

        client.session().login("test-key", "plain-password", map("pass", "x", "ttl", 60));

        List<Map<String, Object>> payloads = mockHttp.allPayloads();
        assertThat(payloads.get(0)).containsExactly(Map.entry("key", "test-key"));
        assertThat(payloads.get(1))
                .containsExactly(
                        Map.entry("pass", PasswordHasher.hash("plain-password", SALT)),
                        Map.entry("ttl", 60L),
                        Map.entry("key", "test-key"));
    }

    @Test
    void loginWithoutSaltInResponseFailsWithReadableMessage() {
        mockHttp.addResponse("Session", "getSalt", map("result", map("other", "value")));

        assertThatThrownBy(() -> client.session().login("test-key", "plain-password"))
                .isInstanceOf(UnknownResponseException.class)
                .hasMessageContaining("Session.getSalt")
                .hasMessageContaining("result.salt")
                .hasMessageContaining("HTTP 200");
        assertThat(mockHttp.requestCount()).isEqualTo(1);
    }

    @Test
    void loginWithNonTextSaltFailsWithReadableMessage() {
        mockHttp.addResponse("Session", "getSalt", map("result", List.of("not", "an", "object")));

        assertThatThrownBy(() -> client.session().login("test-key", "plain-password"))
                .isInstanceOf(UnknownResponseException.class)
                .hasMessageContaining("result.salt");
    }

    @Test
    void loginPropagatesApiErrorOfGenerateSession() {
        mockHttp.addResponse("Session", "getSalt", Fixtures.load("session_salt"));
        mockHttp.addResponse("Session", "generateSession", Fixtures.load("errors/auth_error"), 401);

        assertThatThrownBy(() -> client.session().login("test-key", "wrong-password"))
                .isInstanceOf(AuthenticationException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void getSaltRejectsMissingKey(String key) {
        assertThatThrownBy(() -> client.session().getSalt(key))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Session.getSalt")
                .hasMessageContaining("key is required");
        assertThat(mockHttp.requestCount()).isZero();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void generateSessionRejectsMissingArguments(String value) {
        assertThatThrownBy(() -> client.session().generateSession(value, "hash"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Session.generateSession")
                .hasMessageContaining("key is required");
        assertThatThrownBy(() -> client.session().generateSession("test-key", value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pass is required")
                .hasMessageContaining("PasswordHasher.hash");
        assertThat(mockHttp.requestCount()).isZero();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void loginRejectsMissingArguments(String value) {
        assertThatThrownBy(() -> client.session().login(value, "secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Session.login")
                .hasMessageContaining("key is required");
        assertThatThrownBy(() -> client.session().login("test-key", value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("password is required");
        assertThat(mockHttp.requestCount()).isZero();
    }

    @Test
    void validationMessagesNeverContainThePassword() {
        assertThatThrownBy(() -> client.session().login(null, "top-secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("top-secret");
        assertThatThrownBy(() -> client.session().generateSession(" ", "top-secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("top-secret");
    }

    @Test
    void constructorRequiresClient() {
        assertThatThrownBy(() -> new Session(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("client.session()");
    }

    @Test
    void constructorCreatesUsableEndpoint() {
        mockHttp.addResponse("Session", "getSalt", Fixtures.load("session_salt"));

        ApiResponse response = new Session(client).getSalt("test-key");

        assertThat(response.dataAsMap()).containsEntry("salt", SALT);
    }
}
