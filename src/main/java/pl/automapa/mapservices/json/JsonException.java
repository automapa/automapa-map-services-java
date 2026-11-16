package pl.automapa.mapservices.json;

/**
 * Thrown when JSON text cannot be parsed or a Java value cannot be written as JSON.
 *
 * <p>Parse errors carry the character offset in the input at which the problem was detected; errors raised while
 * writing JSON have no offset ({@link #getOffset()} returns {@code -1}).
 */
public class JsonException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Character offset of a parse error, or {@code -1} when not applicable. */
    private final int offset;

    /**
     * Creates an exception for a problem that is not tied to a position in JSON text.
     *
     * @param message description of what went wrong and how to fix it
     */
    public JsonException(String message) {
        this(message, -1);
    }

    /**
     * Creates an exception for a problem found at a given position in JSON text.
     *
     * @param message description of what went wrong and how to fix it
     * @param offset zero-based character offset in the input, or {@code -1} when not applicable
     */
    public JsonException(String message, int offset) {
        super(message);
        this.offset = offset;
    }

    /**
     * Returns the zero-based character offset in the parsed text at which the error was detected.
     *
     * @return the offset, or {@code -1} when the error is not a parse error
     */
    public int getOffset() {
        return offset;
    }
}
