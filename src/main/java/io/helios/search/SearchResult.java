package io.helios.search;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SearchResult(
        String repository,
        @JsonProperty("file_path") String filePath,
        String language,
        @JsonProperty("symbol_name") String symbolName,
        @JsonProperty("start_line") int startLine,
        @JsonProperty("end_line") int endLine,
        String snippet,
        double score,
        @JsonProperty("matched_terms") List<String> matchedTerms
) {
}
