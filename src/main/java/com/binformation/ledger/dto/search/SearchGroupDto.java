package com.binformation.ledger.dto.search;

import java.util.List;

public record SearchGroupDto(
        String entityType,
        String label,
        int count,
        List<SearchHitDto> items
) {
}
