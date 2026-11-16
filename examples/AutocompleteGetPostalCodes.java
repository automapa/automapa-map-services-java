import java.util.List;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.Config;
import pl.automapa.mapservices.exception.AutomapaException;

/**
 * Example: Autocomplete.getPostalCodesForStreet() - postal codes of a street.
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
 *   <li>collects the unique postal codes of a street (sorted ascending),
 *   <li>handles errors via AutomapaException.
 * </ul>
 */
public final class AutocompleteGetPostalCodes {

    private AutocompleteGetPostalCodes() {}

    /**
     * Runs the example.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        System.out.println("=== Autocomplete.getPostalCodesForStreet() - postal codes ===");
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
            List<String> codes = client.autocomplete().getPostalCodesForStreet("Warszawa", "Domaniewska");

            System.out.println("Postal codes: " + String.join(", ", codes));
        } catch (AutomapaException e) {
            System.err.println("API error: " + e.getMessage());
            System.exit(1);
        }
    }
}
