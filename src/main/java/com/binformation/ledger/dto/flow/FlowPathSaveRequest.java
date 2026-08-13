package com.binformation.ledger.dto.flow;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record FlowPathSaveRequest(
        @NotBlank String name,
        @NotNull Boolean enabled,
        @NotNull Integer sortOrder,
        String remark,
        @Valid @NotNull List<FlowStepSaveRequest> steps
) {
}
