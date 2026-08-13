package com.binformation.ledger.dto.export;

import com.binformation.ledger.dto.flow.FlowDetailDto;

import java.util.List;

public record AssetExportDto(
        Long id,
        String name,
        String code,
        String dataType,
        String status,
        String owner,
        String remark,
        List<FlowDetailDto> flows
) {
}
