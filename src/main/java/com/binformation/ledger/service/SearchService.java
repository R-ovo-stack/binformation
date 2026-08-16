package com.binformation.ledger.service;

import com.binformation.ledger.dto.search.SearchGroupDto;
import com.binformation.ledger.dto.search.SearchHitDto;
import com.binformation.ledger.dto.search.SearchResultDto;
import com.binformation.ledger.entity.DataAsset;
import com.binformation.ledger.entity.Derivation;
import com.binformation.ledger.entity.Endpoint;
import com.binformation.ledger.entity.Executor;
import com.binformation.ledger.entity.Flow;
import com.binformation.ledger.mapper.DataAssetMapper;
import com.binformation.ledger.mapper.DerivationMapper;
import com.binformation.ledger.mapper.EndpointMapper;
import com.binformation.ledger.mapper.ExecutorMapper;
import com.binformation.ledger.mapper.FlowMapper;
import com.binformation.ledger.support.EndpointSupport;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private static final int DEFAULT_LIMIT = 8;
    private static final int MAX_LIMIT = 20;

    private static final Map<String, String> GROUP_LABELS = Map.of(
            "ASSET", "数据资产",
            "ENDPOINT", "落点",
            "FLOW", "流向",
            "EXECUTOR", "程序/脚本",
            "DERIVATION", "派生");

    private final DataAssetMapper dataAssetMapper;
    private final EndpointMapper endpointMapper;
    private final FlowMapper flowMapper;
    private final ExecutorMapper executorMapper;
    private final DerivationMapper derivationMapper;

    public SearchService(
            DataAssetMapper dataAssetMapper,
            EndpointMapper endpointMapper,
            FlowMapper flowMapper,
            ExecutorMapper executorMapper,
            DerivationMapper derivationMapper) {
        this.dataAssetMapper = dataAssetMapper;
        this.endpointMapper = endpointMapper;
        this.flowMapper = flowMapper;
        this.executorMapper = executorMapper;
        this.derivationMapper = derivationMapper;
    }

    public SearchResultDto search(String query, Integer limit) {
        String q = normalizeQuery(query);
        if (q.isEmpty()) {
            return new SearchResultDto("", 0, List.of());
        }
        int perGroup = clampLimit(limit);

        Map<Long, Endpoint> endpointMap = endpointMapper.selectList(null).stream()
                .collect(Collectors.toMap(Endpoint::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<Long, DataAsset> assetMap = dataAssetMapper.selectList(null).stream()
                .collect(Collectors.toMap(DataAsset::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        List<SearchGroupDto> groups = new ArrayList<>();
        appendGroup(groups, searchAssets(q, perGroup, assetMap));
        appendGroup(groups, searchEndpoints(q, perGroup, endpointMap));
        appendGroup(groups, searchFlows(q, perGroup, assetMap, endpointMap));
        appendGroup(groups, searchExecutors(q, perGroup));
        appendGroup(groups, searchDerivations(q, perGroup, assetMap));

        int total = groups.stream().mapToInt(SearchGroupDto::count).sum();
        return new SearchResultDto(q, total, groups);
    }

    private SearchGroupDto searchAssets(String q, int limit, Map<Long, DataAsset> assetMap) {
        List<SearchHitDto> hits = assetMap.values().stream()
                .filter(asset -> matchesAsset(q, asset))
                .sorted(Comparator.comparing(DataAsset::getName, String.CASE_INSENSITIVE_ORDER))
                .limit(limit)
                .map(asset -> new SearchHitDto(
                        "ASSET",
                        asset.getId(),
                        asset.getName(),
                        asset.getCode() + " · " + nullToDash(asset.getDataType()),
                        asset.getId(),
                        asset.getName(),
                        null,
                        null))
                .toList();
        return group("ASSET", hits);
    }

    private SearchGroupDto searchEndpoints(String q, int limit, Map<Long, Endpoint> endpointMap) {
        List<SearchHitDto> hits = endpointMap.values().stream()
                .filter(ep -> matchesEndpoint(q, ep, endpointMap))
                .sorted(Comparator.comparing(Endpoint::getName, String.CASE_INSENSITIVE_ORDER))
                .limit(limit)
                .map(ep -> {
                    String breadcrumb = EndpointSupport.buildBreadcrumb(ep, endpointMap);
                    return new SearchHitDto(
                            "ENDPOINT",
                            ep.getId(),
                            ep.getName(),
                            breadcrumb + " · " + ep.getType() + " · #" + ep.getId(),
                            null,
                            null,
                            null,
                            ep.getId());
                })
                .toList();
        return group("ENDPOINT", hits);
    }

    private SearchGroupDto searchFlows(
            String q,
            int limit,
            Map<Long, DataAsset> assetMap,
            Map<Long, Endpoint> endpointMap) {
        List<SearchHitDto> hits = flowMapper.selectList(null).stream()
                .filter(flow -> matchesFlow(q, flow, assetMap, endpointMap))
                .sorted(Comparator.comparing(Flow::getId))
                .limit(limit)
                .map(flow -> {
                    DataAsset asset = assetMap.get(flow.getAssetId());
                    Endpoint source = endpointMap.get(flow.getSourceEndpointId());
                    Endpoint target = endpointMap.get(flow.getTargetEndpointId());
                    String sourceLabel = source == null ? "#" + flow.getSourceEndpointId() : source.getName();
                    String targetLabel = target == null ? "#" + flow.getTargetEndpointId() : target.getName();
                    String assetName = asset == null ? "资产#" + flow.getAssetId() : asset.getName();
                    return new SearchHitDto(
                            "FLOW",
                            flow.getId(),
                            "流向 #" + flow.getId(),
                            assetName + " · " + sourceLabel + " → " + targetLabel,
                            flow.getAssetId(),
                            assetName,
                            flow.getId(),
                            null);
                })
                .toList();
        return group("FLOW", hits);
    }

    private SearchGroupDto searchExecutors(String q, int limit) {
        List<SearchHitDto> hits = executorMapper.selectList(null).stream()
                .filter(exec -> matchesExecutor(q, exec))
                .sorted(Comparator.comparing(Executor::getName, String.CASE_INSENSITIVE_ORDER))
                .limit(limit)
                .map(exec -> new SearchHitDto(
                        "EXECUTOR",
                        exec.getId(),
                        exec.getName(),
                        exec.getCode() + " · " + exec.getKind() + " · #" + exec.getId(),
                        null,
                        null,
                        null,
                        null))
                .toList();
        return group("EXECUTOR", hits);
    }

    private SearchGroupDto searchDerivations(String q, int limit, Map<Long, DataAsset> assetMap) {
        List<SearchHitDto> hits = derivationMapper.selectList(null).stream()
                .filter(d -> matchesDerivation(q, d, assetMap))
                .sorted(Comparator.comparing(Derivation::getName, String.CASE_INSENSITIVE_ORDER))
                .limit(limit)
                .map(d -> {
                    DataAsset output = assetMap.get(d.getOutputAssetId());
                    String assetName = output == null ? "资产#" + d.getOutputAssetId() : output.getName();
                    return new SearchHitDto(
                            "DERIVATION",
                            d.getId(),
                            d.getName(),
                            "输出 " + assetName + " · #" + d.getId(),
                            d.getOutputAssetId(),
                            assetName,
                            null,
                            null);
                })
                .toList();
        return group("DERIVATION", hits);
    }

    private boolean matchesAsset(String q, DataAsset asset) {
        return containsAny(q,
                asset.getName(),
                asset.getCode(),
                asset.getDataType(),
                asset.getOwner(),
                asset.getRemark())
                || idMatches(q, asset.getId());
    }

    private boolean matchesEndpoint(String q, Endpoint ep, Map<Long, Endpoint> endpointMap) {
        return containsAny(q,
                ep.getName(),
                ep.getCode(),
                ep.getType(),
                ep.getOwner(),
                ep.getRemark(),
                EndpointSupport.buildBreadcrumb(ep, endpointMap))
                || idMatches(q, ep.getId());
    }

    private boolean matchesFlow(
            String q,
            Flow flow,
            Map<Long, DataAsset> assetMap,
            Map<Long, Endpoint> endpointMap) {
        DataAsset asset = assetMap.get(flow.getAssetId());
        Endpoint source = endpointMap.get(flow.getSourceEndpointId());
        Endpoint target = endpointMap.get(flow.getTargetEndpointId());
        return idMatches(q, flow.getId())
                || containsAny(q,
                flow.getPurpose(),
                flow.getStatus(),
                flow.getOwner(),
                flow.getRemark(),
                asset == null ? null : asset.getName(),
                asset == null ? null : asset.getCode(),
                source == null ? null : source.getName(),
                target == null ? null : target.getName());
    }

    private boolean matchesExecutor(String q, Executor exec) {
        return containsAny(q,
                exec.getName(),
                exec.getCode(),
                exec.getKind(),
                exec.getOwner(),
                exec.getRemark())
                || idMatches(q, exec.getId());
    }

    private boolean matchesDerivation(String q, Derivation d, Map<Long, DataAsset> assetMap) {
        DataAsset output = assetMap.get(d.getOutputAssetId());
        return idMatches(q, d.getId())
                || containsAny(q,
                d.getName(),
                d.getStatus(),
                d.getOwner(),
                d.getRemark(),
                output == null ? null : output.getName(),
                output == null ? null : output.getCode());
    }

    private boolean containsAny(String q, String... values) {
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(q)) {
                return true;
            }
        }
        return false;
    }

    private boolean idMatches(String q, Long id) {
        if (id == null) {
            return false;
        }
        try {
            long parsed = Long.parseLong(q);
            return parsed == id;
        } catch (NumberFormatException ignored) {
            return String.valueOf(id).contains(q);
        }
    }

    private SearchGroupDto group(String entityType, List<SearchHitDto> hits) {
        return new SearchGroupDto(
                entityType,
                GROUP_LABELS.getOrDefault(entityType, entityType),
                hits.size(),
                hits);
    }

    private void appendGroup(List<SearchGroupDto> groups, SearchGroupDto group) {
        if (group.count() > 0) {
            groups.add(group);
        }
    }

    private String normalizeQuery(String query) {
        if (query == null) {
            return "";
        }
        return query.trim().toLowerCase(Locale.ROOT);
    }

    private int clampLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String nullToDash(String value) {
        return Objects.toString(value, "—");
    }
}
