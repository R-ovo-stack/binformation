package com.binformation.ledger.dto.graph;

import java.util.List;

/**
 * 一键成图 API 返回体：资产视角的完整流向图数据。
 * 坐标由前端布局引擎计算；若存在已保存布局则附带 layoutX/layoutY。
 */
public record AssetGraphDto(
        Long assetId,
        String assetName,
        String assetCode,
        String dataType,
        List<GraphGroupDto> groups,
        List<GraphNodeDto> nodes,
        List<GraphEdgeDto> edges,
        List<GraphDerivationDto> derivations
) {
}
