package pl.automapa.mapservices.live;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.Config;
import pl.automapa.mapservices.exception.AutomapaException;
import pl.automapa.mapservices.http.HttpResponse;
import pl.automapa.mapservices.http.JdkHttpClient;
import pl.automapa.mapservices.http.RequestBuilder;
import pl.automapa.mapservices.json.Json;
import pl.automapa.mapservices.session.PasswordHasher;

/**
 * Base of the tests against the live API ({@code make test-integration}).
 *
 * <p>Credentials come from the process environment; variables that are missing or empty there are read from
 * {@code .env} in the project directory. Without credentials every test is skipped. A suite with a
 * {@link #suiteName()} runs only when the name is listed in {@code AUTOMAPA_API_REQUIRED_ACCESS}; a listed (required)
 * suite fails on a permission or server error instead of being skipped. Secret values are never printed.
 */
@Tag("live-api")
public abstract class LiveApiTestBase {

    /** Name of the variable with the comma-separated list of suites that must pass. */
    protected static final String ENV_REQUIRED_ACCESS = "AUTOMAPA_API_REQUIRED_ACCESS";

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Map<String, String> ENVIRONMENT = LiveSupport.resolveEnvironment(
            System.getenv(), Path.of(System.getProperty("basedir", System.getProperty("user.dir")), ".env"));

    /** Skips the test when credentials are missing or the suite is not listed as required. */
    @BeforeEach
    void checkLiveApiPrerequisites() {
        Assumptions.assumeTrue(
                hasValue(Config.ENV_API_KEY),
                Config.ENV_API_KEY + " not set (environment or .env) - skipping live API tests;"
                        + " run `cp .env.example .env` and fill it");
        Assumptions.assumeTrue(
                hasValue(Config.ENV_API_PASSWORD),
                Config.ENV_API_PASSWORD + " not set (environment or .env) - skipping live API tests;"
                        + " run `cp .env.example .env` and fill it");
        String suite = suiteName();
        Assumptions.assumeTrue(
                suite == null || isSuiteRequired(),
                () -> "Live test suite " + suite + " is skipped: not listed in " + ENV_REQUIRED_ACCESS + " (see .env)");
    }

    /**
     * Returns the access name of the tested service, as listed in {@code AUTOMAPA_API_REQUIRED_ACCESS}.
     *
     * @return the suite name, or {@code null} when the suite is not gated (e.g. {@code Session})
     */
    protected String suiteName() {
        return null;
    }

    /**
     * Tells whether this suite must pass: it has a name listed in {@code AUTOMAPA_API_REQUIRED_ACCESS}.
     *
     * @return whether permission and server errors fail the test instead of skipping it
     */
    protected final boolean isSuiteRequired() {
        return LiveSupport.isSuiteRequired(
                suiteName(), LiveSupport.requiredAccess(ENVIRONMENT.get(ENV_REQUIRED_ACCESS)));
    }

    /**
     * Returns the API key.
     *
     * @return the key; never print it
     */
    protected final String apiKey() {
        return ENVIRONMENT.get(Config.ENV_API_KEY);
    }

    /**
     * Returns the API password.
     *
     * @return the password; never print it
     */
    protected final String apiPassword() {
        return ENVIRONMENT.get(Config.ENV_API_PASSWORD);
    }

    /**
     * Builds a client of the live API with the native format.
     *
     * @return the client
     */
    protected final ApiClient buildApiClient() {
        return buildApiClient(Config.DEFAULT_FORMAT);
    }

    /**
     * Builds a client of the live API.
     *
     * @param defaultFormat default response format, e.g. {@code google}
     * @return the client
     */
    protected final ApiClient buildApiClient(String defaultFormat) {
        Config config = Config.builder(apiKey(), apiPassword())
                .timeout(TIMEOUT)
                .defaultFormat(defaultFormat)
                .build();
        return new ApiClient(config, new JdkHttpClient(TIMEOUT));
    }

    /**
     * Sends a raw request, bypassing {@link ApiClient}, and retries once after HTTP 429.
     *
     * @param service API service name
     * @param method API method name
     * @param params request body
     * @param sessionId value of the {@code Session-Id} header, or {@code null} for none
     * @return the raw response
     */
    protected final HttpResponse sendRequest(String service, String method, Map<String, ?> params, String sessionId) {
        RequestBuilder builder = new RequestBuilder(
                Config.DEFAULT_BASE_URL, Config.DEFAULT_VERSION, "automapa-java-sdk/" + ApiClient.SDK_VERSION);
        JdkHttpClient http = new JdkHttpClient(TIMEOUT);
        return withRateLimitRetry(() -> http.send(builder.build(service, method, params, sessionId)));
    }

    /**
     * Opens a session with raw requests ({@code getSalt} and {@code generateSession}).
     *
     * @return the session id
     */
    protected final String acquireSession() {
        Map<String, Object> saltBody =
                assertSuccessResponse(sendRequest("Session", "getSalt", Map.of("key", apiKey()), null));
        String salt = String.valueOf(result(saltBody).get("salt"));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("key", apiKey());
        params.put("pass", PasswordHasher.hash(apiPassword(), salt));
        Map<String, Object> sessionBody =
                assertSuccessResponse(sendRequest("Session", "generateSession", params, null));
        String sessionId = String.valueOf(result(sessionBody).get("sessionId"));
        assertThat(sessionId).as("sessionId of Session.generateSession").isNotBlank();
        return sessionId;
    }

    /**
     * Runs a call and retries it once after a 30 second pause when the API answers HTTP 429.
     *
     * @param call the call
     * @param <T> result type
     * @return the result
     */
    protected final <T> T withRateLimitRetry(Supplier<T> call) {
        return LiveSupport.retryOnceOnRateLimit(call, LiveSupport::sleep);
    }

    /**
     * Runs an SDK call with {@link #withRateLimitRetry}; a permission (HTTP 403 "no permission") or server (5xx)
     * error skips the test when the suite is not required and fails it otherwise.
     *
     * @param call the call
     * @param <T> result type
     * @return the result
     */
    protected final <T> T callLive(Supplier<T> call) {
        try {
            return withRateLimitRetry(call);
        } catch (AutomapaException e) {
            boolean accessProblem = LiveSupport.isPermissionDenied(e) || LiveSupport.isServerError(e);
            Assumptions.assumeFalse(
                    accessProblem && !isSuiteRequired(),
                    () -> "API key has no access to this endpoint (HTTP " + e.getStatusCode() + ") - skipping: "
                            + e.getMessage());
            throw e;
        }
    }

    /**
     * Skips the test when a raw response says that the key has no permission and the suite is not required.
     *
     * @param response the raw response
     */
    protected final void skipIfNoPermission(HttpResponse response) {
        Assumptions.assumeFalse(
                LiveSupport.isPermissionDenied(response) && !isSuiteRequired(),
                () -> "API key lacks permission for this endpoint - skipping. Body: " + response.body());
    }

    /**
     * Asserts that a raw response is a 2xx JSON object with a {@code result} key.
     *
     * @param response the raw response
     * @return the parsed body
     */
    @SuppressWarnings("unchecked")
    protected static Map<String, Object> assertSuccessResponse(HttpResponse response) {
        assertThat(response.statusCode())
                .as("Expected a 2xx response, got HTTP %d. Body: %s", response.statusCode(), response.body())
                .isBetween(200, 299);
        Object body = Json.parse(response.body());
        assertThat(body).as("Response body is not a JSON object").isInstanceOf(Map.class);
        assertThat((Map<String, Object>) body)
                .as("Response missing \"result\" key")
                .containsKey("result");
        return (Map<String, Object>) body;
    }

    /**
     * Returns the {@code result} object of a parsed body.
     *
     * @param body parsed response body
     * @return the result object
     */
    @SuppressWarnings("unchecked")
    protected static Map<String, Object> result(Map<String, Object> body) {
        Object result = body.get("result");
        assertThat(result).as("result is a JSON object").isInstanceOf(Map.class);
        return (Map<String, Object>) result;
    }

    /**
     * Returns the suites listed in {@code AUTOMAPA_API_REQUIRED_ACCESS}.
     *
     * @return the suite names
     */
    protected static List<String> requiredAccess() {
        return LiveSupport.requiredAccess(ENVIRONMENT.get(ENV_REQUIRED_ACCESS));
    }

    private static boolean hasValue(String name) {
        String value = ENVIRONMENT.get(name);
        return value != null && !value.isBlank();
    }
}
