package com.binformation.ledger.dto.derivation;

import jakarta.validation.constraints.NotNull;

public record DerivationInputSaveRequest(
        @NotNull Long inputAssetId,
        @NotNull Integer sortOrder
) {
}
