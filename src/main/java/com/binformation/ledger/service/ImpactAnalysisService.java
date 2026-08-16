package com.binformation.ledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.binformation.ledger.dto.impact.ImpactAnalysisDto;
import com.binformation.ledger.dto.impact.ImpactGroupDto;
import com.binformation.ledger.dto.impact.ImpactItemDto;
import com.binformation.ledger.entity.DataAsset;
import com.binformation.ledger.entity.Derivation;
import com.binformation.ledger.entity.DerivationInput;
import com.binformation.ledger.entity.Endpoint;
import com.binformation.ledger.entity.Executor;
import com.binformation.ledger.entity.Flow;
import com.binformation.ledger.entity.FlowLayout;
import com.binformation.ledger.entity.FlowPath;
import com.binformation.ledger.entity.FlowStep;
import com.binformation.ledger.exception.BadRequestException;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ImpactAnalysisService {

    private static final int ITEM_LIMIT = 12;
    private static final String SEVERITY_BLOCKER = "BLOCKER";
    private static final String SEVERITY_WARNING = "WARNING";
    private static final String SEVERITY_INFO = "INFO";

    private final EndpointMapper endpointMapper;
    private final FlowMapper flowMapper;
    private final FlowPathMapper flowPathMapper;
    private final FlowStepMapper flowStepMapper;
    private final FlowLayoutMapper flowLayoutMapper;
    private final DataAssetMapper dataAssetMapper;
    private final ExecutorMapper executorMapper;
    private final DerivationMapper derivationMapper;
    private final DerivationInputMapper derivationInputMapper;

    public ImpactAnalysisService(
            EndpointMapper endpointMapper,
            FlowMapper flowMapper,
            FlowPathMapper flowPathMapper,
            FlowStepMapper flowStepMapper,
            FlowLayoutMapper flowLayoutMapper,
            DataAssetMapper dataAssetMapper,
            ExecutorMapper executorMapper,
            DerivationMapper derivationMapper,
            DerivationInputMapper derivationInputMapper) {
        this.endpointMapper = endpointMapper;
        this.flowMapper = flowMapper;
        this.flowPathMapper = flowPathMapper;
        this.flowStepMapper = flowStepMapper;
        this.flowLayoutMapper = flowLayoutMapper;
        this.dataAssetMapper = dataAssetMapper;
        this.executorMapper = executorMapper;
        this.derivationMapper = derivationMapper;
        this.derivationInputMapper = derivationInputMapper;
    }

    public ImpactAnalysisDto analyze(String entityType, Long entityId, String action) {
        String type = normalizeEntityType(entityType);
        String act = normalizeAction(action);
        return switch (type) {
            case "ENDPOINT" -> "DELETE".equals(act)
                    ? analyzeEndpointDelete(entityId)
                    : analyzeEndpointUpdate(entityId);
            case "ASSET" -> "DELETE".equals(act)
                    ? analyzeAssetDelete(entityId)
                    : asUpdateView(analyzeAssetDelete(entityId));
            case "FLOW" -> "DELETE".equals(act)
                    ? analyzeFlowDelete(entityId)
                    : asUpdateView(analyzeFlowDelete(entityId));
            case "EXECUTOR" -> "DELETE".equals(act)
                    ? analyzeExecutorDelete(entityId)
                    : asUpdateView(analyzeExecutorDelete(entityId));
            case "DERIVATION" -> "DELETE".equals(act)
                    ? analyzeDerivationDelete(entityId)
                    : asUpdateView(analyzeDerivationDelete(entityId));
            default -> throw new BadRequestException("不支持的实体类型: " + entityType);
        };
    }

    /** Remap delete blockers into informational warnings for UPDATE preview. */
    private ImpactAnalysisDto asUpdateView(ImpactAnalysisDto deleteView) {
        List<ImpactGroupDto> warnings = new ArrayList<>();
        for (ImpactGroupDto group : deleteView.blockers()) {
            warnings.add(new ImpactGroupDto(
                    group.kind(),
                    SEVERITY_INFO,
                    group.count(),
                    group.message(),
                    group.items()));
        }
        for (ImpactGroupDto group : deleteView.warnings()) {
            warnings.add(new ImpactGroupDto(
                    group.kind(),
                    SEVERITY_INFO.equals(group.severity()) ? SEVERITY_INFO : SEVERITY_WARNING,
                    group.count(),
                    group.message(),
                    group.items()));
        }
        return buildResult(
                deleteView.entityType(),
                deleteView.entityId(),
                deleteView.entityLabel(),
                "UPDATE",
                List.of(),
                warnings);
    }

    public ImpactAnalysisDto analyzeEndpointDelete(Long endpointId) {
        Endpoint endpoint = requireEndpoint(endpointId);
        Map<Long, Endpoint> endpoints = loadEndpointMap();
        String label = EndpointSupport.buildBreadcrumb(endpoint, endpoints);

        List<ImpactGroupDto> blockers = new ArrayList<>();
        List<ImpactGroupDto> warnings = new ArrayList<>();

        appendChildEndpointBlockers(endpointId, endpoints, blockers);
        appendFlowEndpointBlockers(endpointId, "FLOW_SOURCE", "SOURCE", SEVERITY_BLOCKER, blockers,
                flowMapper.selectList(new LambdaQueryWrapper<Flow>().eq(Flow::getSourceEndpointId, endpointId)));
        appendFlowEndpointBlockers(endpointId, "FLOW_TARGET", "TARGET", SEVERITY_BLOCKER, blockers,
                flowMapper.selectList(new LambdaQueryWrapper<Flow>().eq(Flow::getTargetEndpointId, endpointId)));
        appendFlowStepHostBlockers(endpointId, SEVERITY_BLOCKER, blockers);
        appendExecutorDefaultHostBlockers(endpointId, SEVERITY_BLOCKER, blockers);
        appendDerivationHostBlockers(endpointId, SEVERITY_BLOCKER, blockers);
        appendFlowLayoutWarnings(endpointId, warnings);

        return buildResult("ENDPOINT", endpointId, label, "DELETE", blockers, warnings);
    }

    public ImpactAnalysisDto analyzeEndpointUpdate(Long endpointId) {
        Endpoint endpoint = requireEndpoint(endpointId);
        Map<Long, Endpoint> endpoints = loadEndpointMap();
        String label = EndpointSupport.buildBreadcrumb(endpoint, endpoints);

        List<ImpactGroupDto> blockers = List.of();
        List<ImpactGroupDto> warnings = new ArrayList<>();

        appendFlowEndpointBlockers(endpointId, "FLOW_SOURCE", "SOURCE", SEVERITY_INFO, warnings,
                flowMapper.selectList(new LambdaQueryWrapper<Flow>().eq(Flow::getSourceEndpointId, endpointId)));
        appendFlowEndpointBlockers(endpointId, "FLOW_TARGET", "TARGET", SEVERITY_INFO, warnings,
                flowMapper.selectList(new LambdaQueryWrapper<Flow>().eq(Flow::getTargetEndpointId, endpointId)));
        appendFlowStepHostBlockers(endpointId, SEVERITY_INFO, warnings);
        appendExecutorDefaultHostBlockers(endpointId, SEVERITY_INFO, warnings);
        appendDerivationHostBlockers(endpointId, SEVERITY_INFO, warnings);
        appendFlowLayoutWarnings(endpointId, warnings);

        return buildResult("ENDPOINT", endpointId, label, "UPDATE", blockers, warnings);
    }

    public ImpactAnalysisDto analyzeAssetDelete(Long assetId) {
        DataAsset asset = requireAsset(assetId);
        List<ImpactGroupDto> blockers = new ArrayList<>();
        List<ImpactGroupDto> warnings = new ArrayList<>();

        List<Flow> flows = flowMapper.selectList(
                new LambdaQueryWrapper<Flow>().eq(Flow::getAssetId, assetId));
        if (!flows.isEmpty()) {
            blockers.add(flowGroup("ASSET_FLOW", SEVERITY_BLOCKER,
                    "该资产下仍有 " + flows.size() + " 条流向",
                    flows.stream().map(f -> flowItem(f, "FLOW", "所属资产流向")).toList()));
        }

        List<Derivation> outputDerivations = derivationMapper.selectList(
                new LambdaQueryWrapper<Derivation>().eq(Derivation::getOutputAssetId, assetId));
        if (!outputDerivations.isEmpty()) {
            blockers.add(derivationGroup(
                    "ASSET_DERIVATION_OUTPUT",
                    SEVERITY_BLOCKER,
                    "仍有 " + outputDerivations.size() + " 条派生以该资产为输出",
                    outputDerivations));
        }

        List<DerivationInput> inputs = derivationInputMapper.selectList(
                new LambdaQueryWrapper<DerivationInput>().eq(DerivationInput::getInputAssetId, assetId));
        if (!inputs.isEmpty()) {
            Map<Long, Derivation> derivations = derivationMapper.selectBatchIds(
                            inputs.stream().map(DerivationInput::getDerivationId).distinct().toList())
                    .stream()
                    .collect(Collectors.toMap(Derivation::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
            List<ImpactItemDto> items = inputs.stream()
                    .map(in -> {
                        Derivation d = derivations.get(in.getDerivationId());
                        if (d == null) {
                            return new ImpactItemDto(
                                    in.getDerivationId(),
                                    "派生#" + in.getDerivationId() + " (输入资产)",
                                    "DERIVATION",
                                    null,
                                    null,
                                    null,
                                    null,
                                    "INPUT",
                                    "派生输入引用");
                        }
                        ImpactItemDto base = derivationItem(d);
                        return new ImpactItemDto(
                                base.id(),
                                base.label() + " (输入资产)",
                                base.entityType(),
                                base.assetId(),
                                base.assetName(),
                                base.flowId(),
                                base.endpointId(),
                                "INPUT",
                                "派生输入引用");
                    })
                    .distinct()
                    .limit(ITEM_LIMIT)
                    .toList();
            blockers.add(new ImpactGroupDto(
                    "ASSET_DERIVATION_INPUT",
                    SEVERITY_BLOCKER,
                    inputs.size(),
                    "仍有 " + inputs.size() + " 条派生输入引用该资产",
                    items));
        }

        return buildResult("ASSET", assetId, asset.getName(), "DELETE", blockers, warnings);
    }

    public ImpactAnalysisDto analyzeFlowDelete(Long flowId) {
        Flow flow = requireFlow(flowId);
        DataAsset asset = dataAssetMapper.selectById(flow.getAssetId());
        Map<Long, Endpoint> endpoints = loadEndpointMap();
        Endpoint source = endpoints.get(flow.getSourceEndpointId());
        Endpoint target = endpoints.get(flow.getTargetEndpointId());
        String sourceLabel = source == null ? "#" + flow.getSourceEndpointId()
                : EndpointSupport.buildBreadcrumb(source, endpoints);
        String targetLabel = target == null ? "#" + flow.getTargetEndpointId()
                : EndpointSupport.buildBreadcrumb(target, endpoints);
        String label = (asset == null ? "资产#" + flow.getAssetId() : asset.getName())
                + " · " + sourceLabel + " → " + targetLabel;

        List<ImpactGroupDto> blockers = List.of();
        List<ImpactGroupDto> warnings = new ArrayList<>();

        List<FlowPath> paths = flowPathMapper.selectList(
                new LambdaQueryWrapper<FlowPath>().eq(FlowPath::getFlowId, flowId));
        int stepCount = 0;
        if (!paths.isEmpty()) {
            List<Long> pathIds = paths.stream().map(FlowPath::getId).toList();
            stepCount = Math.toIntExact(flowStepMapper.selectCount(
                    new LambdaQueryWrapper<FlowStep>().in(FlowStep::getPathId, pathIds)));
        }

        List<ImpactItemDto> items = List.of(new ImpactItemDto(
                flowId,
                "流向 #" + flowId,
                "FLOW",
                flow.getAssetId(),
                asset == null ? null : asset.getName(),
                flowId,
                null,
                "DELETE",
                paths.size() + " 条路径 · " + stepCount + " 个步骤将一并删除"));
        warnings.add(new ImpactGroupDto(
                "FLOW_DELETE",
                SEVERITY_WARNING,
                1,
                "删除后将移除该流向的全部路径与步骤",
                items));

        if (Boolean.TRUE.equals(flow.getIsPrimary())) {
            long siblingCount = flowMapper.selectCount(new LambdaQueryWrapper<Flow>()
                    .eq(Flow::getAssetId, flow.getAssetId())
                    .ne(Flow::getId, flowId));
            if (siblingCount == 0) {
                warnings.add(new ImpactGroupDto(
                        "FLOW_LAST",
                        SEVERITY_INFO,
                        1,
                        "这是该资产最后一条流向，删除后资产将无任何流向",
                        List.of()));
            } else {
                warnings.add(new ImpactGroupDto(
                        "FLOW_PRIMARY",
                        SEVERITY_INFO,
                        1,
                        "该流向标记为主流向，删除可能影响成图默认展示",
                        List.of()));
            }
        }

        return buildResult("FLOW", flowId, label, "DELETE", blockers, warnings);
    }

    public ImpactAnalysisDto analyzeExecutorDelete(Long executorId) {
        Executor executor = requireExecutor(executorId);
        List<ImpactGroupDto> blockers = new ArrayList<>();

        List<FlowStep> steps = flowStepMapper.selectList(
                new LambdaQueryWrapper<FlowStep>().eq(FlowStep::getExecutorId, executorId));
        if (!steps.isEmpty()) {
            blockers.add(flowStepGroup("EXECUTOR_FLOW_STEP", SEVERITY_BLOCKER,
                    "该程序仍被 " + steps.size() + " 个流向步骤引用",
                    steps));
        }

        List<Derivation> derivations = derivationMapper.selectList(
                new LambdaQueryWrapper<Derivation>().eq(Derivation::getExecutorId, executorId));
        if (!derivations.isEmpty()) {
            blockers.add(derivationGroup(
                    "EXECUTOR_DERIVATION",
                    SEVERITY_BLOCKER,
                    "该程序仍被 " + derivations.size() + " 条派生引用",
                    derivations));
        }

        return buildResult("EXECUTOR", executorId, executor.getName(), "DELETE", blockers, List.of());
    }

    public ImpactAnalysisDto analyzeDerivationDelete(Long derivationId) {
        Derivation derivation = requireDerivation(derivationId);
        List<ImpactGroupDto> blockers = List.of();
        List<ImpactGroupDto> warnings = new ArrayList<>();

        long inputCount = derivationInputMapper.selectCount(
                new LambdaQueryWrapper<DerivationInput>().eq(DerivationInput::getDerivationId, derivationId));
        if (inputCount > 0) {
            warnings.add(new ImpactGroupDto(
                    "DERIVATION_INPUT",
                    SEVERITY_INFO,
                    (int) inputCount,
                    "将同时移除 " + inputCount + " 条派生输入关联",
                    List.of()));
        }

        DataAsset output = dataAssetMapper.selectById(derivation.getOutputAssetId());
        warnings.add(new ImpactGroupDto(
                "DERIVATION_OUTPUT",
                SEVERITY_INFO,
                1,
                "输出资产「" + (output == null ? derivation.getOutputAssetId() : output.getName()) + "」将不再有此派生链路",
                List.of(new ImpactItemDto(
                        derivation.getOutputAssetId(),
                        output == null ? "资产#" + derivation.getOutputAssetId() : output.getName(),
                        "ASSET",
                        derivation.getOutputAssetId(),
                        output == null ? null : output.getName(),
                        null,
                        null,
                        "OUTPUT",
                        null))));

        return buildResult("DERIVATION", derivationId, derivation.getName(), "DELETE", blockers, warnings);
    }

    private void appendChildEndpointBlockers(
            Long endpointId,
            Map<Long, Endpoint> endpoints,
            List<ImpactGroupDto> blockers) {
        List<Endpoint> children = endpointMapper.selectList(
                new LambdaQueryWrapper<Endpoint>().eq(Endpoint::getParentId, endpointId));
        if (children.isEmpty()) {
            return;
        }
        List<ImpactItemDto> items = children.stream()
                .limit(ITEM_LIMIT)
                .map(child -> new ImpactItemDto(
                        child.getId(),
                        EndpointSupport.buildBreadcrumb(child, endpoints),
                        "ENDPOINT",
                        null,
                        null,
                        null,
                        child.getId(),
                        "CHILD",
                        child.getType()))
                .toList();
        blockers.add(new ImpactGroupDto(
                "CHILD_ENDPOINT",
                SEVERITY_BLOCKER,
                children.size(),
                "该落点下仍有 " + children.size() + " 个子落点，请先删除或迁移",
                items));
    }

    private void appendFlowEndpointBlockers(
            Long endpointId,
            String kind,
            String role,
            String severity,
            List<ImpactGroupDto> target,
            List<Flow> flows) {
        if (flows.isEmpty()) {
            return;
        }
        String roleLabel = "SOURCE".equals(role) ? "源" : "目标";
        target.add(flowGroup(kind, severity, flows.size(),
                "仍作为 " + flows.size() + " 条流向的" + roleLabel + "落点",
                flows.stream().map(f -> flowItem(f, role, "流向" + roleLabel)).toList()));
    }

    private void appendFlowStepHostBlockers(Long endpointId, String severity, List<ImpactGroupDto> target) {
        List<FlowStep> steps = flowStepMapper.selectList(
                new LambdaQueryWrapper<FlowStep>().eq(FlowStep::getHostId, endpointId));
        if (steps.isEmpty()) {
            return;
        }
        target.add(flowStepGroup("FLOW_STEP_HOST", severity,
                "仍被 " + steps.size() + " 个流向步骤引用为部署主机",
                steps));
    }

    private void appendExecutorDefaultHostBlockers(Long endpointId, String severity, List<ImpactGroupDto> target) {
        List<Executor> executors = executorMapper.selectList(
                new LambdaQueryWrapper<Executor>().eq(Executor::getDefaultHostId, endpointId));
        if (executors.isEmpty()) {
            return;
        }
        target.add(simpleGroup("EXECUTOR_DEFAULT_HOST", severity,
                "仍被 " + executors.size() + " 个程序/脚本设为默认主机",
                "EXECUTOR",
                executors,
                Executor::getId,
                e -> e.getName() + " (" + e.getCode() + ")",
                null,
                null));
    }

    private void appendDerivationHostBlockers(Long endpointId, String severity, List<ImpactGroupDto> target) {
        List<Derivation> derivations = derivationMapper.selectList(
                new LambdaQueryWrapper<Derivation>().eq(Derivation::getHostId, endpointId));
        if (derivations.isEmpty()) {
            return;
        }
        target.add(derivationGroup(
                "DERIVATION_HOST",
                severity,
                "仍被 " + derivations.size() + " 条派生引用为部署主机",
                derivations));
    }

    private ImpactGroupDto derivationGroup(
            String kind,
            String severity,
            String message,
            List<Derivation> derivations) {
        List<ImpactItemDto> items = derivations.stream()
                .limit(ITEM_LIMIT)
                .map(this::derivationItem)
                .toList();
        return new ImpactGroupDto(kind, severity, derivations.size(), message, items);
    }

    private ImpactItemDto derivationItem(Derivation derivation) {
        DataAsset output = dataAssetMapper.selectById(derivation.getOutputAssetId());
        return new ImpactItemDto(
                derivation.getId(),
                derivation.getName() + " (#" + derivation.getId() + ")",
                "DERIVATION",
                derivation.getOutputAssetId(),
                output == null ? null : output.getName(),
                null,
                null,
                null,
                null);
    }

    private void appendFlowLayoutWarnings(Long endpointId, List<ImpactGroupDto> warnings) {
        List<FlowLayout> layouts = flowLayoutMapper.selectList(
                new LambdaQueryWrapper<FlowLayout>().eq(FlowLayout::getEndpointId, endpointId));
        if (layouts.isEmpty()) {
            return;
        }
        Map<Long, DataAsset> assets = dataAssetMapper.selectBatchIds(
                        layouts.stream().map(FlowLayout::getAssetId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(DataAsset::getId, Function.identity()));
        List<ImpactItemDto> items = layouts.stream()
                .limit(ITEM_LIMIT)
                .map(layout -> {
                    DataAsset asset = assets.get(layout.getAssetId());
                    return new ImpactItemDto(
                            layout.getId(),
                            asset == null ? "资产#" + layout.getAssetId() : asset.getName(),
                            "LAYOUT",
                            layout.getAssetId(),
                            asset == null ? null : asset.getName(),
                            null,
                            endpointId,
                            "LAYOUT",
                            "图布局坐标");
                })
                .toList();
        warnings.add(new ImpactGroupDto(
                "FLOW_LAYOUT",
                SEVERITY_WARNING,
                layouts.size(),
                "删除将影响 " + layouts.size() + " 个资产图上的落点布局",
                items));
    }

    private ImpactGroupDto flowGroup(
            String kind,
            String severity,
            String message,
            List<ImpactItemDto> items) {
        return new ImpactGroupDto(kind, severity, items.size(), message, limitItems(items));
    }

    private ImpactGroupDto flowGroup(
            String kind,
            String severity,
            int count,
            String message,
            List<ImpactItemDto> items) {
        return new ImpactGroupDto(kind, severity, count, message, limitItems(items));
    }

    private ImpactItemDto flowItem(Flow flow, String role, String detail) {
        DataAsset asset = dataAssetMapper.selectById(flow.getAssetId());
        return new ImpactItemDto(
                flow.getId(),
                "流向 #" + flow.getId(),
                "FLOW",
                flow.getAssetId(),
                asset == null ? null : asset.getName(),
                flow.getId(),
                null,
                role,
                detail);
    }

    private ImpactGroupDto flowStepGroup(String kind, String severity, String message, List<FlowStep> steps) {
        Map<Long, FlowPath> paths = flowPathMapper.selectBatchIds(
                        steps.stream().map(FlowStep::getPathId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(FlowPath::getId, Function.identity()));
        Map<Long, Flow> flows = flowMapper.selectBatchIds(
                        paths.values().stream().map(FlowPath::getFlowId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Flow::getId, Function.identity()));
        Map<Long, DataAsset> assets = dataAssetMapper.selectBatchIds(
                        flows.values().stream().map(Flow::getAssetId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(DataAsset::getId, Function.identity()));

        List<ImpactItemDto> items = steps.stream()
                .limit(ITEM_LIMIT)
                .map(step -> {
                    FlowPath path = paths.get(step.getPathId());
                    Flow flow = path == null ? null : flows.get(path.getFlowId());
                    DataAsset asset = flow == null ? null : assets.get(flow.getAssetId());
                    String pathName = path == null ? "路径" : path.getName();
                    return new ImpactItemDto(
                            step.getId(),
                            (asset == null ? "资产?" : asset.getName()) + " / 流向#"
                                    + (flow == null ? "?" : flow.getId()) + " / " + pathName + " 步骤" + step.getSeq(),
                            "FLOW_STEP",
                            flow == null ? null : flow.getAssetId(),
                            asset == null ? null : asset.getName(),
                            flow == null ? null : flow.getId(),
                            step.getHostId(),
                            "HOST",
                            step.getMethod());
                })
                .toList();
        return new ImpactGroupDto(kind, severity, steps.size(), message, items);
    }

    private <T> ImpactGroupDto simpleGroup(
            String kind,
            String severity,
            String message,
            String entityType,
            List<T> rows,
            Function<T, Long> idFn,
            Function<T, String> labelFn,
            Long assetId,
            String assetName) {
        List<ImpactItemDto> items = rows.stream()
                .limit(ITEM_LIMIT)
                .map(row -> new ImpactItemDto(
                        idFn.apply(row),
                        labelFn.apply(row),
                        entityType,
                        assetId,
                        assetName,
                        null,
                        null,
                        null,
                        null))
                .toList();
        return new ImpactGroupDto(kind, severity, rows.size(), message, items);
    }

    private List<ImpactItemDto> limitItems(List<ImpactItemDto> items) {
        if (items.size() <= ITEM_LIMIT) {
            return items;
        }
        return items.subList(0, ITEM_LIMIT);
    }

    private ImpactAnalysisDto buildResult(
            String entityType,
            Long entityId,
            String entityLabel,
            String action,
            List<ImpactGroupDto> blockers,
            List<ImpactGroupDto> warnings) {
        boolean canProceed = blockers.isEmpty();
        String summary = buildSummary(entityLabel, action, canProceed, blockers, warnings);
        return new ImpactAnalysisDto(
                entityType,
                entityId,
                entityLabel,
                action,
                canProceed,
                summary,
                blockers,
                warnings);
    }

    private String buildSummary(
            String entityLabel,
            String action,
            boolean canProceed,
            List<ImpactGroupDto> blockers,
            List<ImpactGroupDto> warnings) {
        if ("UPDATE".equals(action)) {
            int refs = warnings.stream().mapToInt(ImpactGroupDto::count).sum();
            if (refs == 0) {
                return "「" + entityLabel + "」当前无关联引用";
            }
            return "「" + entityLabel + "」关联 " + refs + " 处引用，修改属性不影响关联关系";
        }
        if (canProceed && warnings.isEmpty()) {
            return "「" + entityLabel + "」删除不会影响其它阻塞项";
        }
        if (!canProceed) {
            int n = blockers.stream().mapToInt(ImpactGroupDto::count).sum();
            return "「" + entityLabel + "」删除受阻：仍有 " + n + " 处引用需先处理";
        }
        return "「" + entityLabel + "」可以删除，但请注意 " + warnings.size() + " 项提示";
    }

    private Endpoint requireEndpoint(Long id) {
        Endpoint endpoint = endpointMapper.selectById(id);
        if (endpoint == null) {
            throw new ResourceNotFoundException("落点不存在: " + id);
        }
        return endpoint;
    }

    private DataAsset requireAsset(Long id) {
        DataAsset asset = dataAssetMapper.selectById(id);
        if (asset == null) {
            throw new ResourceNotFoundException("数据资产不存在: " + id);
        }
        return asset;
    }

    private Flow requireFlow(Long id) {
        Flow flow = flowMapper.selectById(id);
        if (flow == null) {
            throw new ResourceNotFoundException("流向不存在: " + id);
        }
        return flow;
    }

    private Executor requireExecutor(Long id) {
        Executor executor = executorMapper.selectById(id);
        if (executor == null) {
            throw new ResourceNotFoundException("程序/脚本不存在: " + id);
        }
        return executor;
    }

    private Derivation requireDerivation(Long id) {
        Derivation derivation = derivationMapper.selectById(id);
        if (derivation == null) {
            throw new ResourceNotFoundException("派生不存在: " + id);
        }
        return derivation;
    }

    private Map<Long, Endpoint> loadEndpointMap() {
        return endpointMapper.selectList(null).stream()
                .collect(Collectors.toMap(Endpoint::getId, Function.identity()));
    }

    private String normalizeEntityType(String entityType) {
        if (entityType == null || entityType.isBlank()) {
            throw new BadRequestException("entityType 不能为空");
        }
        String normalized = entityType.trim().toUpperCase(Locale.ROOT);
        if ("DATA_ASSET".equals(normalized)) {
            return "ASSET";
        }
        return normalized;
    }

    private String normalizeAction(String action) {
        if (action == null || action.isBlank()) {
            return "DELETE";
        }
        String normalized = action.trim().toUpperCase(Locale.ROOT);
        if (!Objects.equals(normalized, "DELETE") && !Objects.equals(normalized, "UPDATE")) {
            throw new BadRequestException("action 仅支持 DELETE 或 UPDATE");
        }
        return normalized;
    }
}
