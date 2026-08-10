package com.binformation.ledger.dto.graph;

public record GraphStepDto(
        int seq,
        Long hostId,
        String hostLabel,
        Long executorId,
        String executorName,
        String method,
        String remark
) {
}
