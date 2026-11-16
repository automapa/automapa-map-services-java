import java.util.List;
import java.util.Map;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.Config;
import pl.automapa.mapservices.exception.AutomapaException;
import pl.automapa.mapservices.request.OptimizeRequest;
import pl.automapa.mapservices.request.OptimizeType;
import pl.automapa.mapservices.request.Point;
import pl.automapa.mapservices.response.ApiResponse;

/**
 * Example: Routes.optimize() - synchronous waypoint order optimisation.
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
 *   <li>optimises the visiting order of 4 cities (Warsaw, Gdansk, Poznan, Krakow),
 *   <li>prints the optimal visit sequence and the time/distance from the start to each point,
 *   <li>handles errors via AutomapaException.
 * </ul>
 *
 * <p>API limit: max 10 points (synchronous). For larger routes use the asynchronous optimizeQueue() - see
 * RoutesOptimizeAsync.java. {@code fixedEnd(false)} lets the API choose the last point; {@code true} (default)
 * keeps the last point of the list as the destination.
 */
public final class RoutesOptimize {

    private RoutesOptimize() {}

    /**
     * Runs the example.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        System.out.println("=== Routes.optimize() - route optimisation (sync, max 10 pts) ===");
        System.out.println();

        Config config;
        try {
            config = Config.fromEnvironment();
        } catch (IllegalArgumentException e) {
            System.err.println("Configuration error: " + e.getMessage());
            System.exit(1);
            return;
        }

        // Names are local only - only the coordinates are sent to the API.
        List<String> names = List.of("Warsaw", "Gdansk", "Poznan", "Krakow");
        List<Point> points = List.of(
                new Point(21.0073642, 52.2297),
                new Point(18.6282, 54.3520),
                new Point(16.9252, 52.4064),
                new Point(19.9448, 50.0647));

        try {
            ApiClient client = ApiClient.create(config);
            ApiResponse response = client.routes()
                    .optimize(OptimizeRequest.builder(points, OptimizeType.TIME) // TIME or DISTANCE
                            .fixedEnd(false)
                            .route(Map.of("avoidTolls", false))
                            .object(Map.of("type", "car"))
                            .build());

            Map<String, Object> data = response.dataAsMap();
            String[] order = String.valueOf(data.get("order")).split(",");

            StringBuilder sequence = new StringBuilder();
            for (String index : order) {
                if (sequence.length() > 0) {
                    sequence.append(" -> ");
                }
                sequence.append(label(names, Integer.parseInt(index.trim())));
            }
            System.out.println("Optimal order: " + sequence);
            System.out.println();

            System.out.println("Leg details (from the start):");
            List<?> sections = (List<?>) data.get("sections");
            for (int i = 0; i < sections.size(); i++) {
                if (!(sections.get(i) instanceof Map<?, ?> section)) {
                    continue;
                }
                String name = i < order.length ? label(names, Integer.parseInt(order[i].trim())) : "point " + i;
                long km = Math.round(((Number) section.get("dist")).longValue() / 1000.0);
                long minutes = ((Number) section.get("eta")).longValue();
                System.out.printf(
                        "  [%d] %-10s  x=%.4f y=%.4f  |  distance=%5d km  |  ETA=%dh %02dm%n",
                        i,
                        name,
                        ((Number) section.get("x")).doubleValue(),
                        ((Number) section.get("y")).doubleValue(),
                        km,
                        minutes / 60,
                        minutes % 60);
            }

            // response.raw() always returns the full API response.
        } catch (AutomapaException e) {
            System.err.println("API error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static String label(List<String> names, int index) {
        return index >= 0 && index < names.size() ? names.get(index) : "point " + index;
    }
}
