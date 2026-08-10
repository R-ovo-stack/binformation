package com.binformation.ledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.binformation.ledger.dto.graph.AssetGraphDto;
import com.binformation.ledger.dto.graph.GraphDerivationDto;
import com.binformation.ledger.dto.graph.GraphDerivationInputDto;
import com.binformation.ledger.dto.graph.GraphEdgeDto;
import com.binformation.ledger.dto.graph.GraphGroupDto;
import com.binformation.ledger.dto.graph.GraphNodeDto;
import com.binformation.ledger.dto.graph.GraphPathDto;
import com.binformation.ledger.dto.graph.GraphStepDto;
import com.binformation.ledger.entity.DataAsset;
import com.binformation.ledger.entity.Derivation;
import com.binformation.ledger.entity.DerivationInput;
import com.binformation.ledger.entity.Endpoint;
import com.binformation.ledger.entity.Executor;
import com.binformation.ledger.entity.Flow;
import com.binformation.ledger.entity.FlowLayout;
import com.binformation.ledger.entity.FlowPath;
import com.binformation.ledger.entity.FlowStep;
import com.binformation.ledger.exception.ResourceNotFoundException;
import com.binformation.ledger.mapper.DataAssetMapper;
import com.binformation.ledger.mapper.DerivationInputMapper;
import com.binformation.ledger.mapper.DerivationMapper;
import com.binformation.ledger.mapper.EndpointMapper;
import com.binformation.ledger.mapper.ExecutorMapper;
import com.binformation.ledger.mapper.FlowLayoutMapper;
import com.binformation.ledger.mapper.FlowMapper;
import com.binformation.ledger.mapper.FlowPathMapper;
import com.binformation.ledger.mapper.FlowStepMapper;
import com.binformation.ledger.support.EndpointSupport;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AssetGraphService {

    private final DataAssetMapper dataAssetMapper;
    private final FlowMapper flowMapper;
    private final FlowPathMapper flowPathMapper;
    private final FlowStepMapper flowStepMapper;
    private final EndpointMapper endpointMapper;
    private final ExecutorMapper executorMapper;
    private final DerivationMapper derivationMapper;
    private final DerivationInputMapper derivationInputMapper;
    private final FlowLayoutMapper flowLayoutMapper;

    public AssetGraphService(
            DataAssetMapper dataAssetMapper,
            FlowMapper flowMapper,
            FlowPathMapper flowPathMapper,
            FlowStepMapper flowStepMapper,
            EndpointMapper endpointMapper,
            ExecutorMapper executorMapper,
            DerivationMapper derivationMapper,
            DerivationInputMapper derivationInputMapper,
            FlowLayoutMapper flowLayoutMapper) {
        this.dataAssetMapper = dataAssetMapper;
        this.flowMapper = flowMapper;
        this.flowPathMapper = flowPathMapper;
        this.flowStepMapper = flowStepMapper;
        this.endpointMapper = endpointMapper;
        this.executorMapper = executorMapper;
        this.derivationMapper = derivationMapper;
        this.derivationInputMapper = derivationInputMapper;
        this.flowLayoutMapper = flowLayoutMapper;
    }

    public AssetGraphDto buildGraph(Long assetId, boolean includeAuxiliary) {
        DataAsset asset = dataAssetMapper.selectById(assetId);
        if (asset == null) {
            throw new ResourceNotFoundException("数据资产不存在: " + assetId);
        }

        List<Flow> flows = flowMapper.selectList(
                new LambdaQueryWrapper<Flow>().eq(Flow::getAssetId, assetId));
        if (!includeAuxiliary) {
            flows = flows.stream()
                    .filter(flow -> flow.getIsPrimary() == null || Boolean.TRUE.equals(flow.getIsPrimary()))
                    .toList();
        }

        Map<Long, Endpoint> allEndpoints = endpointMapper.selectList(null).stream()
                .collect(Collectors.toMap(Endpoint::getId, Function.identity()));

        Set<Long> endpointIds = new HashSet<>();
        for (Flow flow : flows) {
            endpointIds.add(flow.getSourceEndpointId());
            endpointIds.add(flow.getTargetEndpointId());
        }

        List<Long> flowIds = flows.stream().map(Flow::getId).toList();
        Map<Long, List<FlowPath>> pathsByFlow = loadPathsByFlow(flowIds);
        Map<Long, List<FlowStep>> stepsByPath = loadStepsByPath(pathsByFlow);

        for (List<FlowStep> steps : stepsByPath.values()) {
            for (FlowStep step : steps) {
                if (step.getHostId() != null) {
                    endpointIds.add(step.getHostId());
                }
            }
        }

        Map<Long, FlowLayout> layoutByEndpoint = flowLayoutMapper.selectList(
                        new LambdaQueryWrapper<FlowLayout>().eq(FlowLayout::getAssetId, assetId))
                .stream()
                .collect(Collectors.toMap(FlowLayout::getEndpointId, Function.identity(), (a, b) -> a));

        Map<Long, Executor> executors = executorMapper.selectList(null).stream()
                .collect(Collectors.toMap(Executor::getId, Function.identity()));

        List<GraphGroupDto> groups = buildGroups(endpointIds, allEndpoints);
        List<GraphNodeDto> nodes = buildNodes(endpointIds, allEndpoints, layoutByEndpoint);
        List<GraphEdgeDto> edges = buildEdges(flows, pathsByFlow, stepsByPath, allEndpoints, executors);
        List<GraphDerivationDto> derivations = buildDerivations(assetId, allEndpoints, executors);

        return new AssetGraphDto(
                asset.getId(),
                asset.getName(),
                asset.getCode(),
                asset.getDataType(),
                groups,
                nodes,
                edges,
                derivations
        );
    }

    private Map<Long, List<FlowPath>> loadPathsByFlow(List<Long> flowIds) {
        if (flowIds.isEmpty()) {
            return Map.of();
        }
        List<FlowPath> paths = flowPathMapper.selectList(
                new LambdaQueryWrapper<FlowPath>().in(FlowPath::getFlowId, flowIds));
        return paths.stream().collect(Collectors.groupingBy(FlowPath::getFlowId));
    }

    private Map<Long, List<FlowStep>> loadStepsByPath(Map<Long, List<FlowPath>> pathsByFlow) {
        List<Long> pathIds = pathsByFlow.values().stream()
                .flatMap(List::stream)
                .map(FlowPath::getId)
                .toList();
        if (pathIds.isEmpty()) {
            return Map.of();
        }
        List<FlowStep> steps = flowStepMapper.selectList(
                new LambdaQueryWrapper<FlowStep>().in(FlowStep::getPathId, pathIds));
        return steps.stream().collect(Collectors.groupingBy(FlowStep::getPathId));
    }

    private List<GraphGroupDto> buildGroups(Set<Long> endpointIds, Map<Long, Endpoint> allEndpoints) {
        Map<Long, GraphGroupDto> groups = new LinkedHashMap<>();
        for (Long endpointId : endpointIds) {
            Endpoint endpoint = allEndpoints.get(endpointId);
            if (endpoint == null) {
                continue;
            }
            Endpoint zone = EndpointSupport.resolveZone(endpoint, allEndpoints);
            if (zone == null) {
                continue;
            }
            groups.putIfAbsent(zone.getId(), new GraphGroupDto(
                    EndpointSupport.groupId(zone.getId()),
                    zone.getId(),
                    zone.getName()
            ));
        }
        return new ArrayList<>(groups.values());
    }

    private List<GraphNodeDto> buildNodes(
            Set<Long> endpointIds,
            Map<Long, Endpoint> allEndpoints,
            Map<Long, FlowLayout> layoutByEndpoint) {
        List<GraphNodeDto> nodes = new ArrayList<>();
        for (Long endpointId : endpointIds.stream().sorted().toList()) {
            Endpoint endpoint = allEndpoints.get(endpointId);
            if (endpoint == null) {
                continue;
            }
            Endpoint zone = EndpointSupport.resolveZone(endpoint, allEndpoints);
            String groupId = zone == null ? null : EndpointSupport.groupId(zone.getId());
            FlowLayout layout = layoutByEndpoint.get(endpointId);
            nodes.add(new GraphNodeDto(
                    EndpointSupport.nodeId(endpointId),
                    endpointId,
                    endpoint.getType(),
                    endpoint.getName(),
                    groupId,
                    EndpointSupport.buildBreadcrumb(endpoint, allEndpoints),
                    layout == null ? null : layout.getLayoutX(),
                    layout == null ? null : layout.getLayoutY()
            ));
        }
        return nodes;
    }

    private List<GraphEdgeDto> buildEdges(
            List<Flow> flows,
            Map<Long, List<FlowPath>> pathsByFlow,
            Map<Long, List<FlowStep>> stepsByPath,
            Map<Long, Endpoint> allEndpoints,
            Map<Long, Executor> executors) {
        List<GraphEdgeDto> edges = new ArrayList<>();
        List<Flow> flowList = new ArrayList<>(flows);
        flowList.sort(Comparator.comparing(Flow::getId));
        for (Flow flow : flowList) {
            List<FlowPath> pathList = new ArrayList<>(pathsByFlow.getOrDefault(flow.getId(), List.of()));
            pathList.sort(Comparator.comparing(FlowPath::getSortOrder).thenComparing(FlowPath::getId));
            List<GraphPathDto> pathDtos = new ArrayList<>();
            for (FlowPath path : pathList) {
                List<FlowStep> steps = new ArrayList<>(stepsByPath.getOrDefault(path.getId(), List.of()));
                steps.sort(Comparator.comparing(FlowStep::getSeq));
                List<GraphStepDto> stepDtos = steps.stream()
                        .map(step -> toStepDto(step, allEndpoints, executors))
                        .toList();
                pathDtos.add(new GraphPathDto(
                        path.getId(),
                        path.getName(),
                        Boolean.TRUE.equals(path.getEnabled()),
                        path.getSortOrder() == null ? 0 : path.getSortOrder(),
                        stepDtos
                ));
            }
            edges.add(new GraphEdgeDto(
                    EndpointSupport.edgeId(flow.getId()),
                    flow.getId(),
                    EndpointSupport.nodeId(flow.getSourceEndpointId()),
                    EndpointSupport.nodeId(flow.getTargetEndpointId()),
                    flow.getPurpose(),
                    Boolean.TRUE.equals(flow.getIsPrimary()),
                    flow.getStatus(),
                    flow.getRemark(),
                    pathDtos
            ));
        }
        return edges;
    }

    private GraphStepDto toStepDto(
            FlowStep step,
            Map<Long, Endpoint> allEndpoints,
            Map<Long, Executor> executors) {
        Endpoint host = step.getHostId() == null ? null : allEndpoints.get(step.getHostId());
        Executor executor = executors.get(step.getExecutorId());
        return new GraphStepDto(
                step.getSeq(),
                step.getHostId(),
                host == null ? null : host.getName(),
                step.getExecutorId(),
                executor == null ? null : executor.getName(),
                step.getMethod(),
                step.getRemark()
        );
    }

    private List<GraphDerivationDto> buildDerivations(
            Long assetId,
            Map<Long, Endpoint> allEndpoints,
            Map<Long, Executor> executors) {
        Map<Long, DataAsset> assets = dataAssetMapper.selectList(null).stream()
                .collect(Collectors.toMap(DataAsset::getId, Function.identity()));

        List<Derivation> asOutput = derivationMapper.selectList(
                new LambdaQueryWrapper<Derivation>().eq(Derivation::getOutputAssetId, assetId));
        List<Long> derivationIdsFromInput = derivationInputMapper.selectList(
                        new LambdaQueryWrapper<DerivationInput>().eq(DerivationInput::getInputAssetId, assetId))
                .stream()
                .map(DerivationInput::getDerivationId)
                .distinct()
                .toList();

        Map<Long, Derivation> derivationMap = new HashMap<>();
        for (Derivation derivation : asOutput) {
            derivationMap.put(derivation.getId(), derivation);
        }
        if (!derivationIdsFromInput.isEmpty()) {
            derivationMapper.selectList(
                            new LambdaQueryWrapper<Derivation>().in(Derivation::getId, derivationIdsFromInput))
                    .forEach(d -> derivationMap.put(d.getId(), d));
        }

        Map<Long, List<DerivationInput>> inputsByDerivation = derivationInputMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(DerivationInput::getDerivationId));

        return derivationMap.values().stream()
                .sorted(Comparator.comparing(Derivation::getId))
                .map(derivation -> toDerivationDto(derivation, inputsByDerivation, assets, allEndpoints, executors))
                .toList();
    }

    private GraphDerivationDto toDerivationDto(
            Derivation derivation,
            Map<Long, List<DerivationInput>> inputsByDerivation,
            Map<Long, DataAsset> assets,
            Map<Long, Endpoint> allEndpoints,
            Map<Long, Executor> executors) {
        List<DerivationInput> inputs = new ArrayList<>(
                inputsByDerivation.getOrDefault(derivation.getId(), List.of()));
        inputs.sort(Comparator.comparing(DerivationInput::getSortOrder).thenComparing(DerivationInput::getId));
        List<GraphDerivationInputDto> inputDtos = inputs.stream()
                .map(input -> {
                    DataAsset asset = assets.get(input.getInputAssetId());
                    return new GraphDerivationInputDto(
                            input.getInputAssetId(),
                            asset == null ? null : asset.getName(),
                            input.getSortOrder() == null ? 0 : input.getSortOrder()
                    );
                })
                .toList();

        DataAsset output = assets.get(derivation.getOutputAssetId());
        Executor executor = executors.get(derivation.getExecutorId());
        Endpoint host = derivation.getHostId() == null ? null : allEndpoints.get(derivation.getHostId());

        return new GraphDerivationDto(
                derivation.getId(),
                derivation.getName(),
                derivation.getStatus(),
                derivation.getOutputAssetId(),
                output == null ? null : output.getName(),
                inputDtos,
                derivation.getExecutorId(),
                executor == null ? null : executor.getName(),
                derivation.getHostId(),
                host == null ? null : host.getName()
        );
    }
}
