import java.util.List;
import java.util.Map;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.Config;
import pl.automapa.mapservices.exception.AutomapaException;
import pl.automapa.mapservices.request.RoadPermitEnvironment;
import pl.automapa.mapservices.response.ApiResponse;

/**
 * Example: RoadPermit.list() - road permits of the test environment (read only).
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
 *   <li>lists the road permits of the {@code uat} (test) environment; nothing is changed,
 *   <li>prints the number of documents and the id, document name, description and validity dates of each,
 *   <li>handles errors via AutomapaException.
 * </ul>
 *
 * <p>Environments: {@link RoadPermitEnvironment#UAT} (test) and {@link RoadPermitEnvironment#PROD} (production).
 */
public final class RoadPermitList {

    private RoadPermitList() {}

    /**
     * Runs the example.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        System.out.println("=== RoadPermit.list() - road permit list (uat) ===");
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
            ApiResponse response = client.roadPermit(RoadPermitEnvironment.UAT).list();

            List<Object> permits = response.dataAsList();
            System.out.println("Total permits: " + permits.size());
            System.out.println();

            for (Object item : permits) {
                if (!(item instanceof Map<?, ?> permit)) {
                    continue;
                }
                System.out.println("ID          : " + valueOrNa(permit.get("id")));
                System.out.println("Document    : " + valueOrNa(permit.get("document_name")));
                System.out.println("Description : " + valueOrNa(permit.get("description")));
                System.out.println("Valid from  : " + valueOrNa(permit.get("date_start")));
                System.out.println("Valid to    : " + valueOrNa(permit.get("date_end")));
                System.out.println("Permissions : " + valueOrNa(permit.get("permissions")));
                System.out.println();
            }
        } catch (AutomapaException e) {
            System.err.println("API error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static String valueOrNa(Object value) {
        return value == null ? "n/a" : String.valueOf(value);
    }
}
