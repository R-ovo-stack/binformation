package com.binformation.ledger.dto.endpoint;

import java.util.List;

public record EndpointImportResultDto(
        int totalRows,
        int created,
        int skipped,
        List<EndpointImportRowError> errors
) {
}
