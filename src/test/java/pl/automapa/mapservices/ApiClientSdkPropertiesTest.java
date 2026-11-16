package pl.automapa.mapservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ApiClientSdkPropertiesTest {

    @Test
    void readsTrimmedVersionFromProperties() {
        assertThat(ApiClient.readSdkVersion(stream("version= 1.2.3 \n"))).isEqualTo("1.2.3");
    }

    @Test
    void missingResourceExplainsHowToFix() {
        assertThatThrownBy(() -> ApiClient.readSdkVersion(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sdk.properties is missing")
                .hasMessageContaining("rebuild");
    }

    @Test
    void unfilteredVersionIsRejected() {
        assertThatThrownBy(() -> ApiClient.readSdkVersion(stream("version=${project.version}\n")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no resolved 'version'");
    }

    @Test
    void blankVersionIsRejected() {
        assertThatThrownBy(() -> ApiClient.readSdkVersion(stream("other=1\n")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no resolved 'version'");
    }

    @Test
    void readFailureIsWrappedWithResourceName() {
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("disk error");
            }
        };

        assertThatThrownBy(() -> ApiClient.readSdkVersion(broken))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("sdk.properties")
                .hasMessageContaining("disk error");
    }

    private static InputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
