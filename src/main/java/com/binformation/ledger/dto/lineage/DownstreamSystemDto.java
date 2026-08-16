package com.binformation.ledger.dto.lineage;

import java.util.List;

public record DownstreamSystemDto(
        Long systemId,
        String systemName,
        String systemBreadcrumb,
        String zoneName,
        String role,
        List<LineageFlowRefDto> flows
) {
}
