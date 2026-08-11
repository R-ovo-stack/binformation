package com.binformation.ledger.dto.flow;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record FlowSaveRequest(
        @NotNull Long sourceEndpointId,
        @NotNull Long targetEndpointId,
        @NotBlank String purpose,
        @NotNull Boolean primary,
        @NotBlank String status,
        String owner,
        String remark,
        @Valid @NotNull List<FlowPathSaveRequest> paths
) {
}
