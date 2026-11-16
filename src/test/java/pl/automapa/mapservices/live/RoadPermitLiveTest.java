package pl.automapa.mapservices.live;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.endpoint.RoadPermit;
import pl.automapa.mapservices.http.HttpResponse;
import pl.automapa.mapservices.request.RoadPermitEnvironment;
import pl.automapa.mapservices.request.RoadPermitRequest;
import pl.automapa.mapservices.response.ApiResponse;

/**
 * RoadPermit service on the live API (suite {@code roadpermit}).
 *
 * <p>Writes go only to the {@code uat} environment; {@code prod} is only listed.
 */
class RoadPermitLiveTest extends LiveApiTestBase {

    private static final String TEST_ENVIRONMENT = "uat";

    @Override
    protected String suiteName() {
        return "roadpermit";
    }

    @Test
    void listReturnsResultViaRawHttp() {
        HttpResponse response =
                sendRequest("RoadPermit", "list", Map.of("environment", TEST_ENVIRONMENT), acquireSession());

        skipIfNoPermission(response);
        Map<String, Object> body = assertSuccessResponse(response);
        assertThat(body.get("result"))
                .as("result must be a list (empty or with documents)")
                .isInstanceOf(List.class);
    }

    @Test
    void listInUatReturnsDocumentList() {
        ApiResponse response = callLive(
                () -> buildApiClient().roadPermit(RoadPermitEnvironment.UAT).list());

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.dataAsList()).as("list result").isNotNull();
        assertThat(response.raw()).containsKey("result");
    }

    @Test
    void addOverwriteAndRemoveInUatPreserveCollectionState() {
        ApiClient client = buildApiClient();
        RoadPermit uat = client.roadPermit(RoadPermitEnvironment.UAT);
        List<Object> initial = callLive(() -> uat.list().dataAsList());
        String newId = null;
        boolean removed = false;
        try {
            ApiResponse added = callLive(() -> uat.add(permit(12)));
            newId = String.valueOf(added.dataAsMap().get("id"));
            assertThat(added.dataAsMap().get("id"))
                    .as("add() must return a permit id")
                    .isNotNull();
            assertThat(newId).as("add() must return a non-empty permit id").isNotBlank();

            List<Object> afterAdd = callLive(() -> uat.list().dataAsList());
            assertThat(afterAdd).as("list() after add()").hasSize(initial.size() + 1);
            assertThat(tonnage(afterAdd, newId)).as("tr_tonnage after add()").isEqualTo(12.0);

            List<Object> production = callLive(
                    () -> client.roadPermit(RoadPermitEnvironment.PROD).list().dataAsList());
            assertThat(ids(production))
                    .as("prod must not contain a document added in uat (environment is sent)")
                    .doesNotContain(newId);

            String id = newId;
            ApiResponse overwritten = callLive(() -> uat.overwrite(id, permit(24)));
            assertThat(overwritten.dataAsMap())
                    .as("overwrite() must return the id of the overwritten document")
                    .containsEntry("id", newId);

            List<Object> afterOverwrite = callLive(() -> uat.list().dataAsList());
            assertThat(afterOverwrite).as("list() after overwrite()").hasSize(initial.size() + 1);
            assertThat(tonnage(afterOverwrite, newId))
                    .as("tr_tonnage after overwrite()")
                    .isEqualTo(24.0);

            ApiResponse removal = callLive(() -> uat.remove(id));
            removed = true;
            assertThat(removal.dataAsMap()).as("remove() must confirm deletion").containsEntry("deleted", true);

            List<Object> afterRemove = callLive(() -> uat.list().dataAsList());
            assertThat(afterRemove).as("list() after remove()").hasSize(initial.size());
            assertThat(ids(afterRemove)).as("ids after remove()").doesNotContain(newId);
        } finally {
            if (newId != null && !newId.isBlank() && !removed) {
                String id = newId;
                withRateLimitRetry(() -> uat.remove(id));
            }
        }
    }

    private static RoadPermitRequest permit(int tonnage) {
        Map<String, Object> poly = new LinkedHashMap<>();
        poly.put("type", "Polygon");
        poly.put(
                "coordinates",
                List.of(List.of(
                        List.of(20.993155973821672, 50.01069571221776),
                        List.of(20.994428585759994, 50.01089900951218),
                        List.of(20.994138589184054, 50.011559010137475),
                        List.of(20.923253, 50.0118279),
                        List.of(20.993155973821672, 50.01069571221776))));
        return RoadPermitRequest.builder(Map.of("tr_tonnage", tonnage), poly).build();
    }

    private static List<Object> ids(List<Object> documents) {
        List<Object> ids = new ArrayList<>();
        for (Object document : documents) {
            ids.add(((Map<?, ?>) document).get("id"));
        }
        return ids;
    }

    private static Double tonnage(List<Object> documents, String id) {
        for (Object item : documents) {
            Map<?, ?> document = (Map<?, ?>) item;
            if (id.equals(document.get("id"))) {
                Object permissions = document.get("permissions");
                assertThat(permissions).as("permissions of %s", id).isInstanceOf(Map.class);
                Object value = ((Map<?, ?>) permissions).get("tr_tonnage");
                assertThat(value).as("tr_tonnage of %s", id).isNotNull();
                return Double.parseDouble(String.valueOf(value));
            }
        }
        throw new AssertionError("document " + id + " not found in list()");
    }
}
