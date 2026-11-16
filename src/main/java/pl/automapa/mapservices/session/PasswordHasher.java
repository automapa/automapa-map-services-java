package pl.automapa.mapservices.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Computes the credential expected by {@code Session.generateSession}: {@code md5(md5(password) + salt)}.
 *
 * <p>Both MD5 digests are written as lowercase hexadecimal strings; text is encoded as UTF-8.
 */
public final class PasswordHasher {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private PasswordHasher() {}

    /**
     * Hashes the password with the salt returned by {@code Session.getSalt}.
     *
     * @param password API password
     * @param salt salt from {@code Session.getSalt}
     * @return {@code md5(md5(password) + salt)} as 32 lowercase hexadecimal characters
     * @throws IllegalArgumentException when the password or the salt is {@code null}
     */
    public static String hash(String password, String salt) {
        if (password == null) {
            throw new IllegalArgumentException("PasswordHasher: password is required; pass the API password");
        }
        if (salt == null) {
            throw new IllegalArgumentException(
                    "PasswordHasher: salt is required; pass result.salt from Session.getSalt");
        }
        return md5Hex(md5Hex(password) + salt);
    }

    private static String md5Hex(String text) {
        byte[] digest = md5().digest(text.getBytes(StandardCharsets.UTF_8));
        char[] hex = new char[digest.length * 2];
        for (int i = 0; i < digest.length; i++) {
            hex[i * 2] = HEX[(digest[i] >> 4) & 0x0F];
            hex[i * 2 + 1] = HEX[digest[i] & 0x0F];
        }
        return new String(hex);
    }

    private static MessageDigest md5() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "MD5 is not available in this JVM, although every Java platform must"
                            + " provide it; use a standard JDK 17+",
                    e);
        }
    }
}
