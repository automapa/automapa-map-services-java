package pl.automapa.mapservices.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Expected values were computed with {@code printf '%s' "$(printf '%s' "$password" | md5sum | cut -d' ' -f1)$salt" |
 * md5sum} inside the build container.
 */
class PasswordHasherTest {

    @Test
    void hashMatchesApiFormulaForAsciiPassword() {
        assertThat(PasswordHasher.hash("test-pass", "xNnlXNS3Bq")).isEqualTo("a53555c51b3cadaf99501cc687d715f6");
    }

    @Test
    void hashOfEmptyPassword() {
        assertThat(PasswordHasher.hash("", "xNnlXNS3Bq")).isEqualTo("1e9b26e34ed268b2847012cc753ba168");
    }

    @Test
    void hashWithEmptySalt() {
        assertThat(PasswordHasher.hash("test-pass", "")).isEqualTo("1456b1684af9b59611f619b112dc3df4");
    }

    @Test
    void hashEncodesPasswordAsUtf8() {
        // "zolw-Zazolc" with Polish letters: z-dot, o-acute, l-stroke, c-acute (built from code points).
        String polish =
                new String(new int[] {0x17C, 0xF3, 0x142, 'w', '-', 'Z', 'a', 0x17C, 0xF3, 0x142, 0x107}, 0, 11);

        assertThat(PasswordHasher.hash(polish, "s4lt")).isEqualTo("fceddf7d3e63a22e4a5805927e81328d");
    }

    @Test
    void hashIsLowercaseHexOf32CharactersAndNeverThePlainPassword() {
        String hash = PasswordHasher.hash("test-pass", "any-salt");

        assertThat(hash).hasSize(32).matches("[0-9a-f]{32}").isNotEqualTo("test-pass");
    }

    @Test
    void rejectsNullArguments() {
        assertThatThrownBy(() -> PasswordHasher.hash(null, "salt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("password");
        assertThatThrownBy(() -> PasswordHasher.hash("pass", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("salt");
    }
}
