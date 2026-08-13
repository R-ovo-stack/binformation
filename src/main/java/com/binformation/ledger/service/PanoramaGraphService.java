package com.binformation.ledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.binformation.ledger.dto.graph.PanoramaAssetNodeDto;
import com.binformation.ledger.dto.graph.PanoramaEdgeDto;
import com.binformation.ledger.dto.graph.PanoramaGraphDto;
import com.binformation.ledger.entity.DataAsset;
import com.binformation.ledger.entity.Derivation;
import com.binformation.ledger.entity.DerivationInput;
import com.binformation.ledger.entity.Endpoint;
import com.binformation.ledger.entity.Flow;
import com.binformation.ledger.mapper.DataAssetMapper;
import com.binformation.ledger.mapper.DerivationInputMapper;
import com.binformation.ledger.mapper.DerivationMapper;
import com.binformation.ledger.mapper.EndpointMapper;
import com.binformation.ledger.mapper.FlowMapper;
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
import java.util.stream.Collectors;

@Service
public class PanoramaGraphService {

    private final DataAssetMapper dataAssetMapper;
    private final FlowMapper flowMapper;
    private final DerivationMapper derivationMapper;
    private final DerivationInputMapper derivationInputMapper;
    private final EndpointMapper endpointMapper;

    public PanoramaGraphService(
            DataAssetMapper dataAssetMapper,
            FlowMapper flowMapper,
            DerivationMapper derivationMapper,
            DerivationInputMapper derivationInputMapper,
            EndpointMapper endpointMapper) {
        this.dataAssetMapper = dataAssetMapper;
        this.flowMapper = flowMapper;
        this.derivationMapper = derivationMapper;
        this.derivationInputMapper = derivationInputMapper;
        this.endpointMapper = endpointMapper;
    }

    /**
     * 资产血缘全景：派生关系 + 可选共享落点衔接。
     */
    public PanoramaGraphDto buildLineage(boolean includeEndpointLinks) {
        List<DataAsset> assets = dataAssetMapper.selectList(
                new LambdaQueryWrapper<DataAsset>().orderByAsc(DataAsset::getId));
        Map<Long, DataAsset> assetById = assets.stream()
                .collect(Collectors.toMap(DataAsset::getId, a -> a, (a, b) -> a, LinkedHashMap::new));

        List<Flow> allFlows = flowMapper.selectList(null);
        Map<Long, List<Flow>> flowsByAsset = allFlows.stream()
                .collect(Collectors.groupingBy(Flow::getAssetId));

        Map<Long, Integer> primaryFlowCount = new HashMap<>();
        for (DataAsset asset : assets) {
            int count = (int) flowsByAsset.getOrDefault(asset.getId(), List.of()).stream()
                    .filter(f -> f.getIsPrimary() == null || Boolean.TRUE.equals(f.getIsPrimary()))
                    .count();
            primaryFlowCount.put(asset.getId(), count);
        }

        List<Derivation> derivations = derivationMapper.selectList(null);
        Map<Long, List<DerivationInput>> inputsByDerivation = derivationInputMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(DerivationInput::getDerivationId));

        Map<Long, Integer> derivationInCount = new HashMap<>();
        Map<Long, Integer> derivationOutCount = new HashMap<>();
        for (Derivation d : derivations) {
            derivationOutCount.merge(d.getOutputAssetId(), 1, Integer::sum);
            for (DerivationInput input : inputsByDerivation.getOrDefault(d.getId(), List.of())) {
                derivationInCount.merge(input.getInputAssetId(), 1, Integer::sum);
            }
        }

        Set<String> edgeKeys = new HashSet<>();
        List<PanoramaEdgeDto> edges = new ArrayList<>();

        for (Derivation derivation : derivations) {
            DataAsset output = assetById.get(derivation.getOutputAssetId());
            if (output == null) {
                continue;
            }

            List<DerivationInput> inputs = new ArrayList<>(inputsByDerivation.getOrDefault(derivation.getId(), List.of()));
            inputs.sort(Comparator.comparing(DerivationInput::getSortOrder).thenComparing(DerivationInput::getId));

            for (DerivationInput input : inputs) {
                DataAsset inputAsset = assetById.get(input.getInputAssetId());
                if (inputAsset == null || Objects.equals(inputAsset.getId(), output.getId())) {
                    continue;
                }
                String key = "DERIVE:" + inputAsset.getId() + ">" + output.getId() + ":" + derivation.getId();
                if (!edgeKeys.add(key)) {
                    continue;
                }
                edges.add(new PanoramaEdgeDto(
                        "derive-" + derivation.getId() + "-" + input.getInputAssetId(),
                        inputAsset.getId(),
                        output.getId(),
                        "DERIVE",
                        "派生 ← " + derivation.getName(),
                        derivation.getId(),
                        null,
                        null
                ));
            }
        }

        if (includeEndpointLinks) {
            edges.addAll(buildEndpointLinkEdges(allFlows, assetById, edgeKeys));
        }

        Set<Long> nodeIds = new LinkedHashSet<>();
        assets.forEach(a -> nodeIds.add(a.getId()));
        for (PanoramaEdgeDto edge : edges) {
            nodeIds.add(edge.sourceAssetId());
            nodeIds.add(edge.targetAssetId());
        }

        List<PanoramaAssetNodeDto> nodes = nodeIds.stream()
                .map(assetById::get)
                .filter(Objects::nonNull)
                .map(a -> new PanoramaAssetNodeDto(
                        a.getId(),
                        a.getName(),
                        a.getCode(),
                        a.getDataType(),
                        a.getStatus(),
                        primaryFlowCount.getOrDefault(a.getId(), 0),
                        derivationInCount.getOrDefault(a.getId(), 0),
                        derivationOutCount.getOrDefault(a.getId(), 0)
                ))
                .toList();

        edges.sort(Comparator.comparing(PanoramaEdgeDto::type).thenComparing(PanoramaEdgeDto::id));

        return new PanoramaGraphDto(nodes, edges, nodes.size(), edges.size());
    }

    private List<PanoramaEdgeDto> buildEndpointLinkEdges(
            List<Flow> allFlows,
            Map<Long, DataAsset> assetById,
            Set<String> edgeKeys) {
        Map<Long, Endpoint> endpoints = endpointMapper.selectList(null).stream()
                .collect(Collectors.toMap(Endpoint::getId, e -> e));

        record FlowRef(Long assetId, Long sourceId, Long targetId) {
        }

        List<FlowRef> primaryFlows = allFlows.stream()
                .filter(f -> f.getIsPrimary() == null || Boolean.TRUE.equals(f.getIsPrimary()))
                .filter(f -> assetById.containsKey(f.getAssetId()))
                .map(f -> new FlowRef(f.getAssetId(), f.getSourceEndpointId(), f.getTargetEndpointId()))
                .toList();

        Map<Long, List<FlowRef>> producersByTarget = new HashMap<>();
        Map<Long, List<FlowRef>> consumersBySource = new HashMap<>();
        for (FlowRef ref : primaryFlows) {
            producersByTarget.computeIfAbsent(ref.targetId(), k -> new ArrayList<>()).add(ref);
            consumersBySource.computeIfAbsent(ref.sourceId(), k -> new ArrayList<>()).add(ref);
        }

        List<PanoramaEdgeDto> result = new ArrayList<>();
        for (Map.Entry<Long, List<FlowRef>> entry : producersByTarget.entrySet()) {
            Long endpointId = entry.getKey();
            List<FlowRef> producers = entry.getValue();
            List<FlowRef> consumers = consumersBySource.getOrDefault(endpointId, List.of());
            if (consumers.isEmpty()) {
                continue;
            }

            Endpoint ep = endpoints.get(endpointId);
            String epLabel = ep == null ? ("落点#" + endpointId) : ep.getName();

            for (FlowRef prod : producers) {
                for (FlowRef cons : consumers) {
                    if (Objects.equals(prod.assetId(), cons.assetId())) {
                        continue;
                    }
                    String key = "LINK:" + prod.assetId() + ">" + cons.assetId() + "@" + endpointId;
                    if (!edgeKeys.add(key)) {
                        continue;
                    }

                    DataAsset from = assetById.get(prod.assetId());
                    DataAsset to = assetById.get(cons.assetId());
                    String label = "共享落点 · " + epLabel;
                    if (from != null && to != null) {
                        label = from.getName() + " → " + epLabel + " → " + to.getName();
                    }

                    result.add(new PanoramaEdgeDto(
                            "link-" + prod.assetId() + "-" + cons.assetId() + "-" + endpointId,
                            prod.assetId(),
                            cons.assetId(),
                            "ENDPOINT_LINK",
                            label,
                            null,
                            endpointId,
                            epLabel
                    ));
                }
            }
        }
        return result;
    }
}
