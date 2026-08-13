package com.binformation.ledger.dto.flow;

public record FlowSummaryDto(
        Long id,
        Long assetId,
        Long sourceEndpointId,
        String sourceEndpointLabel,
        Long targetEndpointId,
        String targetEndpointLabel,
        String purpose,
        boolean primary,
        String status,
        String remark,
        int pathCount,
        int stepCount
) {
}
