package com.binformation.ledger.dto.flow;

public record ExecutorOptionDto(
        Long id,
        String name,
        String code,
        String kind,
        Long defaultHostId,
        String defaultHostLabel
) {
}
