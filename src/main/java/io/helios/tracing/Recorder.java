package io.helios.tracing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Recorder {
    private final int limit;
    private final List<Span> items = new ArrayList<>();

    public Recorder(int limit) {
        this.limit = limit < 1 ? 32 : limit;
    }

    public Span start(String name, Map<String, String> attrs) {
        Span span = new Span();
        span.traceId = UUID.randomUUID().toString();
        span.name = name;
        span.start = Instant.now();
        span.attrs = attrs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attrs);
        return span;
    }

    public synchronized void child(Span root, String name, Map<String, String> attrs) {
        if (root == null) {
            return;
        }
        Instant now = Instant.now();
        Span child = new Span();
        child.traceId = root.traceId;
        child.name = name;
        child.parent = root.name;
        child.start = now;
        child.end = now;
        child.attrs = attrs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attrs);
        root.children.add(child);
    }

    public synchronized Span finish(Span root) {
        if (root == null) {
            return new Span();
        }
        root.end = Instant.now();
        Span copied = copy(root);
        items.add(copied);
        if (items.size() > limit) {
            items.subList(0, items.size() - limit).clear();
        }
        return copied;
    }

    public synchronized Span last() {
        return items.isEmpty() ? null : copy(items.getLast());
    }

    private static Span copy(Span in) {
        Span out = new Span();
        out.traceId = in.traceId;
        out.name = in.name;
        out.parent = in.parent;
        out.start = in.start;
        out.end = in.end;
        out.attrs = new LinkedHashMap<>(in.attrs);
        for (Span child : in.children) {
            out.children.add(copy(child));
        }
        return out;
    }
}
