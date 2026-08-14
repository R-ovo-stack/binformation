package com.binformation.ledger.dto.lineage;

import java.util.List;

public record AssetDownstreamQueryDto(
        Long assetId,
        String assetName,
        String assetCode,
        String dataType,
        int systemCount,
        List<DownstreamSystemDto> systems
) {
}
