package pl.automapa.mapservices;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import pl.automapa.mapservices.endpoint.Autocomplete;
import pl.automapa.mapservices.endpoint.Geocoder;
import pl.automapa.mapservices.endpoint.PingPong;
import pl.automapa.mapservices.endpoint.Road;
import pl.automapa.mapservices.endpoint.RoadPermit;
import pl.automapa.mapservices.endpoint.Routes;
import pl.automapa.mapservices.endpoint.Session;
import pl.automapa.mapservices.formatter.FormatterRegistry;
import pl.automapa.mapservices.formatter.GoogleFormatter;
import pl.automapa.mapservices.formatter.NativeFormatter;
import pl.automapa.mapservices.http.HttpClient;
import pl.automapa.mapservices.http.HttpRequest;
import pl.automapa.mapservices.http.HttpResponse;
import pl.automapa.mapservices.http.JdkHttpClient;
import pl.automapa.mapservices.http.RequestBuilder;
import pl.automapa.mapservices.json.Json;
import pl.automapa.mapservices.json.JsonException;
import pl.automapa.mapservices.request.RoadPermitEnvironment;
import pl.automapa.mapservices.response.ApiResponse;
import pl.automapa.mapservices.response.ResponseParser;
import pl.automapa.mapservices.session.InMemorySessionStorage;
import pl.automapa.mapservices.session.SessionManager;

/**
 * Entry point of the Automapa Map Services SDK: a client of the REST API {@code {baseUrl}/{version}/{Service}/{method}}.
 *
 * <pre>{@code
 * ApiClient client = ApiClient.create(Config.fromEnvironment());
 * ApiResponse pong = client.call("PingPong", "ping", Map.of("msg", "hello"));
 * }</pre>
 *
 * <p>The client opens the API session lazily on the first protected call (every method except
 * {@code Session.getSalt} and {@code Session.generateSession}) and renews it once after an HTTP 403 response, unless
 * the API says that the key has no permission for the service. The
 * optional {@code format} parameter selects a response formatter registered in the {@link FormatterRegistry}; it is
 * never sent to the API.
 *
 * <p>Instances are thread safe: concurrent calls on a fresh client open exactly one session.
 */
public final class ApiClient {

    private static final String SDK_PROPERTIES = "sdk.properties";
    private static final String FORMAT_PARAM = "format";
    private static final int FORBIDDEN = 403;
    private static final String GOOGLE_FORMAT = "google";
    private static final String NO_PERMISSION = "no permission";

    /** Version of this SDK, taken from the Maven project version at build time. */
    public static final String SDK_VERSION = loadSdkVersion();

    private final Config config;
    private final HttpClient httpClient;
    private final RequestBuilder requestBuilder;
    private final SessionManager sessionManager;
    private final FormatterRegistry formatterRegistry;
    private final Session session = new Session(this);
    private final PingPong pingPong = new PingPong(this);
    private final Geocoder geocoder = new Geocoder(this);
    private final Autocomplete autocomplete = new Autocomplete(this);
    private final Routes routes = new Routes(this);
    private final Road road = new Road(this);
    private final Map<RoadPermitEnvironment, RoadPermit> roadPermits = createRoadPermits();

    /**
     * Creates a client with the default session manager (in-memory storage) and the default formatters.
     *
     * @param config client configuration
     * @param httpClient HTTP transport, e.g. {@code new JdkHttpClient(config.getTimeout())}
     * @throws IllegalArgumentException when any argument is {@code null}
     */
    public ApiClient(Config config, HttpClient httpClient) {
        this(config, httpClient, null, null);
    }

    /**
     * Creates a client.
     *
     * @param config client configuration
     * @param httpClient HTTP transport, e.g. {@code new JdkHttpClient(config.getTimeout())}
     * @param sessionManager session manager, or {@code null} for one using {@code httpClient} and an
     *     {@link InMemorySessionStorage}
     * @param formatterRegistry response formatters, or {@code null} for the default registry with {@code native}
     *     and {@code google} ({@link GoogleFormatter})
     * @throws IllegalArgumentException when {@code config} or {@code httpClient} is {@code null}
     */
    public ApiClient(
            Config config, HttpClient httpClient, SessionManager sessionManager, FormatterRegistry formatterRegistry) {
        this.config = requireNonNull(config, "config", "Config.builder(key, password).build()");
        this.httpClient = requireNonNull(
                httpClient, "httpClient", "new JdkHttpClient(config.getTimeout()) or use ApiClient.create(config)");
        this.requestBuilder = new RequestBuilder(config.getBaseUrl(), config.getVersion(), config.getUserAgent());
        this.sessionManager = sessionManager != null
                ? sessionManager
                : new SessionManager(config, httpClient, new InMemorySessionStorage());
        this.formatterRegistry = formatterRegistry != null ? formatterRegistry : defaultFormatterRegistry();
    }

    /**
     * Creates a client that talks to the API over {@link JdkHttpClient} with the timeout from the configuration.
     *
     * @param config client configuration, e.g. {@code Config.fromEnvironment()}
     * @return the client
     * @throws IllegalArgumentException when {@code config} is {@code null}
     */
    public static ApiClient create(Config config) {
        requireNonNull(config, "config", "Config.builder(key, password).build() or Config.fromEnvironment()");
        return new ApiClient(config, new JdkHttpClient(config.getTimeout()));
    }

    /**
     * Calls an API method without parameters.
     *
     * @param service API service name, e.g. {@code PingPong}
     * @param method API method name, e.g. {@code ping}
     * @return the parsed response
     * @throws IllegalArgumentException when {@code service} or {@code method} is {@code null} or blank
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     * @see #call(String, String, Map)
     */
    public ApiResponse call(String service, String method) {
        return call(service, method, null);
    }

    /**
     * Calls an API method with {@code POST {baseUrl}/{version}/{service}/{method}}.
     *
     * <p>All parameters are sent 1:1 as the JSON body, unknown keys included. The {@code format} key is removed and
     * selects the response format ({@code Config.getDefaultFormat()} when missing or {@code null}); a format other
     * than {@code native} that is registered in the {@link FormatterRegistry} fills {@link ApiResponse#data()} with
     * the formatter output, an unregistered one is ignored. The {@code Session-Id} header is added for every method
     * that requires a session; after an HTTP 403 response the session is renewed and the request is sent once more,
     * except when the response {@code message} or {@code error} says {@code no permission} (a new session cannot
     * grant access to a service outside the key's plan). Only the rejected session is forgotten: a session opened
     * meanwhile by another thread is reused. The caller's map is never modified.
     *
     * @param service API service name, e.g. {@code Geocoder}
     * @param method API method name, e.g. {@code geocode}
     * @param params request parameters ({@code null} means none)
     * @return the parsed response with the meta data {@code service} and {@code method}
     * @throws IllegalArgumentException when {@code service} or {@code method} is {@code null} or blank
     * @throws pl.automapa.mapservices.exception.SessionException when the API key has no permission for the service
     *     or the retry after HTTP 403 is also rejected
     * @throws pl.automapa.mapservices.exception.AutomapaException on another HTTP, API or transport error
     * @throws pl.automapa.mapservices.json.JsonException when a parameter value cannot be written as JSON
     */
    public ApiResponse call(String service, String method, Map<String, ?> params) {
        requireText(service, "ApiClient.call", "service", "Geocoder");
        requireText(method, "ApiClient.call", "method", "geocode");
        Map<String, Object> body = params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params);
        Object requestedFormat = body.remove(FORMAT_PARAM);
        String format = requestedFormat == null ? config.getDefaultFormat() : String.valueOf(requestedFormat);

        String sessionId = sessionManager.requiresSession(service, method) ? sessionManager.ensureSession() : null;
        HttpResponse httpResponse = httpClient.send(requestBuilder.build(service, method, body, sessionId));
        if (httpResponse.statusCode() == FORBIDDEN && sessionId != null && !isPermissionDenied(httpResponse)) {
            sessionManager.clearSession(sessionId);
            sessionId = sessionManager.ensureSession();
            httpResponse = httpClient.send(requestBuilder.build(service, method, body, sessionId));
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("service", service);
        meta.put("method", method);
        ApiResponse response = ResponseParser.parse(httpResponse, meta);

        if (!Config.DEFAULT_FORMAT.equals(format) && formatterRegistry.has(format)) {
            Object formatted = formatterRegistry.get(format).format(response.raw(), service + "." + method);
            return new ApiResponse(response.raw(), response.status(), response.meta(), formatted);
        }
        return response;
    }

    /**
     * Opens the session now instead of lazily on the first protected call.
     *
     * <p>Fetches a salt, hashes the configured password as {@code md5(md5(password) + salt)}, calls
     * {@code Session.generateSession} and keeps the returned id for later calls. Returns the kept id when a session
     * is already open.
     *
     * @return the session id
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     */
    public String openSession() {
        return sessionManager.ensureSession();
    }

    /**
     * Sends a low-level request without parameters.
     *
     * @param httpMethod HTTP method, e.g. {@code POST}
     * @param path path appended to the base URL, e.g. {@code /v3/PingPong/ping}
     * @return the parsed response
     * @throws IllegalArgumentException when {@code httpMethod} or {@code path} is {@code null} or blank
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     * @see #request(String, String, Map)
     */
    public ApiResponse request(String httpMethod, String path) {
        return request(httpMethod, path, null);
    }

    /**
     * Sends a low-level request to {@code baseUrl + "/" + path} (slashes between them collapsed to one), bypassing
     * the service/method routing.
     *
     * <p>No session is opened or sent and no formatter is applied; the {@code format} key, if any, is sent as is.
     *
     * @param httpMethod HTTP method, e.g. {@code POST}
     * @param path path appended to the base URL, e.g. {@code /v3/PingPong/ping}
     * @param params request parameters written as the JSON body ({@code null} means none)
     * @return the parsed response with the meta data {@code path}
     * @throws IllegalArgumentException when {@code httpMethod} or {@code path} is {@code null} or blank
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     * @throws pl.automapa.mapservices.json.JsonException when a parameter value cannot be written as JSON
     */
    public ApiResponse request(String httpMethod, String path, Map<String, ?> params) {
        requireText(httpMethod, "ApiClient.request", "httpMethod", "POST");
        requireText(path, "ApiClient.request", "path", "/v3/PingPong/ping");
        String url = trimTrailingSlashes(config.getBaseUrl()) + "/" + trimLeadingSlashes(path);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("User-Agent", config.getUserAgent());
        String body = Json.stringify(params == null ? Map.of() : params);

        HttpResponse httpResponse = httpClient.send(new HttpRequest(httpMethod, url, headers, body));

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("path", path);
        return ResponseParser.parse(httpResponse, meta);
    }

    /**
     * Returns the {@code Session} endpoint: salts, sessions and logging in with a plain-text password.
     *
     * @return the endpoint bound to this client (the same instance on every call)
     */
    public Session session() {
        return session;
    }

    /**
     * Returns the {@code PingPong} endpoint: a connectivity and session check.
     *
     * @return the endpoint bound to this client (the same instance on every call)
     */
    public PingPong pingPong() {
        return pingPong;
    }

    /**
     * Returns the {@code Geocoder} endpoint: addresses to coordinates ({@code geocode}, {@code geocodemulti}) and
     * points to addresses ({@code revgeocode}, {@code revgeocodemulti}).
     *
     * @return the endpoint bound to this client (the same instance on every call)
     */
    public Geocoder geocoder() {
        return geocoder;
    }

    /**
     * Returns the {@code Autocomplete} endpoint: suggestions while typing ({@code search}, {@code searchAddress})
     * and the postal codes of a street ({@code getPostalCodesForStreet}).
     *
     * @return the endpoint bound to this client (the same instance on every call)
     */
    public Autocomplete autocomplete() {
        return autocomplete;
    }

    /**
     * Returns the {@code Routes} endpoint: routes ({@code route}) and distance matrices ({@code matrix}).
     *
     * @return the endpoint bound to this client (the same instance on every call)
     */
    public Routes routes() {
        return routes;
    }

    /**
     * Returns the {@code Road} endpoint: the nearest road segment ({@code getSegmentInfo}) and speed limits
     * ({@code speedCheck}, {@code speedCheckMulti}).
     *
     * @return the endpoint bound to this client (the same instance on every call)
     */
    public Road road() {
        return road;
    }

    /**
     * Returns the {@code RoadPermit} endpoint of the <strong>production</strong> environment, like the PHP SDK.
     * Changes made through it ({@code add}, {@code overwrite}, {@code remove}) affect production documents; use
     * {@link #roadPermit(RoadPermitEnvironment)} with {@link RoadPermitEnvironment#UAT} for development and tests.
     *
     * @return the endpoint of {@link RoadPermitEnvironment#PROD} (the same instance on every call)
     */
    public RoadPermit roadPermit() {
        return roadPermit(RoadPermitEnvironment.PROD);
    }

    /**
     * Returns the {@code RoadPermit} endpoint of an environment: road permit documents ({@code list}, {@code add},
     * {@code overwrite}, {@code remove}).
     *
     * @param environment environment of the documents, e.g. {@link RoadPermitEnvironment#UAT}
     * @return the endpoint bound to this client (the same instance on every call with the same environment)
     * @throws IllegalArgumentException when {@code environment} is {@code null}
     */
    public RoadPermit roadPermit(RoadPermitEnvironment environment) {
        requireNonNull(
                environment,
                "environment",
                "client.roadPermit(RoadPermitEnvironment.UAT) for tests or RoadPermitEnvironment.PROD for production");
        return roadPermits.get(environment);
    }

    /**
     * Returns the configuration of this client.
     *
     * @return the configuration given to the constructor
     */
    public Config getConfig() {
        return config;
    }

    /** Returns the HTTP transport; for tests of {@link #create(Config)}. */
    HttpClient httpClient() {
        return httpClient;
    }

    /**
     * Tells whether an HTTP 403 response means that the API key has no permission for the service, which a new
     * session cannot fix, rather than an expired or invalid session.
     */
    private static boolean isPermissionDenied(HttpResponse response) {
        Object body;
        try {
            body = Json.parse(response.body());
        } catch (JsonException e) {
            return false;
        }
        if (!(body instanceof Map)) {
            return false;
        }
        Map<?, ?> fields = (Map<?, ?>) body;
        return mentionsNoPermission(fields.get("message")) || mentionsNoPermission(fields.get("error"));
    }

    private static boolean mentionsNoPermission(Object text) {
        return text instanceof String
                && ((String) text).toLowerCase(Locale.ROOT).contains(NO_PERMISSION);
    }

    private static FormatterRegistry defaultFormatterRegistry() {
        FormatterRegistry registry = new FormatterRegistry();
        registry.register(Config.DEFAULT_FORMAT, new NativeFormatter());
        registry.register(GOOGLE_FORMAT, new GoogleFormatter());
        return registry;
    }

    private Map<RoadPermitEnvironment, RoadPermit> createRoadPermits() {
        Map<RoadPermitEnvironment, RoadPermit> endpoints = new EnumMap<>(RoadPermitEnvironment.class);
        for (RoadPermitEnvironment environment : RoadPermitEnvironment.values()) {
            endpoints.put(environment, new RoadPermit(this, environment));
        }
        return endpoints;
    }

    private static <T> T requireNonNull(T value, String name, String example) {
        if (value == null) {
            throw new IllegalArgumentException("ApiClient: " + name + " is required, e.g. " + example);
        }
        return value;
    }

    private static void requireText(String value, String operation, String name, String example) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    operation + ": " + name + " is required, e.g. " + example + " (got " + value + ")");
        }
    }

    private static String trimTrailingSlashes(String text) {
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == '/') {
            end--;
        }
        return text.substring(0, end);
    }

    private static String trimLeadingSlashes(String text) {
        int start = 0;
        while (start < text.length() && text.charAt(start) == '/') {
            start++;
        }
        return text.substring(start);
    }

    private static String loadSdkVersion() {
        return readSdkVersion(ApiClient.class.getResourceAsStream(SDK_PROPERTIES));
    }

    /**
     * Reads the SDK version from the content of {@code sdk.properties} and closes the stream.
     *
     * @param input stream with the properties, or {@code null} when the resource is missing
     * @return the resolved, trimmed version
     * @throws IllegalStateException when the resource is missing or the version was not filtered by Maven
     * @throws java.io.UncheckedIOException when the resource cannot be read
     */
    static String readSdkVersion(InputStream input) {
        if (input == null) {
            throw new IllegalStateException("SDK resource pl/automapa/mapservices/sdk.properties is missing;"
                    + " rebuild the jar with Maven so that resources are filtered and packaged");
        }
        try (input) {
            Properties properties = new Properties();
            properties.load(input);
            String version = properties.getProperty("version");
            if (version == null || version.isBlank() || version.contains("${")) {
                throw new IllegalStateException("SDK resource " + SDK_PROPERTIES
                        + " has no resolved 'version'; rebuild with Maven resource filtering enabled");
            }
            return version.trim();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read SDK resource " + SDK_PROPERTIES + ": " + e.getMessage(), e);
        }
    }
}
