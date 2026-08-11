package com.binformation.ledger.dto.derivation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record DerivationSaveRequest(
        @NotBlank String name,
        @NotNull Long executorId,
        Long hostId,
        @NotBlank String status,
        String owner,
        String remark,
        @Valid @NotNull List<DerivationInputSaveRequest> inputs
) {
}
