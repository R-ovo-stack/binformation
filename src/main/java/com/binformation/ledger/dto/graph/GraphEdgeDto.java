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
        List<GraphPathDto> paths,
        /** 是否来自派生输入资产的前置流程（或派生桥接边） */
        boolean upstream,
        /** 该边所属资产（当前资产或前置输入资产） */
        Long fromAssetId,
        String fromAssetName
) {
}
