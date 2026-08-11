package com.binformation.ledger.dto.flow;

public record EndpointOptionDto(
        Long id,
        String type,
        String name,
        String breadcrumb,
        Long zoneId,
        String zoneName
) {
}
