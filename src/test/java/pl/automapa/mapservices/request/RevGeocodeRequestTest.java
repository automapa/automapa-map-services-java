package pl.automapa.mapservices.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RevGeocodeRequestTest {

    private static final Point DOMANIEWSKA = new Point(21.0073642, 52.18288);

    @Test
    void pointAsListAndAlwaysParams() {
        Map<String, Object> params =
                RevGeocodeRequest.builder(DOMANIEWSKA).build().toParams();

        assertThat(params.keySet()).containsExactly("point", "params");
        assertThat(params.get("point")).isEqualTo(List.of(21.0073642, 52.18288));
        assertThat(params.get("params")).isEqualTo(Map.of());
    }

    @Test
    void optionsGoToParamsInPhpOrder() {
        Map<String, Object> params = RevGeocodeRequest.builder(DOMANIEWSKA)
                .getPostalCode(true)
                .snapToBldRadius(50)
                .snapToBld(true)
                .build()
                .toParams();

        assertThat(params.get("params"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsExactly(
                        Map.entry("snapToBld", true),
                        Map.entry("snapToBldRadius", 50),
                        Map.entry("getPostalCode", true));
    }

    @Test
    void falseIsSentAndNullIsLeftOut() {
        Map<String, Object> params = RevGeocodeRequest.builder(DOMANIEWSKA)
                .snapToBld(false)
                .snapToBldRadius(null)
                .getPostalCode(null)
                .build()
                .toParams();

        assertThat(params.get("params")).isEqualTo(Map.of("snapToBld", false));
    }

    @Test
    void namedPointAndParamsOverrideExtra() {
        Map<String, Object> params = RevGeocodeRequest.builder(DOMANIEWSKA)
                .extra("point", List.of(0, 0))
                .extra("params", Map.of("snapToBld", true))
                .extra("future", 1)
                .build()
                .toParams();

        assertThat(params.keySet()).containsExactly("point", "params", "future");
        assertThat(params.get("point")).isEqualTo(List.of(21.0073642, 52.18288));
        assertThat(params.get("params")).isEqualTo(Map.of());
    }

    @Test
    void nullPointIsRejected() {
        assertThatThrownBy(() -> RevGeocodeRequest.builder(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RevGeocodeRequest")
                .hasMessageContaining("point is required")
                .hasMessageContaining("new Point(");
    }
}
