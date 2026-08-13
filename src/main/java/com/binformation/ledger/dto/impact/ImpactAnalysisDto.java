package com.binformation.ledger.dto.impact;

import java.util.List;

public record ImpactAnalysisDto(
        String entityType,
        Long entityId,
        String entityLabel,
        String action,
        boolean canProceed,
        String summary,
        List<ImpactGroupDto> blockers,
        List<ImpactGroupDto> warnings
) {
}
