import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.Config;
import pl.automapa.mapservices.exception.AutomapaException;
import pl.automapa.mapservices.request.Point;
import pl.automapa.mapservices.request.RouteRequest;
import pl.automapa.mapservices.response.ApiResponse;

/**
 * Example: Routes.route() - calculate a route from A to B.
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
 *   <li>calculates the optimal car route from Warsaw to Gdansk,
 *   <li>prints the route length, the estimated travel time and a map link,
 *   <li>handles errors via AutomapaException.
 * </ul>
 */
public final class RoutesRoute {

    private RoutesRoute() {}

    /**
     * Runs the example.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        System.out.println("=== Routes.route() - route calculation ===");
        System.out.println();

        Config config;
        try {
            config = Config.fromEnvironment();
        } catch (IllegalArgumentException e) {
            System.err.println("Configuration error: " + e.getMessage());
            System.exit(1);
            return;
        }

        // Route waypoints: x = longitude, y = latitude.
        List<Point> points = List.of(
                new Point(21.0073642, 52.2297), // Warsaw city center
                new Point(18.6282, 54.3520)); // Gdansk city center

        Map<String, Object> route = new LinkedHashMap<>();
        route.put("calculateTolls", true);
        route.put("vehicleCatForEToll", 2);
        route.put("start", "NOW");
        route.put("type", "optimal"); // "quick" | "short" | "optimal"
        route.put("traffic", true); // true = take traffic into account
        route.put("desc", true);
        route.put("onlyLE", false);
        route.put("avoidTolls", false);
        route.put("avoidFerries", false);
        route.put("avoidHighways", false);

        Map<String, Object> truckParams = new LinkedHashMap<>();
        truckParams.put("axleLoad", 2.1);
        truckParams.put("truckWeight", 3.5);
        truckParams.put("truckLength", 7.2);
        truckParams.put("truckWidth", 2.5);
        truckParams.put("truckHeight", 3.4);
        truckParams.put("euroNormForToll", 5);

        Map<String, Object> object = new LinkedHashMap<>();
        object.put("type", "car"); // "car" or "truck"
        object.put("truckParams", truckParams);

        try {
            ApiClient client = ApiClient.create(config);
            ApiResponse response = client.routes()
                    .route(RouteRequest.builder(points, route, object).build());

            Map<String, Object> data = response.dataAsMap();
            long length = ((Number) data.get("length")).longValue();
            long etaMinutes = ((Number) data.get("eta")).longValue();

            System.out.printf("Route length  : %.1f km%n", length / 1000.0);
            System.out.printf("Est. duration : %dh %02dm%n", etaMinutes / 60, etaMinutes % 60);
            Object mapUrl = data.get("mapurl");
            if (mapUrl != null && !String.valueOf(mapUrl).isEmpty()) {
                System.out.println("Map           : " + mapUrl);
            }
            Object tolls = data.get("tolls");
            if (tolls instanceof Map<?, ?> tollMap && !tollMap.isEmpty()) {
                System.out.println("Toll roads    : yes");
            }
            Object desc = data.get("desc");
            System.out.println();
            System.out.println("Number of manoeuvres: " + (desc instanceof List<?> list ? list.size() : 0));

            // The raw API response is always accessible via raw():
            // response.raw() -> {"result": {"length": ..., "eta": ..., ...}}
        } catch (AutomapaException e) {
            System.err.println("API error: " + e.getMessage());
            System.exit(1);
        }
    }
}
