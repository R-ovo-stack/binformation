package com.binformation.ledger.dto.layout;

import jakarta.validation.constraints.NotNull;

public record LayoutNodeSaveRequest(
        @NotNull Long endpointId,
        @NotNull Double layoutX,
        @NotNull Double layoutY
) {
}
