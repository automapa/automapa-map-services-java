package pl.automapa.mapservices.exception;

import java.util.Map;

/**
 * Turns an HTTP error response of the Automapa API into the matching {@link AutomapaException} subclass.
 *
 * <p>Status mapping: 400 {@link ValidationException}, 401 {@link AuthenticationException}, 403
 * {@link SessionException}, 404 {@link NotFoundException}, 429 {@link RateLimitException}, 500 and above
 * {@link ServerException}, anything else {@link UnknownResponseException}.
 *
 * <p>The exception message has the form {@code <API message> [code: <API code>] (HTTP <status>)}, where the API
 * message comes from the {@code message} field (or {@code error} when {@code message} is missing or empty) and the
 * code suffix appears only when {@code code} is not empty. Without an API message the message starts with
 * {@code HTTP <status>}. For 401, 403, 429 and 5xx a hint on how to fix or work around the problem is appended.
 */
public final class ExceptionMapper {

    private static final String HINT_AUTHENTICATION = "Check AUTOMAPA_API_KEY and AUTOMAPA_API_PASSWORD.";
    private static final String HINT_SESSION = "Session expired or the API key has no permission for this service.";
    private static final String HINT_RATE_LIMIT = "Rate limit exceeded; retry later.";
    private static final String HINT_SERVER = "Automapa API server error; retry later.";

    private ExceptionMapper() {}

    /**
     * Creates the exception describing an HTTP error response.
     *
     * @param statusCode HTTP status code of the response
     * @param body decoded JSON body of the response; {@code null} or an empty map when the body had no fields
     * @return the exception matching the status code (never {@code null}); the caller decides whether to throw it
     */
    public static AutomapaException fromHttpResponse(int statusCode, Map<String, Object> body) {
        Map<String, Object> fields = body == null ? Map.of() : body;
        String apiMessage = text(fields.get("message"));
        if (apiMessage == null) {
            apiMessage = text(fields.get("error"));
        }
        String apiCode = text(fields.get("code"));
        String message = message(statusCode, apiMessage, apiCode);

        if (statusCode == 400) {
            return new ValidationException(message, statusCode, apiMessage, apiCode);
        }
        if (statusCode == 401) {
            return new AuthenticationException(message, statusCode, apiMessage, apiCode);
        }
        if (statusCode == 403) {
            return new SessionException(message, statusCode, apiMessage, apiCode);
        }
        if (statusCode == 404) {
            return new NotFoundException(message, statusCode, apiMessage, apiCode);
        }
        if (statusCode == 429) {
            return new RateLimitException(message, statusCode, apiMessage, apiCode);
        }
        if (statusCode >= 500) {
            return new ServerException(message, statusCode, apiMessage, apiCode);
        }
        return new UnknownResponseException(message, statusCode, apiMessage, apiCode);
    }

    private static String message(int statusCode, String apiMessage, String apiCode) {
        StringBuilder message = new StringBuilder(apiMessage == null ? "HTTP " + statusCode : apiMessage);
        if (apiCode != null) {
            message.append(" [code: ").append(apiCode).append(']');
        }
        if (apiMessage != null) {
            message.append(" (HTTP ").append(statusCode).append(')');
        }
        String hint = hint(statusCode);
        if (hint != null) {
            message.append(". ").append(hint);
        }
        return message.toString();
    }

    private static String hint(int statusCode) {
        if (statusCode == 401) {
            return HINT_AUTHENTICATION;
        }
        if (statusCode == 403) {
            return HINT_SESSION;
        }
        if (statusCode == 429) {
            return HINT_RATE_LIMIT;
        }
        if (statusCode >= 500) {
            return HINT_SERVER;
        }
        return null;
    }

    /** Returns the field as text, or {@code null} when it is missing, {@code null} or blank. */
    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }
}
