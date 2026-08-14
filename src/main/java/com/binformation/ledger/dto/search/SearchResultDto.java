package com.binformation.ledger.dto.search;

import java.util.List;

public record SearchResultDto(
        String query,
        int total,
        List<SearchGroupDto> groups
) {
}
