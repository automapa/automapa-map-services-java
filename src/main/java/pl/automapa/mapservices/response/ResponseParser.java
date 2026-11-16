package pl.automapa.mapservices.response;

import java.util.Map;
import pl.automapa.mapservices.exception.ExceptionMapper;
import pl.automapa.mapservices.exception.UnknownResponseException;
import pl.automapa.mapservices.http.HttpResponse;
import pl.automapa.mapservices.json.Json;
import pl.automapa.mapservices.json.JsonException;

/**
 * Turns an {@link HttpResponse} of the Automapa API into an {@link ApiResponse}, or throws the matching
 * {@link pl.automapa.mapservices.exception.AutomapaException}.
 *
 * <p>Order of checks: a body that is not valid JSON and a JSON value that is not an object raise
 * {@link UnknownResponseException} (whatever the status); a non-2xx status raises the exception chosen by
 * {@link ExceptionMapper}; a 2xx status returns an {@code ApiResponse}.
 */
public final class ResponseParser {

    /** Maximum number of body characters quoted in an error message. */
    static final int SNIPPET_LENGTH = 200;

    private static final String HINT =
            "expected a JSON object from the Automapa API; check that baseUrl and version point to the API"
                    + " (e.g. https://api.automapa.pl/ and v3), or retry later if the server is failing";

    private ResponseParser() {}

    /**
     * Parses an HTTP response without request metadata.
     *
     * @param response HTTP response received from the API
     * @return the parsed successful response
     * @throws UnknownResponseException when the body is not a JSON object
     * @throws pl.automapa.mapservices.exception.AutomapaException a subclass matching the status for non-2xx responses
     */
    public static ApiResponse parse(HttpResponse response) {
        return parse(response, null);
    }

    /**
     * Parses an HTTP response.
     *
     * @param response HTTP response received from the API
     * @param meta request metadata stored in the result, e.g. {@code service} and {@code method} ({@code null} means
     *     none)
     * @return the parsed successful response
     * @throws UnknownResponseException when the body is not a JSON object
     * @throws pl.automapa.mapservices.exception.AutomapaException a subclass matching the status for non-2xx responses
     */
    @SuppressWarnings("unchecked")
    public static ApiResponse parse(HttpResponse response, Map<String, Object> meta) {
        if (response == null) {
            throw new IllegalArgumentException(
                    "HttpResponse must not be null; pass the response returned by HttpClient.send(request).");
        }
        int status = response.statusCode();
        String body = response.body();
        Object decoded;
        try {
            decoded = Json.parse(body);
        } catch (JsonException e) {
            throw new UnknownResponseException(
                    "Non-JSON response (HTTP " + status + "): " + snippet(body) + " (" + HINT + ")", status, e);
        }
        if (!(decoded instanceof Map)) {
            throw new UnknownResponseException(
                    "Unexpected JSON response (HTTP " + status + "): expected a JSON object, got a "
                            + ApiResponse.describe(decoded) + ": " + snippet(body) + " (" + HINT + ")",
                    status,
                    null,
                    null);
        }
        Map<String, Object> object = (Map<String, Object>) decoded;
        if (!response.isSuccess()) {
            throw ExceptionMapper.fromHttpResponse(status, object);
        }
        return new ApiResponse(object, status, meta);
    }

    /**
     * Returns the beginning of the body for an error message: at most {@value #SNIPPET_LENGTH} characters followed by
     * {@code ...} when cut, never splitting a surrogate pair.
     */
    private static String snippet(String body) {
        if (body.isEmpty()) {
            return "<empty body>";
        }
        if (body.length() <= SNIPPET_LENGTH) {
            return body;
        }
        int end = SNIPPET_LENGTH;
        if (Character.isHighSurrogate(body.charAt(end - 1))) {
            end--;
        }
        return body.substring(0, end) + "...";
    }
}
