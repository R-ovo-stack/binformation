package com.binformation.ledger.dto.impact;

import java.util.List;

public record ImpactGroupDto(
        String kind,
        String severity,
        int count,
        String message,
        List<ImpactItemDto> items
) {
}
