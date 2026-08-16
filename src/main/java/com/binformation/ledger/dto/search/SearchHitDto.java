package com.binformation.ledger.dto.search;

public record SearchHitDto(
        String entityType,
        Long entityId,
        String label,
        String subtitle,
        Long assetId,
        String assetName,
        Long flowId,
        Long endpointId
) {
}
