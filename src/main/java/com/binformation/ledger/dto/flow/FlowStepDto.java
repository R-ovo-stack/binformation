package com.binformation.ledger.dto.flow;

public record FlowStepDto(
        Long id,
        Integer seq,
        Long hostId,
        String hostLabel,
        Long executorId,
        String executorName,
        String method,
        String remark
) {
}
