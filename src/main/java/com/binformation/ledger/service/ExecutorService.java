package com.binformation.ledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.binformation.ledger.dto.executor.ExecutorDetailDto;
import com.binformation.ledger.dto.executor.ExecutorSaveRequest;
import com.binformation.ledger.dto.flow.ExecutorOptionDto;
import com.binformation.ledger.entity.Derivation;
import com.binformation.ledger.entity.Endpoint;
import com.binformation.ledger.entity.Executor;
import com.binformation.ledger.entity.FlowStep;
import com.binformation.ledger.exception.BadRequestException;
import com.binformation.ledger.exception.ResourceNotFoundException;
import com.binformation.ledger.mapper.DerivationMapper;
import com.binformation.ledger.mapper.EndpointMapper;
import com.binformation.ledger.mapper.ExecutorMapper;
import com.binformation.ledger.mapper.FlowStepMapper;
import com.binformation.ledger.support.EndpointSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ExecutorService {

    private static final Set<String> KINDS = Set.of("PROGRAM", "SCRIPT");
    private static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "INACTIVE", "DEPRECATED");

    private final ExecutorMapper executorMapper;
    private final EndpointMapper endpointMapper;
    private final FlowStepMapper flowStepMapper;
    private final DerivationMapper derivationMapper;
    private final ChangeLogService changeLogService;

    public ExecutorService(
            ExecutorMapper executorMapper,
            EndpointMapper endpointMapper,
            FlowStepMapper flowStepMapper,
            DerivationMapper derivationMapper,
            ChangeLogService changeLogService) {
        this.executorMapper = executorMapper;
        this.endpointMapper = endpointMapper;
        this.flowStepMapper = flowStepMapper;
        this.derivationMapper = derivationMapper;
        this.changeLogService = changeLogService;
    }

    public List<ExecutorDetailDto> listAll() {
        Map<Long, Endpoint> endpoints = loadEndpoints();
        return executorMapper.selectList(
                        new LambdaQueryWrapper<Executor>().orderByAsc(Executor::getName))
                .stream()
                .map(ex -> toDetail(ex, endpoints))
                .toList();
    }

    public List<ExecutorOptionDto> listOptions() {
        Map<Long, Endpoint> endpoints = loadEndpoints();
        return executorMapper.selectList(
                        new LambdaQueryWrapper<Executor>().orderByAsc(Executor::getName))
                .stream()
                .map(ex -> toOption(ex, endpoints))
                .toList();
    }

    public ExecutorDetailDto getById(Long id) {
        Executor executor = requireById(id);
        return toDetail(executor, loadEndpoints());
    }

    @Transactional
    public ExecutorDetailDto create(ExecutorSaveRequest request) {
        validate(request);
        ensureCodeUnique(request.code().trim(), null);
        if (request.defaultHostId() != null) {
            requireHost(request.defaultHostId());
        }
        LocalDateTime now = LocalDateTime.now();
        Executor executor = new Executor();
        apply(executor, request);
        executor.setCreatedAt(now);
        executor.setUpdatedAt(now);
        executorMapper.insert(executor);
        changeLogService.record("EXECUTOR", executor.getId(), "CREATE",
                "新建程序/脚本: " + executor.getName(), null);
        return toDetail(executor, loadEndpoints());
    }

    @Transactional
    public ExecutorDetailDto update(Long id, ExecutorSaveRequest request) {
        Executor executor = requireById(id);
        validate(request);
        ensureCodeUnique(request.code().trim(), id);
        if (request.defaultHostId() != null) {
            requireHost(request.defaultHostId());
        }
        apply(executor, request);
        executor.setUpdatedAt(LocalDateTime.now());
        executorMapper.updateById(executor);
        changeLogService.record("EXECUTOR", id, "UPDATE",
                "更新程序/脚本: " + executor.getName(), null);
        return toDetail(executor, loadEndpoints());
    }

    @Transactional
    public void delete(Long id) {
        Executor executor = requireById(id);
        Long stepCount = flowStepMapper.selectCount(
                new LambdaQueryWrapper<FlowStep>().eq(FlowStep::getExecutorId, id));
        if (stepCount != null && stepCount > 0) {
            throw new BadRequestException("该程序仍被 " + stepCount + " 个流向步骤引用");
        }
        Long derivCount = derivationMapper.selectCount(
                new LambdaQueryWrapper<Derivation>().eq(Derivation::getExecutorId, id));
        if (derivCount != null && derivCount > 0) {
            throw new BadRequestException("该程序仍被 " + derivCount + " 个派生加工引用");
        }
        executorMapper.deleteById(id);
        changeLogService.record("EXECUTOR", id, "DELETE",
                "删除程序/脚本: " + executor.getName(), null);
    }

    public Executor requireById(Long id) {
        Executor executor = executorMapper.selectById(id);
        if (executor == null) {
            throw new ResourceNotFoundException("程序/脚本不存在: " + id);
        }
        return executor;
    }

    private void validate(ExecutorSaveRequest request) {
        if (!KINDS.contains(request.kind().trim().toUpperCase())) {
            throw new BadRequestException("无效的类型: " + request.kind());
        }
        if (!STATUSES.contains(request.status().trim().toUpperCase())) {
            throw new BadRequestException("无效的状态: " + request.status());
        }
    }

    private void apply(Executor executor, ExecutorSaveRequest request) {
        executor.setName(request.name().trim());
        executor.setCode(request.code().trim());
        executor.setKind(request.kind().trim().toUpperCase());
        executor.setDefaultHostId(request.defaultHostId());
        executor.setStatus(request.status().trim().toUpperCase());
        executor.setOwner(request.owner());
        executor.setRemark(request.remark());
    }

    private void ensureCodeUnique(String code, Long excludeId) {
        Executor existing = executorMapper.selectOne(
                new LambdaQueryWrapper<Executor>().eq(Executor::getCode, code));
        if (existing != null && (excludeId == null || !existing.getId().equals(excludeId))) {
            throw new BadRequestException("程序编码已存在: " + code);
        }
    }

    private void requireHost(Long hostId) {
        Endpoint host = endpointMapper.selectById(hostId);
        if (host == null || !"HOST".equals(host.getType())) {
            throw new BadRequestException("默认部署主机必须是 HOST 类型落点");
        }
    }

    private ExecutorDetailDto toDetail(Executor ex, Map<Long, Endpoint> endpoints) {
        Endpoint host = ex.getDefaultHostId() == null ? null : endpoints.get(ex.getDefaultHostId());
        return new ExecutorDetailDto(
                ex.getId(),
                ex.getName(),
                ex.getCode(),
                ex.getKind(),
                ex.getDefaultHostId(),
                host == null ? null : EndpointSupport.buildBreadcrumb(host, endpoints) + " / " + host.getName(),
                ex.getStatus(),
                ex.getOwner(),
                ex.getRemark()
        );
    }

    private ExecutorOptionDto toOption(Executor ex, Map<Long, Endpoint> endpoints) {
        Endpoint host = ex.getDefaultHostId() == null ? null : endpoints.get(ex.getDefaultHostId());
        return new ExecutorOptionDto(
                ex.getId(),
                ex.getName(),
                ex.getCode(),
                ex.getKind(),
                ex.getDefaultHostId(),
                host == null ? null : EndpointSupport.buildBreadcrumb(host, endpoints)
        );
    }

    private Map<Long, Endpoint> loadEndpoints() {
        return endpointMapper.selectList(null).stream()
                .collect(Collectors.toMap(Endpoint::getId, Function.identity()));
    }
}
