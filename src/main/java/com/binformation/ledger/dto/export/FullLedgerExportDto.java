package com.binformation.ledger.dto.export;

import com.binformation.ledger.dto.derivation.DerivationDetailDto;
import com.binformation.ledger.dto.endpoint.EndpointDetailDto;
import com.binformation.ledger.dto.executor.ExecutorDetailDto;

import java.time.Instant;
import java.util.List;

public record FullLedgerExportDto(
        String version,
        Instant exportedAt,
        int endpointCount,
        int assetCount,
        int flowCount,
        int derivationCount,
        int executorCount,
        List<EndpointDetailDto> endpoints,
        List<ExecutorDetailDto> executors,
        List<AssetExportDto> assets,
        List<DerivationDetailDto> derivations
) {
}
