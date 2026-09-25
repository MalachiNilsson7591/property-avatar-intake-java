package cc.infrai.property;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
    private Json() {}

    static Object parse(String source) {
        Parser parser = new Parser(source);
        Object value = parser.value();
        parser.whitespace();
        if (!parser.atEnd()) throw new IllegalArgumentException("Unexpected trailing JSON");
        return value;
    }

    static String write(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) return quote(text);
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        if (value instanceof Map<?, ?> map) {
            StringBuilder out = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) out.append(',');
                first = false;
                out.append(quote(entry.getKey().toString())).append(':').append(write(entry.getValue()));
            }
            return out.append('}').toString();
        }
        if (value instanceof Iterable<?> values) {
            StringBuilder out = new StringBuilder("[");
            boolean first = true;
            for (Object item : values) {
                if (!first) out.append(',');
                first = false;
                out.append(write(item));
            }
            return out.append(']').toString();
        }
        throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass().getName());
    }

    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '\"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('\"').toString();
    }

    private static final class Parser {
        private final String source;
        private int index;

        private Parser(String source) { this.source = source; }
        private boolean atEnd() { return index == source.length(); }
        private void whitespace() {
            while (!atEnd() && Character.isWhitespace(source.charAt(index))) index++;
        }
        private char take() {
            if (atEnd()) throw new IllegalArgumentException("Unexpected end of JSON");
            return source.charAt(index++);
        }
        private Object value() {
            whitespace();
            if (atEnd()) throw new IllegalArgumentException("Expected JSON value");
            return switch (source.charAt(index)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", true);
                case 'f' -> literal("false", false);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }
        private Map<String, Object> object() {
            take();
            Map<String, Object> result = new LinkedHashMap<>();
            whitespace();
            if (!atEnd() && source.charAt(index) == '}') { index++; return result; }
            while (true) {
                whitespace();
                String key = string();
                whitespace();
                if (take() != ':') throw new IllegalArgumentException("Expected ':'");
                result.put(key, value());
                whitespace();
                char separator = take();
                if (separator == '}') return result;
                if (separator != ',') throw new IllegalArgumentException("Expected ','");
            }
        }
        private List<Object> array() {
            take();
            List<Object> result = new ArrayList<>();
            whitespace();
            if (!atEnd() && source.charAt(index) == ']') { index++; return result; }
            while (true) {
                result.add(value());
                whitespace();
                char separator = take();
                if (separator == ']') return result;
                if (separator != ',') throw new IllegalArgumentException("Expected ','");
            }
        }
        private String string() {
            if (take() != '"') throw new IllegalArgumentException("Expected string");
            StringBuilder result = new StringBuilder();
            while (true) {
                char c = take();
                if (c == '"') return result.toString();
                if (c != '\\') { result.append(c); continue; }
                char escaped = take();
                switch (escaped) {
                    case '"', '\\', '/' -> result.append(escaped);
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'u' -> {
                        String hex = source.substring(index, index + 4);
                        result.append((char) Integer.parseInt(hex, 16));
                        index += 4;
                    }
                    default -> throw new IllegalArgumentException("Invalid JSON escape");
                }
            }
        }
        private Object number() {
            int start = index;
            while (!atEnd() && "-+0123456789.eE".indexOf(source.charAt(index)) >= 0) index++;
            String token = source.substring(start, index);
            try {
                return token.contains(".") || token.contains("e") || token.contains("E")
                        ? Double.parseDouble(token) : Long.parseLong(token);
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("Invalid JSON number", error);
            }
        }
        private Object literal(String token, Object value) {
            if (!source.startsWith(token, index)) throw new IllegalArgumentException("Invalid JSON literal");
            index += token.length();
            return value;
        }
    }
}
