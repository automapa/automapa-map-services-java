package pl.automapa.mapservices.request;

/** Environment of the {@code RoadPermit} service; sent as the text {@code environment}. */
public enum RoadPermitEnvironment {

    /** Production documents ({@code environment = "prod"}); changes here are visible to real users. */
    PROD("prod"),

    /** Test documents ({@code environment = "uat"}); use it for development and tests. */
    UAT("uat");

    private final String apiValue;

    RoadPermitEnvironment(String apiValue) {
        this.apiValue = apiValue;
    }

    /**
     * Returns the value sent to the API.
     *
     * @return {@code "prod"} for {@link #PROD}, {@code "uat"} for {@link #UAT}
     */
    public String apiValue() {
        return apiValue;
    }
}
