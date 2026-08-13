package com.binformation.ledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.binformation.ledger.dto.graph.AssetGraphDto;
import com.binformation.ledger.dto.graph.GraphDerivationDto;
import com.binformation.ledger.dto.graph.GraphDerivationInputDto;
import com.binformation.ledger.dto.graph.GraphEdgeDto;
import com.binformation.ledger.dto.graph.GraphGroupDto;
import com.binformation.ledger.dto.graph.GraphNodeDto;
import com.binformation.ledger.dto.graph.GraphPathDto;
import com.binformation.ledger.dto.graph.GraphRelationDto;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    public AssetGraphDto buildGraph(Long assetId, boolean includeAuxiliary, boolean includeUpstream) {
        DataAsset asset = dataAssetMapper.selectById(assetId);
        if (asset == null) {
            throw new ResourceNotFoundException("数据资产不存在: " + assetId);
        }

        List<Derivation> asOutput = derivationMapper.selectList(
                new LambdaQueryWrapper<Derivation>().eq(Derivation::getOutputAssetId, assetId));
        boolean hasUpstream = !asOutput.isEmpty();

        Map<Long, DataAsset> assetsById = dataAssetMapper.selectList(null).stream()
                .collect(Collectors.toMap(DataAsset::getId, Function.identity()));

        List<Flow> flows = new ArrayList<>(loadFlowsForAsset(assetId, includeAuxiliary));
        Map<Long, DataAsset> assetByFlowId = new HashMap<>();
        Map<Long, Boolean> upstreamByFlowId = new HashMap<>();
        for (Flow flow : flows) {
            assetByFlowId.put(flow.getId(), asset);
            upstreamByFlowId.put(flow.getId(), false);
        }

        if (includeUpstream && hasUpstream) {
            Set<Long> inputAssetIds = new HashSet<>();
            for (Derivation derivation : asOutput) {
                derivationInputMapper.selectList(
                                new LambdaQueryWrapper<DerivationInput>()
                                        .eq(DerivationInput::getDerivationId, derivation.getId()))
                        .forEach(input -> inputAssetIds.add(input.getInputAssetId()));
            }
            for (Long inputAssetId : inputAssetIds) {
                if (Objects.equals(inputAssetId, assetId)) {
                    continue;
                }
                DataAsset inputAsset = assetsById.get(inputAssetId);
                if (inputAsset == null) {
                    continue;
                }
                // 前置资产仅展开主流向，避免图爆炸
                for (Flow upFlow : loadFlowsForAsset(inputAssetId, false)) {
                    flows.add(upFlow);
                    assetByFlowId.put(upFlow.getId(), inputAsset);
                    upstreamByFlowId.put(upFlow.getId(), true);
                }
            }
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

        List<GraphRelationDto> relations = new ArrayList<>();
        relations.addAll(enrichKafkaTopology(endpointIds, allEndpoints));
        relations.addAll(enrichDirectoryHostTopology(endpointIds, allEndpoints));

        Map<Long, FlowLayout> layoutByEndpoint = flowLayoutMapper.selectList(
                        new LambdaQueryWrapper<FlowLayout>().eq(FlowLayout::getAssetId, assetId))
                .stream()
                .collect(Collectors.toMap(FlowLayout::getEndpointId, Function.identity(), (a, b) -> a));

        Map<Long, Executor> executors = executorMapper.selectList(null).stream()
                .collect(Collectors.toMap(Executor::getId, Function.identity()));

        List<GraphNodeDto> executorNodes = new ArrayList<>();
        relations.addAll(enrichExecutorTopology(
                flows, pathsByFlow, stepsByPath, endpointIds, allEndpoints, executors, executorNodes));

        List<GraphEdgeDto> edges = buildEdges(
                flows, pathsByFlow, stepsByPath, allEndpoints, executors, assetByFlowId, upstreamByFlowId);

        if (includeUpstream && hasUpstream) {
            edges.addAll(buildDerivationBridgeEdges(
                    asset,
                    asOutput,
                    flows,
                    allEndpoints,
                    executors,
                    assetsById,
                    endpointIds,
                    executorNodes,
                    relations));
        }

        relations.sort(Comparator.comparing(GraphRelationDto::id));

        List<GraphGroupDto> groups = buildGroups(endpointIds, allEndpoints);
        List<GraphNodeDto> nodes = buildNodes(endpointIds, allEndpoints, layoutByEndpoint);
        nodes.addAll(executorNodes);
        List<GraphDerivationDto> derivations = buildDerivations(assetId, allEndpoints, executors, assetsById);

        return new AssetGraphDto(
                asset.getId(),
                asset.getName(),
                asset.getCode(),
                asset.getDataType(),
                groups,
                nodes,
                edges,
                relations,
                derivations,
                hasUpstream
        );
    }

    /**
     * P1 技术全景：合并多资产（或全部）的主流向/辅助流向，落点级成图。
     * 不使用单资产已保存布局，统一自动布局。
     */
    public AssetGraphDto buildTechnicalPanorama(
            List<Long> assetIds,
            boolean includeAuxiliary,
            boolean includeDerivationBridges) {
        Map<Long, DataAsset> assetsById = dataAssetMapper.selectList(null).stream()
                .collect(Collectors.toMap(DataAsset::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        Set<Long> scope = resolveAssetScope(assetIds, assetsById.keySet());
        if (scope.isEmpty()) {
            return emptyTechnicalPanorama();
        }

        List<Flow> flows = new ArrayList<>();
        Map<Long, DataAsset> assetByFlowId = new HashMap<>();
        Map<Long, Boolean> upstreamByFlowId = new HashMap<>();
        for (Long assetId : scope.stream().sorted().toList()) {
            DataAsset asset = assetsById.get(assetId);
            if (asset == null) {
                continue;
            }
            for (Flow flow : loadFlowsForAsset(assetId, includeAuxiliary)) {
                flows.add(flow);
                assetByFlowId.put(flow.getId(), asset);
                upstreamByFlowId.put(flow.getId(), false);
            }
        }

        Map<Long, Endpoint> allEndpoints = endpointMapper.selectList(null).stream()
                .collect(Collectors.toMap(Endpoint::getId, Function.identity()));

        Set<Long> endpointIds = collectEndpointIdsFromFlows(flows);
        List<Long> flowIds = flows.stream().map(Flow::getId).toList();
        Map<Long, List<FlowPath>> pathsByFlow = loadPathsByFlow(flowIds);
        Map<Long, List<FlowStep>> stepsByPath = loadStepsByPath(pathsByFlow);
        addHostEndpointIds(endpointIds, stepsByPath);

        List<GraphRelationDto> relations = new ArrayList<>();
        relations.addAll(enrichKafkaTopology(endpointIds, allEndpoints));
        relations.addAll(enrichDirectoryHostTopology(endpointIds, allEndpoints));

        Map<Long, Executor> executors = executorMapper.selectList(null).stream()
                .collect(Collectors.toMap(Executor::getId, Function.identity()));

        List<GraphNodeDto> executorNodes = new ArrayList<>();
        relations.addAll(enrichExecutorTopology(
                flows, pathsByFlow, stepsByPath, endpointIds, allEndpoints, executors, executorNodes));

        List<GraphEdgeDto> edges = buildEdges(
                flows, pathsByFlow, stepsByPath, allEndpoints, executors, assetByFlowId, upstreamByFlowId);

        if (includeDerivationBridges) {
            for (Long outputAssetId : scope) {
                DataAsset outputAsset = assetsById.get(outputAssetId);
                if (outputAsset == null) {
                    continue;
                }
                List<Derivation> asOutput = derivationMapper.selectList(
                        new LambdaQueryWrapper<Derivation>().eq(Derivation::getOutputAssetId, outputAssetId));
                if (asOutput.isEmpty()) {
                    continue;
                }
                boolean allInputsInScope = asOutput.stream().allMatch(derivation ->
                        derivationInputMapper.selectList(
                                        new LambdaQueryWrapper<DerivationInput>()
                                                .eq(DerivationInput::getDerivationId, derivation.getId()))
                                .stream()
                                .allMatch(input -> scope.contains(input.getInputAssetId())));
                if (!allInputsInScope) {
                    continue;
                }
                edges.addAll(buildDerivationBridgeEdges(
                        outputAsset,
                        asOutput,
                        flows,
                        allEndpoints,
                        executors,
                        assetsById,
                        endpointIds,
                        executorNodes,
                        relations));
            }
        }

        relations.sort(Comparator.comparing(GraphRelationDto::id));

        List<GraphGroupDto> groups = buildGroups(endpointIds, allEndpoints);
        List<GraphNodeDto> nodes = buildNodes(endpointIds, allEndpoints, Map.of());
        nodes.addAll(executorNodes);
        List<GraphDerivationDto> derivations = buildDerivationsForAssets(scope, allEndpoints, executors, assetsById);

        return new AssetGraphDto(
                0L,
                "技术全景",
                "PANORAMA_TECH",
                "MIXED",
                groups,
                nodes,
                edges,
                relations,
                derivations,
                includeDerivationBridges
        );
    }

    private AssetGraphDto emptyTechnicalPanorama() {
        return new AssetGraphDto(
                0L,
                "技术全景",
                "PANORAMA_TECH",
                "MIXED",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false
        );
    }

    private Set<Long> resolveAssetScope(List<Long> assetIds, Set<Long> allAssetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return new LinkedHashSet<>(allAssetIds);
        }
        return assetIds.stream()
                .filter(allAssetIds::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Long> collectEndpointIdsFromFlows(List<Flow> flows) {
        Set<Long> endpointIds = new HashSet<>();
        for (Flow flow : flows) {
            endpointIds.add(flow.getSourceEndpointId());
            endpointIds.add(flow.getTargetEndpointId());
        }
        return endpointIds;
    }

    private void addHostEndpointIds(Set<Long> endpointIds, Map<Long, List<FlowStep>> stepsByPath) {
        for (List<FlowStep> steps : stepsByPath.values()) {
            for (FlowStep step : steps) {
                if (step.getHostId() != null) {
                    endpointIds.add(step.getHostId());
                }
            }
        }
    }

    private List<GraphDerivationDto> buildDerivationsForAssets(
            Set<Long> assetIds,
            Map<Long, Endpoint> allEndpoints,
            Map<Long, Executor> executors,
            Map<Long, DataAsset> assets) {
        Set<Long> derivationIds = new LinkedHashSet<>();
        for (Long assetId : assetIds) {
            derivationMapper.selectList(
                            new LambdaQueryWrapper<Derivation>().eq(Derivation::getOutputAssetId, assetId))
                    .forEach(d -> derivationIds.add(d.getId()));
            derivationInputMapper.selectList(
                            new LambdaQueryWrapper<DerivationInput>().eq(DerivationInput::getInputAssetId, assetId))
                    .forEach(input -> derivationIds.add(input.getDerivationId()));
        }
        if (derivationIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Derivation> derivationMap = derivationMapper.selectList(
                        new LambdaQueryWrapper<Derivation>().in(Derivation::getId, derivationIds))
                .stream()
                .collect(Collectors.toMap(Derivation::getId, Function.identity()));

        Map<Long, List<DerivationInput>> inputsByDerivation = derivationInputMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(DerivationInput::getDerivationId));

        return derivationMap.values().stream()
                .sorted(Comparator.comparing(Derivation::getId))
                .map(derivation -> toDerivationDto(derivation, inputsByDerivation, assets, allEndpoints, executors))
                .toList();
    }

    private List<Flow> loadFlowsForAsset(Long assetId, boolean includeAuxiliary) {
        List<Flow> flows = flowMapper.selectList(
                new LambdaQueryWrapper<Flow>().eq(Flow::getAssetId, assetId));
        if (!includeAuxiliary) {
            return flows.stream()
                    .filter(flow -> flow.getIsPrimary() == null || Boolean.TRUE.equals(flow.getIsPrimary()))
                    .toList();
        }
        return flows;
    }

    /**
     * 补齐 Kafka 拓扑：主题所属 Kafka 集群，以及同系统下 role=kafka-broker 的主机节点。
     */
    private List<GraphRelationDto> enrichKafkaTopology(
            Set<Long> endpointIds,
            Map<Long, Endpoint> allEndpoints) {
        List<GraphRelationDto> relations = new ArrayList<>();
        Set<Long> seedIds = new HashSet<>(endpointIds);

        for (Long id : seedIds) {
            Endpoint endpoint = allEndpoints.get(id);
            if (endpoint == null) {
                continue;
            }
            if ("KAFKA_TOPIC".equals(endpoint.getType()) && endpoint.getParentId() != null) {
                Endpoint kafka = allEndpoints.get(endpoint.getParentId());
                if (kafka != null && "KAFKA".equals(kafka.getType())) {
                    endpointIds.add(kafka.getId());
                    relations.add(new GraphRelationDto(
                            "rel-contains-" + kafka.getId() + "-" + endpoint.getId(),
                            EndpointSupport.nodeId(kafka.getId()),
                            EndpointSupport.nodeId(endpoint.getId()),
                            "CONTAINS",
                            "包含主题"
                    ));
                }
            }
        }

        Set<Long> kafkaIds = new HashSet<>();
        for (Long id : new HashSet<>(endpointIds)) {
            Endpoint ep = allEndpoints.get(id);
            if (ep != null && "KAFKA".equals(ep.getType())) {
                kafkaIds.add(ep.getId());
            }
        }

        for (Long kafkaId : kafkaIds) {
            Endpoint kafka = allEndpoints.get(kafkaId);
            if (kafka == null || kafka.getParentId() == null) {
                continue;
            }
            Long systemId = kafka.getParentId();
            for (Endpoint candidate : allEndpoints.values()) {
                if (!"HOST".equals(candidate.getType())) {
                    continue;
                }
                if (!systemId.equals(candidate.getParentId())) {
                    continue;
                }
                if (!isKafkaBroker(candidate)) {
                    continue;
                }
                endpointIds.add(candidate.getId());
                relations.add(new GraphRelationDto(
                        "rel-broker-" + kafkaId + "-" + candidate.getId(),
                        EndpointSupport.nodeId(kafkaId),
                        EndpointSupport.nodeId(candidate.getId()),
                        "BROKER_OF",
                        "Kafka节点"
                ));
            }
        }

        return relations;
    }

    /**
     * 目录落点补齐所属主机：HOST CONTAINS DIRECTORY，便于成图表现「机上的目录」。
     */
    private List<GraphRelationDto> enrichDirectoryHostTopology(
            Set<Long> endpointIds,
            Map<Long, Endpoint> allEndpoints) {
        List<GraphRelationDto> relations = new ArrayList<>();
        Set<Long> seedIds = new HashSet<>(endpointIds);

        for (Long id : seedIds) {
            Endpoint endpoint = allEndpoints.get(id);
            if (endpoint == null || !"DIRECTORY".equals(endpoint.getType()) || endpoint.getParentId() == null) {
                continue;
            }
            Endpoint host = allEndpoints.get(endpoint.getParentId());
            if (host == null || !"HOST".equals(host.getType())) {
                continue;
            }
            endpointIds.add(host.getId());
            relations.add(new GraphRelationDto(
                    "rel-contains-dir-" + host.getId() + "-" + endpoint.getId(),
                    EndpointSupport.nodeId(host.getId()),
                    EndpointSupport.nodeId(endpoint.getId()),
                    "CONTAINS",
                    "包含目录"
            ));
        }
        return relations;
    }

    /**
     * 补齐程序/脚本节点，以及部署节点、在流向上的处理位置。
     */
    private List<GraphRelationDto> enrichExecutorTopology(
            List<Flow> flows,
            Map<Long, List<FlowPath>> pathsByFlow,
            Map<Long, List<FlowStep>> stepsByPath,
            Set<Long> endpointIds,
            Map<Long, Endpoint> allEndpoints,
            Map<Long, Executor> executors,
            List<GraphNodeDto> executorNodes) {
        List<GraphRelationDto> relations = new ArrayList<>();
        Map<Long, GraphNodeDto> executorNodeMap = new LinkedHashMap<>();

        for (Flow flow : flows) {
            List<FlowPath> paths = pathsByFlow.getOrDefault(flow.getId(), List.of());
            for (FlowPath path : paths) {
                List<FlowStep> steps = stepsByPath.getOrDefault(path.getId(), List.of());
                for (FlowStep step : steps) {
                    Executor executor = executors.get(step.getExecutorId());
                    if (executor == null) {
                        continue;
                    }

                    Long hostId = step.getHostId() != null ? step.getHostId() : executor.getDefaultHostId();
                    if (hostId != null) {
                        endpointIds.add(hostId);
                    }

                    Endpoint host = hostId == null ? null : allEndpoints.get(hostId);
                    Endpoint zone = host == null ? null : EndpointSupport.resolveZone(host, allEndpoints);
                    String groupId = zone == null ? null : EndpointSupport.groupId(zone.getId());
                    String breadcrumb = host == null
                            ? executor.getName()
                            : EndpointSupport.buildBreadcrumb(host, allEndpoints) + " / " + executor.getName();

                    executorNodeMap.putIfAbsent(executor.getId(), new GraphNodeDto(
                            EndpointSupport.executorNodeId(executor.getId()),
                            "EXECUTOR",
                            null,
                            executor.getId(),
                            executor.getKind(),
                            executor.getName(),
                            groupId,
                            breadcrumb,
                            null,
                            null
                    ));

                    if (hostId != null) {
                        relations.add(new GraphRelationDto(
                                "rel-runs-on-" + executor.getId() + "-" + hostId,
                                EndpointSupport.executorNodeId(executor.getId()),
                                EndpointSupport.nodeId(hostId),
                                "RUNS_ON",
                                "部署于"
                        ));
                    }

                    relations.add(new GraphRelationDto(
                            "rel-from-src-" + flow.getId() + "-" + executor.getId(),
                            EndpointSupport.nodeId(flow.getSourceEndpointId()),
                            EndpointSupport.executorNodeId(executor.getId()),
                            "VIA_EXECUTOR",
                            "经程序处理"
                    ));
                    relations.add(new GraphRelationDto(
                            "rel-to-tgt-" + flow.getId() + "-" + executor.getId(),
                            EndpointSupport.executorNodeId(executor.getId()),
                            EndpointSupport.nodeId(flow.getTargetEndpointId()),
                            "VIA_EXECUTOR",
                            "写出"
                    ));
                }
            }
        }

        // RUNS_ON 可能因多路径重复，按 id 去重
        Map<String, GraphRelationDto> unique = new LinkedHashMap<>();
        for (GraphRelationDto rel : relations) {
            unique.put(rel.id(), rel);
        }
        executorNodes.addAll(executorNodeMap.values());
        return new ArrayList<>(unique.values());
    }

    private boolean isKafkaBroker(Endpoint host) {
        String attrs = host.getAttrs();
        if (attrs != null && attrs.contains("kafka-broker")) {
            return true;
        }
        String remark = host.getRemark();
        return remark != null && remark.contains("Kafka节点");
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
                    "ENDPOINT",
                    endpointId,
                    null,
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
            Map<Long, Executor> executors,
            Map<Long, DataAsset> assetByFlowId,
            Map<Long, Boolean> upstreamByFlowId) {
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
            DataAsset fromAsset = assetByFlowId.get(flow.getId());
            boolean upstream = Boolean.TRUE.equals(upstreamByFlowId.get(flow.getId()));
            edges.add(new GraphEdgeDto(
                    EndpointSupport.edgeId(flow.getId()),
                    flow.getId(),
                    EndpointSupport.nodeId(flow.getSourceEndpointId()),
                    EndpointSupport.nodeId(flow.getTargetEndpointId()),
                    flow.getPurpose(),
                    Boolean.TRUE.equals(flow.getIsPrimary()),
                    flow.getStatus(),
                    flow.getRemark(),
                    pathDtos,
                    upstream,
                    fromAsset == null ? flow.getAssetId() : fromAsset.getId(),
                    fromAsset == null ? null : fromAsset.getName()
            ));
        }
        return edges;
    }

    /**
     * 派生桥接：前置资产末级落点 → 拼接程序 → 本资产首个接入目录。
     * 让「含前置」时图可读：三区 A/B/C → abc-stitch-d → /data/d/
     */
    private List<GraphEdgeDto> buildDerivationBridgeEdges(
            DataAsset outputAsset,
            List<Derivation> asOutput,
            List<Flow> flows,
            Map<Long, Endpoint> allEndpoints,
            Map<Long, Executor> executors,
            Map<Long, DataAsset> assetsById,
            Set<Long> endpointIds,
            List<GraphNodeDto> executorNodes,
            List<GraphRelationDto> relations) {
        List<GraphEdgeDto> bridges = new ArrayList<>();
        Long ingestEndpointId = resolveOutputIngestEndpoint(outputAsset.getId(), flows);
        if (ingestEndpointId == null) {
            return bridges;
        }
        endpointIds.add(ingestEndpointId);

        Map<Long, GraphNodeDto> executorNodeMap = new LinkedHashMap<>();
        for (GraphNodeDto node : executorNodes) {
            if (node.executorId() != null) {
                executorNodeMap.put(node.executorId(), node);
            }
        }

        for (Derivation derivation : asOutput) {
            Executor executor = executors.get(derivation.getExecutorId());
            Long hostId = derivation.getHostId() != null
                    ? derivation.getHostId()
                    : (executor == null ? null : executor.getDefaultHostId());
            if (hostId != null) {
                endpointIds.add(hostId);
            }

            if (executor != null && !executorNodeMap.containsKey(executor.getId())) {
                Endpoint host = hostId == null ? null : allEndpoints.get(hostId);
                Endpoint zone = host == null ? null : EndpointSupport.resolveZone(host, allEndpoints);
                String groupId = zone == null ? null : EndpointSupport.groupId(zone.getId());
                String breadcrumb = host == null
                        ? executor.getName()
                        : EndpointSupport.buildBreadcrumb(host, allEndpoints) + " / " + executor.getName();
                GraphNodeDto execNode = new GraphNodeDto(
                        EndpointSupport.executorNodeId(executor.getId()),
                        "EXECUTOR",
                        null,
                        executor.getId(),
                        executor.getKind(),
                        executor.getName(),
                        groupId,
                        breadcrumb,
                        null,
                        null
                );
                executorNodeMap.put(executor.getId(), execNode);
                executorNodes.add(execNode);
            }

            if (executor != null && hostId != null) {
                relations.add(new GraphRelationDto(
                        "rel-runs-on-" + executor.getId() + "-" + hostId,
                        EndpointSupport.executorNodeId(executor.getId()),
                        EndpointSupport.nodeId(hostId),
                        "RUNS_ON",
                        "部署于"
                ));
            }

            List<DerivationInput> inputs = derivationInputMapper.selectList(
                    new LambdaQueryWrapper<DerivationInput>()
                            .eq(DerivationInput::getDerivationId, derivation.getId())
                            .orderByAsc(DerivationInput::getSortOrder)
                            .orderByAsc(DerivationInput::getId));

            int seq = 0;
            for (DerivationInput input : inputs) {
                Long terminalId = resolveUpstreamTerminalEndpoint(input.getInputAssetId(), flows, allEndpoints);
                if (terminalId == null || Objects.equals(terminalId, ingestEndpointId)) {
                    continue;
                }
                endpointIds.add(terminalId);
                DataAsset inputAsset = assetsById.get(input.getInputAssetId());
                String inputName = inputAsset == null ? ("资产#" + input.getInputAssetId()) : inputAsset.getName();

                if (executor != null) {
                    relations.add(new GraphRelationDto(
                            "rel-bridge-from-" + derivation.getId() + "-" + input.getInputAssetId(),
                            EndpointSupport.nodeId(terminalId),
                            EndpointSupport.executorNodeId(executor.getId()),
                            "VIA_EXECUTOR",
                            "经程序处理"
                    ));
                    relations.add(new GraphRelationDto(
                            "rel-bridge-to-" + derivation.getId() + "-" + input.getInputAssetId(),
                            EndpointSupport.executorNodeId(executor.getId()),
                            EndpointSupport.nodeId(ingestEndpointId),
                            "VIA_EXECUTOR",
                            "写出"
                    ));
                }

                seq++;
                List<GraphStepDto> steps = List.of(new GraphStepDto(
                        1,
                        hostId,
                        hostId == null || allEndpoints.get(hostId) == null
                                ? null
                                : allEndpoints.get(hostId).getName(),
                        derivation.getExecutorId(),
                        executor == null ? null : executor.getName(),
                        "STREAM_JOIN",
                        derivation.getRemark()
                ));
                List<GraphPathDto> paths = List.of(new GraphPathDto(
                        -derivation.getId() * 1000 - input.getInputAssetId(),
                        "派生拼接",
                        true,
                        seq,
                        steps
                ));

                bridges.add(new GraphEdgeDto(
                        "bridge-" + derivation.getId() + "-" + input.getInputAssetId(),
                        null,
                        EndpointSupport.nodeId(terminalId),
                        EndpointSupport.nodeId(ingestEndpointId),
                        "DERIVE",
                        true,
                        derivation.getStatus() == null ? "ACTIVE" : derivation.getStatus(),
                        "派生拼接 ← " + inputName,
                        paths,
                        true,
                        input.getInputAssetId(),
                        inputName
                ));
            }
        }
        return bridges;
    }

    private Long resolveOutputIngestEndpoint(Long outputAssetId, List<Flow> flows) {
        return flows.stream()
                .filter(f -> Objects.equals(f.getAssetId(), outputAssetId))
                .filter(f -> f.getIsPrimary() == null || Boolean.TRUE.equals(f.getIsPrimary()))
                .sorted(Comparator.comparing(Flow::getId))
                .map(Flow::getSourceEndpointId)
                .findFirst()
                .orElse(null);
    }

    private Long resolveUpstreamTerminalEndpoint(
            Long inputAssetId,
            List<Flow> flows,
            Map<Long, Endpoint> allEndpoints) {
        List<Flow> assetFlows = flows.stream()
                .filter(f -> Objects.equals(f.getAssetId(), inputAssetId))
                .filter(f -> f.getIsPrimary() == null || Boolean.TRUE.equals(f.getIsPrimary()))
                .sorted(Comparator.comparing(Flow::getId))
                .toList();
        if (assetFlows.isEmpty()) {
            return null;
        }
        Set<Long> sources = assetFlows.stream().map(Flow::getSourceEndpointId).collect(Collectors.toSet());
        List<Long> leaves = assetFlows.stream()
                .map(Flow::getTargetEndpointId)
                .filter(id -> !sources.contains(id))
                .distinct()
                .toList();
        for (Long leaf : leaves) {
            Endpoint ep = allEndpoints.get(leaf);
            if (ep != null && "KAFKA_TOPIC".equals(ep.getType())) {
                return leaf;
            }
        }
        if (!leaves.isEmpty()) {
            return leaves.get(leaves.size() - 1);
        }
        return assetFlows.get(assetFlows.size() - 1).getTargetEndpointId();
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
            Map<Long, Executor> executors,
            Map<Long, DataAsset> assets) {
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
