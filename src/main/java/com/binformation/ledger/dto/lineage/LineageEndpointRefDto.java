package com.binformation.ledger.dto.lineage;

public record LineageEndpointRefDto(
        Long id,
        String name,
        String type,
        String breadcrumb
) {
}
