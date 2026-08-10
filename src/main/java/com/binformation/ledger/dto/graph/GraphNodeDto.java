package com.binformation.ledger.dto.graph;

/**
 * 画布节点：落点（ENDPOINT）或程序/脚本（EXECUTOR）。
 */
public record GraphNodeDto(
        String id,
        String kind,
        Long endpointId,
        Long executorId,
        String type,
        String label,
        String groupId,
        String breadcrumb,
        Double layoutX,
        Double layoutY
) {
}
