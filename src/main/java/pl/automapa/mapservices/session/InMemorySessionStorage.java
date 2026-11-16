package pl.automapa.mapservices.session;

/** Thread-safe {@link SessionStorage} that keeps the session id in memory of the current JVM. */
public final class InMemorySessionStorage implements SessionStorage {

    private volatile String sessionId;

    /** Creates an empty storage. */
    public InMemorySessionStorage() {}

    @Override
    public String get() {
        return sessionId;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException when the session id is {@code null} or empty
     */
    @Override
    public void store(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            throw new IllegalArgumentException("InMemorySessionStorage: session id must not be "
                    + (sessionId == null ? "null" : "empty") + "; call clear() to remove the session");
        }
        this.sessionId = sessionId;
    }

    @Override
    public void clear() {
        sessionId = null;
    }
}
