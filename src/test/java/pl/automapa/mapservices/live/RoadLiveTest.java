package pl.automapa.mapservices.live;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.automapa.mapservices.request.SegmentInfoRequest;
import pl.automapa.mapservices.request.SpeedCheckPoint;
import pl.automapa.mapservices.request.SpeedCheckRequest;
import pl.automapa.mapservices.response.ApiResponse;

/** Road service ({@code getSegmentInfo}, {@code speedCheck}, {@code speedCheckMulti}) on the live API. */
class RoadLiveTest extends LiveApiTestBase {

    @Override
    protected String suiteName() {
        return "road";
    }

    @Test
    void getSegmentInfoReturnsSegmentNearDomaniewska() {
        ApiResponse response = callLive(() -> buildApiClient()
                .road()
                .getSegmentInfo(SegmentInfoRequest.builder(21.00736, 52.18288).build()));

        assertThat(response.status()).isEqualTo(200);
        Map<String, Object> data = response.dataAsMap();
        assertThat(data).as("getSegmentInfo result").containsKeys("segment", "attractedX", "pureDistance");
        assertThat(data.get("segment"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsKeys("id", "speedLimit", "streetName");
        assertThat(response.raw()).containsKey("result");
    }

    @Test
    void speedCheckWithDirectionReturnsSpeedLimits() {
        ApiResponse response = callLive(() -> buildApiClient()
                .road()
                .speedCheck(SpeedCheckRequest.builder(15.2154636, 52.3118895)
                        .dir(350)
                        .build()));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.dataAsMap())
                .as("speedCheck result")
                .containsKeys("speedLimit", "speedLimitTruck", "urbanArea");
        assertThat(response.raw()).containsKey("result");
    }

    @Test
    void speedCheckMultiReturnsOneResultPerPoint() {
        ApiResponse response = callLive(() -> buildApiClient()
                .road()
                .speedCheckMulti(List.of(
                        new SpeedCheckPoint(15.2154636, 52.3118895, 350),
                        new SpeedCheckPoint(13.8412285, 52.3111549))));

        assertThat(response.status()).isEqualTo(200);
        List<Object> data = response.dataAsList();
        assertThat(data).as("one result per point").hasSize(2);
        assertThat(data.get(0))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsKeys("speedLimit", "speedLimitTruck", "urbanArea");
        assertThat(response.raw()).containsKey("result");
    }
}
