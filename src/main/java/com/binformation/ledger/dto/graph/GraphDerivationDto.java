package com.binformation.ledger.dto.graph;

import java.util.List;

/**
 * 与当前资产相关的派生/加工（作为输出或输入参与）。
 */
public record GraphDerivationDto(
        Long derivationId,
        String name,
        String status,
        Long outputAssetId,
        String outputAssetName,
        List<GraphDerivationInputDto> inputs,
        Long executorId,
        String executorName,
        Long hostId,
        String hostLabel
) {
}
