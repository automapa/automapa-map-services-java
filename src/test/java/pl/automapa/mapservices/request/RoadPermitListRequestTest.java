package pl.automapa.mapservices.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoadPermitListRequestTest {

    @Test
    void emptyBuilderSendsNoParams() {
        assertThat(RoadPermitListRequest.builder().build().toParams()).isEmpty();
    }

    @Test
    void sendsIdAndDocumentNameWhenSet() {
        RoadPermitListRequest request = RoadPermitListRequest.builder()
                .id("60a79ba2")
                .documentName("ZDiK.TO.461.219.2020.AO")
                .build();

        assertThat(request.toParams())
                .containsExactly(Map.entry("id", "60a79ba2"), Map.entry("document_name", "ZDiK.TO.461.219.2020.AO"));
    }

    @Test
    void nullFiltersAreLeftOut() {
        RoadPermitListRequest request =
                RoadPermitListRequest.builder().id(null).documentName(null).build();

        assertThat(request.toParams()).isEmpty();
    }

    @Test
    void namedFiltersOverrideExtra() {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("document_name", "from-extra");
        extra.put("custom", 1);

        RoadPermitListRequest request = RoadPermitListRequest.builder()
                .extra(extra)
                .documentName("named")
                .build();

        assertThat(request.toParams()).containsExactly(Map.entry("document_name", "named"), Map.entry("custom", 1));
    }

    @Test
    void extraIdIsKeptWhenNamedIdIsNull() {
        RoadPermitListRequest request =
                RoadPermitListRequest.builder().extra("id", "from-extra").build();

        assertThat(request.toParams()).containsExactly(Map.entry("id", "from-extra"));
    }
}
