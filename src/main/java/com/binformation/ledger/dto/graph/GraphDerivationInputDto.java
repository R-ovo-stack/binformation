package com.binformation.ledger.dto.graph;

public record GraphDerivationInputDto(
        Long assetId,
        String assetName,
        int sortOrder
) {
}
