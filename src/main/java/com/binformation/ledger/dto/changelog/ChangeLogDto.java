package com.binformation.ledger.dto.changelog;

import java.time.LocalDateTime;
import java.util.List;

public record ChangeLogDto(
        Long id,
        String entityType,
        Long entityId,
        String action,
        String summary,
        String operator,
        LocalDateTime operatedAt,
        Long relatedAssetId,
        String remark,
        List<ChangeLogItemDto> items
) {
}
