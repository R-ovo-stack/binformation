package com.binformation.ledger.dto.flow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FlowStepSaveRequest(
        @NotNull Integer seq,
        Long hostId,
        @NotNull Long executorId,
        @NotBlank String method,
        String remark
) {
}
