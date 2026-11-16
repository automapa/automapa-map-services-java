package pl.automapa.mapservices.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.Config;
import pl.automapa.mapservices.http.HttpRequest;
import pl.automapa.mapservices.request.SegmentInfoRequest;
import pl.automapa.mapservices.request.SpeedCheckPoint;
import pl.automapa.mapservices.request.SpeedCheckRequest;
import pl.automapa.mapservices.response.ApiResponse;
import pl.automapa.mapservices.support.Fixtures;
import pl.automapa.mapservices.support.MockHttpClient;

class RoadTest {

    /** Session id from the {@code session_generate} fixture. */
    private static final String SESSION_ID = "qPPdEpdtpb";

    private static final SpeedCheckPoint WITH_DIR = new SpeedCheckPoint(15.2154636, 52.3118895, 350);
    private static final SpeedCheckPoint NO_DIR = new SpeedCheckPoint(13.8412285, 52.3111549);

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
        assertThat(request.url()).isEqualTo("https://api.automapa.pl/v3/Road/" + method);
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.headers()).containsEntry("Session-Id", SESSION_ID);
    }

    // --- getSegmentInfo ---

    @Test
    void getSegmentInfoSendsOnlyCoordinatesWithSession() {
        mockHttp.addResponse("Road", "getSegmentInfo", Fixtures.load("road_get_segment_info_success"));

        client.road()
                .getSegmentInfo(SegmentInfoRequest.builder(21.00736, 52.18288).build());

        assertProtectedCall("getSegmentInfo");
        assertThat(mockHttp.lastPayload()).containsExactly(Map.entry("x", 21.00736), Map.entry("y", 52.18288));
    }

    @Test
    void getSegmentInfoSendsNamedParams() {
        mockHttp.addResponse("Road", "getSegmentInfo", Fixtures.load("road_get_segment_info_success"));

        client.road()
                .getSegmentInfo(SegmentInfoRequest.builder(21.00736, 52.18288)
                        .dir(90)
                        .distLimit(100)
                        .minCategory(1)
                        .ignoreNoWaySegments(true)
                        .treatTruckTollsAsTolls(false)
                        .ignoreSegmentsUnderConstruction(true)
                        .ignorePhysicallyBlockedSegs(false)
                        .useTolls(1)
                        .useFerries(0)
                        .useHighways(-1)
                        .build());

        assertThat(mockHttp.lastPayload())
                .containsExactly(
                        Map.entry("x", 21.00736),
                        Map.entry("y", 52.18288),
                        Map.entry("dir", 90L),
                        Map.entry("distLimit", 100L),
                        Map.entry("minCategory", 1L),
                        Map.entry("ignoreNoWaySegments", true),
                        Map.entry("treatTruckTollsAsTolls", false),
                        Map.entry("ignoreSegmentsUnderConstruction", true),
                        Map.entry("ignorePhysicallyBlockedSegs", false),
                        Map.entry("useTolls", 1L),
                        Map.entry("useFerries", 0L),
                        Map.entry("useHighways", -1L));
    }

    @Test
    void getSegmentInfoNamedParamsOverrideExtra() {
        mockHttp.addResponse("Road", "getSegmentInfo", Fixtures.load("road_get_segment_info_success"));
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("distLimit", 999);
        extra.put("extraKey", "kept");
        extra.put("unknownFlag", true);

        client.road()
                .getSegmentInfo(SegmentInfoRequest.builder(21.00736, 52.18288)
                        .extra(extra)
                        .distLimit(200)
                        .build());

        assertThat(mockHttp.lastPayload())
                .containsExactly(
                        Map.entry("distLimit", 200L),
                        Map.entry("extraKey", "kept"),
                        Map.entry("unknownFlag", true),
                        Map.entry("x", 21.00736),
                        Map.entry("y", 52.18288));
    }

    @Test
    void getSegmentInfoReturnsSegmentData() {
        Map<String, Object> fixture = Fixtures.load("road_get_segment_info_success");
        mockHttp.addResponse("Road", "getSegmentInfo", fixture);

        ApiResponse response = client.road()
                .getSegmentInfo(SegmentInfoRequest.builder(21.00736, 52.18288).build());

        assertThat(response.raw()).isEqualTo(fixture);
        Map<String, Object> data = response.dataAsMap();
        assertThat(data).containsKeys("segment", "attractedX", "attractedY", "pureDistance");
        assertThat(data.get("segment"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("id", 37530L)
                .containsEntry("streetName", "Domaniewska");
    }

    @Test
    void getSegmentInfoRejectsNullRequest() {
        assertThatThrownBy(() -> client.road().getSegmentInfo(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Road.getSegmentInfo")
                .hasMessageContaining("request is required")
                .hasMessageContaining("SegmentInfoRequest.builder(x, y)");
        assertThat(mockHttp.requestCount()).isZero();
    }

    // --- speedCheck ---

    @Test
    void speedCheckSendsOnlyCoordinatesWithSession() {
        mockHttp.addResponse("Road", "speedCheck", Fixtures.load("road_speed_check_success"));

        client.road()
                .speedCheck(SpeedCheckRequest.builder(15.2154636, 52.3118895).build());

        assertProtectedCall("speedCheck");
        assertThat(mockHttp.lastPayload()).containsExactly(Map.entry("x", 15.2154636), Map.entry("y", 52.3118895));
    }

    @Test
    void speedCheckSendsNamedParams() {
        mockHttp.addResponse("Road", "speedCheck", Fixtures.load("road_speed_check_success"));

        client.road()
                .speedCheck(SpeedCheckRequest.builder(15.2154636, 52.3118895)
                        .dir(350)
                        .distLimit(75)
                        .build());

        assertThat(mockHttp.lastPayload())
                .containsExactly(
                        Map.entry("x", 15.2154636),
                        Map.entry("y", 52.3118895),
                        Map.entry("dir", 350L),
                        Map.entry("distLimit", 75L));
    }

    @Test
    void speedCheckNamedParamsOverrideExtra() {
        mockHttp.addResponse("Road", "speedCheck", Fixtures.load("road_speed_check_success"));

        client.road()
                .speedCheck(SpeedCheckRequest.builder(15.2154636, 52.3118895)
                        .extra("dir", 1)
                        .extra("futureParam", "value")
                        .dir(350)
                        .build());

        assertThat(mockHttp.lastPayload())
                .containsExactly(
                        Map.entry("dir", 350L),
                        Map.entry("futureParam", "value"),
                        Map.entry("x", 15.2154636),
                        Map.entry("y", 52.3118895));
    }

    @Test
    void speedCheckReturnsSpeedLimitData() {
        Map<String, Object> fixture = Fixtures.load("road_speed_check_success");
        mockHttp.addResponse("Road", "speedCheck", fixture);

        ApiResponse response = client.road()
                .speedCheck(SpeedCheckRequest.builder(15.2154636, 52.3118895).build());

        assertThat(response.raw()).isEqualTo(fixture);
        assertThat(response.dataAsMap())
                .containsEntry("speedLimit", 140L)
                .containsEntry("speedLimitTruck", 80L)
                .containsEntry("urbanArea", false);
    }

    @Test
    void speedCheckRejectsNullRequest() {
        assertThatThrownBy(() -> client.road().speedCheck(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Road.speedCheck")
                .hasMessageContaining("request is required")
                .hasMessageContaining("SpeedCheckRequest.builder(x, y)");
        assertThat(mockHttp.requestCount()).isZero();
    }

    // --- speedCheckMulti ---

    @Test
    void speedCheckMultiSendsPointsAsObjectsWithSession() {
        mockHttp.addResponse("Road", "speedCheckMulti", Fixtures.load("road_speed_check_multi_success"));

        client.road().speedCheckMulti(List.of(WITH_DIR, NO_DIR));

        assertProtectedCall("speedCheckMulti");
        assertThat(mockHttp.lastPayload())
                .containsExactly(Map.entry(
                        "points",
                        List.of(
                                Map.of("x", 15.2154636, "y", 52.3118895, "dir", 350L),
                                Map.of("x", 13.8412285, "y", 52.3111549))));
        assertThat(mockHttp.lastRequest().body())
                .contains("{\"x\":15.2154636,\"y\":52.3118895,\"dir\":350}")
                .contains("{\"x\":13.8412285,\"y\":52.3111549}");
    }

    @Test
    void speedCheckMultiNamedPointsOverrideExtra() {
        mockHttp.addResponse("Road", "speedCheckMulti", Fixtures.load("road_speed_check_multi_success"));
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("points", List.of());
        extra.put("futureParam", "value");

        client.road().speedCheckMulti(List.of(NO_DIR), extra);

        assertThat(mockHttp.lastPayload())
                .containsExactly(
                        Map.entry("points", List.of(Map.of("x", 13.8412285, "y", 52.3111549))),
                        Map.entry("futureParam", "value"));
        assertThat(extra).containsEntry("points", List.of());
    }

    @Test
    void speedCheckMultiCopiesThePoints() {
        mockHttp.addResponse("Road", "speedCheckMulti", Fixtures.load("road_speed_check_multi_success"));
        List<SpeedCheckPoint> points = new ArrayList<>(List.of(WITH_DIR));

        client.road().speedCheckMulti(points, null);

        assertThat(points).containsExactly(WITH_DIR);
        assertThat(mockHttp.lastPayload().get("points"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .hasSize(1);
    }

    @Test
    void speedCheckMultiReturnsOneResultPerPoint() {
        Map<String, Object> fixture = Fixtures.load("road_speed_check_multi_success");
        mockHttp.addResponse("Road", "speedCheckMulti", fixture);

        ApiResponse response = client.road().speedCheckMulti(List.of(WITH_DIR, NO_DIR));

        assertThat(response.raw()).isEqualTo(fixture);
        assertThat(response.dataAsList()).hasSize(2);
        assertThat(response.dataAsList().get(0))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("speedLimit", 140L);
        assertThat(response.dataAsList().get(1))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("speedLimit", 0L);
    }

    @Test
    void speedCheckMultiRejectsNullEmptyAndNullItems() {
        assertThatThrownBy(() -> client.road().speedCheckMulti(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Road.speedCheckMulti")
                .hasMessageContaining("points is required but was null")
                .hasMessageContaining("new SpeedCheckPoint(");
        assertThatThrownBy(() -> client.road().speedCheckMulti(List.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("points is required but was empty");
        assertThatThrownBy(() -> client.road().speedCheckMulti(Arrays.asList(WITH_DIR, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("points[1] is null");
        assertThat(mockHttp.requestCount()).isZero();
    }

    @Test
    void constructorRejectsNullClient() {
        assertThatThrownBy(() -> new Road(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("new Road")
                .hasMessageContaining("client is required")
                .hasMessageContaining("client.road()");
    }
}
