package pl.automapa.mapservices.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RoadPermitEnvironmentTest {

    @Test
    void apiValuesMatchTheApi() {
        assertThat(RoadPermitEnvironment.PROD.apiValue()).isEqualTo("prod");
        assertThat(RoadPermitEnvironment.UAT.apiValue()).isEqualTo("uat");
    }

    @Test
    void hasOnlyProdAndUat() {
        assertThat(RoadPermitEnvironment.values())
                .containsExactly(RoadPermitEnvironment.PROD, RoadPermitEnvironment.UAT);
    }
}
