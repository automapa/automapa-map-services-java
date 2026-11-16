import java.util.List;
import java.util.Map;
import java.util.Objects;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.Config;
import pl.automapa.mapservices.exception.AutomapaException;
import pl.automapa.mapservices.response.ApiResponse;

/**
 * Example: Geocoder.geocodemulti() - batch address geocoding.
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
 *   <li>geocodes several addresses in a single API request (up to 100),
 *   <li>prints the coordinates of the best match of each address,
 *   <li>handles errors via AutomapaException.
 * </ul>
 */
public final class GeocoderGeocodeMulti {

    private GeocoderGeocodeMulti() {}

    /**
     * Runs the example.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        System.out.println("=== Geocoder.geocodemulti() - batch geocoding ===");
        System.out.println();

        Config config;
        try {
            config = Config.fromEnvironment();
        } catch (IllegalArgumentException e) {
            System.err.println("Configuration error: " + e.getMessage());
            System.exit(1);
            return;
        }

        List<Map<String, String>> addresses = List.of(
                Map.of("city", "Warszawa", "street", "Domaniewska", "house", "37"),
                Map.of("city", "Krakow"),
                Map.of("city", "Gdansk"));

        try {
            ApiClient client = ApiClient.create(config);
            ApiResponse response = client.geocoder().geocodemulti(addresses);

            List<Object> groups = response.dataAsList();
            for (int i = 0; i < groups.size(); i++) {
                String input = i < addresses.size() ? addresses.get(i).get("city") : "address #" + i;
                System.out.println("Address: " + input);
                Object group = groups.get(i);
                Map<?, ?> result = null;
                if (group instanceof List && !((List<?>) group).isEmpty()) {
                    result = (Map<?, ?>) ((List<?>) group).get(0);
                } else if (group instanceof Map) {
                    result = (Map<?, ?>) group;
                }
                if (result == null || !result.containsKey("x")) {
                    System.out.println("  No results found" + (result == null ? "." : ": " + result.get("message")));
                    System.out.println();
                    continue;
                }
                System.out.println("  x=" + result.get("x") + ", y=" + result.get("y"));
                System.out.println("  City  : " + result.get("city"));
                System.out.println("  Street: " + Objects.toString(result.get("street"), ""));
                System.out.println("  House : " + Objects.toString(result.get("house"), ""));
                System.out.println();
            }
        } catch (AutomapaException e) {
            System.err.println("API error: " + e.getMessage());
            System.exit(1);
        }
    }
}
