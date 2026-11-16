package pl.automapa.mapservices;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ApiClientVersionTest {

    @Test
    void sdkVersionMatchesProjectVersionFromPom() throws Exception {
        String pomVersion = PomModel.load(Path.of("pom.xml").toAbsolutePath()).projectVersion();

        assertThat(pomVersion).isNotBlank();
        assertThat(ApiClient.SDK_VERSION).isEqualTo(pomVersion);
    }
}
