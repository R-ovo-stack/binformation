package com.binformation.ledger.dto.lineage;

import java.util.List;

public record SystemConsumedAssetDto(
        Long assetId,
        String assetName,
        String assetCode,
        String dataType,
        String status,
        String role,
        List<LineageFlowRefDto> flows
) {
}
