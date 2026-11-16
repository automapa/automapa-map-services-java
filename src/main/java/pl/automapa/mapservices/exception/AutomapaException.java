package pl.automapa.mapservices.exception;

/**
 * Base class of every exception thrown by the SDK when a call to the Automapa API fails.
 *
 * <p>Catch this type to handle all SDK errors in one place, or one of its subclasses to react to a specific
 * failure. When the failure comes from an HTTP error response, {@link #getStatusCode()} returns the HTTP status and
 * {@link #getApiMessage()} / {@link #getApiCode()} return the {@code message} (or {@code error}) and {@code code}
 * fields sent by the API. For failures without an HTTP response (for example network errors) the status is
 * {@code 0} and both API fields are {@code null}.
 */
public class AutomapaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** HTTP status code of the error response, or {@code 0} when there was no response. */
    private final int statusCode;

    /** Error message sent by the API, or {@code null}. */
    private final String apiMessage;

    /** Error code sent by the API, or {@code null}. */
    private final String apiCode;

    /**
     * Creates an exception that is not tied to an HTTP response.
     *
     * @param message description of what went wrong and how to fix it
     */
    public AutomapaException(String message) {
        this(message, 0, null, null, null);
    }

    /**
     * Creates an exception that is not tied to an HTTP response, caused by another throwable.
     *
     * @param message description of what went wrong and how to fix it
     * @param cause the underlying problem
     */
    public AutomapaException(String message, Throwable cause) {
        this(message, 0, null, null, cause);
    }

    /**
     * Creates an exception for an HTTP error response.
     *
     * @param message description of what went wrong and how to fix it
     * @param statusCode HTTP status code, or {@code 0} when there was no response
     * @param apiMessage error message sent by the API, or {@code null}
     * @param apiCode error code sent by the API, or {@code null}
     */
    public AutomapaException(String message, int statusCode, String apiMessage, String apiCode) {
        this(message, statusCode, apiMessage, apiCode, null);
    }

    /**
     * Creates an exception for an HTTP response that could not be interpreted, caused by another throwable.
     *
     * @param message description of what went wrong and how to fix it
     * @param statusCode HTTP status code, or {@code 0} when there was no response
     * @param cause the underlying problem
     */
    public AutomapaException(String message, int statusCode, Throwable cause) {
        this(message, statusCode, null, null, cause);
    }

    private AutomapaException(String message, int statusCode, String apiMessage, String apiCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.apiMessage = apiMessage;
        this.apiCode = apiCode;
    }

    /**
     * Returns the HTTP status code of the error response.
     *
     * @return the HTTP status code, or {@code 0} when the failure did not come from an HTTP response
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Returns the error message sent by the API ({@code message} field, or {@code error} when {@code message} is
     * missing or empty).
     *
     * @return the API error message, or {@code null} when the API did not send one
     */
    public String getApiMessage() {
        return apiMessage;
    }

    /**
     * Returns the error code sent by the API ({@code code} field).
     *
     * @return the API error code, or {@code null} when the API did not send one
     */
    public String getApiCode() {
        return apiCode;
    }
}
