package pl.automapa.mapservices.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MatrixRequestTest {

    private static final List<Point> POINTS = List.of(
            new Point(21.0073642, 52.2297),
            new Point(18.6282, 54.352),
            new Point(16.9252, 52.4064),
            new Point(19.9448, 50.0647));

    @Test
    void matrixTypeApiValues() {
        assertThat(MatrixType.TIME.apiValue()).isEqualTo(1);
        assertThat(MatrixType.DISTANCE.apiValue()).isEqualTo(2);
        assertThat(MatrixType.values()).containsExactly(MatrixType.TIME, MatrixType.DISTANCE);
    }

    @Test
    void preciseAndSimpleAreAlwaysSentWithFalseDefaults() {
        Map<String, Object> params =
                MatrixRequest.builder(POINTS, MatrixType.TIME).build().toParams();

        assertThat(params)
                .containsExactly(
                        Map.entry(
                                "points",
                                List.of(
                                        List.of(21.0073642, 52.2297),
                                        List.of(18.6282, 54.352),
                                        List.of(16.9252, 52.4064),
                                        List.of(19.9448, 50.0647))),
                        Map.entry("type", 1),
                        Map.entry("precise", false),
                        Map.entry("simple", false));
    }

    @Test
    void distanceTypeAndExplicitFlags() {
        Map<String, Object> params = MatrixRequest.builder(POINTS, MatrixType.DISTANCE)
                .precise(true)
                .simple(true)
                .build()
                .toParams();

        assertThat(params)
                .containsEntry("type", 2)
                .containsEntry("precise", true)
                .containsEntry("simple", true);
    }

    @Test
    void flagsCanBeSetBackToFalse() {
        Map<String, Object> params = MatrixRequest.builder(POINTS, MatrixType.TIME)
                .precise(true)
                .precise(false)
                .simple(true)
                .simple(false)
                .build()
                .toParams();

        assertThat(params).containsEntry("precise", false).containsEntry("simple", false);
    }

    @Test
    void namedParamsOverrideExtra() {
        Map<String, Object> params = MatrixRequest.builder(POINTS, MatrixType.TIME)
                .extra("type", 99)
                .extra("simple", "yes")
                .extra("future", "x")
                .build()
                .toParams();

        assertThat(params.keySet()).containsExactly("type", "simple", "future", "points", "precise");
        assertThat(params).containsEntry("type", 1).containsEntry("simple", false);
    }

    @Test
    void pointsListIsCopied() {
        List<Point> points = new ArrayList<>(POINTS);
        MatrixRequest.Builder builder = MatrixRequest.builder(points, MatrixType.TIME);
        points.clear();

        assertThat((List<?>) builder.build().toParams().get("points")).hasSize(4);
    }

    @Test
    void nullPointsAreRejected() {
        assertThatThrownBy(() -> MatrixRequest.builder(null, MatrixType.TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MatrixRequest.builder")
                .hasMessageContaining("points is required but was null")
                .hasMessageContaining("at least 4 different points");
    }

    @Test
    void emptyPointsAreRejected() {
        assertThatThrownBy(() -> MatrixRequest.builder(List.of(), MatrixType.TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("points is required but was empty");
    }

    @Test
    void nullPointInsideTheListIsRejectedWithItsIndex() {
        List<Point> points = Arrays.asList(new Point(21.0, 52.0), null);

        assertThatThrownBy(() -> MatrixRequest.builder(points, MatrixType.TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("points[1] is null");
    }

    @Test
    void fewerThanFourPointsAreNotValidatedBySdk() {
        Map<String, Object> params = MatrixRequest.builder(List.of(new Point(21.0, 52.0)), MatrixType.TIME)
                .build()
                .toParams();

        assertThat((List<?>) params.get("points")).hasSize(1);
    }

    @Test
    void nullTypeIsRejected() {
        assertThatThrownBy(() -> MatrixRequest.builder(POINTS, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type is required but was null")
                .hasMessageContaining("MatrixType.TIME");
    }
}
