package com.binformation.ledger.dto.graph;

import java.util.List;

public record GraphPathDto(
        Long pathId,
        String name,
        boolean enabled,
        int sortOrder,
        List<GraphStepDto> steps
) {
}
