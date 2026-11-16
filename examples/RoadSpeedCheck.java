import java.util.Map;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.Config;
import pl.automapa.mapservices.exception.AutomapaException;
import pl.automapa.mapservices.request.SpeedCheckRequest;
import pl.automapa.mapservices.response.ApiResponse;

/**
 * Example: Road.speedCheck() - speed limit for a single point.
 *
 * <p>Usage (credentials from the environment, e.g. {@code cp .env.example .env} and fill it):
 *
 * <pre>
 *   make examples
 * </pre>
 *
 * <p>What this program does:
 *
 * <ul>
 *   <li>returns the legal speed limit on the road nearest to a coordinate,
 *   <li>prints the speed limit for cars and trucks and the urban area flag,
 *   <li>handles errors via AutomapaException.
 * </ul>
 */
public final class RoadSpeedCheck {

    private RoadSpeedCheck() {}

    /**
     * Runs the example.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        System.out.println("=== Road.speedCheck() - speed limit for a point ===");
        System.out.println();

        Config config;
        try {
            config = Config.fromEnvironment();
        } catch (IllegalArgumentException e) {
            System.err.println("Configuration error: " + e.getMessage());
            System.exit(1);
            return;
        }

        // x = longitude, y = latitude.
        double x = 15.2154636;
        double y = 52.3118895;
        System.out.println("Input: x=" + x + ", y=" + y);
        System.out.println();

        try {
            ApiClient client = ApiClient.create(config);
            ApiResponse response = client.road()
                    .speedCheck(SpeedCheckRequest.builder(x, y)
                            .dir(350) // direction of travel in degrees (optional)
                            .distLimit(50) // search radius in metres (optional)
                            .build());

            Map<String, Object> data = response.dataAsMap();
            System.out.println("Speed limit (car)   : " + valueOrNa(data.get("speedLimit")) + " km/h");
            System.out.println("Speed limit (truck) : " + valueOrNa(data.get("speedLimitTruck")) + " km/h");
            System.out.println("Urban area          : " + yesNo(data.get("urbanArea")));
            System.out.println("Attracted X         : " + valueOrNa(data.get("x")));
            System.out.println("Attracted Y         : " + valueOrNa(data.get("y")));
            System.out.println("Distance            : " + valueOrNa(data.get("distance")) + " m");
        } catch (AutomapaException e) {
            System.err.println("API error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static String valueOrNa(Object value) {
        return value == null ? "n/a" : String.valueOf(value);
    }

    private static String yesNo(Object value) {
        return value instanceof Boolean flag ? (flag ? "yes" : "no") : "n/a";
    }
}
