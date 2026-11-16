import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.Config;
import pl.automapa.mapservices.endpoint.RoadPermit;
import pl.automapa.mapservices.exception.AutomapaException;
import pl.automapa.mapservices.request.RoadPermitEnvironment;
import pl.automapa.mapservices.request.RoadPermitRequest;
import pl.automapa.mapservices.response.ApiResponse;

/**
 * Example: RoadPermit.add(), overwrite() and remove() - full permit lifecycle in the test environment.
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
 *   <li>adds a road permit for a GeoJSON Polygon in the {@code uat} (test) environment and prints its id,
 *   <li>overwrites it with a new tonnage limit (all fields are replaced, not merged),
 *   <li>removes it in a {@code finally} block, so the example never leaves data behind,
 *   <li>handles errors via AutomapaException.
 * </ul>
 *
 * <p>Permission keys: {@code tr_tonnage} (total weight limit in tonnes), {@code tr_tonn_axis} (axle load limit in
 * tonnes).
 */
public final class RoadPermitLifecycle {

    private RoadPermitLifecycle() {}

    /**
     * Runs the example.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        System.out.println("=== RoadPermit add -> overwrite -> remove (uat) ===");
        System.out.println();

        Config config;
        try {
            config = Config.fromEnvironment();
        } catch (IllegalArgumentException e) {
            System.err.println("Configuration error: " + e.getMessage());
            System.exit(1);
            return;
        }

        String id = null;
        boolean failed = false;
        RoadPermit permits = null;
        try {
            ApiClient client = ApiClient.create(config);
            // Always write to the test environment; client.roadPermit() alone is production.
            permits = client.roadPermit(RoadPermitEnvironment.UAT);

            ApiResponse added = permits.add(permit(12));
            Object newId = added.dataAsMap().get("id");
            if (newId == null) {
                System.err.println("API error: add() returned no permit id");
                System.exit(1);
                return;
            }
            id = String.valueOf(newId);
            System.out.println("Added permit     : " + id);

            ApiResponse overwritten = permits.overwrite(id, permit(24));
            System.out.println("Overwritten      : " + overwritten.dataAsMap().get("id") + " (tr_tonnage 12 -> 24)");
        } catch (AutomapaException e) {
            System.err.println("API error: " + e.getMessage());
            failed = true;
        } finally {
            if (permits != null && id != null) {
                try {
                    ApiResponse removed = permits.remove(id);
                    System.out.println(
                            "Removed          : " + removed.dataAsMap().get("deleted"));
                } catch (AutomapaException e) {
                    System.err.println("Cleanup error, remove permit " + id + " manually: " + e.getMessage());
                    failed = true;
                }
            }
        }
        if (failed) {
            System.exit(1);
        }
    }

    private static RoadPermitRequest permit(int tonnage) {
        // GeoJSON Polygon: coordinates are [longitude, latitude]; the ring must be closed.
        Map<String, Object> poly = new LinkedHashMap<>();
        poly.put("type", "Polygon");
        poly.put(
                "coordinates",
                List.of(List.of(
                        List.of(21.000, 52.220),
                        List.of(21.020, 52.220),
                        List.of(21.020, 52.240),
                        List.of(21.000, 52.240),
                        List.of(21.000, 52.220))));

        Map<String, Number> permissions = new LinkedHashMap<>();
        permissions.put("tr_tonnage", tonnage);
        permissions.put("tr_tonn_axis", 8.5);

        return RoadPermitRequest.builder(permissions, poly)
                .dateStart("2026-01-01")
                .dateEnd("2026-12-31")
                .description("Example permit created by the Java SDK")
                .documentName("DOC-EXAMPLE-001")
                .location("Warsaw test area")
                .isValidAfterExpiry(false)
                .build();
    }
}
