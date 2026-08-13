package com.binformation.ledger.dto.endpoint;

public record EndpointImportRowError(
        int row,
        String name,
        String message
) {
}
