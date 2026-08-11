package com.binformation.ledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.binformation.ledger.dto.flow.ExecutorOptionDto;
import com.binformation.ledger.entity.Endpoint;
import com.binformation.ledger.entity.Executor;
import com.binformation.ledger.mapper.EndpointMapper;
import com.binformation.ledger.mapper.ExecutorMapper;
import com.binformation.ledger.support.EndpointSupport;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ExecutorService {

    private final ExecutorMapper executorMapper;
    private final EndpointMapper endpointMapper;

    public ExecutorService(ExecutorMapper executorMapper, EndpointMapper endpointMapper) {
        this.executorMapper = executorMapper;
        this.endpointMapper = endpointMapper;
    }

    public List<ExecutorOptionDto> listOptions() {
        Map<Long, Endpoint> endpoints = endpointMapper.selectList(null).stream()
                .collect(Collectors.toMap(Endpoint::getId, Function.identity()));
        return executorMapper.selectList(
                        new LambdaQueryWrapper<Executor>().orderByAsc(Executor::getName))
                .stream()
                .map(ex -> toOption(ex, endpoints))
                .toList();
    }

    public Executor requireById(Long id) {
        Executor executor = executorMapper.selectById(id);
        if (executor == null) {
            throw new com.binformation.ledger.exception.ResourceNotFoundException("程序/脚本不存在: " + id);
        }
        return executor;
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
}
