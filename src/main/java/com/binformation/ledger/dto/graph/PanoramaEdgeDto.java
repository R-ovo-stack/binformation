package com.binformation.ledger.dto.graph;

/**
 * 资产全景图 · 跨资产边。
 *
 * @param type DERIVE 派生输入；ENDPOINT_LINK 共享落点衔接（上游资产某流向目标 = 下游资产某流向源）
 */
public record PanoramaEdgeDto(
        String id,
        Long sourceAssetId,
        Long targetAssetId,
        String type,
        String label,
        Long derivationId,
        Long endpointId,
        String endpointLabel
) {
}
