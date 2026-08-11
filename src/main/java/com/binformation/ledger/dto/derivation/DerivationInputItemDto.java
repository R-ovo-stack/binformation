package com.binformation.ledger.dto.derivation;

public record DerivationInputItemDto(
        Long inputAssetId,
        String inputAssetName,
        int sortOrder
) {
}
