package com.binformation.ledger.dto.graph;

/**
 * 安全区分组，供前端画布分区渲染。
 */
public record GraphGroupDto(
        String id,
        Long zoneEndpointId,
        String label
) {
}
