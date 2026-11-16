package pl.automapa.mapservices.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.Config;
import pl.automapa.mapservices.http.HttpRequest;
import pl.automapa.mapservices.request.RoadPermitEnvironment;
import pl.automapa.mapservices.request.RoadPermitListRequest;
import pl.automapa.mapservices.request.RoadPermitRequest;
import pl.automapa.mapservices.response.ApiResponse;
import pl.automapa.mapservices.support.Fixtures;
import pl.automapa.mapservices.support.MockHttpClient;

class RoadPermitTest {

    /** Session id from the {@code session_generate} fixture. */
    private static final String SESSION_ID = "qPPdEpdtpb";

    private MockHttpClient mockHttp;
    private ApiClient client;

    @BeforeEach
    void setUp() {
        mockHttp = new MockHttpClient();
        client = new ApiClient(Config.builder("test-key", "test-pass").build(), mockHttp);
        mockHttp.addResponse("Session", "getSalt", Fixtures.load("session_salt"));
        mockHttp.addResponse("Session", "generateSession", Fixtures.load("session_generate"));
    }

    private void assertProtectedCall(String method) {
        HttpRequest request = mockHttp.lastRequest();
        assertThat(request.url()).isEqualTo("https://api.automapa.pl/v3/RoadPermit/" + method);
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.headers()).containsEntry("Session-Id", SESSION_ID);
    }

    // --- factories ---

    @Test
    void defaultFactoryUsesProduction() {
        assertThat(client.roadPermit().environment()).isEqualTo(RoadPermitEnvironment.PROD);
    }

    @Test
    void factoryWithEnvironmentUsesIt() {
        assertThat(client.roadPermit(RoadPermitEnvironment.UAT).environment()).isEqualTo(RoadPermitEnvironment.UAT);
        assertThat(client.roadPermit(RoadPermitEnvironment.PROD).environment()).isEqualTo(RoadPermitEnvironment.PROD);
    }

    @Test
    void factoryReturnsTheSameInstancePerEnvironment() {
        assertThat(client.roadPermit()).isSameAs(client.roadPermit(RoadPermitEnvironment.PROD));
        assertThat(client.roadPermit(RoadPermitEnvironment.UAT)).isSameAs(client.roadPermit(RoadPermitEnvironment.UAT));
    }

    @Test
    void factoryRejectsNullEnvironment() {
        assertThatThrownBy(() -> client.roadPermit(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("environment")
                .hasMessageContaining("RoadPermitEnvironment.UAT");
    }

    @Test
    void constructorRejectsNullArguments() {
        assertThatThrownBy(() -> new RoadPermit(null, RoadPermitEnvironment.UAT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("client");
        assertThatThrownBy(() -> new RoadPermit(client, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("environment");
    }

    // --- list ---

    @Test
    void listSendsOnlyProductionEnvironmentByDefault() {
        mockHttp.addResponse("RoadPermit", "list", Fixtures.load("road_permit_list_success"));

        client.roadPermit().list();

        assertProtectedCall("list");
        assertThat(mockHttp.lastPayload()).containsExactly(Map.entry("environment", "prod"));
    }

    @Test
    void listSendsChosenEnvironment() {
        mockHttp.addResponse("RoadPermit", "list", Fixtures.load("road_permit_list_success"));

        client.roadPermit(RoadPermitEnvironment.UAT).list();

        assertProtectedCall("list");
        assertThat(mockHttp.lastPayload()).containsExactly(Map.entry("environment", "uat"));
    }

    @Test
    void listSendsFiltersWhenSet() {
        mockHttp.addResponse("RoadPermit", "list", Fixtures.load("road_permit_list_success"));

        client.roadPermit(RoadPermitEnvironment.UAT)
                .list(RoadPermitListRequest.builder()
                        .id("60a79ba2")
                        .documentName("ZDiK.TO.461.219.2020.AO")
                        .build());

        assertProtectedCall("list");
        assertThat(mockHttp.lastPayload())
                .containsExactly(
                        Map.entry("id", "60a79ba2"),
                        Map.entry("document_name", "ZDiK.TO.461.219.2020.AO"),
                        Map.entry("environment", "uat"));
    }

    @Test
    void listWithEmptyRequestSendsOnlyEnvironment() {
        mockHttp.addResponse("RoadPermit", "list", Fixtures.load("road_permit_list_success"));

        client.roadPermit().list(RoadPermitListRequest.builder().build());

        assertThat(mockHttp.lastPayload()).containsExactly(Map.entry("environment", "prod"));
    }

    @Test
    void listMergesExtraAndAlwaysSendsEndpointEnvironment() {
        mockHttp.addResponse("RoadPermit", "list", Fixtures.load("road_permit_list_success"));
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("environment", "prod");
        extra.put("id", "from-extra");
        extra.put("custom", true);

        client.roadPermit(RoadPermitEnvironment.UAT)
                .list(RoadPermitListRequest.builder().extra(extra).id("named").build());

        assertThat(mockHttp.lastPayload())
                .containsExactly(Map.entry("environment", "uat"), Map.entry("id", "named"), Map.entry("custom", true));
    }

    @Test
    void listForwardsIdFromExtraWhenNamedIdIsNotSet() {
        mockHttp.addResponse("RoadPermit", "list", Fixtures.load("road_permit_list_success"));

        client.roadPermit()
                .list(RoadPermitListRequest.builder().extra("id", "from-extra").build());

        assertThat(mockHttp.lastPayload())
                .containsExactly(Map.entry("id", "from-extra"), Map.entry("environment", "prod"));
    }

    @Test
    void listRejectsNullRequest() {
        assertThatThrownBy(() -> client.roadPermit().list(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RoadPermit.list")
                .hasMessageContaining("RoadPermitListRequest.builder()");
        assertThat(mockHttp.requestCount()).isZero();
    }

    @Test
    void listReturnsDocuments() {
        mockHttp.addResponse("RoadPermit", "list", Fixtures.load("road_permit_list_success"));

        ApiResponse response = client.roadPermit(RoadPermitEnvironment.UAT).list();

        assertThat(response.status()).isEqualTo(200);
        List<Object> documents = response.dataAsList();
        assertThat(documents).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> document = (Map<String, Object>) documents.get(0);
        assertThat(document)
                .containsEntry("id", "60a79ba2")
                .containsEntry("document_name", "ZDiK.TO.461.219.2020.AO")
                .containsEntry("is_valid_after_expiry", true)
                .containsKeys("permissions", "poly", "date_start", "date_end", "location", "description");
    }

    @Test
    void listReturnsEmptyListWhenThereAreNoDocuments() {
        mockHttp.addResponse("RoadPermit", "list", Map.of("result", List.of()));

        ApiResponse response = client.roadPermit(RoadPermitEnvironment.UAT).list();

        assertThat(response.dataAsList()).isEmpty();
    }

    // --- add ---

    private static Map<String, Object> polygon() {
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
        return poly;
    }

    private static RoadPermitRequest.Builder permit(int tonnage) {
        return RoadPermitRequest.builder(Map.of("tr_tonnage", tonnage), polygon());
    }

    @Test
    void addMinimalSendsPermissionsPolyAndProductionEnvironmentOnly() {
        mockHttp.addResponse("RoadPermit", "add", Fixtures.load("road_permit_add_success"));

        client.roadPermit().add(permit(12).build());

        assertProtectedCall("add");
        assertThat(mockHttp.lastPayload())
                .containsExactly(
                        Map.entry("permissions", Map.of("tr_tonnage", 12L)),
                        Map.entry("poly", polygon()),
                        Map.entry("environment", "prod"));
    }

    @Test
    void addFullSendsEverySnakeCaseField() {
        mockHttp.addResponse("RoadPermit", "add", Fixtures.load("road_permit_add_success"));

        client.roadPermit(RoadPermitEnvironment.UAT)
                .add(permit(12)
                        .dateStart("2026-01-01")
                        .dateEnd("2026-12-31")
                        .description("Test permit")
                        .documentName("DOC-001")
                        .location("Tarnow")
                        .isValidAfterExpiry(false)
                        .build());

        assertProtectedCall("add");
        assertThat(mockHttp.lastPayload())
                .containsExactly(
                        Map.entry("permissions", Map.of("tr_tonnage", 12L)),
                        Map.entry("poly", polygon()),
                        Map.entry("date_start", "2026-01-01"),
                        Map.entry("date_end", "2026-12-31"),
                        Map.entry("description", "Test permit"),
                        Map.entry("document_name", "DOC-001"),
                        Map.entry("location", "Tarnow"),
                        Map.entry("is_valid_after_expiry", false),
                        Map.entry("environment", "uat"));
    }

    @Test
    void addWithIdInExtraCallsOverwrite() {
        mockHttp.addResponse("RoadPermit", "overwrite", Fixtures.load("road_permit_overwrite_success"));

        client.roadPermit(RoadPermitEnvironment.UAT)
                .add(permit(12).extra("id", "60a79ba2").build());

        assertProtectedCall("overwrite");
        assertThat(mockHttp.lastPayload())
                .containsExactly(
                        Map.entry("id", "60a79ba2"),
                        Map.entry("permissions", Map.of("tr_tonnage", 12L)),
                        Map.entry("poly", polygon()),
                        Map.entry("environment", "uat"));
    }

    @Test
    void addWithNullIdInExtraCallsAddAndLeavesIdOut() {
        mockHttp.addResponse("RoadPermit", "add", Fixtures.load("road_permit_add_success"));

        client.roadPermit().add(permit(12).extra("id", null).build());

        assertProtectedCall("add");
        assertThat(mockHttp.lastPayload()).doesNotContainKey("id").containsKeys("permissions", "poly", "environment");
    }

    @Test
    void addForwardsExtraAndNamedFieldsWin() {
        mockHttp.addResponse("RoadPermit", "add", Fixtures.load("road_permit_add_success"));
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("newApiField", "some-value");
        extra.put("date_start", "1999-01-01");
        extra.put("environment", "prod");

        client.roadPermit(RoadPermitEnvironment.UAT)
                .add(permit(12).extra(extra).dateStart("2026-06-01").build());

        assertThat(mockHttp.lastPayload())
                .containsExactly(
                        Map.entry("newApiField", "some-value"),
                        Map.entry("date_start", "2026-06-01"),
                        Map.entry("environment", "uat"),
                        Map.entry("permissions", Map.of("tr_tonnage", 12L)),
                        Map.entry("poly", polygon()));
    }

    @Test
    void addRejectsNullRequest() {
        assertThatThrownBy(() -> client.roadPermit().add(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RoadPermit.add")
                .hasMessageContaining("RoadPermitRequest.builder(");
        assertThat(mockHttp.requestCount()).isZero();
    }

    @Test
    void addReturnsNewDocumentId() {
        mockHttp.addResponse("RoadPermit", "add", Fixtures.load("road_permit_add_success"));

        ApiResponse response =
                client.roadPermit(RoadPermitEnvironment.UAT).add(permit(12).build());

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.dataAsMap()).containsEntry("id", "60a79ba2");
    }

    // --- overwrite ---

    @Test
    void overwriteSendsIdEnvironmentPermissionsAndPoly() {
        mockHttp.addResponse("RoadPermit", "overwrite", Fixtures.load("road_permit_overwrite_success"));

        client.roadPermit(RoadPermitEnvironment.UAT)
                .overwrite("60a79ba2", permit(24).build());

        assertProtectedCall("overwrite");
        assertThat(mockHttp.lastPayload())
                .containsExactly(
                        Map.entry("id", "60a79ba2"),
                        Map.entry("permissions", Map.of("tr_tonnage", 24L)),
                        Map.entry("poly", polygon()),
                        Map.entry("environment", "uat"));
    }

    @Test
    void overwriteUsesProductionEnvironmentByDefault() {
        mockHttp.addResponse("RoadPermit", "overwrite", Fixtures.load("road_permit_overwrite_success"));

        client.roadPermit().overwrite("60a79ba2", permit(24).build());

        assertProtectedCall("overwrite");
        assertThat(mockHttp.lastPayload()).containsEntry("environment", "prod");
    }

    @Test
    void overwriteForwardsExtra() {
        mockHttp.addResponse("RoadPermit", "overwrite", Fixtures.load("road_permit_overwrite_success"));

        client.roadPermit(RoadPermitEnvironment.UAT)
                .overwrite(
                        "60a79ba2",
                        permit(24).extra("newApiField", "some-value").build());

        assertThat(mockHttp.lastPayload())
                .containsExactly(
                        Map.entry("id", "60a79ba2"),
                        Map.entry("newApiField", "some-value"),
                        Map.entry("permissions", Map.of("tr_tonnage", 24L)),
                        Map.entry("poly", polygon()),
                        Map.entry("environment", "uat"));
    }

    @Test
    void overwriteExplicitIdWinsOverExtraId() {
        mockHttp.addResponse("RoadPermit", "overwrite", Fixtures.load("road_permit_overwrite_success"));

        client.roadPermit(RoadPermitEnvironment.UAT)
                .overwrite("60a79ba2", permit(24).extra("id", "from-extra").build());

        assertProtectedCall("overwrite");
        assertThat(mockHttp.lastPayload()).containsEntry("id", "60a79ba2");
    }

    @Test
    void overwriteRejectsNullOrBlankIdAndNullRequest() {
        RoadPermit roadPermit = client.roadPermit(RoadPermitEnvironment.UAT);
        RoadPermitRequest request = permit(24).build();

        assertThatThrownBy(() -> roadPermit.overwrite(null, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RoadPermit.overwrite")
                .hasMessageContaining("id is required but was null")
                .hasMessageContaining("add");
        assertThatThrownBy(() -> roadPermit.overwrite(" ", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id is required but was empty");
        assertThatThrownBy(() -> roadPermit.overwrite("60a79ba2", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RoadPermit.overwrite")
                .hasMessageContaining("request");
        assertThat(mockHttp.requestCount()).isZero();
    }

    @Test
    void overwriteReturnsDocumentId() {
        mockHttp.addResponse("RoadPermit", "overwrite", Fixtures.load("road_permit_overwrite_success"));

        ApiResponse response = client.roadPermit(RoadPermitEnvironment.UAT)
                .overwrite("60a79ba2", permit(24).build());

        assertThat(response.dataAsMap()).containsEntry("id", "60a79ba2");
    }

    // --- remove ---

    @Test
    void removeSendsIdAndProductionEnvironmentByDefault() {
        mockHttp.addResponse("RoadPermit", "remove", Fixtures.load("road_permit_remove_success"));

        client.roadPermit().remove("60a79ba2");

        assertProtectedCall("remove");
        assertThat(mockHttp.lastPayload())
                .containsExactly(Map.entry("id", "60a79ba2"), Map.entry("environment", "prod"));
    }

    @Test
    void removeSendsChosenEnvironment() {
        mockHttp.addResponse("RoadPermit", "remove", Fixtures.load("road_permit_remove_success"));

        client.roadPermit(RoadPermitEnvironment.UAT).remove("60a79ba2");

        assertProtectedCall("remove");
        assertThat(mockHttp.lastPayload())
                .containsExactly(Map.entry("id", "60a79ba2"), Map.entry("environment", "uat"));
    }

    @Test
    void removeMergesExtraAndNamedValuesWin() {
        mockHttp.addResponse("RoadPermit", "remove", Fixtures.load("road_permit_remove_success"));
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("custom", "kept");
        extra.put("id", "from-extra");
        extra.put("environment", "prod");

        client.roadPermit(RoadPermitEnvironment.UAT).remove("60a79ba2", extra);

        assertThat(mockHttp.lastPayload())
                .containsExactly(
                        Map.entry("custom", "kept"), Map.entry("id", "60a79ba2"), Map.entry("environment", "uat"));
        assertThat(extra).containsEntry("id", "from-extra").containsEntry("environment", "prod");
    }

    @Test
    void removeWithNullExtraSendsIdAndEnvironment() {
        mockHttp.addResponse("RoadPermit", "remove", Fixtures.load("road_permit_remove_success"));

        client.roadPermit(RoadPermitEnvironment.UAT).remove("60a79ba2", null);

        assertThat(mockHttp.lastPayload())
                .containsExactly(Map.entry("id", "60a79ba2"), Map.entry("environment", "uat"));
    }

    @Test
    void removeRejectsNullOrBlankId() {
        RoadPermit roadPermit = client.roadPermit(RoadPermitEnvironment.UAT);

        assertThatThrownBy(() -> roadPermit.remove(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RoadPermit.remove")
                .hasMessageContaining("id is required but was null")
                .hasMessageContaining("list");
        assertThatThrownBy(() -> roadPermit.remove("  ", Map.of("id", "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id is required but was empty");
        assertThat(mockHttp.requestCount()).isZero();
    }

    @Test
    void removeReturnsDeletedFlag() {
        mockHttp.addResponse("RoadPermit", "remove", Fixtures.load("road_permit_remove_success"));

        ApiResponse response = client.roadPermit(RoadPermitEnvironment.UAT).remove("60a79ba2");

        assertThat(response.dataAsMap()).containsEntry("id", "60a79ba2").containsEntry("deleted", true);
    }
}
