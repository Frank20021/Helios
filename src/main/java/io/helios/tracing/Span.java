package io.helios.tracing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Span {
    public String traceId = "";
    public String name = "";
    public String parent = "";
    public Instant start = Instant.now();
    public Instant end;
    public Map<String, String> attrs = new LinkedHashMap<>();
    public List<Span> children = new ArrayList<>();
}
