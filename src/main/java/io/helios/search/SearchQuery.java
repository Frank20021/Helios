package io.helios.search;

import java.util.UUID;

public record SearchQuery(
        String text,
        UUID repositoryId,
        String language,
        String fileExtension,
        int page,
        int pageSize,
        String mode,
        double alpha,
        boolean hasAlpha
) {
}
