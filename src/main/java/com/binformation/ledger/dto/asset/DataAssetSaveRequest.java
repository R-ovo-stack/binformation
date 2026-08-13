package com.binformation.ledger.dto.asset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DataAssetSaveRequest(
        @NotBlank String name,
        @NotBlank String code,
        @NotBlank String dataType,
        @NotBlank String status,
        String owner,
        String remark
) {
}
