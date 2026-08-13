package com.binformation.ledger.dto.graph;

/**
 * 资产全景图 · 资产节点（血缘视角）。
 */
public record PanoramaAssetNodeDto(
        Long assetId,
        String name,
        String code,
        String dataType,
        String status,
        int primaryFlowCount,
        int derivationInCount,
        int derivationOutCount
) {
}
