package com.binformation.ledger.dto.flow;

import java.util.List;

public record FlowDetailDto(
        Long id,
        Long assetId,
        String assetName,
        Long sourceEndpointId,
        String sourceEndpointLabel,
        Long targetEndpointId,
        String targetEndpointLabel,
        String purpose,
        boolean primary,
        String status,
        String owner,
        String remark,
        List<FlowPathDto> paths
) {
}
