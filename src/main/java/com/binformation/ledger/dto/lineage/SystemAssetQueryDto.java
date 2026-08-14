package com.binformation.ledger.dto.lineage;

import java.util.List;

public record SystemAssetQueryDto(
        Long systemId,
        String systemName,
        String systemBreadcrumb,
        String zoneName,
        int assetCount,
        List<SystemConsumedAssetDto> assets
) {
}
