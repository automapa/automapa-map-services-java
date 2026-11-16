import java.util.List;
import java.util.Map;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.Config;
import pl.automapa.mapservices.exception.AutomapaException;
import pl.automapa.mapservices.request.GeocodeRequest;
import pl.automapa.mapservices.response.ApiResponse;

/**
 * Example: Geocoder.geocode() - single address geocoding.
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
 *   <li>geocodes a single address (city, street, house number) to coordinates,
 *   <li>prints the resolved x/y coordinates and the matched address,
 *   <li>handles errors via AutomapaException.
 * </ul>
 */
public final class GeocoderGeocode {

    private GeocoderGeocode() {}

    /**
     * Runs the example.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        System.out.println("=== Geocoder.geocode() - single address ===");
        System.out.println();

        Config config;
        try {
            config = Config.fromEnvironment();
        } catch (IllegalArgumentException e) {
            System.err.println("Configuration error: " + e.getMessage());
            System.exit(1);
            return;
        }

        try {
            ApiClient client = ApiClient.create(config);
            ApiResponse response = client.geocoder()
                    .geocode(GeocodeRequest.builder("Warszawa")
                            .street("Marszalkowska")
                            .house("1")
                            .maxResults(1)
                            .build());

            List<Object> results = response.dataAsList();
            if (results.isEmpty()) {
                System.out.println("No results found.");
                return;
            }
            Map<?, ?> result = (Map<?, ?>) results.get(0);
            System.out.println("Coordinates : x=" + result.get("x") + ", y=" + result.get("y"));
            System.out.println("City        : " + result.get("city"));
            System.out.println("Street      : " + result.get("street"));
            System.out.println("House       : " + result.get("house"));
            // The full API response is always available via response.raw().
        } catch (AutomapaException e) {
            System.err.println("API error: " + e.getMessage());
            System.exit(1);
        }
    }
}
