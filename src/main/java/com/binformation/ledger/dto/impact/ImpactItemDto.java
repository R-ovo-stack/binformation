package com.binformation.ledger.dto.impact;

public record ImpactItemDto(
        Long id,
        String label,
        String entityType,
        Long assetId,
        String assetName,
        Long flowId,
        Long endpointId,
        String role,
        String detail
) {
}
