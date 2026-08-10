package com.binformation.ledger.dto.graph;

import java.util.List;

/**
 * 画布边，对应一条 flow（单源单目标）。
 */
public record GraphEdgeDto(
        String id,
        Long flowId,
        String source,
        String target,
        String purpose,
        boolean primary,
        String status,
        String remark,
        List<GraphPathDto> paths
) {
}
