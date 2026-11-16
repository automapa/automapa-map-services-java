import java.util.List;
import java.util.Map;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.Config;
import pl.automapa.mapservices.exception.AutomapaException;
import pl.automapa.mapservices.request.MatrixRequest;
import pl.automapa.mapservices.request.MatrixType;
import pl.automapa.mapservices.request.Point;
import pl.automapa.mapservices.response.ApiResponse;

/**
 * Example: Routes.matrix() - distance/time matrix between points.
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
 *   <li>calculates the travel-time matrix between 4 cities (the API needs at least 4 different points),
 *   <li>prints the results as a table: city A, city B, km, time,
 *   <li>handles errors via AutomapaException.
 * </ul>
 *
 * <p>Type: {@link MatrixType#TIME} or {@link MatrixType#DISTANCE}; every row has {@code length} in metres and
 * {@code duration} in minutes.
 */
public final class RoutesMatrix {

    private RoutesMatrix() {}

    /**
     * Runs the example.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        System.out.println("=== Routes.matrix() - distance matrix ===");
        System.out.println();

        Config config;
        try {
            config = Config.fromEnvironment();
        } catch (IllegalArgumentException e) {
            System.err.println("Configuration error: " + e.getMessage());
            System.exit(1);
            return;
        }

        // Labels are local only - they are not sent to the API.
        List<String> names = List.of("Warsaw", "Gdansk", "Poznan", "Krakow");
        List<Point> points = List.of(
                new Point(21.0073642, 52.2297),
                new Point(18.6282, 54.3520),
                new Point(16.9252, 52.4064),
                new Point(19.9448, 50.0647));

        try {
            ApiClient client = ApiClient.create(config);
            ApiResponse response = client.routes()
                    .matrix(MatrixRequest.builder(points, MatrixType.TIME)
                            .precise(false)
                            .simple(true)
                            .build());

            System.out.printf("%-15s  %-15s  %8s  %8s%n", "From", "To", "Distance", "Time");
            System.out.println("-".repeat(52));

            for (Object item : response.dataAsList()) {
                if (!(item instanceof Map<?, ?> row)) {
                    continue;
                }
                String[] pair = String.valueOf(row.get("points")).split("-");
                int from = Integer.parseInt(pair[0]);
                int to = Integer.parseInt(pair[1]);
                if (from == to) {
                    continue;
                }
                long km = Math.round(((Number) row.get("length")).longValue() / 1000.0);
                long minutes = ((Number) row.get("duration")).longValue();
                System.out.printf(
                        "%-15s  %-15s  %8s  %8s%n",
                        label(names, from),
                        label(names, to),
                        km + " km",
                        String.format("%dh %02dm", minutes / 60, minutes % 60));
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
