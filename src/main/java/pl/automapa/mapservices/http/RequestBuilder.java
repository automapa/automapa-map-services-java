package pl.automapa.mapservices.http;

import java.util.LinkedHashMap;
import java.util.Map;
import pl.automapa.mapservices.json.Json;

/**
 * Builds {@link HttpRequest} objects for the Automapa REST API.
 *
 * <ul>
 *   <li>URL: {@code {baseUrl without trailing slashes}/{version}/{Service}/{method}},
 *   <li>method: {@code POST},
 *   <li>headers: {@code Content-Type: application/json}, {@code User-Agent} and {@code Session-Id} (only when a
 *       session id is given),
 *   <li>body: the parameters written as a JSON object, passed 1:1 without an envelope ({@code {}} when empty).
 * </ul>
 *
 * <p>The class is immutable and thread safe.
 */
public final class RequestBuilder {

    private static final String HTTP_METHOD = "POST";

    private final String baseUrl;
    private final String version;
    private final String userAgent;
    private final String urlPrefix;

    /**
     * Creates a builder.
     *
     * @param baseUrl API base URL, e.g. {@code https://api.automapa.pl}; trailing slashes are ignored
     * @param version API version path segment, e.g. {@code v3}
     * @param userAgent value of the {@code User-Agent} header
     * @throws IllegalArgumentException when any argument is {@code null} or blank
     */
    public RequestBuilder(String baseUrl, String version, String userAgent) {
        this.baseUrl = requireText(baseUrl, "baseUrl", "e.g. https://api.automapa.pl");
        this.version = requireText(version, "version", "e.g. v3");
        this.userAgent = requireText(userAgent, "userAgent", "e.g. automapa-java-sdk/1.0.0");
        this.urlPrefix = trimTrailingSlashes(baseUrl) + "/" + version + "/";
    }

    /**
     * Builds a request without parameters and without a session.
     *
     * @param service API service name, e.g. {@code PingPong}
     * @param method API method name, e.g. {@code ping}
     * @return the request
     */
    public HttpRequest build(String service, String method) {
        return build(service, method, Map.of(), null);
    }

    /**
     * Builds a request.
     *
     * @param service API service name, e.g. {@code Geocoder}
     * @param method API method name, e.g. {@code geocode}
     * @param params request parameters written as the JSON body; {@code null} means no parameters
     * @param sessionId session id sent in the {@code Session-Id} header, or {@code null} to omit the header
     * @return the request
     * @throws IllegalArgumentException when {@code service} or {@code method} is {@code null} or blank
     * @throws pl.automapa.mapservices.json.JsonException when a parameter value cannot be written as JSON
     */
    public HttpRequest build(String service, String method, Map<String, ?> params, String sessionId) {
        requireText(service, "service", "e.g. Geocoder");
        requireText(method, "method", "e.g. geocode");

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("User-Agent", userAgent);
        if (sessionId != null) {
            headers.put("Session-Id", sessionId);
        }
        String body = Json.stringify(params == null ? Map.of() : params);
        return new HttpRequest(HTTP_METHOD, urlPrefix + service + "/" + method, headers, body);
    }

    /**
     * Returns the base URL as given to the constructor.
     *
     * @return the base URL
     */
    public String baseUrl() {
        return baseUrl;
    }

    /**
     * Returns the API version path segment.
     *
     * @return the version, e.g. {@code v3}
     */
    public String version() {
        return version;
    }

    /**
     * Returns the value of the {@code User-Agent} header.
     *
     * @return the user agent
     */
    public String userAgent() {
        return userAgent;
    }

    private static String trimTrailingSlashes(String url) {
        int end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') {
            end--;
        }
        return url.substring(0, end);
    }

    private static String requireText(String value, String name, String example) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "RequestBuilder: " + name + " is required (" + example + "), got " + value);
        }
        return value;
    }
}
