package pl.automapa.mapservices.session;

/**
 * Keeps the id of the current API session.
 *
 * <p>Implementations used by a shared {@link SessionManager} must be thread safe.
 */
public interface SessionStorage {

    /**
     * Returns the stored session id.
     *
     * @return the session id, or {@code null} when no session is stored
     */
    String get();

    /**
     * Stores a session id, replacing the previous one.
     *
     * @param sessionId non-empty session id returned by {@code Session.generateSession}
     */
    void store(String sessionId);

    /** Removes the stored session id. */
    void clear();
}
