package pl.automapa.mapservices.exception;

/**
 * Thrown when the API returns a response the SDK cannot interpret: an unexpected HTTP status or a body that is not
 * valid JSON.
 */
public final class UnknownResponseException extends AutomapaException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception for an HTTP error response.
     *
     * @param message description of what went wrong and how to fix it
     * @param statusCode HTTP status code, or {@code 0} when there was no response
     * @param apiMessage error message sent by the API, or {@code null}
     * @param apiCode error code sent by the API, or {@code null}
     */
    public UnknownResponseException(String message, int statusCode, String apiMessage, String apiCode) {
        super(message, statusCode, apiMessage, apiCode);
    }

    /**
     * Creates the exception for a failure without an HTTP response, caused by another throwable.
     *
     * @param message description of what went wrong and how to fix it
     * @param cause the underlying problem
     */
    public UnknownResponseException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates the exception for an HTTP response whose body could not be interpreted, caused by another throwable.
     *
     * @param message description of what went wrong and how to fix it
     * @param statusCode HTTP status code of the response
     * @param cause the underlying problem, for example the JSON parsing error
     */
    public UnknownResponseException(String message, int statusCode, Throwable cause) {
        super(message, statusCode, cause);
    }
}
