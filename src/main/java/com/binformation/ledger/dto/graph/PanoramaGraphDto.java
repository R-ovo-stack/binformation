package com.binformation.ledger.dto.graph;

import java.util.List;

/**
 * 资产全景图（P0：血缘视角）API 返回体。
 */
public record PanoramaGraphDto(
        List<PanoramaAssetNodeDto> nodes,
        List<PanoramaEdgeDto> edges,
        int assetCount,
        int edgeCount
) {
}
