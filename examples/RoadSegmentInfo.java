import java.util.Map;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.Config;
import pl.automapa.mapservices.exception.AutomapaException;
import pl.automapa.mapservices.request.SegmentInfoRequest;
import pl.automapa.mapservices.response.ApiResponse;

/**
 * Example: Road.getSegmentInfo() - road segment information.
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
 *   <li>retrieves the road segment nearest to a coordinate,
 *   <li>prints the street name, speed limit, toll and highway flags and the snapped point,
 *   <li>handles errors via AutomapaException.
 * </ul>
 */
public final class RoadSegmentInfo {

    private RoadSegmentInfo() {}

    /**
     * Runs the example.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        System.out.println("=== Road.getSegmentInfo() - road segment data ===");
        System.out.println();

        Config config;
        try {
            config = Config.fromEnvironment();
        } catch (IllegalArgumentException e) {
            System.err.println("Configuration error: " + e.getMessage());
            System.exit(1);
            return;
        }

        // x = longitude, y = latitude - Warsaw city centre.
        double x = 21.0073642;
        double y = 52.2297;
        System.out.println("Input: x=" + x + ", y=" + y);
        System.out.println();

        try {
            ApiClient client = ApiClient.create(config);
            ApiResponse response = client.road()
                    .getSegmentInfo(SegmentInfoRequest.builder(x, y)
                            .distLimit(50) // search radius in metres (optional)
                            .minCategory(1) // minimum road category to consider (optional)
                            .build());

            Map<String, Object> data = response.dataAsMap();
            Map<?, ?> segment = data.get("segment") instanceof Map<?, ?> map ? map : Map.of();

            System.out.println("Segment ID    : " + valueOrNa(segment.get("id")));
            System.out.println("Street name   : " + valueOrNa(segment.get("streetName")));
            System.out.println("Speed limit   : " + valueOrNa(segment.get("speedLimit")) + " km/h");
            System.out.println("Is toll       : " + valueOrNa(segment.get("isToll")));
            System.out.println("Is highway    : " + valueOrNa(segment.get("isHighway")));
            System.out.println("Attracted X   : " + valueOrNa(data.get("attractedX")));
            System.out.println("Attracted Y   : " + valueOrNa(data.get("attractedY")));
            System.out.println("Pure distance : " + valueOrNa(data.get("pureDistance")) + " m");

            // The raw API response is always accessible via raw().
        } catch (AutomapaException e) {
            System.err.println("API error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static String valueOrNa(Object value) {
        return value == null ? "n/a" : String.valueOf(value);
    }
}
