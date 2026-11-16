package pl.automapa.mapservices.request;

/** What {@code Routes.optimizeQueue} minimises; sent as the text {@code optimizeBy}. */
public enum OptimizeBy {

    /** Total travel time ({@code optimizeBy = "time"}). */
    TIME("time"),

    /** Total route length ({@code optimizeBy = "distance"}). */
    DISTANCE("distance");

    private final String apiValue;

    OptimizeBy(String apiValue) {
        this.apiValue = apiValue;
    }

    /**
     * Returns the value sent to the API.
     *
     * @return {@code "time"} for {@link #TIME}, {@code "distance"} for {@link #DISTANCE}
     */
    public String apiValue() {
        return apiValue;
    }
}
