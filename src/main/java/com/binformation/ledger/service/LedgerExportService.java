package com.binformation.ledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.binformation.ledger.dto.derivation.DerivationDetailDto;
import com.binformation.ledger.dto.export.AssetExportDto;
import com.binformation.ledger.dto.export.FullLedgerExportDto;
import com.binformation.ledger.dto.flow.FlowDetailDto;
import com.binformation.ledger.dto.flow.FlowSummaryDto;
import com.binformation.ledger.entity.DataAsset;
import com.binformation.ledger.entity.Derivation;
import com.binformation.ledger.mapper.DerivationMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class LedgerExportService {

    private static final String EXPORT_VERSION = "1.0";

    private final EndpointService endpointService;
    private final ExecutorService executorService;
    private final DataAssetService dataAssetService;
    private final FlowService flowService;
    private final DerivationService derivationService;
    private final DerivationMapper derivationMapper;

    public LedgerExportService(
            EndpointService endpointService,
            ExecutorService executorService,
            DataAssetService dataAssetService,
            FlowService flowService,
            DerivationService derivationService,
            DerivationMapper derivationMapper) {
        this.endpointService = endpointService;
        this.executorService = executorService;
        this.dataAssetService = dataAssetService;
        this.flowService = flowService;
        this.derivationService = derivationService;
        this.derivationMapper = derivationMapper;
    }

    public FullLedgerExportDto buildFullExport() {
        var endpoints = endpointService.listAll(null, null).stream()
                .sorted(Comparator.comparing(e -> e.id()))
                .toList();
        var executors = executorService.listAll().stream()
                .sorted(Comparator.comparing(e -> e.id()))
                .toList();

        List<DataAsset> assets = dataAssetService.listAll().stream()
                .sorted(Comparator.comparing(DataAsset::getId))
                .toList();

        List<AssetExportDto> assetExports = new ArrayList<>();
        int flowCount = 0;
        for (DataAsset asset : assets) {
            List<FlowDetailDto> flows = new ArrayList<>();
            for (FlowSummaryDto summary : flowService.listByAsset(asset.getId())) {
                flows.add(flowService.getById(summary.id()));
            }
            flows.sort(Comparator.comparing(FlowDetailDto::id));
            flowCount += flows.size();
            assetExports.add(new AssetExportDto(
                    asset.getId(),
                    asset.getName(),
                    asset.getCode(),
                    asset.getDataType(),
                    asset.getStatus(),
                    asset.getOwner(),
                    asset.getRemark(),
                    flows
            ));
        }

        List<DerivationDetailDto> derivations = derivationMapper.selectList(
                        new LambdaQueryWrapper<Derivation>().orderByAsc(Derivation::getId))
                .stream()
                .map(d -> derivationService.getById(d.getId()))
                .toList();

        return new FullLedgerExportDto(
                EXPORT_VERSION,
                Instant.now(),
                endpoints.size(),
                assetExports.size(),
                flowCount,
                derivations.size(),
                executors.size(),
                endpoints,
                executors,
                assetExports,
                derivations
        );
    }
}
