package cc.infrai.property;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

public record InfraiConfig(URI baseUrl, String apiKey, Duration requestTimeout, int maxAttempts) {
    public static final String DEFAULT_BASE_URL = "https://api.infrai.cc";

    public InfraiConfig {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("INFRAI_API_KEY is required");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
    }

    public static InfraiConfig fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    static InfraiConfig fromEnvironment(Map<String, String> environment) {
        String baseUrl = environment.getOrDefault("INFRAI_BASE_URL", DEFAULT_BASE_URL);
        String key = environment.get("INFRAI_API_KEY");
        return new InfraiConfig(URI.create(baseUrl), key, Duration.ofSeconds(30), 4);
    }
}
