package com.binformation.ledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.binformation.ledger.dto.lineage.AssetDownstreamQueryDto;
import com.binformation.ledger.dto.lineage.DownstreamSystemDto;
import com.binformation.ledger.dto.lineage.LineageEndpointRefDto;
import com.binformation.ledger.dto.lineage.LineageFlowRefDto;
import com.binformation.ledger.dto.lineage.SystemAssetQueryDto;
import com.binformation.ledger.dto.lineage.SystemConsumedAssetDto;
import com.binformation.ledger.dto.lineage.SystemOptionDto;
import com.binformation.ledger.entity.DataAsset;
import com.binformation.ledger.entity.Endpoint;
import com.binformation.ledger.entity.Flow;
import com.binformation.ledger.exception.ResourceNotFoundException;
import com.binformation.ledger.mapper.DataAssetMapper;
import com.binformation.ledger.mapper.EndpointMapper;
import com.binformation.ledger.mapper.FlowMapper;
import com.binformation.ledger.support.EndpointSupport;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SystemAssetLineageService {

    public static final String TYPE_SYSTEM = "SYSTEM";

    private final EndpointMapper endpointMapper;
    private final FlowMapper flowMapper;
    private final DataAssetMapper dataAssetMapper;

    public SystemAssetLineageService(
            EndpointMapper endpointMapper,
            FlowMapper flowMapper,
            DataAssetMapper dataAssetMapper) {
        this.endpointMapper = endpointMapper;
        this.flowMapper = flowMapper;
        this.dataAssetMapper = dataAssetMapper;
    }

    public List<SystemOptionDto> listSystems() {
        Map<Long, Endpoint> endpoints = loadEndpointMap();
        return endpoints.values().stream()
                .filter(ep -> TYPE_SYSTEM.equals(ep.getType()))
                .sorted(Comparator.comparing(Endpoint::getName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Endpoint::getId))
                .map(ep -> toSystemOption(ep, endpoints))
                .toList();
    }

    /**
     * 某系统获取了哪些数据资产：流向目标落点属于该系统（含子系统落点）。
     */
    public SystemAssetQueryDto consumedBySystem(Long systemId, boolean includeAuxiliary) {
        Map<Long, Endpoint> endpoints = loadEndpointMap();
        Endpoint system = requireSystem(systemId, endpoints);
        Map<Long, DataAsset> assets = loadAssetMap();

        List<Flow> inbound = flowMapper.selectList(null).stream()
                .filter(flow -> includeAuxiliary || !"AUX".equals(flow.getPurpose()))
                .filter(flow -> belongsToSystem(flow.getTargetEndpointId(), systemId, endpoints))
                .toList();

        Map<Long, List<Flow>> byAsset = inbound.stream()
                .collect(Collectors.groupingBy(Flow::getAssetId, LinkedHashMap::new, Collectors.toList()));

        List<SystemConsumedAssetDto> rows = byAsset.entrySet().stream()
                .map(entry -> toConsumedAsset(entry.getKey(), entry.getValue(), assets, endpoints))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(SystemConsumedAssetDto::assetName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new SystemAssetQueryDto(
                system.getId(),
                system.getName(),
                EndpointSupport.buildBreadcrumb(system, endpoints),
                zoneName(system, endpoints),
                rows.size(),
                rows);
    }

    /**
     * 某资产流向了哪些下游应用系统：流向目标落点所属 SYSTEM。
     */
    public AssetDownstreamQueryDto downstreamSystems(Long assetId, boolean includeAuxiliary) {
        DataAsset asset = dataAssetMapper.selectById(assetId);
        if (asset == null) {
            throw new ResourceNotFoundException("数据资产不存在: " + assetId);
        }
        Map<Long, Endpoint> endpoints = loadEndpointMap();

        List<Flow> outbound = flowMapper.selectList(
                        new LambdaQueryWrapper<Flow>().eq(Flow::getAssetId, assetId))
                .stream()
                .filter(flow -> includeAuxiliary || !"AUX".equals(flow.getPurpose()))
                .toList();

        Map<Long, List<Flow>> bySystem = new LinkedHashMap<>();
        List<Flow> unmatched = new ArrayList<>();
        for (Flow flow : outbound) {
            Endpoint system = resolveSystem(flow.getTargetEndpointId(), endpoints);
            if (system == null) {
                unmatched.add(flow);
                continue;
            }
            bySystem.computeIfAbsent(system.getId(), k -> new ArrayList<>()).add(flow);
        }

        List<DownstreamSystemDto> systems = bySystem.entrySet().stream()
                .map(entry -> {
                    Endpoint system = endpoints.get(entry.getKey());
                    List<LineageFlowRefDto> flows = entry.getValue().stream()
                            .map(flow -> toFlowRef(flow, asset, endpoints))
                            .toList();
                    return new DownstreamSystemDto(
                            system.getId(),
                            system.getName(),
                            EndpointSupport.buildBreadcrumb(system, endpoints),
                            zoneName(system, endpoints),
                            dominantRole(entry.getValue()),
                            flows);
                })
                .sorted(Comparator.comparing(DownstreamSystemDto::systemName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toCollection(ArrayList::new));

        if (!unmatched.isEmpty()) {
            systems.add(new DownstreamSystemDto(
                    null,
                    "未归属系统的落点",
                    null,
                    null,
                    "UNKNOWN",
                    unmatched.stream().map(flow -> toFlowRef(flow, asset, endpoints)).toList()));
        }

        return new AssetDownstreamQueryDto(
                asset.getId(),
                asset.getName(),
                asset.getCode(),
                asset.getDataType(),
                (int) systems.stream().filter(s -> s.systemId() != null).count(),
                systems);
    }

    private SystemConsumedAssetDto toConsumedAsset(
            Long assetId,
            List<Flow> flows,
            Map<Long, DataAsset> assets,
            Map<Long, Endpoint> endpoints) {
        DataAsset asset = assets.get(assetId);
        if (asset == null) {
            return null;
        }
        List<LineageFlowRefDto> flowRefs = flows.stream()
                .map(flow -> toFlowRef(flow, asset, endpoints))
                .toList();
        return new SystemConsumedAssetDto(
                asset.getId(),
                asset.getName(),
                asset.getCode(),
                asset.getDataType(),
                asset.getStatus(),
                dominantRole(flows),
                flowRefs);
    }

    private LineageFlowRefDto toFlowRef(Flow flow, DataAsset asset, Map<Long, Endpoint> endpoints) {
        return new LineageFlowRefDto(
                flow.getId(),
                flow.getAssetId(),
                asset == null ? null : asset.getName(),
                flow.getPurpose(),
                flow.getStatus(),
                Boolean.TRUE.equals(flow.getIsPrimary()),
                toEndpointRef(flow.getSourceEndpointId(), endpoints),
                toEndpointRef(flow.getTargetEndpointId(), endpoints));
    }

    private LineageEndpointRefDto toEndpointRef(Long endpointId, Map<Long, Endpoint> endpoints) {
        Endpoint ep = endpoints.get(endpointId);
        if (ep == null) {
            return new LineageEndpointRefDto(endpointId, "落点#" + endpointId, null, null);
        }
        return new LineageEndpointRefDto(
                ep.getId(),
                ep.getName(),
                ep.getType(),
                EndpointSupport.buildBreadcrumb(ep, endpoints));
    }

    private SystemOptionDto toSystemOption(Endpoint system, Map<Long, Endpoint> endpoints) {
        return new SystemOptionDto(
                system.getId(),
                system.getName(),
                EndpointSupport.buildBreadcrumb(system, endpoints),
                zoneName(system, endpoints),
                system.getStatus());
    }

    Endpoint resolveSystem(Long endpointId, Map<Long, Endpoint> endpoints) {
        Endpoint current = endpoints.get(endpointId);
        int guard = 0;
        while (current != null && guard++ < 32) {
            if (TYPE_SYSTEM.equals(current.getType())) {
                return current;
            }
            if (current.getParentId() == null) {
                return null;
            }
            current = endpoints.get(current.getParentId());
        }
        return null;
    }

    private boolean belongsToSystem(Long endpointId, Long systemId, Map<Long, Endpoint> endpoints) {
        Endpoint system = resolveSystem(endpointId, endpoints);
        return system != null && systemId.equals(system.getId());
    }

    private String dominantRole(List<Flow> flows) {
        boolean ingest = flows.stream().anyMatch(f -> "INGEST".equals(f.getPurpose()));
        boolean share = flows.stream().anyMatch(f -> "SHARE".equals(f.getPurpose()));
        boolean sync = flows.stream().anyMatch(f -> "SYNC".equals(f.getPurpose()));
        boolean forward = flows.stream().anyMatch(f -> "FORWARD".equals(f.getPurpose()));
        if (share) {
            return "CONSUMER";
        }
        if (ingest) {
            return "INGEST_TARGET";
        }
        if (sync) {
            return "SYNC_TARGET";
        }
        if (forward) {
            return "FORWARD_TARGET";
        }
        return "TARGET";
    }

    private String zoneName(Endpoint endpoint, Map<Long, Endpoint> endpoints) {
        Endpoint zone = EndpointSupport.resolveZone(endpoint, endpoints);
        return zone == null ? null : zone.getName();
    }

    private Endpoint requireSystem(Long systemId, Map<Long, Endpoint> endpoints) {
        Endpoint endpoint = endpoints.get(systemId);
        if (endpoint == null) {
            throw new ResourceNotFoundException("系统不存在: " + systemId);
        }
        if (!TYPE_SYSTEM.equals(endpoint.getType())) {
            throw new ResourceNotFoundException("落点不是系统: " + systemId);
        }
        return endpoint;
    }

    private Map<Long, Endpoint> loadEndpointMap() {
        return endpointMapper.selectList(null).stream()
                .collect(Collectors.toMap(Endpoint::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private Map<Long, DataAsset> loadAssetMap() {
        return dataAssetMapper.selectList(null).stream()
                .collect(Collectors.toMap(DataAsset::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }
}
