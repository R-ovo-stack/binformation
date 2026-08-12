package com.binformation.ledger.dto.flow;

public record EndpointOptionDto(
        Long id,
        String type,
        String name,
        Long parentId,
        String breadcrumb,
        Long zoneId,
        String zoneName
) {
}
