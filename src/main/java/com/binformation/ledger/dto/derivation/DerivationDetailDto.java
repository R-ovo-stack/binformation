package com.binformation.ledger.dto.derivation;

import java.util.List;

public record DerivationDetailDto(
        Long id,
        String name,
        Long outputAssetId,
        String outputAssetName,
        Long executorId,
        String executorName,
        Long hostId,
        String hostLabel,
        String status,
        String owner,
        String remark,
        List<DerivationInputItemDto> inputs
) {
}
