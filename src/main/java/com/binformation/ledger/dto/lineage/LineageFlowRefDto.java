package com.binformation.ledger.dto.lineage;

public record LineageFlowRefDto(
        Long id,
        Long assetId,
        String assetName,
        String purpose,
        String status,
        boolean primary,
        LineageEndpointRefDto source,
        LineageEndpointRefDto target
) {
}
