package pl.automapa.mapservices.exception;

/**
 * Thrown when the session is expired or invalid, or the API key has no permission for the service (HTTP 403).
 */
public final class SessionException extends AutomapaException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception for an HTTP error response.
     *
     * @param message description of what went wrong and how to fix it
     * @param statusCode HTTP status code, or {@code 0} when there was no response
     * @param apiMessage error message sent by the API, or {@code null}
     * @param apiCode error code sent by the API, or {@code null}
     */
    public SessionException(String message, int statusCode, String apiMessage, String apiCode) {
        super(message, statusCode, apiMessage, apiCode);
    }

    /**
     * Creates the exception for a failure without an HTTP response, caused by another throwable.
     *
     * @param message description of what went wrong and how to fix it
     * @param cause the underlying problem
     */
    public SessionException(String message, Throwable cause) {
        super(message, cause);
    }
}
