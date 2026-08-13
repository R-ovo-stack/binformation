package com.binformation.ledger.dto.executor;

public record ExecutorDetailDto(
        Long id,
        String name,
        String code,
        String kind,
        Long defaultHostId,
        String defaultHostLabel,
        String status,
        String owner,
        String remark
) {
}
