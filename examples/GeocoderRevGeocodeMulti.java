import java.util.List;
import java.util.Map;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.Config;
import pl.automapa.mapservices.exception.AutomapaException;
import pl.automapa.mapservices.request.Point;
import pl.automapa.mapservices.request.RevGeocodeMultiRequest;
import pl.automapa.mapservices.response.ApiResponse;

/**
 * Example: Geocoder.revgeocodemulti() - batch reverse geocoding.
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
 *   <li>resolves several points to street addresses in one request,
 *   <li>prints the matched address of each point,
 *   <li>handles errors via AutomapaException.
 * </ul>
 */
public final class GeocoderRevGeocodeMulti {

    private GeocoderRevGeocodeMulti() {}

    /**
     * Runs the example.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        System.out.println("=== Geocoder.revgeocodemulti() - batch reverse geocoding ===");
        System.out.println();

        Config config;
        try {
            config = Config.fromEnvironment();
        } catch (IllegalArgumentException e) {
            System.err.println("Configuration error: " + e.getMessage());
            System.exit(1);
            return;
        }

        // Each point is (longitude, latitude).
        List<Point> points = List.of(
                new Point(21.0073642, 52.2297), // Warsaw
                new Point(19.9449799, 50.0646501), // Krakow
                new Point(18.6282, 54.3520)); // Gdansk
        List<String> labels = List.of("Warsaw", "Krakow", "Gdansk");

        try {
            ApiClient client = ApiClient.create(config);
            ApiResponse response = client.geocoder()
                    .revgeocodemulti(RevGeocodeMultiRequest.builder(points).build());

            List<Object> results = response.dataAsList();
            for (int i = 0; i < results.size(); i++) {
                String label = i < labels.size() ? labels.get(i) : "point #" + i;
                Point point = points.get(Math.min(i, points.size() - 1));
                Map<?, ?> result = results.get(i) instanceof Map ? (Map<?, ?>) results.get(i) : Map.of();
                System.out.println("Point: " + label + " (x=" + point.x() + ", y=" + point.y() + ")");
                System.out.println("  City   : " + result.get("city"));
                System.out.println("  Street : " + result.get("street"));
                System.out.println("  House  : " + result.get("house"));
                System.out.println();
            }
        } catch (AutomapaException e) {
            System.err.println("API error: " + e.getMessage());
            System.exit(1);
        }
    }
}
