package io.helios.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;
import java.time.Duration;

@JsonDeserialize(using = DurationSetting.Deserializer.class)
public record DurationSetting(Duration value) {
    public static DurationSetting of(Duration value) {
        return new DurationSetting(value);
    }

    public static DurationSetting parse(String raw) {
        return new DurationSetting(parseGo(raw));
    }

    public static Duration parseGo(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("empty duration");
        }
        raw = raw.trim();
        if (raw.startsWith("-")) {
            throw new IllegalArgumentException("duration must be positive");
        }
        long totalNanos = 0;
        int i = 0;
        while (i < raw.length()) {
            int start = i;
            while (i < raw.length() && (Character.isDigit(raw.charAt(i)) || raw.charAt(i) == '.')) {
                i++;
            }
            if (i == start) {
                throw new IllegalArgumentException("invalid duration " + raw);
            }
            double n = Double.parseDouble(raw.substring(start, i));
            int unitStart = i;
            while (i < raw.length() && !Character.isDigit(raw.charAt(i)) && raw.charAt(i) != '.') {
                i++;
            }
            String unit = raw.substring(unitStart, i);
            totalNanos += switch (unit) {
                case "ns" -> (long) n;
                case "us", "µs" -> (long) (n * 1_000);
                case "ms" -> (long) (n * 1_000_000);
                case "s" -> (long) (n * 1_000_000_000);
                case "m" -> (long) (n * 60_000_000_000L);
                case "h" -> (long) (n * 3_600_000_000_000L);
                default -> throw new IllegalArgumentException("invalid duration unit " + unit);
            };
        }
        return Duration.ofNanos(totalNanos);
    }

    public static final class Deserializer extends JsonDeserializer<DurationSetting> {
        @Override
        public DurationSetting deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return parse(p.getValueAsString());
        }
    }
}
