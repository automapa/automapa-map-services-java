import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.Config;
import pl.automapa.mapservices.exception.AutomapaException;
import pl.automapa.mapservices.request.Point;
import pl.automapa.mapservices.request.RevGeocodeRequest;
import pl.automapa.mapservices.response.ApiResponse;

/**
 * Example: Geocoder.revgeocode() - single point reverse geocoding.
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
 *   <li>resolves a point (x = longitude, y = latitude) to a street address,
 *   <li>snaps the result to the nearest building and asks for the postal code,
 *   <li>handles errors via AutomapaException.
 * </ul>
 */
public final class GeocoderRevGeocode {

    private GeocoderRevGeocode() {}

    /**
     * Runs the example.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        System.out.println("=== Geocoder.revgeocode() - reverse geocoding ===");
        System.out.println();

        Config config;
        try {
            config = Config.fromEnvironment();
        } catch (IllegalArgumentException e) {
            System.err.println("Configuration error: " + e.getMessage());
            System.exit(1);
            return;
        }

        Point point = new Point(21.0072113, 52.1828575); // Domaniewska 36/38, Warszawa
        System.out.println("Input: x=" + point.x() + ", y=" + point.y());
        System.out.println();

        try {
            ApiClient client = ApiClient.create(config);
            ApiResponse response = client.geocoder()
                    .revgeocode(RevGeocodeRequest.builder(point)
                            .snapToBld(true) // snap the result to the nearest building
                            .getPostalCode(true) // include the postal code (if available)
                            .build());

            System.out.println("Output: " + response.dataAsMap());
        } catch (AutomapaException e) {
            System.err.println("API error: " + e.getMessage());
            System.exit(1);
        }
    }
}
