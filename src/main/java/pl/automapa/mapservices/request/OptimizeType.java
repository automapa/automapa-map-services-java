package pl.automapa.mapservices.request;

/** What {@code Routes.optimize} minimises; sent as the integer {@code type}. */
public enum OptimizeType {

    /** Total travel time ({@code type = 1}). */
    TIME(1),

    /** Total route length ({@code type = 2}). */
    DISTANCE(2);

    private final int apiValue;

    OptimizeType(int apiValue) {
        this.apiValue = apiValue;
    }

    /**
     * Returns the value sent to the API.
     *
     * @return {@code 1} for {@link #TIME}, {@code 2} for {@link #DISTANCE}
     */
    public int apiValue() {
        return apiValue;
    }
}
