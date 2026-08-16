package com.binformation.ledger.dto.lineage;

public record SystemOptionDto(
        Long id,
        String name,
        String breadcrumb,
        String zoneName,
        String status
) {
}
