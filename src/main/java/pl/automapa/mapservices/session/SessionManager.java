package pl.automapa.mapservices.session;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import pl.automapa.mapservices.Config;
import pl.automapa.mapservices.exception.UnknownResponseException;
import pl.automapa.mapservices.http.HttpClient;
import pl.automapa.mapservices.http.RequestBuilder;
import pl.automapa.mapservices.response.ApiResponse;
import pl.automapa.mapservices.response.ResponseParser;

/**
 * Opens and keeps the API session used by protected methods.
 *
 * <p>A session is opened lazily: {@code Session.getSalt} with the API key, then {@code Session.generateSession} with
 * the key and {@code md5(md5(password) + salt)} (see {@link PasswordHasher}); the returned {@code result.sessionId} is
 * kept in the {@link SessionStorage}. The API ends a session after 10 minutes of validity; the client renews it by
 * calling {@link #clearSession()} and {@link #ensureSession()} again after an HTTP 403 response.
 *
 * <p>The class is thread safe: concurrent {@link #ensureSession()} calls open exactly one session.
 */
public final class SessionManager {

    private static final String SERVICE = "Session";
    private static final String GET_SALT = "getSalt";
    private static final String GENERATE_SESSION = "generateSession";
    private static final Set<String> NO_SESSION_METHODS =
            Set.of(SERVICE + "." + GET_SALT, SERVICE + "." + GENERATE_SESSION);
    private static final String HINT = "check that baseUrl and version point to the Automapa API (e.g."
            + " https://api.automapa.pl/ and v3) and that AUTOMAPA_API_KEY is valid";

    private final Config config;
    private final HttpClient httpClient;
    private final SessionStorage storage;
    private final RequestBuilder requestBuilder;

    /**
     * Creates a session manager.
     *
     * @param config client configuration with the API key, password and connection settings
     * @param httpClient HTTP transport
     * @param storage storage of the session id
     * @throws IllegalArgumentException when any argument is {@code null}
     */
    public SessionManager(Config config, HttpClient httpClient, SessionStorage storage) {
        this.config = requireNonNull(config, "config", "Config.builder(key, password).build()");
        this.httpClient = requireNonNull(httpClient, "httpClient", "new JdkHttpClient(Duration.ofSeconds(30))");
        this.storage = requireNonNull(storage, "storage", "new InMemorySessionStorage()");
        this.requestBuilder = new RequestBuilder(config.getBaseUrl(), config.getVersion(), config.getUserAgent());
    }

    /**
     * Tells whether an API method needs the {@code Session-Id} header.
     *
     * @param service API service name, e.g. {@code Geocoder}
     * @param method API method name, e.g. {@code geocode}
     * @return {@code false} only for {@code Session.getSalt} and {@code Session.generateSession}
     */
    public boolean requiresSession(String service, String method) {
        return !NO_SESSION_METHODS.contains(service + "." + method);
    }

    /**
     * Returns the stored session id, opening a new session first when none is stored.
     *
     * @return the session id
     * @throws pl.automapa.mapservices.exception.AutomapaException when the API returns an HTTP error (mapped by
     *     {@link pl.automapa.mapservices.exception.ExceptionMapper}), the transport fails, or the response has no
     *     salt or session id ({@link UnknownResponseException})
     */
    public synchronized String ensureSession() {
        String sessionId = storage.get();
        if (sessionId != null) {
            return sessionId;
        }
        return openSession();
    }

    /** Forgets the stored session id, so the next {@link #ensureSession()} opens a new session. */
    public synchronized void clearSession() {
        storage.clear();
    }

    /**
     * Forgets the stored session id only when it is still {@code sessionId}, e.g. the session rejected by the API.
     *
     * <p>A session opened meanwhile by another thread is kept, so concurrent callers that saw the same rejected
     * session do not replace each other's fresh session.
     *
     * @param sessionId the session id that is no longer valid
     */
    public synchronized void clearSession(String sessionId) {
        String stored = storage.get();
        if (stored != null && stored.equals(sessionId)) {
            storage.clear();
        }
    }

    private String openSession() {
        String salt = resultText(call(GET_SALT, Map.of("key", config.getKey())), GET_SALT, "salt");
        String pass = PasswordHasher.hash(config.getPassword(), salt);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("key", config.getKey());
        params.put("pass", pass);
        String sessionId = resultText(call(GENERATE_SESSION, params), GENERATE_SESSION, "sessionId");
        storage.store(sessionId);
        return sessionId;
    }

    private ApiResponse call(String method, Map<String, Object> params) {
        return ResponseParser.parse(httpClient.send(requestBuilder.build(SERVICE, method, params, null)));
    }

    private static String resultText(ApiResponse response, String method, String field) {
        Object result = response.raw().get("result");
        Object value = result instanceof Map ? ((Map<?, ?>) result).get(field) : null;
        if (value instanceof String && !((String) value).isEmpty()) {
            return (String) value;
        }
        String problem;
        if (!(result instanceof Map) && result != null) {
            problem = "result is " + describe(result) + " instead of a JSON object";
        } else if (value == null) {
            problem = "the field is missing";
        } else if (value instanceof String) {
            problem = "the field is empty";
        } else {
            problem = "the field is " + describe(value) + " instead of a string";
        }
        throw new UnknownResponseException(
                "Unexpected " + SERVICE + "." + method + " response (HTTP " + response.status() + "): expected a"
                        + " non-empty string in result." + field + ", but " + problem + "; " + HINT,
                response.status(),
                null);
    }

    private static String describe(Object value) {
        if (value instanceof Map) {
            return "a JSON object";
        }
        if (value instanceof Iterable) {
            return "a JSON array";
        }
        if (value instanceof Number) {
            return "a JSON number";
        }
        if (value instanceof Boolean) {
            return "a JSON boolean";
        }
        return "a JSON string";
    }

    private static <T> T requireNonNull(T value, String name, String example) {
        if (value == null) {
            throw new IllegalArgumentException("SessionManager: " + name + " is required, e.g. " + example);
        }
        return value;
    }
}
