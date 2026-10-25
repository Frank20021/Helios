package io.helios.search;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SearchResponse(
        String query,
        String mode,
        double alpha,
        @JsonProperty("total_results") int totalResults,
        @JsonProperty("query_time_ms") double queryTimeMs,
        int page,
        @JsonProperty("page_size") int pageSize,
        boolean cached,
        List<SearchResult> results
) {
    public SearchResponse withCached(boolean cached, double queryTimeMs) {
        return new SearchResponse(query, mode, alpha, totalResults, queryTimeMs, page, pageSize, cached, results);
    }
}
