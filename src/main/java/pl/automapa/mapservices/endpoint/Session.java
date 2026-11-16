package pl.automapa.mapservices.endpoint;

import java.util.Map;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.exception.UnknownResponseException;
import pl.automapa.mapservices.response.ApiResponse;
import pl.automapa.mapservices.session.PasswordHasher;

/**
 * Raw access to the {@code Session} API service.
 *
 * <p>Sessions are normally opened automatically by {@link ApiClient}. Use {@link ApiClient#openSession()} to open
 * one manually; use {@link #login(String, String)} when you want to call the endpoints yourself with a plain-text
 * password. Sessions returned by this endpoint are <em>not</em> stored in the client. None of these methods sends the
 * {@code Session-Id} header.
 *
 * <pre>{@code
 * String sessionId = (String) client.session().login(key, password).dataAsMap().get("sessionId");
 * }</pre>
 */
public final class Session {

    private static final String SERVICE = "Session";
    private static final String GET_SALT = "getSalt";
    private static final String GENERATE_SESSION = "generateSession";
    private static final String KEY_HINT = "pass the API key, e.g. config.getKey()";

    private final ApiClient client;

    /**
     * Creates the endpoint; usually obtained from {@link ApiClient#session()}.
     *
     * @param client client sending the requests
     * @throws IllegalArgumentException when {@code client} is {@code null}
     */
    public Session(ApiClient client) {
        this.client = EndpointParams.requireNonNull(client, "new Session", "client", "use client.session()");
    }

    /**
     * Fetches a one-time salt for {@code key}; the result contains {@code salt}.
     *
     * @param key API key
     * @return the response
     * @throws IllegalArgumentException when {@code key} is {@code null} or blank
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     */
    public ApiResponse getSalt(String key) {
        return getSalt(key, null);
    }

    /**
     * Fetches a one-time salt for {@code key} with additional parameters.
     *
     * @param key API key; overrides {@code key} in {@code extra}
     * @param extra additional parameters sent unchanged, or {@code null} for none
     * @return the response; the result contains {@code salt}
     * @throws IllegalArgumentException when {@code key} is {@code null} or blank
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     */
    public ApiResponse getSalt(String key, Map<String, ?> extra) {
        EndpointParams.requireText(key, "Session.getSalt", "key", KEY_HINT);
        return client.call(SERVICE, GET_SALT, EndpointParams.merge(extra, "key", key));
    }

    /**
     * Opens a session with an already hashed credential.
     *
     * <p>{@code pass} must be {@code md5(md5(password) + salt)} (see {@link PasswordHasher#hash(String, String)}),
     * where {@code salt} comes from a preceding {@link #getSalt(String)} call. Passing the plain-text password
     * results in an authentication error from the API. The returned session is not stored in the client.
     *
     * @param key API key
     * @param pass hashed password
     * @return the response; the result contains {@code sessionId}
     * @throws IllegalArgumentException when {@code key} or {@code pass} is {@code null} or blank
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     */
    public ApiResponse generateSession(String key, String pass) {
        return generateSession(key, pass, null);
    }

    /**
     * Opens a session with an already hashed credential and additional parameters.
     *
     * @param key API key; overrides {@code key} in {@code extra}
     * @param pass hashed password, see {@link #generateSession(String, String)}; overrides {@code pass} in
     *     {@code extra}
     * @param extra additional parameters sent unchanged, or {@code null} for none
     * @return the response; the result contains {@code sessionId}
     * @throws IllegalArgumentException when {@code key} or {@code pass} is {@code null} or blank
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     */
    public ApiResponse generateSession(String key, String pass, Map<String, ?> extra) {
        EndpointParams.requireText(key, "Session.generateSession", "key", KEY_HINT);
        EndpointParams.requireText(
                pass,
                "Session.generateSession",
                "pass",
                "pass PasswordHasher.hash(password, salt) with the salt from getSalt(key), or use login(key, password)");
        return client.call(SERVICE, GENERATE_SESSION, EndpointParams.merge(extra, "key", key, "pass", pass));
    }

    /**
     * Fetches a salt, hashes the plain-text {@code password} and calls {@link #generateSession(String, String)}.
     *
     * @param key API key
     * @param password plain-text password
     * @return the {@code generateSession} response; the result contains {@code sessionId}
     * @throws IllegalArgumentException when {@code key} or {@code password} is {@code null} or blank
     * @throws UnknownResponseException when the {@code getSalt} response has no salt
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     */
    public ApiResponse login(String key, String password) {
        return login(key, password, null);
    }

    /**
     * Fetches a salt, hashes the plain-text {@code password} and calls {@code generateSession} with additional
     * parameters; the {@code getSalt} request sends the key only.
     *
     * @param key API key
     * @param password plain-text password
     * @param extra additional parameters of the {@code generateSession} request, or {@code null} for none
     * @return the {@code generateSession} response; the result contains {@code sessionId}
     * @throws IllegalArgumentException when {@code key} or {@code password} is {@code null} or blank
     * @throws UnknownResponseException when the {@code getSalt} response has no salt
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     */
    public ApiResponse login(String key, String password, Map<String, ?> extra) {
        EndpointParams.requireText(key, "Session.login", "key", KEY_HINT);
        EndpointParams.requireText(password, "Session.login", "password", "pass the plain-text API password");
        ApiResponse saltResponse = getSalt(key);
        String salt = saltOf(saltResponse);
        return generateSession(key, PasswordHasher.hash(password, salt), extra);
    }

    private static String saltOf(ApiResponse response) {
        Object result = response.raw().get("result");
        Object salt = result instanceof Map ? ((Map<?, ?>) result).get("salt") : null;
        if (salt instanceof String && !((String) salt).isEmpty()) {
            return (String) salt;
        }
        throw new UnknownResponseException(
                "Unexpected " + SERVICE + "." + GET_SALT + " response (HTTP " + response.status()
                        + "): expected a non-empty string in result.salt; check the API key and the base URL,"
                        + " or report the response to Automapa support",
                response.status(),
                null);
    }
}
