package com.binformation.ledger.dto.graph;

/**
 * 画布节点，对应一个 endpoint。
 */
public record GraphNodeDto(
        String id,
        Long endpointId,
        String type,
        String label,
        String groupId,
        String breadcrumb,
        Double layoutX,
        Double layoutY
) {
}
