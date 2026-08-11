package com.binformation.ledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.binformation.ledger.dto.flow.FlowDetailDto;
import com.binformation.ledger.dto.flow.FlowPathDto;
import com.binformation.ledger.dto.flow.FlowPathSaveRequest;
import com.binformation.ledger.dto.flow.FlowSaveRequest;
import com.binformation.ledger.dto.flow.FlowStepDto;
import com.binformation.ledger.dto.flow.FlowStepSaveRequest;
import com.binformation.ledger.dto.flow.FlowSummaryDto;
import com.binformation.ledger.entity.DataAsset;
import com.binformation.ledger.entity.Endpoint;
import com.binformation.ledger.entity.Executor;
import com.binformation.ledger.entity.Flow;
import com.binformation.ledger.entity.FlowPath;
import com.binformation.ledger.entity.FlowStep;
import com.binformation.ledger.exception.BadRequestException;
import com.binformation.ledger.exception.ResourceNotFoundException;
import com.binformation.ledger.mapper.DataAssetMapper;
import com.binformation.ledger.mapper.EndpointMapper;
import com.binformation.ledger.mapper.ExecutorMapper;
import com.binformation.ledger.mapper.FlowMapper;
import com.binformation.ledger.mapper.FlowPathMapper;
import com.binformation.ledger.mapper.FlowStepMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FlowService {

    private static final Set<String> PURPOSES = Set.of(
            "INGEST", "SHARE", "SYNC", "FORWARD", "AUX");
    private static final Set<String> METHODS = Set.of(
            "DIRECT_PUSH", "CROSS_ZONE_PUSH", "CROSS_ZONE_SEND", "CROSS_ZONE_RECV",
            "KAFKA_SUBSCRIBE_FORWARD", "NOTIFY_THEN_PULL", "NOTIFY_THEN_SHARED_READ",
            "SCRIPT_PULL", "STREAM_JOIN", "SFTP_PUSH", "DIR_WATCH_PUSH", "OTHER");
    private static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "INACTIVE", "DEPRECATED");

    private final DataAssetMapper dataAssetMapper;
    private final FlowMapper flowMapper;
    private final FlowPathMapper flowPathMapper;
    private final FlowStepMapper flowStepMapper;
    private final EndpointMapper endpointMapper;
    private final ExecutorMapper executorMapper;
    private final EndpointService endpointService;
    private final ExecutorService executorService;
    private final ChangeLogService changeLogService;

    public FlowService(
            DataAssetMapper dataAssetMapper,
            FlowMapper flowMapper,
            FlowPathMapper flowPathMapper,
            FlowStepMapper flowStepMapper,
            EndpointMapper endpointMapper,
            ExecutorMapper executorMapper,
            EndpointService endpointService,
            ExecutorService executorService,
            ChangeLogService changeLogService) {
        this.dataAssetMapper = dataAssetMapper;
        this.flowMapper = flowMapper;
        this.flowPathMapper = flowPathMapper;
        this.flowStepMapper = flowStepMapper;
        this.endpointMapper = endpointMapper;
        this.executorMapper = executorMapper;
        this.endpointService = endpointService;
        this.executorService = executorService;
        this.changeLogService = changeLogService;
    }

    public List<FlowSummaryDto> listByAsset(Long assetId) {
        requireAsset(assetId);
        Map<Long, Endpoint> endpoints = loadAllEndpoints();
        List<Flow> flows = flowMapper.selectList(
                new LambdaQueryWrapper<Flow>()
                        .eq(Flow::getAssetId, assetId)
                        .orderByAsc(Flow::getId));
        if (flows.isEmpty()) {
            return List.of();
        }
        List<Long> flowIds = flows.stream().map(Flow::getId).toList();
        Map<Long, List<FlowPath>> pathsByFlow = loadPathsByFlow(flowIds);
        Map<Long, List<FlowStep>> stepsByPath = loadStepsByPath(pathsByFlow);

        return flows.stream()
                .map(flow -> toSummary(flow, endpoints, pathsByFlow, stepsByPath))
                .toList();
    }

    public FlowDetailDto getById(Long flowId) {
        Flow flow = requireFlow(flowId);
        DataAsset asset = requireAsset(flow.getAssetId());
        return toDetail(flow, asset.getName());
    }

    @Transactional
    public FlowDetailDto create(Long assetId, FlowSaveRequest request) {
        DataAsset asset = requireAsset(assetId);
        validateSaveRequest(request);
        LocalDateTime now = LocalDateTime.now();

        Flow flow = new Flow();
        flow.setAssetId(assetId);
        applyFlowFields(flow, request);
        flow.setCreatedAt(now);
        flow.setUpdatedAt(now);
        flowMapper.insert(flow);

        savePathsAndSteps(flow.getId(), request.paths(), now);
        changeLogService.record("FLOW", flow.getId(), "CREATE",
                "新建流向: " + flow.getPurpose(), assetId);
        return toDetail(flow, asset.getName());
    }

    @Transactional
    public FlowDetailDto update(Long flowId, FlowSaveRequest request) {
        Flow flow = requireFlow(flowId);
        DataAsset asset = requireAsset(flow.getAssetId());
        validateSaveRequest(request);
        LocalDateTime now = LocalDateTime.now();

        applyFlowFields(flow, request);
        flow.setUpdatedAt(now);
        flowMapper.updateById(flow);

        deletePathsForFlow(flowId);
        savePathsAndSteps(flowId, request.paths(), now);
        changeLogService.record("FLOW", flowId, "UPDATE",
                "更新流向: " + flow.getPurpose(), flow.getAssetId());
        return toDetail(flow, asset.getName());
    }

    @Transactional
    public void delete(Long flowId) {
        Flow flow = requireFlow(flowId);
        flowMapper.deleteById(flowId);
        changeLogService.record("FLOW", flowId, "DELETE",
                "删除流向: " + flow.getPurpose(), flow.getAssetId());
    }

    private void applyFlowFields(Flow flow, FlowSaveRequest request) {
        flow.setSourceEndpointId(request.sourceEndpointId());
        flow.setTargetEndpointId(request.targetEndpointId());
        flow.setPurpose(request.purpose().trim().toUpperCase());
        flow.setIsPrimary(Boolean.TRUE.equals(request.primary()));
        flow.setStatus(request.status().trim().toUpperCase());
        flow.setOwner(request.owner());
        flow.setRemark(request.remark());
    }

    private void validateSaveRequest(FlowSaveRequest request) {
        if (!PURPOSES.contains(request.purpose().trim().toUpperCase())) {
            throw new BadRequestException("无效的流向用途: " + request.purpose());
        }
        if (!STATUSES.contains(request.status().trim().toUpperCase())) {
            throw new BadRequestException("无效的状态: " + request.status());
        }
        endpointService.requireById(request.sourceEndpointId());
        endpointService.requireById(request.targetEndpointId());
        if (Objects.equals(request.sourceEndpointId(), request.targetEndpointId())) {
            throw new BadRequestException("源落点与目标落点不能相同");
        }
        if (request.paths().isEmpty()) {
            throw new BadRequestException("至少需要一个路径");
        }

        for (FlowPathSaveRequest path : request.paths()) {
            if (path.steps().isEmpty()) {
                throw new BadRequestException("路径「" + path.name() + "」至少需要一个步骤");
            }
            Set<Integer> seqs = new HashSet<>();
            for (FlowStepSaveRequest step : path.steps()) {
                if (!METHODS.contains(step.method().trim().toUpperCase())) {
                    throw new BadRequestException("无效的步骤方式: " + step.method());
                }
                if (!seqs.add(step.seq())) {
                    throw new BadRequestException("路径「" + path.name() + "」步骤序号不能重复");
                }
                executorService.requireById(step.executorId());
                if (step.hostId() != null) {
                    endpointService.requireById(step.hostId());
                }
            }
        }
    }

    private void savePathsAndSteps(Long flowId, List<FlowPathSaveRequest> paths, LocalDateTime now) {
        List<FlowPathSaveRequest> sortedPaths = new ArrayList<>(paths);
        sortedPaths.sort(Comparator.comparing(FlowPathSaveRequest::sortOrder));

        for (FlowPathSaveRequest pathReq : sortedPaths) {
            FlowPath path = new FlowPath();
            path.setFlowId(flowId);
            path.setName(pathReq.name().trim());
            path.setEnabled(Boolean.TRUE.equals(pathReq.enabled()));
            path.setSortOrder(pathReq.sortOrder() == null ? 0 : pathReq.sortOrder());
            path.setRemark(pathReq.remark());
            path.setCreatedAt(now);
            path.setUpdatedAt(now);
            flowPathMapper.insert(path);

            List<FlowStepSaveRequest> sortedSteps = new ArrayList<>(pathReq.steps());
            sortedSteps.sort(Comparator.comparing(FlowStepSaveRequest::seq));
            for (FlowStepSaveRequest stepReq : sortedSteps) {
                FlowStep step = new FlowStep();
                step.setPathId(path.getId());
                step.setSeq(stepReq.seq());
                step.setHostId(stepReq.hostId());
                step.setExecutorId(stepReq.executorId());
                step.setMethod(stepReq.method().trim().toUpperCase());
                step.setRemark(stepReq.remark());
                step.setCreatedAt(now);
                step.setUpdatedAt(now);
                flowStepMapper.insert(step);
            }
        }
    }

    private void deletePathsForFlow(Long flowId) {
        List<FlowPath> paths = flowPathMapper.selectList(
                new LambdaQueryWrapper<FlowPath>().eq(FlowPath::getFlowId, flowId));
        if (paths.isEmpty()) {
            return;
        }
        List<Long> pathIds = paths.stream().map(FlowPath::getId).toList();
        flowStepMapper.delete(new LambdaQueryWrapper<FlowStep>().in(FlowStep::getPathId, pathIds));
        flowPathMapper.delete(new LambdaQueryWrapper<FlowPath>().eq(FlowPath::getFlowId, flowId));
    }

    private FlowSummaryDto toSummary(
            Flow flow,
            Map<Long, Endpoint> endpoints,
            Map<Long, List<FlowPath>> pathsByFlow,
            Map<Long, List<FlowStep>> stepsByPath) {
        List<FlowPath> paths = pathsByFlow.getOrDefault(flow.getId(), List.of());
        int stepCount = paths.stream()
                .mapToInt(p -> stepsByPath.getOrDefault(p.getId(), List.of()).size())
                .sum();
        return new FlowSummaryDto(
                flow.getId(),
                flow.getAssetId(),
                flow.getSourceEndpointId(),
                endpointService.labelFor(flow.getSourceEndpointId(), endpoints),
                flow.getTargetEndpointId(),
                endpointService.labelFor(flow.getTargetEndpointId(), endpoints),
                flow.getPurpose(),
                Boolean.TRUE.equals(flow.getIsPrimary()),
                flow.getStatus(),
                flow.getRemark(),
                paths.size(),
                stepCount
        );
    }

    private FlowDetailDto toDetail(Flow flow, String assetName) {
        Map<Long, Endpoint> endpoints = loadAllEndpoints();
        Map<Long, Executor> executors = loadAllExecutors();
        List<FlowPath> paths = flowPathMapper.selectList(
                new LambdaQueryWrapper<FlowPath>()
                        .eq(FlowPath::getFlowId, flow.getId())
                        .orderByAsc(FlowPath::getSortOrder)
                        .orderByAsc(FlowPath::getId));
        Map<Long, List<FlowStep>> stepsByPath = loadStepsByPath(Map.of(flow.getId(), paths));

        List<FlowPathDto> pathDtos = paths.stream()
                .map(path -> {
                    List<FlowStep> steps = new ArrayList<>(stepsByPath.getOrDefault(path.getId(), List.of()));
                    steps.sort(Comparator.comparing(FlowStep::getSeq));
                    List<FlowStepDto> stepDtos = steps.stream()
                            .map(step -> toStepDto(step, endpoints, executors))
                            .toList();
                    return new FlowPathDto(
                            path.getId(),
                            path.getName(),
                            Boolean.TRUE.equals(path.getEnabled()),
                            path.getSortOrder() == null ? 0 : path.getSortOrder(),
                            path.getRemark(),
                            stepDtos
                    );
                })
                .toList();

        return new FlowDetailDto(
                flow.getId(),
                flow.getAssetId(),
                assetName,
                flow.getSourceEndpointId(),
                endpointService.labelFor(flow.getSourceEndpointId(), endpoints),
                flow.getTargetEndpointId(),
                endpointService.labelFor(flow.getTargetEndpointId(), endpoints),
                flow.getPurpose(),
                Boolean.TRUE.equals(flow.getIsPrimary()),
                flow.getStatus(),
                flow.getOwner(),
                flow.getRemark(),
                pathDtos
        );
    }

    private FlowStepDto toStepDto(
            FlowStep step,
            Map<Long, Endpoint> endpoints,
            Map<Long, Executor> executors) {
        Endpoint host = step.getHostId() == null ? null : endpoints.get(step.getHostId());
        Executor executor = executors.get(step.getExecutorId());
        return new FlowStepDto(
                step.getId(),
                step.getSeq(),
                step.getHostId(),
                host == null ? null : host.getName(),
                step.getExecutorId(),
                executor == null ? null : executor.getName(),
                step.getMethod(),
                step.getRemark()
        );
    }

    private Map<Long, List<FlowPath>> loadPathsByFlow(List<Long> flowIds) {
        if (flowIds.isEmpty()) {
            return Map.of();
        }
        return flowPathMapper.selectList(
                        new LambdaQueryWrapper<FlowPath>().in(FlowPath::getFlowId, flowIds))
                .stream()
                .collect(Collectors.groupingBy(FlowPath::getFlowId));
    }

    private Map<Long, List<FlowStep>> loadStepsByPath(Map<Long, List<FlowPath>> pathsByFlow) {
        List<Long> pathIds = pathsByFlow.values().stream()
                .flatMap(List::stream)
                .map(FlowPath::getId)
                .toList();
        if (pathIds.isEmpty()) {
            return Map.of();
        }
        return flowStepMapper.selectList(
                        new LambdaQueryWrapper<FlowStep>().in(FlowStep::getPathId, pathIds))
                .stream()
                .collect(Collectors.groupingBy(FlowStep::getPathId));
    }

    private Map<Long, Endpoint> loadAllEndpoints() {
        return endpointMapper.selectList(null).stream()
                .collect(Collectors.toMap(Endpoint::getId, Function.identity()));
    }

    private Map<Long, Executor> loadAllExecutors() {
        return executorMapper.selectList(null).stream()
                .collect(Collectors.toMap(Executor::getId, Function.identity()));
    }

    private DataAsset requireAsset(Long assetId) {
        DataAsset asset = dataAssetMapper.selectById(assetId);
        if (asset == null) {
            throw new ResourceNotFoundException("数据资产不存在: " + assetId);
        }
        return asset;
    }

    private Flow requireFlow(Long flowId) {
        Flow flow = flowMapper.selectById(flowId);
        if (flow == null) {
            throw new ResourceNotFoundException("流向不存在: " + flowId);
        }
        return flow;
    }
}
