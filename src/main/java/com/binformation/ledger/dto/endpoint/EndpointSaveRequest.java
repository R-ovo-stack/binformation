package com.binformation.ledger.dto.endpoint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EndpointSaveRequest(
        @NotBlank String type,
        @NotBlank String name,
        String code,
        Long parentId,
        String attrs,
        @NotBlank String status,
        String owner,
        String remark
) {
}
