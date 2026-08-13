package com.binformation.ledger.dto.executor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ExecutorSaveRequest(
        @NotBlank String name,
        @NotBlank String code,
        @NotBlank String kind,
        Long defaultHostId,
        @NotBlank String status,
        String owner,
        String remark
) {
}
