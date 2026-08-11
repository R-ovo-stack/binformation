package com.binformation.ledger.dto.changelog;

public record ChangeLogItemDto(
        String fieldName,
        String oldValue,
        String newValue
) {
}
