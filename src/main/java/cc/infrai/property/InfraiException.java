package cc.infrai.property;

import java.util.Map;

public final class InfraiException extends Exception {
    private final String code;
    private final int statusCode;
    private final Map<String, Object> details;

    public InfraiException(String code, int statusCode, Map<String, Object> details) {
        super(details.getOrDefault("message", code).toString());
        this.code = code;
        this.statusCode = statusCode;
        this.details = Map.copyOf(details);
    }

    public String code() { return code; }
    public int statusCode() { return statusCode; }
    public Map<String, Object> details() { return details; }
    public boolean isClientRejection() { return statusCode >= 400 && statusCode < 500 && statusCode != 429; }
}
