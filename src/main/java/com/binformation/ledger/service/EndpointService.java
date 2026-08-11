package com.binformation.ledger.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.binformation.ledger.dto.flow.EndpointOptionDto;
import com.binformation.ledger.entity.Endpoint;
import com.binformation.ledger.exception.ResourceNotFoundException;
import com.binformation.ledger.mapper.EndpointMapper;
import com.binformation.ledger.support.EndpointSupport;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EndpointService {

    private final EndpointMapper endpointMapper;

    public EndpointService(EndpointMapper endpointMapper) {
        this.endpointMapper = endpointMapper;
    }

    public List<EndpointOptionDto> listOptions(String type) {
        LambdaQueryWrapper<Endpoint> query = new LambdaQueryWrapper<Endpoint>()
                .ne(Endpoint::getType, EndpointSupport.TYPE_SECURITY_ZONE)
                .orderByAsc(Endpoint::getType)
                .orderByAsc(Endpoint::getName);
        if (type != null && !type.isBlank()) {
            query.eq(Endpoint::getType, type.trim());
        }
        Map<Long, Endpoint> all = endpointMapper.selectList(null).stream()
                .collect(Collectors.toMap(Endpoint::getId, Function.identity()));
        return endpointMapper.selectList(query).stream()
                .map(ep -> toOption(ep, all))
                .toList();
    }

    public Endpoint requireById(Long id) {
        Endpoint endpoint = endpointMapper.selectById(id);
        if (endpoint == null) {
            throw new ResourceNotFoundException("落点不存在: " + id);
        }
        return endpoint;
    }

    public String labelFor(Long endpointId, Map<Long, Endpoint> all) {
        if (endpointId == null) {
            return null;
        }
        Endpoint endpoint = all.get(endpointId);
        if (endpoint == null) {
            endpoint = endpointMapper.selectById(endpointId);
        }
        if (endpoint == null) {
            return "#" + endpointId;
        }
        return EndpointSupport.buildBreadcrumb(endpoint, all) + " / " + endpoint.getName();
    }

    private EndpointOptionDto toOption(Endpoint ep, Map<Long, Endpoint> all) {
        Endpoint zone = EndpointSupport.resolveZone(ep, all);
        return new EndpointOptionDto(
                ep.getId(),
                ep.getType(),
                ep.getName(),
                EndpointSupport.buildBreadcrumb(ep, all),
                zone == null ? null : zone.getId(),
                zone == null ? null : zone.getName()
        );
    }
}
