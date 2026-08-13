package com.binformation.ledger.dto.flow;

import java.util.List;

public record FlowPathDto(
        Long id,
        String name,
        boolean enabled,
        int sortOrder,
        String remark,
        List<FlowStepDto> steps
) {
}
