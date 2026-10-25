package io.helios.tracing;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Otlp {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Otlp() {
    }

    public static byte[] encode(Span root) {
        try {
            Map<String, Object> resource = new LinkedHashMap<>();
            resource.put("attributes", List.of(attr("service.name", "helios")));
            Map<String, Object> scope = new LinkedHashMap<>();
            scope.put("name", "helios");
            scope.put("version", "phase6");
            List<Map<String, Object>> spans = new ArrayList<>();
            spans.add(toOtlp(root, ""));
            for (Span child : root.children) {
                spans.add(toOtlp(child, root.name));
            }
            Map<String, Object> scopeSpan = new LinkedHashMap<>();
            scopeSpan.put("scope", scope);
            scopeSpan.put("spans", spans);
            Map<String, Object> rs = new LinkedHashMap<>();
            rs.put("resource", resource);
            rs.put("scopeSpans", List.of(scopeSpan));
            return MAPPER.writeValueAsBytes(Map.of("resourceSpans", List.of(rs)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static final class Exporter {
        private final String endpoint;
        private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

        public Exporter(String endpoint) {
            this.endpoint = endpoint;
        }

        public void export(Span root) {
            if (endpoint == null || endpoint.isBlank() || root == null || root.name == null || root.name.isBlank()) {
                return;
            }
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                        .timeout(Duration.ofSeconds(2))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(encode(root)))
                        .build();
                HttpResponse<Void> res = client.send(req, HttpResponse.BodyHandlers.discarding());
                if (res.statusCode() >= 300) {
                    throw new IllegalStateException("otlp export status " + res.statusCode());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception ignored) {
            }
        }
    }

    private static Map<String, Object> toOtlp(Span span, String parent) {
        Instant end = span.end == null ? span.start : span.end;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("traceId", span.traceId);
        out.put("spanId", span.name);
        if (parent != null && !parent.isBlank()) {
            out.put("parentSpanId", parent);
        }
        out.put("name", span.name);
        out.put("startTimeUnixNano", nanos(span.start));
        out.put("endTimeUnixNano", nanos(end));
        if (!span.attrs.isEmpty()) {
            List<Map<String, Object>> attrs = new ArrayList<>();
            span.attrs.forEach((k, v) -> attrs.add(attr(k, v)));
            out.put("attributes", attrs);
        }
        return out;
    }

    private static Map<String, Object> attr(String key, String value) {
        return Map.of("key", key, "value", Map.of("stringValue", value));
    }

    private static long nanos(Instant i) {
        return i.getEpochSecond() * 1_000_000_000L + i.getNano();
    }
}
