package pl.automapa.mapservices.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SegmentInfoRequestTest {

    @Test
    void onlyCoordinatesByDefault() {
        Map<String, Object> params =
                SegmentInfoRequest.builder(21.00736, 52.18288).build().toParams();

        assertThat(params).containsExactly(Map.entry("x", 21.00736), Map.entry("y", 52.18288));
    }

    @Test
    void allOptionsInPhpOrder() {
        Map<String, Object> params = SegmentInfoRequest.builder(21.00736, 52.18288)
                .useHighways(-1)
                .useFerries(0)
                .useTolls(1)
                .ignorePhysicallyBlockedSegs(true)
                .ignoreSegmentsUnderConstruction(false)
                .treatTruckTollsAsTolls(true)
                .ignoreNoWaySegments(false)
                .minCategory(2)
                .distLimit(100)
                .dir(90)
                .build()
                .toParams();

        assertThat(params)
                .containsExactly(
                        Map.entry("x", 21.00736),
                        Map.entry("y", 52.18288),
                        Map.entry("dir", 90),
                        Map.entry("distLimit", 100),
                        Map.entry("minCategory", 2),
                        Map.entry("ignoreNoWaySegments", false),
                        Map.entry("treatTruckTollsAsTolls", true),
                        Map.entry("ignoreSegmentsUnderConstruction", false),
                        Map.entry("ignorePhysicallyBlockedSegs", true),
                        Map.entry("useTolls", 1),
                        Map.entry("useFerries", 0),
                        Map.entry("useHighways", -1));
    }

    @Test
    void nullOptionsAreLeftOut() {
        Map<String, Object> params = SegmentInfoRequest.builder(21.0, 52.0)
                .dir(90)
                .dir(null)
                .distLimit(null)
                .minCategory(null)
                .ignoreNoWaySegments(null)
                .treatTruckTollsAsTolls(null)
                .ignoreSegmentsUnderConstruction(null)
                .ignorePhysicallyBlockedSegs(null)
                .useTolls(null)
                .useFerries(null)
                .useHighways(null)
                .build()
                .toParams();

        assertThat(params.keySet()).containsExactly("x", "y");
    }

    @Test
    void namedOverrideExtraAndExtraIsKept() {
        Map<String, Object> params = SegmentInfoRequest.builder(21.0, 52.0)
                .extra("distLimit", 999)
                .extra("futureParam", "value")
                .distLimit(200)
                .build()
                .toParams();

        assertThat(params)
                .containsExactly(
                        Map.entry("distLimit", 200),
                        Map.entry("futureParam", "value"),
                        Map.entry("x", 21.0),
                        Map.entry("y", 52.0));
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsNonFiniteX(double x) {
        assertThatThrownBy(() -> SegmentInfoRequest.builder(x, 52.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SegmentInfoRequest.builder")
                .hasMessageContaining("x (longitude)")
                .hasMessageContaining("finite")
                .hasMessageContaining("SegmentInfoRequest.builder(21.00736, 52.18288)");
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsNonFiniteY(double y) {
        assertThatThrownBy(() -> SegmentInfoRequest.builder(21.0, y))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("y (latitude)")
                .hasMessageContaining("finite");
    }
}
