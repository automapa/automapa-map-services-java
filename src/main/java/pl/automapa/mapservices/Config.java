package pl.automapa.mapservices;

import java.time.Duration;
import java.util.Map;

/**
 * Immutable configuration of the Automapa API client: credentials and connection settings.
 *
 * <pre>{@code
 * Config config = Config.builder(System.getenv("AUTOMAPA_API_KEY"), System.getenv("AUTOMAPA_API_PASSWORD"))
 *         .baseUrl("https://api.automapa.pl/")
 *         .build();
 * }</pre>
 *
 * <p>{@link #fromEnvironment()} builds the same configuration from the {@code AUTOMAPA_API_KEY} and
 * {@code AUTOMAPA_API_PASSWORD} environment variables. {@link #toString()} never reveals the password. Instances
 * are thread safe.
 */
public final class Config {

    /** Default API base URL. */
    public static final String DEFAULT_BASE_URL = "https://api.automapa.pl/";

    /** Default API version path segment. */
    public static final String DEFAULT_VERSION = "v3";

    /** Default limit for connecting and, separately, for receiving the response of one request. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    /** Default response format name. */
    public static final String DEFAULT_FORMAT = "native";

    /** Environment variable holding the API key. */
    public static final String ENV_API_KEY = "AUTOMAPA_API_KEY";

    /** Environment variable holding the API password. */
    public static final String ENV_API_PASSWORD = "AUTOMAPA_API_PASSWORD";

    private static final String ENV_FIX = "export it in the environment of the process or, in this SDK repository,"
            + " run `cp .env.example .env` and fill in the value";

    private final String key;
    private final String password;
    private final String baseUrl;
    private final String version;
    private final String userAgent;
    private final Duration timeout;
    private final String defaultFormat;

    private Config(Builder builder) {
        this.key = builder.key;
        this.password = builder.password;
        this.baseUrl = builder.baseUrl;
        this.version = builder.version;
        this.userAgent = builder.userAgent;
        this.timeout = builder.timeout;
        this.defaultFormat = builder.defaultFormat;
    }

    /**
     * Starts building a configuration.
     *
     * @param key API key (usually the {@code AUTOMAPA_API_KEY} environment variable)
     * @param password API password (usually the {@code AUTOMAPA_API_PASSWORD} environment variable)
     * @return the builder
     * @throws IllegalArgumentException when the key or the password is {@code null} or blank
     */
    public static Builder builder(String key, String password) {
        return new Builder(
                requireText(key, "API key", "pass the value of AUTOMAPA_API_KEY"),
                requireText(password, "API password", "pass the value of AUTOMAPA_API_PASSWORD"));
    }

    /**
     * Builds a configuration with default options from the {@code AUTOMAPA_API_KEY} and
     * {@code AUTOMAPA_API_PASSWORD} environment variables of the current process. The library does not read
     * {@code .env} files.
     *
     * @return the configuration
     * @throws IllegalArgumentException when a variable is not set or empty; the message names the variable
     */
    public static Config fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    /**
     * Builds a configuration with default options from the {@code AUTOMAPA_API_KEY} and
     * {@code AUTOMAPA_API_PASSWORD} entries of the given environment map.
     *
     * @param env environment variables, usually {@link System#getenv()}
     * @return the configuration
     * @throws IllegalArgumentException when {@code env} is {@code null} or a variable is not set or empty; the
     *     message names the variable but never its value
     */
    public static Config fromEnvironment(Map<String, String> env) {
        if (env == null) {
            throw new IllegalArgumentException(
                    "Config.fromEnvironment: the environment map is required but was null; pass System.getenv()");
        }
        return new Builder(requireEnv(env, ENV_API_KEY), requireEnv(env, ENV_API_PASSWORD)).build();
    }

    /**
     * Returns the API key.
     *
     * @return the key
     */
    public String getKey() {
        return key;
    }

    /**
     * Returns the API password used to open a session.
     *
     * @return the password
     */
    public String getPassword() {
        return password;
    }

    /**
     * Returns the API base URL.
     *
     * @return the base URL, e.g. {@code https://api.automapa.pl/}
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Returns the API version path segment.
     *
     * @return the version, e.g. {@code v3}
     */
    public String getVersion() {
        return version;
    }

    /**
     * Returns the value of the {@code User-Agent} header.
     *
     * @return the user agent, by default {@code automapa-java-sdk/<SDK version>}
     */
    public String getUserAgent() {
        return userAgent;
    }

    /**
     * Returns the limit for connecting and, separately, for receiving the response of one request.
     *
     * @return the timeout, by default 30 seconds
     */
    public Duration getTimeout() {
        return timeout;
    }

    /**
     * Returns the name of the response format used when a call does not pass {@code format}.
     *
     * @return the format name, by default {@code native}
     */
    public String getDefaultFormat() {
        return defaultFormat;
    }

    /**
     * Describes the configuration with the API key and the password masked as {@code ***}.
     *
     * @return the description
     */
    @Override
    public String toString() {
        return "Config{key=***, password=***, baseUrl=" + baseUrl + ", version=" + version + ", userAgent=" + userAgent
                + ", timeout=" + timeout + ", defaultFormat=" + defaultFormat + "}";
    }

    private static String requireText(String value, String name, String fix) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Config: " + name + " is required but was " + (value == null ? "null" : "empty") + "; " + fix);
        }
        return value;
    }

    private static String requireEnv(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Config.fromEnvironment: environment variable " + name + " is "
                    + (value == null ? "not set" : "empty") + "; " + ENV_FIX);
        }
        return value;
    }

    private static String requireHttpUrl(String baseUrl) {
        String value = requireText(baseUrl, "baseUrl", "use e.g. " + DEFAULT_BASE_URL);
        if (!value.regionMatches(true, 0, "http://", 0, 7) && !value.regionMatches(true, 0, "https://", 0, 8)) {
            throw new IllegalArgumentException("Config: baseUrl must start with http:// or https:// but was \"" + value
                    + "\"; use e.g. " + DEFAULT_BASE_URL);
        }
        return value;
    }

    /** Builder of {@link Config}; not thread safe. */
    public static final class Builder {

        private final String key;
        private final String password;
        private String baseUrl = DEFAULT_BASE_URL;
        private String version = DEFAULT_VERSION;
        private String userAgent = "automapa-java-sdk/" + ApiClient.SDK_VERSION;
        private Duration timeout = DEFAULT_TIMEOUT;
        private String defaultFormat = DEFAULT_FORMAT;

        private Builder(String key, String password) {
            this.key = key;
            this.password = password;
        }

        /**
         * Sets the API base URL.
         *
         * @param baseUrl base URL, e.g. {@code https://api.automapa.pl/}
         * @return this builder
         * @throws IllegalArgumentException when the value is {@code null}, blank or does not start with
         *     {@code http://} or {@code https://}
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = requireHttpUrl(baseUrl);
            return this;
        }

        /**
         * Sets the API version path segment.
         *
         * @param version version, e.g. {@code v3}
         * @return this builder
         * @throws IllegalArgumentException when the value is {@code null} or blank
         */
        public Builder version(String version) {
            this.version = requireText(version, "version", "use e.g. " + DEFAULT_VERSION);
            return this;
        }

        /**
         * Sets the value of the {@code User-Agent} header.
         *
         * @param userAgent user agent, e.g. {@code my-app/1.0}
         * @return this builder
         * @throws IllegalArgumentException when the value is {@code null} or blank
         */
        public Builder userAgent(String userAgent) {
            this.userAgent = requireText(userAgent, "userAgent", "use e.g. my-app/1.0 or keep the default");
            return this;
        }

        /**
         * Sets the limit for connecting and, separately, for receiving the response of one request.
         *
         * @param timeout positive duration, e.g. {@code Duration.ofSeconds(30)}
         * @return this builder
         * @throws IllegalArgumentException when the value is {@code null}, zero or negative
         */
        public Builder timeout(Duration timeout) {
            if (timeout == null) {
                throw new IllegalArgumentException(
                        "Config: timeout is required but was null; pass a positive duration, e.g. Duration.ofSeconds(30)");
            }
            if (timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException(
                        "Config: timeout must be positive but was " + timeout + "; pass e.g. Duration.ofSeconds(30)");
            }
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the response format used when a call does not pass {@code format}.
         *
         * @param defaultFormat format name registered in the client, e.g. {@code native} or {@code google}
         * @return this builder
         * @throws IllegalArgumentException when the value is {@code null} or blank
         */
        public Builder defaultFormat(String defaultFormat) {
            this.defaultFormat =
                    requireText(defaultFormat, "defaultFormat", "use e.g. " + DEFAULT_FORMAT + " or google");
            return this;
        }

        /**
         * Builds the configuration.
         *
         * @return the configuration
         */
        public Config build() {
            return new Config(this);
        }
    }
}
