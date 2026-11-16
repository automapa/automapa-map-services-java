import java.util.List;
import java.util.Map;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.Config;
import pl.automapa.mapservices.exception.AutomapaException;
import pl.automapa.mapservices.request.SpeedCheckPoint;
import pl.automapa.mapservices.response.ApiResponse;

/**
 * Example: Road.speedCheckMulti() - speed limits for multiple points.
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
 *   <li>returns speed limits for up to 100 points in a single request,
 *   <li>prints the speed limit for cars and trucks for each input point,
 *   <li>handles errors via AutomapaException.
 * </ul>
 */
public final class RoadSpeedCheckMulti {

    private RoadSpeedCheckMulti() {}

    /**
     * Runs the example.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        System.out.println("=== Road.speedCheckMulti() - speed limits for multiple points ===");
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
        List<SpeedCheckPoint> points = List.of(
                new SpeedCheckPoint(15.2154636, 52.3118895, 350), // with direction
                new SpeedCheckPoint(13.8412285, 52.3111549), // no direction
                new SpeedCheckPoint(19.9449799, 50.0646501)); // Krakow
        List<String> labels = List.of("Point 1 (with dir)", "Point 2", "Krakow");

        try {
            ApiClient client = ApiClient.create(config);
            ApiResponse response = client.road().speedCheckMulti(points);

            List<Object> data = response.dataAsList();
            for (int i = 0; i < data.size(); i++) {
                if (!(data.get(i) instanceof Map<?, ?> result)) {
                    continue;
                }
                String label = i < labels.size() ? labels.get(i) : "point #" + i;
                System.out.println("Point: " + label);
                System.out.println("  Speed limit (car)   : " + valueOrNa(result.get("speedLimit")) + " km/h");
                System.out.println("  Speed limit (truck) : " + valueOrNa(result.get("speedLimitTruck")) + " km/h");
                System.out.println("  Urban area          : " + yesNo(result.get("urbanArea")));
                System.out.println("  Distance            : " + valueOrNa(result.get("distance")) + " m");
                System.out.println();
            }
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
