package com.binformation.ledger.dto.endpoint;

public record EndpointDetailDto(
        Long id,
        String type,
        String name,
        String code,
        Long parentId,
        String parentName,
        Long zoneId,
        String zoneName,
        String breadcrumb,
        String attrs,
        String status,
        String owner,
        String remark
) {
}
