package cc.infrai.property;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class InfraiClient {
    private final InfraiConfig config;
    private final HttpClient http;

    public InfraiClient(InfraiConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(config.requestTimeout()).build());
    }

    InfraiClient(InfraiConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    public String upload(Path file, String requestId) throws IOException, InterruptedException, InfraiException {
        String boundary = "infrai-" + UUID.randomUUID();
        byte[] body = multipart(boundary, file);
        return requireUrl(send("POST", "/v1/image/upload", requestId,
                "multipart/form-data; boundary=" + boundary, HttpRequest.BodyPublishers.ofByteArray(body)));
    }

    public String smartCrop(String image, String aspect, String requestId)
            throws IOException, InterruptedException, InfraiException {
        return requireUrl(json("POST", "/v1/image/smart_crop", requestId,
                Map.of("image", image, "aspect", aspect)));
    }

    public String optimizeAvatar(String image, String requestId)
            throws IOException, InterruptedException, InfraiException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("image", image);
        body.put("width", 512);
        body.put("height", 512);
        body.put("fit", "cover");
        body.put("enlarge", false);
        body.put("format", "webp");
        body.put("store", true);
        return requireUrl(json("POST", "/v1/image/resize", requestId, body));
    }

    public void updateUserAvatar(String userId, String avatarUrl, String requestId)
            throws IOException, InterruptedException, InfraiException {
        json("PATCH", "/v1/auth/user/update/" + encodePath(userId), requestId,
                Map.of("metadata", Map.of("avatar_url", avatarUrl)));
    }

    private Map<String, Object> json(String method, String path, String requestId, Map<String, Object> body)
            throws IOException, InterruptedException, InfraiException {
        return send(method, path, requestId, "application/json",
                HttpRequest.BodyPublishers.ofString(Json.write(body), StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> send(String method, String path, String requestId, String contentType,
                                     HttpRequest.BodyPublisher body)
            throws IOException, InterruptedException, InfraiException {
        for (int attempt = 1; attempt <= config.maxAttempts(); attempt++) {
            HttpRequest request = HttpRequest.newBuilder(config.baseUrl().resolve(path))
                    .timeout(config.requestTimeout())
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", contentType)
                    .header("Accept", "application/json")
                    .header("Idempotency-Key", requestId)
                    .method(method, body)
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            Object decoded;
            try {
                decoded = Json.parse(response.body());
            } catch (IllegalArgumentException invalidJson) {
                if (response.statusCode() >= 500) {
                    throw new IOException("Infrai transport response was not JSON", invalidJson);
                }
                throw new IOException("Expected an Infrai response envelope", invalidJson);
            }
            if (!(decoded instanceof Map<?, ?> rawEnvelope)) {
                throw new IOException("Expected an Infrai response envelope");
            }
            Map<String, Object> envelope = (Map<String, Object>) rawEnvelope;
            if (response.statusCode() == 429 && attempt < config.maxAttempts()) {
                Thread.sleep(retryDelay(response, attempt).toMillis());
                continue;
            }
            if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                Map<String, Object> error = envelope.get("error") instanceof Map<?, ?> rawError
                        ? (Map<String, Object>) rawError : Map.of("message", "Request rejected");
                String code = error.getOrDefault("code", "REQUEST_REJECTED").toString();
                throw new InfraiException(code, response.statusCode(), error);
            }
            if (response.statusCode() >= 500) {
                throw new IOException("Infrai transport request failed with status " + response.statusCode());
            }
            Object data = envelope.get("data");
            return data instanceof Map<?, ?> rawData ? (Map<String, Object>) rawData : Map.of();
        }
        throw new IOException("Retry budget exhausted");
    }

    private static Duration retryDelay(HttpResponse<?> response, int attempt) {
        String value = response.headers().firstValue("Retry-After").orElse("");
        try {
            return Duration.ofSeconds(Math.max(0, Long.parseLong(value)));
        } catch (NumberFormatException ignored) {
            return Duration.ofMillis(250L * (1L << (attempt - 1)));
        }
    }

    private static String requireUrl(Map<String, Object> data) throws IOException {
        Object url = data.get("url");
        if (!(url instanceof String text) || text.isBlank()) {
            throw new IOException("Image response did not contain a URL");
        }
        return text;
    }

    private static byte[] multipart(String boundary, Path file) throws IOException {
        String filename = file.getFileName().toString();
        byte[] bytes = Files.readAllBytes(file);
        String prefix = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename.replace("\"", "") + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n";
        String suffix = "\r\n--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"filename\"\r\n\r\n"
                + filename + "\r\n--" + boundary + "--\r\n";
        byte[] before = prefix.getBytes(StandardCharsets.UTF_8);
        byte[] after = suffix.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[before.length + bytes.length + after.length];
        System.arraycopy(before, 0, result, 0, before.length);
        System.arraycopy(bytes, 0, result, before.length, bytes.length);
        System.arraycopy(after, 0, result, before.length + bytes.length, after.length);
        return result;
    }

    private static String encodePath(String value) {
        return URI.create("https://local/" + value).getRawPath().substring(1);
    }
}
